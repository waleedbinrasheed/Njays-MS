# Inventory Management System

Inventory, purchase and sales management for a single book business, used over the local network.
See [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) for the design and phase plan.

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

Environment variables `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` work instead of the file.

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
| `DB_URL` | `jdbc:postgresql://localhost:5432/inventory` | Database |
| `DB_USERNAME` / `DB_PASSWORD` | `inventory_app` / — | Database login |
| `SESSION_TIMEOUT` | `30m` | Inactivity logout |
| `SERVER_ADDRESS` / `SERVER_PORT` | `127.0.0.1` / `8080` | Backend bind (keep on localhost) |
| `SESSION_COOKIE_SECURE` | `false` | Set `true` if served over HTTPS |
| `BACKEND_URL` (frontend) | `http://localhost:8080` | Where Next.js proxies `/api` |
