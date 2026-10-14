# Inventory Management System

Inventory, purchase and sales management for a single book business, used over the local network.
See [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) for the design and phase plan.
A free cloud demo for client review can be set up with [docs/DEPLOY-RENDER.md](docs/DEPLOY-RENDER.md).

| Part | Tech |
|---|---|
| Backend | Java 21, Spring Boot 3.5, Spring Security, JPA/Hibernate, Flyway, Maven |
| Frontend | Next.js 16, React 19, TypeScript, Tailwind CSS |
| Database | PostgreSQL 17 |

## Prerequisites

- JDK 21 or newer (`JAVA_HOME` pointing at it). The code targets Java 21.
- Node.js 20.9+ and npm
- PostgreSQL 17 running locally

## One-time database setup

```bash
psql -U postgres -v app_password='choose-a-strong-password' -f scripts/db/create-databases.sql
```

Then create `backend/application-local.yml` (git-ignored) from the example and set the same password:

```bash
cp backend/application-local.example.yml backend/application-local.yml
```

Environment variables (`DB_HOST`, `DB_PORT`, `DB_USERNAME`, `DB_PASSWORD`) work instead of the file.

## First sign-in

On first start against an empty database the backend creates the administrator `admin` and prints a
**temporary password** in the backend console, inside a box titled "First administrator created".
Sign in with it; you will be asked to choose a new password before anything else.

To choose the temporary password yourself, set it before the first start in `application-local.yml`:

```yaml
app:
  bootstrap:
    admin-password: SomeTemp123
```

(or the `ADMIN_INITIAL_PASSWORD` environment variable). It is only used when no users exist.

## Business details on invoices

Printed purchase and sales invoices show your shop's name and contact details. Set them in
`backend/application-local.yml` (then restart the backend):

```yaml
app:
  business:
    name: Leading Publishers
    address: "Shop 12, Main Market, Lahore"
    phone: "042-1234567"
    email: shop@example.com
```

## Run (development)

Backend (http://127.0.0.1:8080, Flyway migrates the schema on startup):

```bash
cd backend && ./mvnw spring-boot:run
```

Frontend (http://localhost:3000, proxies `/api` to the backend):

```bash
cd frontend && npm install && npm run dev
```

## Build & test

```bash
cd backend && ./mvnw test        # unit / web-layer tests (no database needed)
cd backend && ./mvnw verify      # + integration tests against the inventory_test database
cd frontend && npm run lint && npm run typecheck && npm run build
```

## Configuration

| Variable | Default | Purpose |
|---|---|---|
| `DB_HOST` / `DB_PORT` / `DB_NAME` | `localhost` / `5432` / `inventory` | Database location |
| `DB_USERNAME` / `DB_PASSWORD` | `inventory_app` / — | Database login |
| `APP_TIMEZONE` | `Asia/Karachi` | "Today", months and date filters |
| `BUSINESS_NAME` (and `_ADDRESS`, `_PHONE`, `_EMAIL`) | `Leading Publishers` | Invoice header |
| `SESSION_TIMEOUT` | `30m` | Inactivity logout (e.g. `15m`, `1h`) |
| `ADMIN_INITIAL_PASSWORD` | random | Temporary password for the first `admin` (empty database only) |
| `SERVER_ADDRESS` / `SERVER_PORT` | `127.0.0.1` / `8080` | Backend bind (keep on localhost) |
| `SESSION_COOKIE_SECURE` | `false` | Set `true` if served over HTTPS |
| `BACKEND_URL` (frontend) | `http://localhost:8080` | Where Next.js proxies `/api` |
