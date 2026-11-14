# BPB Freedom VPN — Future Engineering Roadmap

This roadmap outlines the phased evolution of **BPB Freedom VPN** from an Android/desktop multi-protocol Xray + anti-censorship client into an autonomous, adaptive censorship-circumvention platform supporting zero-cost community/free VPN backends and reverse-tunnel architectures.

---

## Phase 1: Core Tunneling Hardening & Native Xray Binding (Near-Term)
- **Native Go Core (`libXray` / `tun2socks`) Integration**
  - Bundle compiled Go `xray-core` + `hev-socks5-tunnel` Android AAR/JNI binaries so `BpbVpnService` forwards real `tun0` IP packets into the local SOCKS5/HTTP inbounds instead of running only the control-plane lifecycle.
  - Validate generated Xray configs strictly (including `fragment-dialer` freedom outbound chaining, `dialerProxy`, and DNS `fakedns` + DoH fallback).
- **Accurate Interface Telemetry**
  - Read real-time `rxBytes`/`txBytes` directly from Xray's gRPC Stats API (`127.0.0.1:10085`) and `TrafficStats` so both Android `SpeedChart` and Flutter `SpeedChartFlChart` plot exact tunnel throughput.
- **Per-App Split Tunneling Enforcement**
  - Apply `VpnService.Builder.addAllowedApplication` / `addDisallowedApplication` from `RoutingSettings.selectedPackages` inside `BpbVpnService.startTunnel()`.

---

## Phase 2: Free VPN Backends Ecosystem (`firefox-vpn-client` & Peer Relays)
- **Mozilla / Fastly HTTP/2 & HTTP/3 Tunnel Adapter (`FIREFOX_VPN`)**
  - Support connecting to Fastly-fronted HTTP/2 and HTTP/3 proxy tunnels (`fxvpn://` URI format), inspired by `imanbarati/firefox-vpn-client` (`arman-vpn-client`).
  - Implement **Multi-Account Quota Pool & Rotation**: Track per-account free-tier bandwidth budgets (e.g., 50 GB/month per Firefox Account), detect `402`/`403`/`429` exhaustion responses, and hot-swap tokens seamlessly without dropping active connections.
  - Integrate automated OAuth/device-token onboarding for Firefox Accounts stored encrypted in Android Keystore.
- **Cloudflare Workers, WARP MASQUE & Bridge Fallbacks**
  - Support dynamic WARP key registration, MASQUE (HTTP/3 UDP-over-QUIC) transport, and automated fallback chains: `Direct -> TLS Fragment -> Cloudflare Worker -> Firefox Fastly H2/H3 -> Psiphon / Tor Snowflake`.

---

## Phase 3: Reverse Tunneler & On-Device 1B-Class Adaptive Tunnel Policy (`redesigned-robot`)
- **Reverse Tunneler Architecture (`REVERSE_TUNNEL`)**
  - Support `revtun://` reverse WebSocket/QUIC bridge-and-portal topologies for NAT traversal, asymmetric routing, and relay chaining across restricted networks.
  - Enable peer/relay health heartbeats, automatic reconnect backoff, and bidirectional multiplex framing.
- **On-Device Adaptive Tunnel Optimizer (Contextual Bandit -> Quantized 1B Edge Model)**
  - **Stage 3A (Implemented Foundation — Contextual Policy Engine):** Lightweight on-device reinforcement policy (`AdaptiveReverseTunnelPolicy`) that continuously scores transport arms (`FRAGMENT_SMALL_H2`, `FRAGMENT_MEDIUM_WS`, `NOISE_QUIC_H3`, `REVERSE_RELAY_FALLBACK`) using live RTT, TLS handshake reset rates, and throughput reward signals.
  - **Stage 3B (Distilled Edge Inference — 1B-Parameter Class Local Policy):** Run a quantized (INT4/Q4) compact sequence/decision model via MediaPipe/ExecuTorch/ONNX Runtime completely offline to predict ISP-specific DPI fingerprints, optimal packet fragmentation windows, SNI camouflage profiles, and clean Anycast IP candidates from historical connection logs with zero telemetry leaves-device guarantees.

---

## Phase 4: Cross-Platform Parity & Power Efficiency
- **Unified Kotlin Multiplatform / Flutter Core Channel**
  - Unify Windows (`DesktopVpnController` + `bpb-xray.exe`), Android (`BpbVpnService`), and macOS/Linux network config models under a single schema.
- **Adaptive Battery & Background Guard**
  - Integrate Android `WorkManager` / `PowerManager` thermal & Doze awareness with `BatteryDrainMonitor` to automatically lengthen keep-alive intervals or suspend idle noise bursts when on low battery.

---

## Phase 5: Continuous Verification, Security & Play Policy Readiness
- **Automated Socket & Compose UI Verification**
  - Expand Robolectric JVM test suites with local loopback TCP/HTTP2 mock backends verifying real handshakes, account rotation, adaptive policy convergence, and end-to-end Compose UI flows.
- **Security & Zero-Trust Storage**
  - Encrypt imported VLESS/Trojan/Firefox tokens using AndroidX Security `EncryptedSharedPreferences` / Keystore-backed keys and enforce Play Store compliant zero-storage-permission imports.
