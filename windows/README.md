# Pexpo for Windows

Pexpo Music desktop edition for Microsoft Windows.

## Release

- Product: **Pexpo Music**
- Windows version: **1.1**
- Platform: **Windows 10/11 (x64)**
- Developer / Publisher: **Ritam Das**
- Executable: `Pexpo.exe`
- Installer: `Pexpo-1.1-Setup.exe`
- Architecture: x64

## Identity

- Product name: Pexpo Music
- Internal product ID: Pexpo.Windows
- Publisher: Ritam Das
- Copyright: © Ritam Das
- Upgrade channel: Pexpo Windows releases

## Signing

Windows executable signing is separate from the Android Pexpo release keystore. The Windows build is designed to use an Authenticode code-signing certificate supplied through GitHub Actions secrets. No private signing material is stored in the repository.

Required CI secrets will be documented when the signing certificate is provisioned:

- `WINDOWS_CERT_BASE64`
- `WINDOWS_CERT_PASSWORD`

## Goal

The Windows edition should preserve the Pexpo Music experience and behavior while packaging it as a proper Windows desktop application and installer. Android production development remains on `main` and is not modified by this branch.
