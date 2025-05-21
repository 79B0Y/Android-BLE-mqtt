package com.linknlink.blecontroller;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothLeScanner;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanResult;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;

import androidx.core.app.NotificationCompat;

import org.eclipse.paho.client.mqttv3.IMqttMessageListener;
import org.json.JSONObject;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 前台 Service：
 *  • 扫描 BLE 设备，并通过 MQTT 发布 scan 结果
 *  • 根据 MQTT 指令连接 / 读 / 写特征
 *  • 连接状态 & 数据通过 MQTT 回传
 */
public class BLEService extends Service {

    private static final String TAG = "BLEService";
    private static final String NOTI_CHANNEL_ID = "ble_channel";

    /* BLE */
    private BluetoothLeScanner scanner;
    private final Map<String, BluetoothGatt> gattMap = new ConcurrentHashMap<>();

    /* ============ Lifecycle ============ */
    @Override
    public void onCreate() {
        super.onCreate();

        /* 初始化 MQTT（Prefs 在 ApplicationContext 下已读取） */
        try {
            MQTTManager.init(
                    Prefs.host(),
                    Prefs.user(),
                    Prefs.pass(),
                    Prefs.prefix()
            );
            subscribeControlTopics();
        } catch (Exception e) {
            Log.e(TAG, "MQTT init error", e);
        }

        /* 创建前台通知，避免被系统杀掉 */
        startForeground(1, buildForegroundNotification());
    }

    @Override
    public IBinder onBind(Intent intent) {
        /* 本服务无需绑定 */
        return null;
    }

    /* ============ Foreground Notification ============ */
    private Notification buildForegroundNotification() {
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel ch = new NotificationChannel(
                    NOTI_CHANNEL_ID, "BLE MQTT",
                    NotificationManager.IMPORTANCE_LOW
            );
            getSystemService(NotificationManager.class).createNotificationChannel(ch);
        }
        return new NotificationCompat.Builder(this, NOTI_CHANNEL_ID)
                .setSmallIcon(android.R.drawable.stat_sys_data_bluetooth)
                .setContentTitle("BLE-MQTT Controller 正在运行 …")
                .build();
    }

    /* ============ MQTT Subscribe 指令 ============ */
    private void subscribeControlTopics() throws Exception {
        MQTTManager.get().subscribe("cmd/#", controlListener);
    }

    /** 处理下行控制指令 */
    private final IMqttMessageListener controlListener = (topic, msg) -> {
        String[] seg = topic.split("/");
        if (seg.length < 2) return;

        String cmd = seg[1];
        switch (cmd) {
            case "scan":
                startScan();
                break;

            case "connect":            // ble/cmd/connect/<MAC>
                if (seg.length >= 3) connectDevice(seg[2]);
                break;

            case "write":
                handleWrite(new String(msg.getPayload()));
                break;

            case "read":
                handleRead(new String(msg.getPayload()));
                break;
        }
    };

    /* ============ 扫描 ============ */
    private void startScan() {
        BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
        if (adapter == null || !adapter.isEnabled()) {
            Log.e(TAG, "Bluetooth disabled");
            return;
        }
        if (scanner == null) scanner = adapter.getBluetoothLeScanner();

        Log.i(TAG, "Begin BLE scan …");
        scanner.startScan(scanCallback);
    }

    private final ScanCallback scanCallback = new ScanCallback() {
        @Override
        public void onScanResult(int callbackType, ScanResult result) {
            BluetoothDevice dev = result.getDevice();
            try {
                JSONObject js = new JSONObject();
                js.put("mac", dev.getAddress());
                js.put("name", dev.getName());
                js.put("rssi", result.getRssi());
                MQTTManager.get().publish("scan", js.toString());
            } catch (Exception ignore) {}
        }
    };

    /* ============ 连接 ============ */
    private void connectDevice(String mac) {
        BluetoothDevice dev = BluetoothAdapter.getDefaultAdapter().getRemoteDevice(mac);
        if (dev == null) {
            Log.e(TAG, "Device not found: " + mac);
            return;
        }
        Log.i(TAG, "Connecting to " + mac);
        dev.connectGatt(this, false, gattCallback);
    }

    private final BluetoothGattCallback gattCallback = new BluetoothGattCallback() {
        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            String mac = gatt.getDevice().getAddress();
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                gatt.discoverServices();
                gattMap.put(mac, gatt);
                MQTTManager.get().publish("device/" + mac + "/status", "\"connected\"");
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                gattMap.remove(mac);
                MQTTManager.get().publish("device/" + mac + "/status", "\"disconnected\"");
            }
        }

        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
            Log.i(TAG, "Services discovered on " + gatt.getDevice().getAddress());
        }

        @Override
        public void onCharacteristicRead(BluetoothGatt gatt, BluetoothGattCharacteristic ch, int status) {
            if (status == BluetoothGatt.GATT_SUCCESS) publishCharacteristic(gatt, ch, "read");
        }

        @Override
        public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic ch) {
            publishCharacteristic(gatt, ch, "notify");
        }
    };

    /* ============ 发布特征数据 ============ */
    private void publishCharacteristic(BluetoothGatt gatt, BluetoothGattCharacteristic ch, String event) {
        try {
            JSONObject js = new JSONObject();
            js.put("svc", ch.getService().getUuid());
            js.put("char", ch.getUuid());
            js.put("hex", bytesToHex(ch.getValue()));

            String mac = gatt.getDevice().getAddress();
            MQTTManager.get().publish("device/" + mac + "/" + event, js.toString());
        } catch (Exception ignore) {}
    }

    /* ============ 处理 Write / Read 指令 ============ */
    private void handleWrite(String payload) {
        try {
            JSONObject js = new JSONObject(payload);
            String mac = js.getString("mac");

            BluetoothGatt gatt = gattMap.get(mac);
            if (gatt == null) return;

            BluetoothGattCharacteristic ch = getCharacteristic(gatt,
                    js.getString("svc"), js.getString("char"));
            if (ch == null) return;

            ch.setValue(hexToBytes(js.getString("hex")));
            gatt.writeCharacteristic(ch);
        } catch (Exception e) {
            Log.e(TAG, "handleWrite error", e);
        }
    }

    private void handleRead(String payload) {
        try {
            JSONObject js = new JSONObject(payload);
            String mac = js.getString("mac");

            BluetoothGatt gatt = gattMap.get(mac);
            if (gatt == null) return;

            BluetoothGattCharacteristic ch = getCharacteristic(gatt,
                    js.getString("svc"), js.getString("char"));
            if (ch == null) return;

            gatt.readCharacteristic(ch);
        } catch (Exception e) {
            Log.e(TAG, "handleRead error", e);
        }
    }

    private BluetoothGattCharacteristic getCharacteristic(BluetoothGatt gatt, String svcUuid, String charUuid) {
        BluetoothGattService svc = gatt.getService(UUID.fromString(svcUuid));
        return (svc == null) ? null : svc.getCharacteristic(UUID.fromString(charUuid));
    }

    /* ============ Utils ============ */
    private static byte[] hexToBytes(String hex) {
        int len = hex.length();
        byte[] data = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            data[i / 2] = (byte) ((Character.digit(hex.charAt(i), 16) << 4)
                                + Character.digit(hex.charAt(i + 1), 16));
        }
        return data;
    }

    private static String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) sb.append(String.format("%02X", b));
        return sb.toString();
    }
}
