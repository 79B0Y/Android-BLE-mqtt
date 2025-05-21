# BLE‑MQTT Controller – 设计说明文档（v1.0）

> 适用平台：iSG (Android 9+)  最后更新：2025‑05‑21

---

## 1  项目目标

| 目标          | 描述                                                 |
| ----------- | -------------------------------------------------- |
| ✔ BLE 扫描    | 后台服务扫描周边低功耗蓝牙设备，输出名称 / MAC / RSSI                  |
| ✔ BLE 连接    | 通过 MQTT 指令连接指定 MAC，维护多连接状态                         |
| ✔ GATT 读写   | 支持读、写、通知订阅（RGB、Sensor 等场景）                         |
| ✔ MQTT 桥接   | 所有控制均通过 MQTT Topic 收发，便于 Home Assistant / n8n 集成   |
| ✔ 动态 Broker | 运行期可通过 ADB 广播修改 Broker、用户名、密码、前缀                   |
| ✔ 无 UI 无上架  | 仅后台 Service + BroadcastReceiver，在已 root 的 iSG 本地运行 |

---

## 2  架构概览

```
┌────────────┐      MQTT (TCP)      ┌──────────────┐
│ HomeAssistant│◀───────────────▶│ BLE MQTT App │
└────────────┘                     │  • BLEService│
                                   │  • MQTTMgr   │
                                   │  • Prefs     │
        ADB Broadcasts             │  • Receiver  │
Termux ────────────────▶┌──────────┤              │
                       ││          └──────────────┘
                       ││  BLE HCI / Stack
                       ││  (系统蓝牙服务)
                       ▼▼
                 低功耗蓝牙设备
```

---

## 3  组件说明

| 模块                   | 包 & 类                                    | 责任                                                   |
| -------------------- | ---------------------------------------- | ---------------------------------------------------- |
| `BLEService`         | `com.linknlink.blecontroller.BLEService` | ① 前台 Service 保活 ② 扫描 / 连接 / GATT ③ 发布事件到 MQTT        |
| `MQTTManager`        | `MQTTManager`                            | 单例封装 Eclipse Paho 客户端，提供 `publish()` & `subscribe()` |
| `Prefs`              | `Prefs`                                  | 初始读取 `res/xml/mqtt_config.xml`，可被广播动态覆盖              |
| `MQTTConfigReceiver` | `BroadcastReceiver`                      | 监听 `com.linknlink.MQTT_CFG`，运行期更新 Broker 配置          |

---

## 4  MQTT Topic 设计

### 4.1 指令（下行）

| Topic                   | Payload              | 说明                         |
| ----------------------- | -------------------- | -------------------------- |
| `ble/cmd/scan`          | *null*               | 触发扫描 30 秒（结果多条 `ble/scan`） |
| `ble/cmd/connect/<MAC>` | *null*               | 建立 GATT 连接                 |
| `ble/cmd/write`         | `{mac,svc,char,hex}` | 写入特征，十六进制字符串               |
| `ble/cmd/read`          | `{mac,svc,char}`     | 读取特征                       |

### 4.2 事件（上行）

| Topic                     | Payload                        | 说明          |
| ------------------------- | ------------------------------ | ----------- |
| `ble/scan`                | `{mac,name,rssi}`              | 每发现一个设备发布一条 |
| `ble/device/<MAC>/status` | `"connected" / "disconnected"` |             |
| `ble/device/<MAC>/read`   | `{svc,char,hex}`               | 主动 read 成功  |
| `ble/device/<MAC>/notify` | 同上                             | 订阅通知回包      |

---

## 5  广播接口（仅配置）

```bash
adb shell am broadcast -a com.linknlink.MQTT_CFG \
        --es host "tcp://192.168.1.2:1883" \
        --es username "ha" --es password "mypw" \
        --es prefix "ble/"
```

---

## 6  BLE 流程

### 6.1 扫描 Sequence

1. HA 发布 `ble/cmd/scan`
2. MQTTMgr 收到 → 调用 `BLEService.startScan()`
3. `onScanResult()` 逐条 publish `ble/scan`

### 6.2 连接 & 读写

```
HA → ble/cmd/connect/MAC     → BLEService.connect()
GATT connected               → ble/device/MAC/status
HA → ble/cmd/write {hex}     → gatt.writeCharacteristic()
WriteCallback (success)      → ble/device/MAC/writeAck
BLE Notify                   → ble/device/MAC/notify
```

---

## 7  权限 & 兼容性

| 权限                           | 说明                                                                                 |
| ---------------------------- | ---------------------------------------------------------------------------------- |
| `BLUETOOTH_CONNECT` / `SCAN` | Android 12+ 所需 Runtime 权限；因 iSG 已 root，可在 `adb shell` 执行 `appops set … allow` 避免弹窗 |
| `FOREGROUND_SERVICE`         | 保证长时运行；前台通知 ID=1，可在系统设置隐藏                                                          |

---

## 8  Build & 部署

```bash
# 1. Android Studio 打开工程
./gradlew assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
# 2. 如需修改 Broker
adb shell am broadcast -a com.linknlink.MQTT_CFG --es host "tcp://…"
# 3. 在 HA / MQTT Explorer 测试指令
mosquitto_pub -t ble/cmd/scan -n
```

---

## 9  后续扩展建议

* **自动重连**：失去连接时重试 N 次并上报 `reconnecting` 状态
* **安全认证**：MQTT TLS & 蓝牙 Bonding/Passkey
* **OTA 升级**：APK 注册 FileProvider，通过 MQTT 下发更新包
* **设备影子**：维持 JSON Shadow 映射简化 HA 自动发现

---

## 10  版本记录

| 版本   | 日期         | 变更                                        |
| ---- | ---------- | ----------------------------------------- |
| v1.0 | 2025‑05‑21 | 初版：扫描 / 连接 / 读写 / MQTT 控制 + ADB Broker 配置 |

---

© 2025 LinknLink ｜ 内部文档，严禁外传。
