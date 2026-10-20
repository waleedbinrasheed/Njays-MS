# Deploying on the client's Windows PC with Docker (offline)

The client PC has no internet, so releases are built on a machine that has Docker and internet,
copied over (USB) and loaded there. The client never builds anything.

```
Office PCs (browser) ──► http://<server-pc-ip>  (port 80)
                          └─ web  (Next.js, the only published port)
                               └─ api  (Spring Boot, internal)
                                    └─ db  (PostgreSQL 17, internal, data in a Docker volume)
                          backup (daily pg_dump into C:\LeadingIMS\backups, kept 14 days)
```

| File | Purpose |
|---|---|
| `backend/Dockerfile`, `frontend/Dockerfile` | Multi-stage builds (JDK 21 / Node 20), small runtime images, non-root users, health checks |
| `deploy/docker-compose.yml` | The four services; settings from `.env`; only the web port is published |
| `deploy/docker-compose.build.yml` | Adds the build sections; used only by the release scripts |
| `deploy/.env.example` | Settings template (passwords, invoice header, port, time zone, backup schedule) |
| `deploy/scripts/backup.sh`, `restore.sh` | Run inside the `backup` container |
| `deploy/windows/*.bat` | Client helpers: install, start, stop, status, logs, backup-now, restore, update, open-firewall |
| `deploy/build-release.bat` / `.sh` | Builds a release zip on the build machine |
| `deploy/INSTALL.txt` | Step-by-step instructions shipped inside the release |

## 1. Build a release (machine with Docker and internet)

1. Set the version in `deploy/VERSION` (e.g. `1.0.0`; raise it for every release).
2. Windows: `deploy\build-release.bat`. Linux/macOS: `bash deploy/build-release.sh`
   (on an ARM Mac set `DOCKER_DEFAULT_PLATFORM=linux/amd64` first, because the client PC is x86-64).
3. Result: `release/LeadingIMS-<version>.zip` (about 300-400 MB; the images are inside).

Quick check on the build machine before shipping:

```bash
cd release/LeadingIMS-1.0.0
copy .env.example .env      # set DB_PASSWORD and ADMIN_INITIAL_PASSWORD
docker compose up -d
```

Then open http://localhost, sign in as `admin`, and run `docker compose down -v` when done (`-v`
deletes the test database).

## 2. Install at the client

Follow `INSTALL.txt` in the release. In short:

1. Install Docker Desktop (installer on USB) and tick **Start Docker Desktop when you sign in**.
2. Copy the release folder to `C:\LeadingIMS`.
3. Run `install.bat` **as administrator**. The first run creates `.env` and opens it; set the
   passwords and business details, then run it again. It loads the images, starts everything,
   opens port 80 in Windows Firewall (private/domain networks only) and prints the address.
4. Give the office PCs `http://<server-pc-ip>`. First sign-in: `admin` + `ADMIN_INITIAL_PASSWORD`.

Notes:

- Give the server PC a **fixed IP** (DHCP reservation on the router) so the address never changes.
- Docker Desktop starts only after someone signs in to Windows. On a dedicated PC, enable Windows
  automatic sign-in for that account, or remember to sign in after every restart.
- Port 80 must be free. If something else uses it (IIS, Skype, another web server), set
  `HTTP_PORT=8080` in `.env` and use `http://<ip>:8080`.
- Docker Desktop is free for small businesses (fewer than 250 employees and under US$10 million
  annual revenue); check the current Docker subscription terms for the client.

## 3. Updates

Build a new release with a higher `VERSION`, copy its files over `C:\LeadingIMS` (overwrite; `.env`
and `backups` are not in a release) and run `update.bat`: it takes a backup, loads the new images
and restarts. Flyway applies database changes on start.

## 4. Backups and restore

- `backup` container: every day, the first time the PC is on after `BACKUP_HOUR` (default 2:00),
  writes `backups\leadingims-YYYY-MM-DD_HHMM.dump`; files older than `BACKUP_KEEP_DAYS` (14) are
  deleted. `backup-now.bat` makes one on demand.
- The backups folder is on the same disk, so copy the newest file elsewhere (USB, another PC)
  regularly.
- `restore.bat` stops the app, takes a safety backup, restores the chosen file
  (`pg_restore --clean --single-transaction`) and starts again.

## Security notes

- Only port 80 is published; the API (8080) and PostgreSQL (5432) are not reachable from the LAN.
- `.env` holds the database password; it is git-ignored and is never part of a release.
- The session cookie is not marked Secure because the office network uses plain HTTP. Everything
  else (CSRF, session timeout, sign-in lockout, permission checks) is unchanged.
