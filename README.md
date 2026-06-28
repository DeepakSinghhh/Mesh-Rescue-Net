# 📡 Mesh Rescue Net — *EchoChat*
### The Off-Grid Emergency Bridge · Smarter Rescue. Zero Infrastructure.

> **Built for HACKSHODH 2026** · CSJM University · Problem Statement PS-04  
> *Disaster Management · Networking · Mobile Ad-Hoc Networks (MANET)*

---

## 🚨 The Problem

During natural disasters — Himachal floods, Kerala landslides, earthquakes — cellular towers fail, leaving victims completely isolated. Existing off-grid mesh apps treat every message equally: a **"Heart Attack — need ambulance"** SOS sits in the same queue as **"I'm hungry"** status updates.

In a congested mesh network with limited battery and bandwidth, that equality is fatal.

---

## 💡 The Solution

**EchoChat** transforms ordinary Android phones into a self-organizing emergency mesh network with one critical differentiator: **Urgency Intelligence**.

A custom Priority Triage Protocol ensures life-critical SOS signals always jump the queue — no internet, no towers, no infrastructure required.

---

## ✨ Key Features

| Feature | Description |
|---|---|
| **Zero Infrastructure** | Works 100% offline using Bluetooth LE + Wi-Fi Direct |
| **Priority Triage Protocol** | 6-level urgency system; critical SOS is *never* dropped |
| **Multi-Hop Routing** | Messages hop Phone A → B → C until reaching a gateway node |
| **TTL Packet Expiry** | Packets expire after N hops to prevent infinite mesh loops |
| **End-to-End Encryption** | Libsodium (Curve25519 + XSalsa20); relay nodes are blind couriers |
| **Battery Aware** | BLE for discovery, Wi-Fi Direct for data transfer |
| **Gateway Sync** | Queued messages auto-upload when satellite/internet is found |

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

### The Triage Algorithm

When network buffer exceeds **80% capacity**, the algorithm kicks in:

```
Priority Level 0 — CRITICAL     → Medical SOS, Fire, Life-Threatening   [NEVER DROPPED]
Priority Level 1 — URGENT       → Rescue Team Coordination               [Dropped at 100%]
Priority Level 2 — ROUTINE      → "I am safe" status updates             [Dropped at 80%]

Congestion Rules:
  Buffer > 80%  → Drop all Level > 1
  Buffer = 100% → Drop oldest SOS (Freshness Eviction)
```

### Security Model

```
1. Key Exchange   →  Curve25519 ECC generates a Shared Secret (keys never transmitted)
2. Encryption     →  XSalsa20 stream cipher encrypts the message payload
3. Relay Nodes    →  See only [TargetID + Ciphertext] — act as blind couriers
4. Decryption     →  Only the intended recipient can read the message
```

---

## 🗂️ Project Structure

```
Mesh-Rescue-Net/
├── app/
│   └── src/
│       └── main/
│           ├── java/          # Kotlin source — mesh logic, triage, encryption
│           └── res/           # Layouts, drawables, strings
├── gradle/
├── build.gradle.kts
├── settings.gradle.kts
└── README.md
```

---

## 🚀 Getting Started

### Prerequisites

- Android Studio **Iguana** or newer
- **2 or more physical Android devices** (emulator Bluetooth support is limited)
- Minimum SDK: **API 26 (Android 8.0)**

### Installation

```bash
# 1. Clone the repository
git clone https://github.com/DeepakSinghhh/Mesh-Rescue-Net.git

# 2. Open in Android Studio
# File → Open → select the cloned folder

# 3. Let Gradle sync (lazysodium-android will be downloaded automatically)

# 4. Deploy to 2+ physical devices
# Run → Run 'app' on each device
```

### First Launch

On first launch, grant the following permissions when prompted:
- **Nearby Devices** — required for Bluetooth/Wi-Fi mesh
- **Location** — required by Android for BLE scanning

---

## 📱 Demo Scenarios

### Scenario 1 — The "Flood" Test (Priority Triage)

1. Connect 2+ phones to the same mesh session.
2. Tap the **"Simulate Flood"** button (🔴 Red).
3. Watch the **Network Buffer** bar fill with low-priority spam.
4. Send a **Critical SOS** from any device.

**Expected Result:** Buffer drops low-priority traffic instantly; the SOS arrives with zero latency.

---

### Scenario 2 — The "Blind Relay" (End-to-End Encryption)

Setup: **Phone A → Phone B (Relay) → Phone C**

1. On Phone A, select **"Private Mode"** and enter Phone C's device ID.
2. Send a message: `"Secret: Need insulin at grid B4"`.

**Expected Result:**
- Phone B logs: `"Relaying Encrypted Packet... [payload unreadable]"`
- Phone C receives the fully decrypted message.

---

### Scenario 3 — Multi-Hop Routing (Judges' Full Demo)

1. Place Phone A, B, C out of direct range of each other (Phone B in the middle).
2. Phone A sends an SOS with GPS coordinates.
3. Phone B relays automatically.
4. Phone C (Gateway — with internet) uploads the SOS to the cloud dashboard.

---

## 🔮 Future Roadmap

- [ ] **Drone Relay Nodes** — mount gateway phones on UAVs to bridge distant clusters
- [ ] **Voice-to-Text SOS** — NLP for injured victims who cannot type
- [ ] **NDRF/SDMA API Integration** — direct pipeline to national disaster response teams
- [ ] **LoRa Radio Support** — extend mesh range beyond Wi-Fi Direct limits
- [ ] **Battery Optimization V2** — adaptive scan intervals based on motion sensors

---

## 🏆 Hackathon Context

This project was built for **HACKSHODH 2026**, organized by CSJM University (Student Council · Aatmoday · Innovation Cell · IRAC Cell).

- **Problem Statement:** PS-04 — The Off-Grid Emergency Bridge
- **Domain:** Disaster Management · MANET · Mobile Networking
- **Event:** 30–31 January 2026, Kanpur

---

## 👤 Author

**Deepak Kr Singh**  
[GitHub — @DeepakSinghhh](https://github.com/DeepakSinghhh)

---

## 📄 License

This project is open source and available under the [MIT License](LICENSE).

---

*Built with ❤️ for the people who need help most, when infrastructure fails them.*
