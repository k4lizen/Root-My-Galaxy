# Root My Galaxy

<img width="108" height="108" alt="sprout_icon_108" src="https://github.com/user-attachments/assets/2ba0e360-0876-489c-b256-f75df7589785" />


*This fork adds an offline install so your phone doesn't reach out for OTA updates. its slopped*

Root My Galaxy is a one-click installer for explicitly
supported Samsung model and kernel combinations. The application itself is kept separate
from device offsets, native exploit payloads, and KernelSU build artifacts.


[Latest release](https://github.com/BuSung-dev/Root-My-Galaxy/releases)

The device feed and native payloads are maintained in
[Root-My-Galaxy-Payloads](https://github.com/BuSung-dev/Root-My-Galaxy-Payloads).

## Application


<img width="200" alt="KakaoTalk_20260718_170922353" src="https://github.com/user-attachments/assets/3f562ea4-8c39-4ade-bfd3-93eea1a1cc24" />
<img width="200" alt="KakaoTalk_20260718_171127319" src="https://github.com/user-attachments/assets/8dde0443-12cf-4058-ba76-0337aefb92a0" />
<img width="200" alt="KakaoTalk_20260718_171030202" src="https://github.com/user-attachments/assets/f656e8af-60a6-4fcb-a3db-d4232bede613" />

The app selects a payload whose model list and three-part kernel version match
the phone. For example, `6.6.98-android15-8-...` matches `6.6.98`. Advanced
mode filters the catalog by both values and allows manual selection with model
and kernel-version warnings.

## Offline install

Install the app first:
```bash
adb install app-debug.apk
```

You'll need to fetch the artifacts that the app otherwise fetches itself.

Find out your model and kernel
```bash
adb shell getprop ro.product.model    # e.g. SM-S938B
adb shell uname -r                    # e.g. 6.6.98-android15-8-...
```

See list of supported targets
```bash
curl -s https://raw.githubusercontent.com/BuSung-dev/Root-My-Galaxy-Payloads/main/support/targets-v3.json | jq .
```

Download the proper targets exploits (`exploit.url`) and kernelsu (`kernelsu.url`). And push both to the phone.
```bash
adb push cve-2026-43499-app.so /sdcard/Download/
adb push ksud-s25u-kdp         /sdcard/Download/
```

In the app, turn on **Settings -> Advanced -> Offline mode**, then pick each
file under **Offline payloads**, then run the install.

## Build

Requirements:

- Android Studio JBR 21
- Android SDK 37
- Android NDK 28 or newer
- CMake 3.22.1

```powershell
$env:JAVA_HOME='C:\Program Files\Android\Android Studio\jbr'
.\gradlew.bat :app:assembleDebug
```

Output:

```text
app/build/outputs/apk/debug/app-debug.apk
```

Use only on devices you own or are explicitly authorized to test.
