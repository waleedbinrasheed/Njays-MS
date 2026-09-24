# Architecture & Implementation Plan

Single-business inventory system for a book shop. **There is no branch concept anywhere** —
one company-wide inventory, one stock location.

## Runtime topology (LAN, no Docker)

```
 Users' browsers (LAN)
        │  http://<server-ip>:3000
        ▼
 Next.js server  (0.0.0.0:3000)      pages + proxy: /api/*  ──►  Spring Boot (127.0.0.1:8080)
                                                                        │ JDBC
                                                                        ▼
                                                               PostgreSQL 17 (localhost:5432)
```

- Browsers only ever talk to Next.js. `/api/*` is rewritten to the backend, so the session
  cookie and CSRF cookie are same-origin and the API port is not exposed on the LAN.
- Auth: server-side HTTP session, `IMS_SESSION` cookie (HttpOnly, SameSite=Lax), idle timeout
  `SESSION_TIMEOUT` (default 30m). CSRF: `XSRF-TOKEN` cookie → `X-XSRF-TOKEN` header.
- Every authorization decision is made in the backend: coarse URL rules in `SecurityConfig`
  (checked before request bodies are read) plus `@PreAuthorize` permission checks on controllers.
- `SessionUserRefreshFilter` reloads the user on every request, so deactivation and role changes
  take effect immediately, and a pending password change blocks everything except the
  password-change endpoints.
- No privilege escalation: a user can only assign roles, edit users, or edit roles whose
  permissions they hold themselves. At least one active ADMIN always remains.
- Login verifies the password before revealing that an account is inactive; unknown user and
  wrong password return the same message. A new session id is issued on login and the CSRF token
  is rotated.

## Backend (`backend/`, package `com.inventory`)

| Package | Responsibility |
|---|---|
| `config` | Spring configuration (security, clock/timezone, JPA auditing) |
| `controller` | Thin REST controllers: validate input DTO, call service, return DTO |
| `service` | All business rules and transaction boundaries |
| `repository` | Spring Data JPA repositories and report queries |
| `entity` | JPA entities (never returned from controllers) |
| `dto` | Request/response records |
| `mapper` | Entity ⇄ DTO mapping (hand-written, no magic) |
| `exception` | `ApiError`, `BusinessException`, `GlobalExceptionHandler` |
| `security` | User details, login/logout handlers, JSON 401/403 |
| `validation` | Custom Bean Validation constraints |

### Inventory integrity design (the most important rule)

- One class, `InventoryService`, is the **only** code allowed to change `inventory.quantity`.
  Purchases, sales, returns, cancellations and adjustments all call
  `applyMovement(productId, type, signedQty, referenceType, referenceId, notes)`.
- Inside one DB transaction it: locks the inventory row (`SELECT … FOR UPDATE`), validates
  (product active, resulting qty ≥ 0), updates quantity, and inserts a `stock_transaction`
  with previous / change / resulting quantity, user and timestamp.
- Safety nets in the schema: `CHECK (quantity >= 0)`, `CHECK (resulting = previous + quantity)`
  on stock transactions, FKs everywhere, unique `inventory.product_id`.
- Multi-line documents lock rows in product-id order to avoid deadlocks.
- Transaction types: `PURCHASE`, `SALE`, `SALE_RETURN`, `PURCHASE_RETURN`, `ADJUSTMENT`,
  `PURCHASE_CANCEL`, `SALE_CANCEL`.
- Saved sales/purchases are immutable. Cancelling writes reversing movements; returns do
  partial reversals referencing the original document line.
- `InventoryService` methods are `Propagation.MANDATORY`: they refuse to run unless the calling
  business operation already opened a transaction, so a stock change can never commit on its own.
- Stock adjustments carry the quantity the user saw (`expectedCurrentQuantity`); if stock moved in
  the meantime the adjustment is rejected (409) instead of being applied to an unseen number.
- There is deliberately no API that sets a quantity directly. Reorder level (stock-row setting,
  permission `STOCK_ADJUST`) is the only editable inventory field.
- `InventoryConcurrencyIT` commits real data from parallel threads and checks: no overselling,
  no lost updates, all-or-nothing multi-line documents, and `quantity = SUM(ledger)` for every product.

### Other design decisions

- **Products:** table `product` with `product_type` (`BOOK` now). Book attributes (ISBN, author,
  edition, publication year) are nullable columns so non-book products can be added later
  without redesign. UI labels it "Books/Products". ISBN unique when present, stored without
  hyphens/spaces; only the format (10 or 13 digits, ISBN-10 may end in X) is checked, not the
  check digit, because some locally printed books carry ISBNs with a wrong check digit.
- **Master data lifecycle:** records are deactivated rather than deleted once used. Delete only
  succeeds for records nothing references (FK-enforced). Inactive publishers/categories stay on
  existing products but cannot be chosen for new ones. Updates carry `version`; a stale version
  returns 409 so one user's edit never silently overwrites another's.
