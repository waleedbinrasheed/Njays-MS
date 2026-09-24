# Client-review demo on Render + Neon (free, no Docker)

This hosts a **demo copy** for client review. The real installation is still the local-network
setup in the README. Nothing secret is stored in the repository: passwords and URLs are entered in
the Render dashboard.

```
Client's browser ──HTTPS──► leadingims-web (Next.js, Render)  ──/api proxy──►  leadingims-api (Spring Boot, Render)
                                                                                   │ JDBC + SSL
                                                                                   ▼
                                                                          Neon PostgreSQL (free)
```

**Free-tier behaviour to tell the client:** Render stops free services after ~15 minutes without
visits. The first page load after that can take about a minute while both services wake up; after
that it is fast. The database (Neon) keeps its data.

---

## 1. Neon: create the database

1. In Neon, **New Project** → name `leadingims`, region **AWS Asia Pacific (Singapore)** (closest to Render Singapore).
2. Open **Connect** (Connection details). Turn **Connection pooling OFF** (use the *direct* connection;
   Flyway migrations need it).
3. You will see a string like:
   ```
   postgresql://neondb_owner:AbC123xyz@ep-cool-name-123456.ap-southeast-1.aws.neon.tech/neondb?sslmode=require
   ```
   Split it into three values for Render:

   | Render variable | Value from the example |
   |---|---|
   | `DB_URL` | `jdbc:postgresql://ep-cool-name-123456.ap-southeast-1.aws.neon.tech/neondb?sslmode=require` |
   | `DB_USERNAME` | `neondb_owner` |
   | `DB_PASSWORD` | `AbC123xyz` |

   (`DB_URL` = `jdbc:` + the part after `@`, with `postgresql://` in front of the host.)

   Shortcut: you may also paste Neon's string **exactly as shown** (`postgresql://user:password@host/db?...`)
   into `DB_URL`; the backend converts it and takes the user and password from it when
   `DB_USERNAME` / `DB_PASSWORD` are empty. Setting the three values separately is still preferred,
   because then the password is not part of the URL.

The tables are created automatically by the backend on its first start.

## 2. Render: create both services from the Blueprint

1. Render dashboard → **New** → **Blueprint** → connect GitHub → choose **waleedbinrasheed/LeadingIMS**.
2. Render reads `render.yaml` and proposes two free web services: `leadingims-api` and `leadingims-web`.
3. It asks for the values marked secret:

   | Service | Variable | What to enter |
   |---|---|---|
   | leadingims-api | `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` | from step 1 |
   | leadingims-api | `ADMIN_INITIAL_PASSWORD` | a strong temporary password, e.g. 12+ characters with letters and numbers |
   | leadingims-api | `BUSINESS_NAME` | shop name for invoices (address/phone can be added later as `BUSINESS_ADDRESS`, `BUSINESS_PHONE`) |
   | leadingims-web | `BACKEND_URL` | leave `https://leadingims-api.onrender.com` for now (fix in step 3 if Render gave a different URL) |

4. **Apply**. The API build downloads Java 21 and compiles (about 3–6 minutes the first time).

## 3. Connect the frontend to the API

1. Open the **leadingims-api** service and copy its URL at the top (e.g. `https://leadingims-api.onrender.com`,
   or `https://leadingims-api-xxxx.onrender.com` if the name was taken).
2. Open **leadingims-web** → **Environment** → set `BACKEND_URL` to that URL (no trailing slash) → **Save, rebuild and deploy**.
   `BACKEND_URL` is read when the frontend is **built**, so it must be rebuilt after changing it.

## 4. First sign-in

1. Open the **leadingims-web** URL (this is the link for the client).
2. Sign in as `admin` with your `ADMIN_INITIAL_PASSWORD`; you will be asked to set a new password.
3. Create a user for the client under **Administration → Users** (temporary password; they change it on first sign-in).

## Checking it works

- `https://<api-url>/api/health` → `{"status":"UP"}`
- API **Logs** show `Successfully applied 9 migrations` on the first start and `Started InventoryApplication`.

## Updating the demo

Every `git push` to `main` redeploys both services automatically (Render auto-deploy).

## Troubleshooting

| Symptom | Fix |
|---|---|
| Login page shows "The server is unavailable" | The API is still waking up or failed to start: check **leadingims-api → Logs**. |
| API log: `Connection to ... refused` / `password authentication failed` | Re-check `DB_URL` / `DB_USERNAME` / `DB_PASSWORD` (direct connection, `?sslmode=require`). |
| Every page says "Authentication required" right after signing in | `BACKEND_URL` is wrong or the frontend was not rebuilt after changing it. |
| API restarts with "out of memory" | Free instances have 512 MB; memory is already tuned in `backend/scripts/render-start.sh`. Upgrade the plan if data grows large. |

## Security notes for the demo

- Use a demo-only admin password, not one used anywhere else.
- The API URL is public (free Render services cannot be private), but every endpoint except
  `/api/health` requires a signed-in session, and state-changing calls require the CSRF token.
- Do not load real customer data into the demo.
