# MyChord

App muzik peribadi untuk Android — dengar lagu dari YouTube Music tanpa iklan, import senarai main Spotify, main fail FLAC lossless dalam telefon, tema & ikon boleh tukar, Bahasa Melayu / English, dan sistem addon auto-pasang.

## Muat turun APK
Pergi ke tab **Releases** dan muat turun fail `MyChord-v1.0.x.apk` terkini. APK dibina automatik oleh GitHub Actions setiap kali kod dikemas kini.

## MyChord Hi-Fi Engine (v1.0.23)
Butang **Hi-Fi** di skrin pemain:
- Papar kualiti **sebenar** stream: codec, bitrate, kadar sampel, kedalaman bit, lossless/lossy, output & peranti (USB DAC / Bluetooth / earphone).
- **Mod Hi-Fi Tulen** — matikan semua EQ/kesan, laluan 32-bit float (FLAC 24-bit tidak dipotong), kualiti tertinggi.
- **Bit-perfect ke USB DAC** (Android 14+).

> YouTube Music hanya beri audio lossy (Opus/AAC). MyChord tidak memaparkan label FLAC palsu — lossless sebenar untuk fail FLAC/WAV/ALAC dan radio FLAC.

## Addon
Katalog addon ada dalam `addons/catalog.json`. Tambah addon baharu di situ dan app akan memasangnya sendiri (jika "Auto-pasang" dihidupkan).

> MyChord tiada kaitan dengan YouTube, Google atau Spotify. Untuk kegunaan peribadi.
