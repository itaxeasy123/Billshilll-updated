"""
Server configuration (Phase 6, Priority 6.20). Never hardcode secrets in source - every value here
is read from the environment (see .env.example), matching the Android side's own rule of never
storing server secrets in the APK.
"""
from functools import lru_cache

from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    model_config = SettingsConfigDict(env_file=".env", env_file_encoding="utf-8", extra="ignore")

    database_url: str = "sqlite+aiosqlite:///./ledgerprime_dev.db"
    jwt_secret_key: str = "change-me-in-a-real-deployment"
    jwt_algorithm: str = "HS256"
    jwt_access_token_expire_minutes: int = 15
    jwt_refresh_token_expire_days: int = 30
    environment: str = "development"


@lru_cache
def get_settings() -> Settings:
    return Settings()
