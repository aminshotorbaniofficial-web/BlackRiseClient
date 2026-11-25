# BlackRise VPN client

Cores: sing-box v1.8.13 (VLESS + Reality, Hysteria2) and WireGuard.

## Build (recommended: GitHub Actions)
Push to GitHub (branch `main`). The workflow compiles `libbox.aar` from sing-box source,
then builds the APK. Download `BlackRise-APK` from the Actions run.

## Build locally (Android Studio)
Download the `libbox-aar` artifact from a CI run, place `libbox.aar` in `app/libs/`, then Build > Build APK.

## Supported inputs (Servers > Add)
- `vless://...` (tcp / ws / grpc / httpupgrade, tls / reality)
- `hy2://...` or `hysteria2://...` (obfs salamander supported)
- WireGuard `.conf` text
- A raw sing-box outbound JSON object

Licence note: sing-box is GPL-3.0+; distributing this app means your app must comply with it.
