# Examples

One folder per TV model, each with a `debloat.txt` listing the packages that are safe to disable
on that firmware, grouped and commented. These are starting points: comment out anything you use.

| Folder | TV | OS | Notes |
|---|---|---|---|
| `xiaomi-mi-tv-4a/` | Xiaomi Mi TV 4A (MiTV-MOOQ1) | Android TV 10 | PatchWall, Mi analytics, SmartShare, MiLink |
| `tcl-g10-google-tv/` | TCL 55" 4K Google TV, 2023 (G10, G10_4K_US_NF) | Google TV 12 | TCL TV+, TCL Live, store demo, smartlink installer |
| `xiaomi-mitv-axso0/` | Xiaomi MiTV-AXSO0 (dangal) | Android TV 9 | PatchWall, Mi analytics, MiLink; voice, Chromecast, unused streaming apps off by choice |
| `xiaomi-mi-box-s/` | Xiaomi Mi Box S (MIBOX4, oneday), streaming box | Android TV 9 | PatchWall, Mi analytics, Alphonso ad tracking, MiLink; voice, casting, phone remote off by choice |
| `sanyo-atv-r2/` | SANYO ATV R2 (SCBC, Realtek RTD285O) | Android TV 9 | S-Channels, E-SHOW demo, Vewd, TV Booster, SANYO OTA |
| `oneplus-tv-dubai/` | OnePlus TV (D00, Dubai) | Google TV 14 | HeyTap/OnePlus extras, OnePlus Connect, Smart Home |
| `benq-e55-735/` | BenQ E55-735 (Changhong AI PONT, DVB96BQ) | Google TV 14 | Homwee store/help, Anoki ACR, Google TV recommendations; wireless-debugging pairing, setupwraith, deep-standby notes |
| `hisense-smarttv-4k-ffm/` | Hisense SmartTV 4K FFM (huangshan, MediaTek) | Android TV 10 | ACR (evservice), VIDAA preload, store/hotel mode, OTA; smartsense must stay enabled |
| `tcl-65c725/` | TCL 65C725 (Smart TV Pro, BeyondTV4) | Android TV 11 | TCL analytics/promos, Google recommendation rows; voice off, OTA off by choice |
| `sony-bravia-4k-ur2/` | Sony BRAVIA 4K UR2 (BRAVIA_UR2_4K, MediaTek) | Android TV 10 | Samba TV, Sony promos/Select, demo mode; Assistant, Chromecast, unused streaming apps off by choice |
| `xiaomi-mitv-mooq0/` | Xiaomi Mi TV (MiTV-MOOQ0, croods) | Android TV 10 | PatchWall, Mi analytics, MiChannel, TV Manager, Alexa bridge; factory test app also claims HOME |
| `sony-xbr-x900e/` | Sony BRAVIA XBR-65X900E (2017, BRAVIA_ATV2) | Android TV 9 | Samba TV ACR, Sony Select, My BRAVIA promos, Discover, device agents; Chromecast, DLNA, mirroring off by choice |
| `tcl-beyondtv/` | TCL BeyondTV (India) | Android TV 9 | TCL analytics, content feed, TCL app store, Google recommendation rows; Jio/Airtel/Hotstar apps untouched |
| `nvidia-shield-tv-pro-2019/` | NVIDIA SHIELD TV Pro 2019 (P2897, mdarcy) | Android TV 11 | NVIDIA telemetry, GameStream leftovers, sponsored rows, partner auto-installs |
| `sony-bravia-vn/` | Sony BRAVIA XR-65X95J and other Sony Google TV / Android TV sets (Vietnam) | Google TV 12 | Grouped list used by `tv-sony.ps1`, with a Vietnamese description per package |
| `xiaomi-mitv-moor2/` | Xiaomi MiTV-MOOR2 (venom, MediaTek) | Google TV 11 | Mi ad row and PatchWall, Mi analytics, MiLink, store demo; setup wizard off so a third-party launcher can own Home |

Most of these lists were collected by the [tv-vibehack](https://github.com/Gathin23/tv-vibehack)
community. To add your TV, see [CONTRIBUTING.md](../CONTRIBUTING.md).
