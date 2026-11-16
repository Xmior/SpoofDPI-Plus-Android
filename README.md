<p align="center">
  <img src="SpoofDPIPlus-1024.png" width="128" alt="SpoofDPI+">
</p>

<h1 align="center">SpoofDPI+ Android</h1>

<p align="center">
  Bypass DPI (Deep Packet Inspection) blocks on Android: no VPN, no root, no computer.<br>
  <b>English</b> · <a href="README.tr.md">Türkçe</a>
</p>

> **macOS version:** [SpoofDPI+ macOS](https://github.com/Xmior/SpoofDPI-Plus-MacOS)

---

## Features

- **One-tap on/off** from the app, the notification or a Quick Settings tile.
- **Not a VPN.** A tiny proxy runs on your phone. Traffic goes straight to the internet and is not routed through any server.
- **Persistent notification menu** with status and **Start/Stop**, **Wi-Fi settings** and **Quit completely** buttons.
- **Status panel** showing the proxy service, port 8080, the Wi-Fi proxy setting and a reachability test (discord.com).
- **Safety warning** when the Wi-Fi proxy is set but SpoofDPI+ is stopped.
- **Starts on boot** if it was running before.
- **English / Turkish.** The language follows your device language: Turkish on Turkish devices, English everywhere else.
- Light and dark theme. About 200 KB.

## Requirements

- **Android 8.0 (Oreo, API 26) or newer**
- A **Wi-Fi** connection (see [Limitations](#limitations))

Tested on a Samsung Galaxy M31 (Android 12) and an Android 15 emulator.

## Installation

1. Download `SpoofDPIPlus-<version>-Android.apk` from the [Releases](../../releases) page.
2. Open it on your phone and install it. If asked, allow installing from unknown sources.
3. Open **SpoofDPI+** and tap **Start**. Allow notifications if asked.
4. Go to **Settings → Wi-Fi**, tap the gear next to your network, and set **Proxy → Manual**:

   | Field | Value |
   |---|---|
   | Hostname | `127.0.0.1` |
   | Port | `8080` |

5. Save. All four rows in the status panel should turn green.
6. (Recommended) Tap **Allow background running** so battery saving does not stop the app.

Repeat step 4 once for each Wi-Fi network you use.

### Quick Settings tile

Pull down the notification panel, tap the pencil (edit) icon, and drag the **SpoofDPI+** tile into your active tiles.

## How it works

```
App (browser, Discord...)
        │  HTTP / HTTPS
        ▼
Wi-Fi proxy ──► SpoofDPI+ (127.0.0.1:8080, foreground service)
                    │  1. Resolves DNS with DNS-over-HTTPS (1.1.1.1)
                    │  2. Sends the first 40 bytes of the TLS ClientHello
                    │     one byte at a time, 2 ms apart
                    ▼
                 Internet
```

Blocking happens in two places:

1. **DNS poisoning.** The ISP's DNS server returns a fake IP for blocked sites. SpoofDPI+ resolves names over DNS-over-HTTPS instead, so the ISP never sees or rewrites the query.
2. **SNI filtering.** The first packet of every HTTPS connection (the TLS ClientHello) carries the site name (SNI) in plain text. The DPI box reads it and resets the connection. Our tests showed the DPI stops inspecting after roughly 20 small packets, so SpoofDPI+ sends the first 40 bytes as separate 1-byte packets and the rest in one piece. By the time the site name arrives, the DPI is no longer looking. The server reassembles the stream and answers normally.

**Why the 2 ms gap?** The Linux kernel inside Android merges small back-to-back writes into one packet (TCP autocorking), even with `TCP_NODELAY`. Without the gap, the 1-byte fragments leave the phone as a single packet and the site stays blocked. This is why the plain "1-byte chunk" method from desktop SpoofDPI does not work on Android.

Only the first packet of each connection is split. After that, data flows normally, so there is no noticeable slowdown.

## Limitations

- **Wi-Fi only.** Without a VPN or root, Android does not allow apps to set a proxy for mobile data.
- **If SpoofDPI+ is stopped while the Wi-Fi proxy is still set, that Wi-Fi has no internet.** Either keep SpoofDPI+ running, or set Proxy back to **None** in Wi-Fi settings. The app and the notification warn you when this happens.
- **Some apps ignore the system proxy.** Browsers and most apps follow it; some games and apps do not.
- **Discord voice** uses UDP, which an HTTP proxy cannot carry, so voice channels may not connect.

## Troubleshooting

| Problem | Fix |
|---|---|
| No internet on Wi-Fi | SpoofDPI+ is stopped. Tap **Start**, or set the Wi-Fi proxy back to **None**. |
| The app stops in the background | Tap **Allow background running**. On Samsung, also check Settings → Battery → Background usage limits. |
| A site is still blocked | Make sure the status panel is all green, then open the **Logs** in the app. |
| "Wi-Fi proxy: Not set" | Check that the hostname is `127.0.0.1` and the port is `8080` for the network you are on. |

## Building from source

Requirements: JDK 17+, Android SDK (platform 35, build-tools 35.0.0).

```bash
./gradlew assembleRelease
```

Output: `app/build/outputs/apk/release/SpoofDPIPlus-<version>-Android.apk`. The version number is read from the `VERSION` file.

### Project layout

```
app/src/main/java/com/xmior/spoofdpiplus/
  ProxyServer.java    DPI-bypass HTTP proxy (CONNECT + ClientHello fragmentation)
  Dns.java            DNS-over-HTTPS resolver (1.1.1.1, cached)
  ProxyService.java   Foreground service and notification menu
  ToggleTile.java     Quick Settings tile
  BootReceiver.java   Restart on boot
  MainActivity.java   Status panel, buttons, About and Help
  Logs.java           In-app log
app/src/main/res/values/strings.xml      English (default)
app/src/main/res/values-tr/strings.xml   Turkish
```

## Credits

- [Xmior/SpoofDPI-Plus-MacOS](https://github.com/Xmior/SpoofDPI-Plus-MacOS): the macOS version

- [xvzc/SpoofDPI](https://github.com/xvzc/SpoofDPI): the DPI bypass method
- [cagritaskn/SplitWire-Turkey](https://github.com/cagritaskn/SplitWire-Turkey): inspiration

Made by **Caferius/Xmior**.

## Disclaimer

This tool is provided for freedom of access to information. You are responsible for how you use it.
