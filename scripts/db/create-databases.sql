-- One-time setup: creates the application login and its two databases.
-- Run as the PostgreSQL superuser, choosing your own password for the app login:
--
--   psql -U postgres -v app_password='choose-a-strong-password' -f scripts/db/create-databases.sql
--
-- Then put the same password in backend/application-local.yml.
-- Tables are NOT created here; Flyway creates them when the backend starts.

CREATE ROLE inventory_app WITH LOGIN PASSWORD :'app_password';

CREATE DATABASE inventory      OWNER inventory_app ENCODING 'UTF8';
CREATE DATABASE inventory_test OWNER inventory_app ENCODING 'UTF8';