- **Search:** product search matches ISBN (ignoring hyphens), title and author, backed by
  `pg_trgm` GIN indexes. All pickers query the API (type-to-search), never load full lists.
- **Reorder level** lives only on `inventory`.
- **Roles & permissions:** `app_user`, `role`, `user_role`, plus `permission` and
  `role_permission`. Code checks permissions (e.g. `SALE_CREATE`), not role names, so new roles
  are pure data. Seeded roles: `ADMIN`, `NORMAL_USER`.
- **Document numbers:** `INV-2026-000001` (sales) and `PUR-2026-000001` (purchases), generated from a
  `document_sequence` table row-locked per (type, year) — gap-free and race-safe.
- **Money:** `NUMERIC(12,2)`, PKR. Line: `qty × unit price − line discount % + line tax %`.
  Header: optional fixed discount on the whole invoice. Payment status: `PAID/UNPAID/PARTIAL`.
- **Purchases:** one line per product; supplier and products must be active; date not in the future
  (shop time zone). Save = number + document + stock movements in one transaction. Only payment status
  can change afterwards. Cancel requires a reason and reverses the stock; it is refused if that stock
  has already been used. Product purchase prices are not changed automatically by purchases.
- **Sales:** same document rules as purchases; customer defaults to the walk-in customer and must be
  active; unit price must be above zero. Stock is checked while each product row is locked, so two
  cashiers cannot sell the same last copy (`SaleConcurrencyIT`). Cancelling returns all stock and is
  always possible; partial returns are handled by Returns (Phase 7).
- **Returns:** separate numbered documents (`SRT-…` sales returns, `PRT-…` purchase returns), each line
  linked to the original invoice/purchase line. The original document row is locked while a return is
  recorded, so concurrent returns can never exceed the quantity on it (`ReturnConcurrencyIT`). Amount per
  line = share of the original line total (after line discount/tax); the last units get the exact
  remainder so refunds never exceed what was charged; the invoice-level discount is not apportioned.
  Returns are immutable. Cancelled documents cannot be returned, and a document with returns can no
  longer be cancelled (return the rest instead). Purchase returns are refused if the stock is gone.
- **Numbering:** `document_sequence` upsert inside the document transaction; the year is the year the
  document is entered. `PurchaseConcurrencyIT` proves concurrent saves get consecutive unique numbers
  and a failed save consumes none.
- **Walk-in customer:** seeded customer record used as the default on new sales.
- **Time:** stored as `timestamptz` (UTC); "today/this month" computed in `Asia/Karachi`.
- **Audit:** `created_by/created_at/updated_by/updated_at` on master data and documents.

## Frontend (`frontend/`, Next.js App Router, TypeScript, Tailwind)

| Folder | Responsibility |
|---|---|
| `app/` | Routes and layouts only |
| `components/` | Reusable UI (tables, dialogs, forms, toasts, pagination) |
| `features/` | Feature screens composed from components (sales, purchases, …) |
| `services/` | Backend calls (`apiClient` handles cookies, CSRF, errors, 401) |
| `hooks/` | Data-fetching and UI state hooks |
| `types/` | DTO type definitions |
| `utils/` | Formatting (PKR, dates) and helpers |

Business rules are never implemented in React components; the UI only reflects backend results.

## Phases

| # | Phase | Deliverables | Verified by |
|---|---|---|---|
| 1 | Project setup | Spring Boot + Next.js skeletons, Flyway, security foundation, error format, DB scripts | Backend compiles + tests, frontend builds, app boots against Postgres |
| 2 | Authentication | Users/roles/permissions migrations, login/logout/me, session timeout, last-login, inactive-user block, user & role admin screens, login page + route guard | Login, expiry, role authorization tests |
| 3 | Master data | Products, publishers, categories, suppliers, customers: CRUD, search, paging, soft-deactivate, duplicate checks | Duplicate ISBN/category tests |
| 4 | Inventory | Inventory + stock transactions, `InventoryService`, adjustments, history, low-stock | Consistency + concurrency tests |
| 5 | Purchases | Purchase entry, numbering, history, details, printable invoice, cancel | Purchase → stock tests |
| 6 | Sales | Sale entry with stock lookup, numbering, invoice, history, cancel | Sale reduces stock, insufficient stock tests |
| 7 | Returns | Sales & purchase returns against original lines | Return quantity limits, stock tests |
| 8 | Dashboard & reports | Dashboard KPIs, 5 reports with filters + paging | Report query tests |
| 9 | Testing & security review | End-to-end workflow checks, authz sweep of every endpoint | Full test suite |
| 10 | Local deployment | Production builds, Windows service scripts, backup script, LAN instructions | Clean-machine run-through |

Each phase ends with: backend compile + tests, frontend build, workflow check, security review,
and a logical git commit.
