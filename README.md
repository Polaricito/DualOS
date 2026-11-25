# DualOS 2.0.0 — Galaxy A53 5G Edition

**DualOS** runs full Android 16 QPR0 custom ROM GSIs (**LineageOS 23** and **Project Infinity-X 3.5**) side-by-side next to stock **One UI 8** on a rooted **Samsung Galaxy A53 5G (SM-A536, Exynos 1280 / `s5e8825`)** — without flashing partitions, and returning cleanly to One UI in place.

---

## What's New in DualOS 2.0.0

- **Jetpack Compose + Material 3 Expressive Redesign**: Both the host app (`:app`, `com.dualos.a53`) and the in-guest companion app (`:guest`, labeled **DualOS** inside guest sessions) have been rebuilt from scratch in Jetpack Compose + Material 3 Expressive (`material3:1.5.0`, Dynamic Color, expressive motion & shapes, wavy progress indicators, segmented controls) with all Samsung SESL9 / OneUIProject dependencies removed.
- **Unified 3-Tab Hub**: Systems selector, readiness checks, Galaxy A53 guest capabilities, settings, and diagnostics/log tools organized cleanly into **Systems**, **Settings**, and **Diagnostics**.
- **Isolated Guest Lockscreen PIN / Password**: Guest lockscreen credentials are verified and stored strictly inside the guest stage (`OneOSGuestLock` in `services_patched.jar`), never touching One UI's Weaver, Gatekeeper, Keystore2, or `vold` keys.
- **Guest NFC (`ONEOS_GUEST_NFC=1|0`)**: Safe guest NFC staging with host vendor HAL restart handoff and symlink-safe first-boot preferences (no NFC factory reset).
- **Safe microSD Access (`ONEOS_GUEST_SDCARD=ro|rw|off`)**: Sanitizes guest fstab `voldmanaged=sdcard:*` entries so guest `vold` never touches raw block devices, and bind-mounts kernel-only SD filesystems into `/storage/emulated/0/ExternalSD` (enforcing read-only when One UI eCryptfs encryption is detected).
- **Smooth Kawase2 Window Blurs (`ONEOS_GUEST_BLUR=1|0`)**: Hardware-accelerated RenderEngine blurs on Mali-G68 with automatic thermal protection (`>= 44 °C` pause / `<= 41 °C` restore).
- **ART & SELinux Reliability Fixes**: Fixes ART `profman` / `odrefresh` boot compilation, repairs guest `plat_seapp_contexts` and `plat_mac_permissions.xml` for `NetworkStackGoogle`, and protects `/data/tombstones` while restoring SELinux Enforcing on return to One UI. *(Note: the kernel remains Permissive during active guest sessions and returns to Enforcing when going back to One UI.)*

---

## Supported Hardware & Systems

| Component | Supported Target |
| :--- | :--- |
| **Device** | Samsung Galaxy A53 5G (`SM-A536E` / `SM-A536B` / `SM-A536*`, `a53x`, Exynos 1280 `s5e8825`) |
| **Host OS** | One UI 8 (Android 16 QPR0, SDK 36, `BP2A.250805.005`) |
| **Root** | Magisk, KernelSU, or APatch |
| **Guest Systems** | **LineageOS 23** (Android 16 QPR0 VANILLA EXT4 GSI) & **Project Infinity-X 3.5** (Android 16 QPR0 VANILLA EXT4 GSI) |

Other Samsung devices are intentionally excluded from DualOS to keep patches, scripts, and APK assets tightly focused on the Galaxy A53 5G.

---

## Building Locally

### Prerequisites
- JDK 21 (`JAVA_HOME`)
- Android SDK with `platforms/android-37.0` and `build-tools/36.0.0`+ (`ANDROID_HOME`)
- Python 3 (for overlay verification & shell checks)

### 1. Build & Stage the Guest Companion App (`:guest`)
```bash
# Debug-signed local build (or pass `release` with ~/.oneos-release/keystore.properties)
scripts/build-guest-manager.sh --debug
```

### 2. Build Overlay Payloads (when updating framework/services patches)
```bash
scripts/make-overlays.sh
```

### 3. Run Unit Tests & Verification
```bash
cd app-android
./gradlew :core:model:test :core:safety:test :core:root:testDebugUnitTest
```

### 4. Build Host APKs (`:app`)
```bash
cd app-android

# DualOS Full (with locally built out/overlays_* packed into APK)
./gradlew :app:assembleFullDebug -Poneos.series=a53 -Poneos.packSets=true

# DualOS Probe (read-only diagnostics edition)
./gradlew :app:assembleProbeDebug
```

---

## Safety Model

DualOS never flashes partitions or modifies One UI system partitions. Every destructive or root-privileged action requires explicit user confirmation and passes strict safety gates (`core/safety` + `oneos_root.sh` + `oneos_switch.sh`), including:
- Host lockscreen safety check (with a mandatory 5-second warning dialog if explicitly overridden)
- Read-only `DualOS Probe` build variant verified by `EditionsTest` and `ProbeApkCheckTask`
- Multiple guaranteed ways back to One UI: **DualOS** guest app ("Back to One UI"), Quick Settings tile, holding **Volume Up + Volume Down** for 3 seconds, or force-restarting with Power + Volume Down.

---

## Credits & Upstream Attribution

- **[OneOS](https://github.com/fahadalijaved/OneOS)** — Original upstream OneOS application, framework patch architecture, and switcher engine that DualOS builds upon.
- **[OneLineage](https://github.com/WeWaffles/OneLineage)** — Original session switcher concepts and shell architecture.
- **[LineageOS](https://lineageos.org)** & **[Project Infinity-X](https://github.com/ProjectInfinity-X)** — Supported Android 16 QPR0 GSIs.
- **Jetpack Compose & Material 3 Expressive** — AndroidX UI toolkit (Apache 2.0 License).

*Not affiliated with Samsung, Google, LineageOS, or Project Infinity-X. Galaxy, One UI, and Android are trademarks of their respective owners.*
