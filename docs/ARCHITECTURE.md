# Architecture

Tailoring shop management: customers, garment orders, fabric stock and body measurements. **There is no branch concept anywhere** - one shop, one stock location.


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
- Password guessing is throttled: 5 wrong passwords for a username lock that username for 15 minutes
  (HTTP 429, checked before the password; in memory, cleared by a successful sign-in).
- `EndpointSecurityIT` discovers every API endpoint at runtime and checks that anonymous callers get
  401, a user whose role has no permissions gets 403 (before any body is read), and state-changing
  requests without a CSRF token are refused. A new endpoint without a permission rule fails the build.
- Pages from Next.js carry X-Frame-Options, nosniff, Referrer-Policy, Permissions-Policy and COOP headers.

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
### Fabric stock integrity (the most important rule)

- One class, `InventoryService`, is the **only** code allowed to change `inventory.quantity`.
  Order issue/cancel and manual adjustments all go through `applyAll(List<Movement>)`.
- Inside one DB transaction it locks the stock row (`SELECT … FOR UPDATE`), validates (fabric product,
  active product for new cuts, resulting quantity ≥ 0), updates the quantity and inserts a
  `stock_transaction` with previous / change / resulting quantity, user and timestamp.
- Quantities are decimal metres with 3 places (`NUMERIC(12,3)`). Inputs with more decimals are rejected.
- Safety nets in the schema: `CHECK (quantity >= 0)`, `CHECK (resulting = previous + quantity)`, FKs,
  unique `inventory.product_id`. Multi-line operations lock rows in product-id order.
- Transaction types: `ADJUSTMENT` (incl. stock-in, reason `STOCK_IN`), `ORDER_ISSUE` (−), `ORDER_CANCEL` (+).
  Older book-era types remain in the schema for history only.
- Methods are `Propagation.MANDATORY`: a stock change can never commit on its own.
- Adjustments carry the quantity the user saw (`expectedCurrentQuantity`); a changed quantity is a 409.
- Order edits move stock by the **net** change per fabric, so one check runs on the final figure.
- There is deliberately no API that sets a quantity directly. Reorder level (`STOCK_ADJUST`) is the only
  other editable inventory field.

### Other design decisions

- **Fabrics:** rows of `product` with `product_type = 'FABRIC'`. `title` is the fabric name,
  `purchase_price` the cost per metre, `selling_price` the price per metre. `fabric_code` is unique
  (case-insensitive). Creating a fabric creates its stock row at zero. Legacy `BOOK` rows are not mapped
  and never shown.
- **Customers:** people who order garments. Walk-in rows (legacy) are excluded from search, pickers and
  orders. Two active customers may not share name and phone. Delete only when nothing references them.
- **Garment types and measurement points:** a garment type has a silhouette (`TOP`, `BOTTOM`, `FULL`) and
  ordered points, measured in inches. Removing a point deletes it if unused, otherwise deactivates it so old
  records keep their labels and new forms hide it.
- **Measurement records:** taken per customer, per garment type, per day. Never edited or deleted; a
  changed measurement is a new record. Values carry snapshots of the point label and order.
- **Orders:** numbered `ORD-2026-000001` (`document_sequence`, gap-free). Table `orders`.
  - Line prices are copied from the fabric when the order is saved; later price changes never alter it.
  - Totals are computed on the server: fabric amount = metres × price (rounded to 2 decimals), line total =
    fabric amount + stitching charge, order total = sum of line totals.
  - Customer-supplied cloth ("CUSTOMER" lines) carries no fabric, no metres and no stock movement.
  - Measurements are copied from the chosen record onto the line when the order is saved (and again on edit).
  - Status: `RECEIVED → CUTTING → STITCHING → TRIAL → READY → DELIVERED`, one step at a time in either
    direction; `CANCELLED` only through cancel. Every change writes `order_status_history`.
  - Edits are allowed only in `RECEIVED`, `CUTTING` and `STITCHING`.
  - Cancel returns all stock-line fabric and is refused once delivered, or when any payment exists
    (refunds are handled outside the system).
  - Payments (`ADVANCE`, `PAYMENT`) are append-only; the amount may not exceed the balance due. Payment status
    is derived: `UNPAID`, `PARTIAL`, `PAID`.
- **Calendar:** orders with a due, trial or delivery date in a range of at most 93 days, cancelled excluded.
- **Money:** `NUMERIC(14,2)`, PKR. Metres `NUMERIC(12,3)`. Dates are shop-local (`Asia/Karachi`); timestamps
  are `timestamptz` (UTC).
- **Roles & permissions:** `app_user`, `role`, `user_role`, `permission` and `role_permission`. Code checks
  permissions, not role names. Seeded roles: `ADMIN` (all permissions), `NORMAL_USER` (day-to-day set).
- **Master data lifecycle:** records are deactivated rather than deleted once used. Updates carry `version`;
  a stale version returns 409 so one user's edit never silently overwrites another's.
- **Reports & dashboard:** computed live with JPA queries. Report totals cover all matching rows, not just
  the page. Cancelled orders are listed but excluded from totals. The dashboard returns only the sections the
  viewer may see (order figures need `ORDER_VIEW`, low-stock count needs `INVENTORY_VIEW`); other sections are null.
- **Audit:** `created_by/created_at/updated_by/updated_at` on master data and orders.

## Tailoring shop (current scope)

Main menu: **Dashboard, Orders, Calendar**. Master data: **Customers, Fabrics, Garment types, Measurements**.
Inventory: **fabric stock, stock movements, stock-in and adjustments**. Reports: **Orders** and fabric stock.
Administration (users, roles, permissions, settings) is unchanged.

Permissions: `DASHBOARD_VIEW`, `ORDER_VIEW`, `ORDER_MANAGE`, `MASTER_DATA_VIEW`, `MASTER_DATA_MANAGE`,
`INVENTORY_VIEW`, `STOCK_ADJUST`, `REPORT_VIEW`, plus the administration codes.

Book-shop features (sales, purchases, returns, samples, representatives, publishers, categories, suppliers
and the book catalogue) were removed from the code. Their tables and data are kept by migration `V13`; the
application no longer maps them.

## Frontend (`frontend/`, Next.js App Router, TypeScript, Tailwind)

| Folder | Responsibility |
|---|---|
| `app/` | Routes and layouts only |
| `components/` | Reusable UI (tables, dialogs, forms, toasts, pagination) |
| `features/` | Feature screens composed from components (orders, fabrics, customers, …) |
| `services/` | Backend calls (`apiClient` handles cookies, CSRF, errors, 401) |
| `hooks/` | Data-fetching and UI state hooks |
| `types/` | DTO type definitions |
| `utils/` | Formatting (PKR, dates) and helpers |

Business rules are never implemented in React components; the UI only reflects backend results.

