
# BLEControllerMQTT

Android Studio project: BLE (LE) scanner & controller bridged to MQTT.

## Build
```
./gradlew assembleDebug
adb install app/build/outputs/apk/debug/app-debug.apk
```
## MQTT Topics
- `ble/cmd/scan` – start scan
- `ble/cmd/connect/<MAC>` – connect
- `ble/scan` – scan results
...

