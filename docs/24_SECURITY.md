# 24. Client Security Architecture

## Storage Hardening (`SecureStorage`)
- Backed by Android Jetpack Security `EncryptedSharedPreferences`.
- Cryptographic keys: `MasterKey.AES256_GCM` for values, `AES256_SIV` for keys.
- **Hardware vs Emulated Keystore Fallback**:
  - Production Hardware: Backed by Hardware Security Module (HSM) / StrongBox Keymaster.
  - Development / CI / Headless Sandbox: Graceful fallback to app-private sandboxed preferences if the Android KeyStore provider is unavailable during automated testing.
