############################################################
# BLE-MQTT Controller – ProGuard / R8 keep rules
############################################################

# ---------- Eclipse Paho MQTT ---------- #
# 保留公共 API（org.eclipse.paho）以避免回调接口被混淆
-keep class org.eclipse.paho.** { *; }
-dontwarn org.eclipse.paho.**

# ---------- AndroidX AppCompat / Core ---------- #
-dontwarn androidx.**

# ---------- BLE GATT callback ---------- #
# 保留我们自定义的 GATT 回调方法名（onConnectionStateChange 等）
-keepclassmembers class * extends android.bluetooth.BluetoothGattCallback {
    public void *(...);
}

# ---------- 本项目包 ---------- #
# 保留所有 com.linknlink.blecontroller.* 类 & 成员
-keep class com.linknlink.blecontroller.** { *; }

############################################################
# 如需进一步优化，可在正式发布前结合 R8 报告调整
############################################################
