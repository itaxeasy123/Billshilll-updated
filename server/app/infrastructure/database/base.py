"""
Async SQLAlchemy engine/session setup. `DATABASE_URL` decides the dialect: a real deployment
points this at PostgreSQL (`postgresql+asyncpg://...`); this development environment (no
Docker/Postgres available - documented in the Phase 6 plan) runs tests against SQLite
(`sqlite+aiosqlite:///...`) via the exact same models/migrations path. The schema itself is written
against SQLAlchemy's portable column types so both dialects work unchanged.
"""
from __future__ import annotations

from collections.abc import AsyncGenerator

from sqlalchemy.ext.asyncio import AsyncSession, async_sessionmaker, create_async_engine
from sqlalchemy.orm import DeclarativeBase
from sqlalchemy.pool import StaticPool

from app.config import get_settings


class Base(DeclarativeBase):
    pass


def make_engine(database_url: str | None = None):
    url = database_url or get_settings().database_url
    if url.startswith("sqlite"):
        # In-memory SQLite is per-connection by default - every new connection would otherwise see
        # a fresh, empty database, silently breaking any test that opens more than one session
        # (e.g. a fixture seeding data via one session while the FastAPI test client's overridden
        # get_db() opens another). StaticPool forces every connection from this engine to reuse the
        # SAME underlying SQLite connection, so they all see the same data.
        return create_async_engine(url, connect_args={"check_same_thread": False}, poolclass=StaticPool)
    return create_async_engine(url)


_engine = make_engine()
_session_factory = async_sessionmaker(bind=_engine, expire_on_commit=False, class_=AsyncSession)


def get_session_factory(engine=None) -> async_sessionmaker:
    if engine is None:
        return _session_factory
    return async_sessionmaker(bind=engine, expire_on_commit=False, class_=AsyncSession)


async def get_db() -> AsyncGenerator[AsyncSession, None]:
    async with _session_factory() as session:
        yield session
