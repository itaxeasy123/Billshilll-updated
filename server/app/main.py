"""
FastAPI application entrypoint (Phase 6). All routes are mounted under `/api/v1` (Priority 6.15 -
never make the first production API impossible to evolve; `/api/v2` can coexist later without
breaking this one).
"""
from __future__ import annotations

from fastapi import FastAPI, Request
from fastapi.responses import JSONResponse

from app.api.routes import auth, banking, documents, exports, health, invoices, parties, reports, subscriptions, sync, trade_documents, vouchers
from app.domain.errors import AppError

app = FastAPI(title="LedgerPrime API", version="6.0.0")


@app.exception_handler(AppError)
async def app_error_handler(request: Request, exc: AppError):
    """Single structured-error mapping point (Phase 6, Priority 6.11) - every AppError subtype
    becomes `{"code": "...", "message": "..."}` at the right HTTP status. The Android side maps
    these codes to friendly business language; it never shows a raw exception name to the user."""
    return JSONResponse(status_code=exc.status_code, content={"code": exc.code, "message": exc.message})


app.include_router(health.router, prefix="/api/v1")
app.include_router(auth.router, prefix="/api/v1")
app.include_router(sync.router, prefix="/api/v1")
app.include_router(reports.router, prefix="/api/v1")
app.include_router(vouchers.router, prefix="/api/v1")
app.include_router(parties.router, prefix="/api/v1")
app.include_router(invoices.router, prefix="/api/v1")
app.include_router(trade_documents.router, prefix="/api/v1")
app.include_router(documents.router, prefix="/api/v1")
app.include_router(exports.router, prefix="/api/v1")
app.include_router(subscriptions.router, prefix="/api/v1")
app.include_router(banking.router, prefix="/api/v1")
