# Lykon Shield

Lykon Shield is a local VPN-based ad-blocker and privacy guard for Android. It runs a local DNS server using Android's VPN API to intercept DNS queries and sink known tracking and advertisement domains entirely on-device, offering a low-overhead, privacy-first web experience.

## Features
- **Local DNS Sinkhole**: Blocks ads and track requests without routing traffic to external proxy servers.
- **Bypass App Selection**: Customize apps to bypass VPN protection.
- **Premium UI**: Uses backdrop-based glassmorphism, AGSL custom shaders, and interactive liquid layouts.

## Known Limitations
- **Unified CDN Ads**: Lykon Shield blocks tracking and ad domains using a DNS-level sinkhole. Some major audio and video streaming services have recently unified their Content Delivery Networks (CDNs), serving both legitimate media content and audio/video ads from the exact same servers. Because a DNS-level blocker cannot distinguish between an ad stream and a music stream originating from the same domain, blocking these domains will break playback entirely.
- **Status**: We are actively researching alternative methods and workarounds to reliably filter these in-stream ads without breaking legitimate media playback. Currently, visual ads and trackers on these platforms are successfully blocked, but audio/video in-stream ads may still occasionally slip through.

## Ownership & Licensing
- **Organization**: ProjectArcade
- **Developer**: Abhinav Thakur
- **License**: Lykon Shield is open-source software licensed under the [GNU General Public License v3.0 (GPL-3.0)](LICENSE).
