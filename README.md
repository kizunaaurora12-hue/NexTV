# 📺 NexTV — Android TV IPTV App

Aplikasi TV streaming modern untuk **Android TV Box** (Android 5.0+).  
Build otomatis via **GitHub Actions** — push code → APK siap download.

---

## ✨ Fitur

- 🔴 Live streaming channel dari `channels.json`
- 🗂️ Filter by kategori channel
- 🔍 Pencarian channel real-time
- ▶️ ExoPlayer (HLS / RTMP / HTTP)
- 📺 Optimised D-Pad navigation untuk TV Box
- ⚙️ URL channels.json bisa diganti dari Settings
- 💾 Cache channel 30 menit (hemat data)
- 🌙 Dark theme full

---

## 🚀 Cara Build (GitHub Actions — Otomatis)

1. **Fork / upload** repo ini ke akun GitHub kamu
2. Pergi ke tab **Actions** → pilih workflow **Build NexTV APK**
3. Klik **Run workflow** → tunggu ~5 menit
4. APK muncul di tab **Releases** → download & install ke TV Box

> Build juga berjalan **otomatis** setiap kamu push commit baru.

---

## 📁 Format channels.json

Taruh file `channels.json` di root repo. Dukung dua format:

**Format A — Array langsung:**
```json
[
  {
    "id": "rcti",
    "name": "RCTI",
    "url": "http://example.com/rcti/stream.m3u8",
    "logo": "https://example.com/logos/rcti.png",
    "category": "Hiburan",
    "number": 1,
    "quality": "HD",
    "is_live": true
  }
]
```

**Format B — Object dengan key "channels":**
```json
{
  "channels": [
    { "name": "RCTI", "url": "...", "category": "Hiburan" }
  ]
}
```

### Field yang tersedia

| Field      | Tipe    | Keterangan                          |
|------------|---------|-------------------------------------|
| `name`     | string  | **Wajib** — nama channel            |
| `url`      | string  | **Wajib** — URL stream              |
| `logo`     | string  | URL gambar logo                     |
| `category` | string  | Kategori (untuk filter)             |
| `number`   | int     | Nomor urut channel                  |
| `quality`  | string  | `SD` / `HD` / `FHD` / `4K`         |
| `is_live`  | boolean | Tampilkan badge LIVE                |

---

## ⚙️ Ganti URL channels.json

Dari dalam app: **Settings → URL channels.json → isi URL baru → Simpan**

Atau edit langsung di `ChannelRepository.java`:
```java
public static final String DEFAULT_CHANNELS_URL =
    "https://raw.githubusercontent.com/USERNAME/REPO/main/channels.json";
```

---

## 📦 Struktur Project

```
NexTV/
├── .github/workflows/
│   └── build.yml              ← GitHub Actions CI/CD
├── app/src/main/
│   ├── AndroidManifest.xml
│   ├── java/com/nextv/app/
│   │   ├── data/
│   │   │   ├── Channel.java           ← Model data channel
│   │   │   └── ChannelRepository.java ← Fetch + cache channels.json
│   │   └── ui/
│   │       ├── home/
│   │       │   ├── MainActivity.java  ← Halaman utama + grid channel
│   │       │   └── ChannelAdapter.java
│   │       ├── player/
│   │       │   └── PlayerActivity.java ← ExoPlayer fullscreen
│   │       └── settings/
│   │           └── SettingsActivity.java
│   └── res/
│       ├── layout/   ← XML layout
│       ├── drawable/ ← Background, icon, badge
│       ├── values/   ← Colors, strings, themes, dimens
│       └── xml/      ← Network security config
├── app/build.gradle           ← Dependencies
├── build.gradle
├── settings.gradle
├── gradlew                    ← Build script
└── channels.json              ← Data channel (diambil oleh app)
```

---

## 🔧 Requirement

- Android **5.0** (API 21) ke atas
- Koneksi internet (untuk load channel & streaming)
- TV Box dengan remote D-Pad

---

## 📝 Lisensi

MIT License — bebas digunakan dan dimodifikasi.
