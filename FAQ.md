# Frequently Asked Questions (FAQ)

---

## How It Works

### Architecture Overview

Lykon Shield operates as a **local VPN on your Android device**. It does not route your traffic through any external server — the "VPN" is entirely self-contained on your phone.

Here's what happens when an app makes a network request:
App makes request
↓
Lykon Shield's local VPN intercepts it
↓
DNS query extracted → checked against blocklist
↓
[Blocked?]
↙          ↘
YES           NO
↓             ↓
Request        Request
dropped     forwarded to
silently    your real DNS
↓
Response returned to the app

**Key points:**
- Everything runs **on-device**. No cloud, no relay, no third-party server ever sees your traffic.
- Lykon Shield only inspects the **domain name** of each request — not the content, URL path, or payload.
- Blocking happens by returning an empty/null response for known ad and tracker domains, so the app never makes the connection.
- Your actual internet traffic flows normally through your ISP or chosen DNS — Lykon Shield just sits in front of it as a filter.

**Filter lists** are stored locally and updated periodically. They contain known ad, tracker, and telemetry domains sourced from community-maintained blocklists (similar to uBlock Origin's approach).

---

## General

### Q: Does Lykon Shield slow down my internet?
**A:** Minimally, if at all. Since blocking happens locally before any network request leaves your device, blocked requests are actually *faster* to resolve — they never make a round trip to a server. For allowed traffic, there is a small processing overhead as each DNS query is checked against the blocklist, but this is measured in microseconds and is imperceptible in real-world use.

### Q: Does it work without root?
**A:** Yes. Lykon Shield uses Android's built-in local VPN API (`VpnService`), which does not require root access. This is the same mechanism used by apps like Blokada and NetGuard.

### Q: Does it work on WiFi and mobile data?
**A:** Yes. The local VPN intercepts all DNS traffic regardless of your connection type — WiFi, 4G, 5G, or anything else.

### Q: Will it drain my battery?
**A:** Lykon Shield is designed to be lightweight. The local VPN service runs persistently in the background, but since it only processes DNS queries (not full packet inspection), CPU and memory usage is minimal. In practice, blocking ad requests may actually *reduce* battery drain slightly by preventing unnecessary network activity from tracker-heavy apps.

### Q: Does it work with other VPNs?
**A:** No. Android only allows one active VPN at a time. If you connect to an external VPN, Lykon Shield will be paused. This is an Android OS limitation, not something we can work around. We are exploring a mode where Lykon Shield can run *within* a VPN tunnel in a future release.

---

## Privacy & Security

### Q: Is my banking information, passwords, or personal data safe?
**A:** Yes — and here is exactly why. Lykon Shield only ever sees **domain names** (e.g., `ads.example.com`). It never sees what is inside your network requests.

When you open your banking app:
- The connection between your app and your bank is **end-to-end encrypted via HTTPS/TLS**.
- Lykon Shield sees: *"this app wants to talk to `yourbank.com`"* — and lets it through.
- It never sees your account number, password, OTP, or any transaction data. That data is encrypted before it ever reaches the VPN layer.

Lykon Shield does **not** perform SSL inspection, Man-in-the-Middle decryption, or any form of traffic analysis. It is architecturally incapable of reading your encrypted data. This is by design.

### Q: How exactly does Lykon Shield block ads?
**A:** Ad blocking works in three steps:

1. **Intercept** — When any app on your phone makes a network request, the DNS query passes through Lykon Shield's local VPN first.
2. **Check** — The requested domain is looked up against a locally stored blocklist of known ad, tracker, and telemetry domains (e.g., `doubleclick.net`, `facebook-hardware.com`).
3. **Drop or Allow** —
   - If the domain is on the blocklist: Lykon Shield returns an empty response. The app gets no address back, so the connection is never made. The ad never loads.
   - If the domain is not on the blocklist: the query is forwarded to your real DNS normally and the request goes through.

Nothing is rerouted through an external server. The entire process happens in milliseconds, locally on your device.

### Q: Why is Lykon Shield safe to use?
**A:** Several reasons:

- **No external servers** — Your traffic is never routed through our infrastructure. We have no servers to be hacked, subpoenaed, or compromised.
- **No SSL inspection** — We never decrypt your HTTPS traffic. Your encrypted connections remain fully private.
- **No accounts, no sign-in** — We collect no personal information because we have no system to collect it with.
- **No telemetry** — The app sends no usage data, crash reports, or analytics anywhere.
- **Open source** — The full source code is publicly available on GitHub. Anyone can audit exactly what the app does. There is no black box.
- **DNS-only scope** — Lykon Shield is architecturally limited to seeing domain names. It cannot see passwords, messages, or any content even if it wanted to.

### Q: Does Lykon Shield see my passwords or personal data?
**A:** No. Lykon Shield only inspects **DNS queries** — the domain names your apps request (e.g., `ads.example.com`). It never sees the content of your requests, your passwords, your messages, or any payload data. All HTTPS traffic remains fully encrypted end-to-end.

### Q: Does it send any data to your servers?
**A:** No. There are no servers. Lykon Shield has no cloud backend, no telemetry, no analytics, and no account system. Everything — the blocklist, the VPN logic, the logs — lives entirely on your device.

### Q: Is it open source?
**A:** Yes. The full source code is available on GitHub. You can audit exactly what the app does, build it yourself, or contribute.

---

## Limitations

### Q: What are the limitations of DNS-level blocking?
**A:** DNS-level blocking is powerful but it has real boundaries worth understanding:

- **Domain-level only** — Lykon Shield blocks entire domains, not specific URLs or request types. If an ad and legitimate content share the same domain, we cannot block one without breaking the other.
- **No content inspection** — We cannot see inside encrypted HTTPS traffic. This means we cannot block ads injected within a page's own domain or filter specific request parameters.
- **No cosmetic filtering** — Unlike browser extensions, Lykon Shield cannot hide ad placeholder elements left behind after a network request is blocked. You may sometimes see an empty box where an ad was.
- **Encrypted DNS can bypass blocking** — Some apps use hardcoded DNS-over-HTTPS (DoH) servers (like Google's `8.8.8.8` via encrypted channels) that bypass the local VPN's DNS interception entirely. We are working on detection for this.
- **Single VPN slot** — Android allows only one active VPN at a time. Lykon Shield cannot run simultaneously with an external VPN like Mullvad or ProtonVPN.
- **Unified CDN ads** — Audio and video streaming services that serve ads and media from the same CDN domain cannot have their in-stream ads blocked without also breaking playback. See below.

### Q: Why do I still hear audio ads on some music streaming apps?
**A:** Lykon Shield is a DNS-level ad-blocker. It works by intercepting network requests to known ad and tracking domains and dropping them before they reach your device.

Recently, major audio and video streaming platforms have adopted a "unified" Content Delivery Network (CDN) model. This means that both the actual media (songs, podcasts, videos) and the in-stream ads (audio/video ads) are sent to your device from the **exact same servers**.

Because a DNS firewall cannot inspect the *contents* of encrypted HTTPS traffic (due to Android security protocols and certificate pinning), it only sees the domain name being requested. If we block the domain to stop the audio ad, it will also completely break the media playback.

### Q: Can you use HTTP inspection (like browser extensions do) to block these ads?
**A:** Browser-based ad-blockers (like those for Chrome or Firefox) live *inside* the browser. They have access to the raw data and exact URLs before the data is encrypted or after it is decrypted.

A VPN-based blocker on Android sits *outside* the app and only sees encrypted packets. To inspect this traffic, the VPN would need to perform Man-In-The-Middle (MITM) SSL decryption. Modern Android versions and security-conscious apps use a technique called "Certificate Pinning", which strictly prevents this kind of interception to protect your data. If we attempt to inspect the traffic, the streaming app will immediately refuse to connect.

### Q: What is the current status on the unified CDN ads issue?
**A:** We are actively researching workarounds to reliably filter unified CDN ads without disrupting legitimate playback. While the audio/video in-stream ads may occasionally slip through on these specific platforms, **Lykon Shield still successfully blocks the visual ads, tracking requests, and popups on these apps.**

We appreciate your patience as we work on a permanent resolution.

### Q: An app broke after enabling Lykon Shield. What do I do?
**A:** Some apps use aggressive domain names that overlap with legitimate services, or bundle ads on the same domain as core functionality (the unified CDN problem described above). If an app breaks:
1. Check the **Lykon Shield logs** to see which domain is being blocked.
2. Add that domain to your **whitelist** inside the app.
3. Report it to us on GitHub so we can investigate and fix the blocklist.

### Q: How often are the blocklists updated?
**A:** Blocklists are updated periodically via the app. You can also trigger a manual update from the settings screen. The lists are sourced from community-maintained blocklist projects and supplemented with domains we identify during testing.

---

## Installation & Updates

### Q: Where do I download Lykon Shield?
**A:** Lykon Shield is currently in Alpha. Download the latest release from GitHub:
[https://github.com/ProjectArcade/Acrade-LykonShield-Android/releases](https://github.com/ProjectArcade/Acrade-LykonShield-Android/releases)

Always download the latest release. Do not use older APKs from unofficial sources.

### Q: Is it on the Play Store?
**A:** Not yet. Google charges a one-time $25 developer registration fee to publish on the Play Store :) . We are a zero-budget open source project. Twenty-five dollars is, at this moment in time, a financial obstacle.

We plan to publish once we exit Alpha (and once we locate $25). For now, enable "Install from unknown sources" in your Android settings and sideload the APK like a true Android power user.