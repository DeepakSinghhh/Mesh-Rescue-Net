<div align="center">

# 📡 MESH//RESCUE
### *EchoChat — The Off-Grid Emergency Bridge*

**Smarter Rescue. Zero Infrastructure. Every Life Matters.**

[![Android](https://img.shields.io/badge/Platform-Android%208.0%2B-brightgreen?style=for-the-badge&logo=android)](https://developer.android.com)
[![Kotlin](https://img.shields.io/badge/Language-Kotlin-purple?style=for-the-badge&logo=kotlin)](https://kotlinlang.org)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow?style=for-the-badge)](LICENSE)
[![HACKSHODH 2026](https://img.shields.io/badge/HACKSHODH_2026-PS--04-red?style=for-the-badge)](https://hackshodhcsjmu.site)

<br/>

> *Built for the people who need help most, when infrastructure fails them.*

</div>

---

## 📸 App Screenshots

<div align="center">

| Splash & Permissions | Main Dashboard | Triage Demo |
|:---:|:---:|:---:|
| ![Splash](screenshots/WhatsApp%20Image%202026-06-29%20at%2010.23.15%20AM.jpeg) | ![Dashboard](screenshots/WhatsApp%20Image%202026-06-29%20at%2010.23.16%20AM.jpeg) | ![Triage](screenshots/WhatsApp%20Image%202026-06-29%20at%2010.23.18%20AM%20(1).jpeg) |
| Grant Location + Bluetooth + Nearby | NODE ID · PEERS · STATUS | High Priority + Private Mode |

| Receiving Node | Flood Simulation |
|:---:|:---:|
| ![Receiver](screenshots/WhatsApp%20Image%202026-06-29%20at%2010.23.18%20AM.jpeg) | ![Flood](screenshots/WhatsApp%20Image%202026-06-29%20at%2010.23.18%20AM%20(2).jpeg) |
| SOS received: *"helpp me — GPS(27.51, 83.45)"* | Low Priority Spam 22–29 flushed instantly |

</div>

---

## 🚨 The Problem

During natural disasters — Himachal floods, Kerala landslides, earthquakes — **cellular towers fail**, leaving victims completely isolated.

Existing off-grid mesh apps treat every message equally:

> *"Heart Attack — need ambulance"* sits in the same queue as *"I'm hungry"*

In a congested mesh with limited battery and bandwidth, **that equality is fatal.**

---

## 💡 The Solution

**MESH//RESCUE** transforms ordinary Android phones into a self-organizing emergency mesh network with one critical differentiator:

### ⚡ Urgency Intelligence

A custom **Priority Triage Protocol** ensures life-critical SOS signals always jump the queue — no internet, no towers, no infrastructure required.

---

## ✨ Key Features

| Feature | Description |
|---|---|
| 📵 **Zero Infrastructure** | Works 100% offline — Bluetooth LE + Wi-Fi Direct |
| 🚨 **Priority Triage Protocol** | 6-level urgency system; critical SOS is *never* dropped |
| 🔁 **Multi-Hop Routing** | Messages hop Phone A → B → C until reaching a gateway node |
| ⏱️ **TTL Packet Expiry** | Packets expire after N hops to prevent infinite mesh loops |
| 🔒 **End-to-End Encryption** | Libsodium (Curve25519 + XSalsa20); relay nodes are blind couriers |
| 🔋 **Battery Aware** | BLE for discovery, Wi-Fi Direct only for active data transfer |
| ☁️ **Gateway Sync** | Queued SOS messages auto-upload when satellite/internet is found |

---

## ⚙️ Architecture

### Tech Stack

| Layer | Technology |
|---|---|
| Language | Kotlin (Native Android) |
| Connectivity | Google Nearby Connections API (`Strategy.P2P_CLUSTER`) |
| Encryption | Libsodium — Curve25519 Key Exchange + XSalsa20 Stream Cipher |
| Local Storage | SQLite (offline message persistence) |
| Cloud Gateway | Supabase / PostgreSQL (sync on reconnect) |
| Min SDK | Android 8.0 (API 26) |

### Mesh Topology

```
[Phone A] ──BLE Discovery──▶ [Phone B (Relay)] ──▶ [Phone C (Gateway)]
   SOS Packet                  Blind Forward            Internet Upload
   Priority: 0                 Sees: TargetID +         Decrypts & Routes
                               Ciphertext only
```

### 🧠 The Triage Algorithm

When network buffer exceeds **80% capacity**, the algorithm kicks in:

```
Priority Level 0 — CRITICAL   → Medical SOS, Fire, Life-Threatening   [NEVER DROPPED]
Priority Level 1 — URGENT     → Rescue Team Coordination               [Dropped at 100%]
Priority Level 2 — ROUTINE    → "I am safe" status updates             [Dropped at 80%]

Congestion Rules:
  Buffer > 80%  → Drop all packets with Priority > 1
  Buffer = 100% → Freshness Eviction — oldest low-priority packet dropped
```

### 🔐 Security Model

```
1. Key Exchange  →  Curve25519 ECC generates a Shared Secret
                    (private keys are NEVER transmitted)
2. Encryption    →  XSalsa20 stream cipher encrypts the message payload
3. Relay Nodes   →  See only [TargetID + Ciphertext] — blind couriers
4. Decryption    →  Only the intended recipient can read the message
```

---

## 📱 Demo Scenarios

### Scenario 1 — The "Flood" Test · Priority Triage

> *As shown in the screenshots: buffer floods with Low Priority Spam 22–29, SOS cuts through instantly.*

1. Connect 2+ phones to the same mesh session.
2. Tap **TEST TRIAGE [FLOOD + SOS]** (🔴 Red button).
3. Watch the buffer fill with low-priority spam packets.
4. Send a **Critical SOS** from any device.

**✅ Result:** Buffer drops all low-priority traffic instantly. The SOS arrives with zero latency.

---

### Scenario 2 — The "Blind Relay" · End-to-End Encryption

Setup: **Phone A → Phone B (Relay) → Phone C**

1. On Phone A, toggle **PRIVATE** mode and enter Phone C's public key.
2. Send a message: `"Secret: Need insulin at grid B4"`.

**✅ Result:**
- Phone B logs: `Relaying Encrypted Packet... [payload unreadable]`
- Phone C receives the fully decrypted message.

---

### Scenario 3 — Multi-Hop Routing · Judges' Full Demo

1. Place Phone A, B, C out of direct range of each other (Phone B in the middle).
2. Phone A sends **SEND SOS + GPS** (🟡 Yellow button) — coordinates auto-attached.
3. Phone B relays automatically as a blind courier.
4. Phone C (Gateway — with internet) uploads the SOS to the cloud dashboard.

**✅ Result:** Full 3-hop SOS delivery. GPS coordinates visible on the receiving node via **VIEW ON MAP**.

---

## 🗂️ Project Structure

```
Mesh-Rescue-Net/
├── app/
│   └── src/
│       └── main/
│           ├── java/          # Kotlin source — mesh logic, triage, encryption
│           └── res/           # Layouts, drawables, strings
├── screenshots/               # Demo screenshots (add yours here)
├── gradle/
├── build.gradle.kts
├── settings.gradle.kts
└── README.md
```

---

## 🚀 Getting Started

### Prerequisites

- Android Studio **Iguana** or newer
- **2 or more physical Android devices** (emulator Bluetooth is limited)
- Minimum SDK: **API 26 (Android 8.0)**

### Installation

```bash
# 1. Clone the repository
git clone https://github.com/DeepakSinghhh/Mesh-Rescue-Net.git

# 2. Open in Android Studio
# File → Open → select the cloned folder

# 3. Let Gradle sync
# lazysodium-android will be downloaded automatically

# 4. Deploy to 2+ physical devices
# Run → Run 'app' on each device
```

### First Launch

Grant the following permissions when prompted:

| Permission | Why |
|---|---|
| 📍 **Location** | Required by Android for BLE scanning |
| 📶 **Nearby Devices** | Required for Bluetooth/Wi-Fi mesh |
| 🔵 **Bluetooth** | Core mesh discovery |

> Use **DEMO: FORCE START** to bypass permissions for quick demo purposes.

---

## 🔮 Roadmap

- [ ] **Drone Relay Nodes** — mount gateway phones on UAVs to bridge distant clusters
- [ ] **Voice-to-Text SOS** — NLP for injured victims who cannot type
- [ ] **NDRF/SDMA API Integration** — direct pipeline to national disaster response teams
- [ ] **LoRa Radio Support** — extend mesh range beyond Wi-Fi Direct limits
- [ ] **Battery Optimization V2** — adaptive scan intervals based on motion sensors

---

## 🏆 Hackathon Context

Built for **HACKSHODH 2026** · CSJM University, Kanpur

| | |
|---|---|
| **Problem Statement** | PS-04 — The Off-Grid Emergency Bridge |
| **Domain** | Disaster Management · MANET · Mobile Networking |
| **Organizers** | Student Council · Aatmoday · Innovation Cell · IRAC Cell |
| **Event** | 30–31 January 2026, Kanpur |

---

## 👤 Author

**Deepak Kr Singh**
[GitHub — @DeepakSinghhh](https://github.com/DeepakSinghhh)

---

## 📄 License

This project is open source under the [MIT License](LICENSE).

---

<div align="center">

*Built with ❤️ for the people who need help most, when infrastructure fails them.*

**⭐ Star this repo if you believe connectivity is a right, not a privilege.**

</div>
