# Frequently Asked Questions (FAQ)

### Q: Why do I still hear audio ads on some music streaming apps?
**A:** Lykon Shield is a DNS-level ad-blocker. It works by intercepting network requests to known ad and tracking domains and dropping them before they reach your device. 

Recently, major audio and video streaming platforms have adopted a "unified" Content Delivery Network (CDN) model. This means that both the actual media (songs, podcasts, videos) and the in-stream ads (audio/video ads) are sent to your device from the **exact same servers**. 

Because a DNS firewall cannot inspect the *contents* of encrypted HTTPS traffic (due to Android security protocols and certificate pinning), it only sees the domain name being requested. If we block the domain to stop the audio ad, it will also completely break the media playback. 

### Q: Can you use HTTP inspection (like browser extensions do) to block these ads?
**A:** Browser-based ad-blockers (like those for Chrome or Firefox) live *inside* the browser. They have access to the raw data and exact URLs before the data is encrypted or after it is decrypted. 

A VPN-based blocker on Android sits *outside* the app and only sees encrypted packets. To inspect this traffic, the VPN would need to perform Man-In-The-Middle (MITM) SSL decryption. Modern Android versions and security-conscious apps use a technique called "Certificate Pinning", which strictly prevents this kind of interception to protect your data. If we attempt to inspect the traffic, the streaming app will immediately refuse to connect.

### Q: What is the current status on this issue?
**A:** We are actively researching workarounds to reliably filter unified CDN ads without disrupting legitimate playback. While the audio/video in-stream ads may occasionally slip through on these specific platforms, **Lykon Shield still successfully blocks the visual ads, tracking requests, and popups on these apps.**

We appreciate your patience as we work on a permanent resolution.
