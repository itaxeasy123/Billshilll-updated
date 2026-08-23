# 24. Security Architecture

## WHAT
Security policies covering on-device data encryption, credential management, and multi-tenant access control.

## IMPLEMENTATION
- `SecureStorage`: Android Jetpack Security (`MasterKeys.AES256_GCM` + `EncryptedSharedPreferences`) stores sensitive JWT tokens, tenant keys, and encryption secrets.
- In-memory tenant isolation in ViewModels and DAOs.
- Zero plaintext storage of credentials or sensitive personal information.
