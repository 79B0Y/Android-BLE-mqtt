package com.linknlink.blecontroller;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

/**
 * ADB 广播 → 动态更新 MQTT 配置
 *
 * 示例：
 * adb shell am broadcast -a com.linknlink.MQTT_CFG \
 *       --es host "tcp://192.168.1.2:1883" \
 *       --es username "ha" --es password "mypwd" \
 *       --es prefix "ble/"
 */
public class MQTTConfigReceiver extends BroadcastReceiver {

    private static final String TAG = "MQTTConfigReceiver";

    /* 广播 Action */
    public static final String ACTION_CFG = "com.linknlink.MQTT_CFG";

    @Override
    public void onReceive(Context ctx, Intent intent) {
        if (!ACTION_CFG.equals(intent.getAction())) return;

        /* 确保 Prefs 初始化 */
        Prefs.init(ctx);

        /* 解析并写入 SharedPreferences */
        if (intent.hasExtra("host"))     Prefs.set("host", intent.getStringExtra("host"));
        if (intent.hasExtra("username")) Prefs.set("username", intent.getStringExtra("username"));
        if (intent.hasExtra("password")) Prefs.set("password", intent.getStringExtra("password"));
        if (intent.hasExtra("prefix"))   Prefs.set("topic_prefix", intent.getStringExtra("prefix"));

        Log.i(TAG, "MQTT config updated via ADB");

        /* 如果 BLEService 正在运行且 MQTT 已初始化 → 重新连接 */
        try {
            MQTTManager old = MQTTManager.get();
            if (old != null) {
                old.publish("status", "\"reconnect\"");   // 可选：通知外部
                MQTTManager.init(
                        Prefs.host(),
                        Prefs.user(),
                        Prefs.pass(),
                        Prefs.prefix()
                );
                Log.i(TAG, "MQTT reconnected to " + Prefs.host());
            }
        } catch (Exception e) {
            Log.e(TAG, "MQTT reconnect error", e);
        }
    }
}
