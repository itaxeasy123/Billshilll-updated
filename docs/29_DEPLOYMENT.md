# 29. Deployment & Infrastructure

## Android Application
- Min SDK: 24 (Android 7.0 Nougat).
- Target SDK / Compile SDK: 34 (Android 14).
- JVM Target: 17.
- ProGuard / R8 rules preserving Room entities, serializers, and crypto interfaces.

## Server (`server/`, Phase 6)
- Local development / this build environment: `DATABASE_URL` defaults to SQLite
  (`sqlite+aiosqlite:///./ledgerprime_dev.db`) since no Docker/Postgres is available here - see
  `docs/33_POSTGRESQL.md`. `py -m venv .venv`, `pip install -r requirements/dev.txt`,
  `uvicorn app.main:app --reload --port 8000`.
- Production: set `DATABASE_URL` to a real `postgresql+asyncpg://` DSN, run
  `alembic upgrade head`, and run behind a real ASGI server (uvicorn/gunicorn) with TLS terminated
  in front of it. No Celery/Redis/Kubernetes claim is made here until one is actually built and
  tested - this section previously described infrastructure that was never implemented.
- Secrets (`JWT_SECRET_KEY`, `DATABASE_URL`) come from environment/secret configuration only,
  never committed to source - see `server/.env.example`.

## Android <-> Server
- Emulator default: `http://10.0.2.2:8000/api/v1/` (`SecureStorage.DEFAULT_API_BASE_URL`),
  cleartext-permitted only for that specific loopback alias
  (`app/src/main/res/xml/network_security_config.xml`) - a real deployment overrides the base URL
  to an `https://` host via `SecureStorage.setApiBaseUrl()`, at which point cleartext is irrelevant.
