package com.linknlink.blecontroller;

import android.util.Log;

import org.eclipse.paho.client.mqttv3.IMqttActionListener;
import org.eclipse.paho.client.mqttv3.IMqttMessageListener;
import org.eclipse.paho.client.mqttv3.MqttCallback;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;

/**
 * 单例 MQTT 管理器
 *  • 初始化连接
 *  • 统一 publish / subscribe 接口
 *  • 自动重连（简单实现）
 */
public class MQTTManager {

    private static final String TAG = "MQTT";

    private static MQTTManager INSTANCE;
    private final MqttClient client;
    private final String prefix;

    /* ============ 初始化 ============ */
    private MQTTManager(String uri, String user, String pass, String topicPrefix) throws MqttException {
        prefix = topicPrefix;
        client = new MqttClient(uri, MqttClient.generateClientId(), null);

        /* 连接配置 */
        MqttConnectOptions opts = new MqttConnectOptions();
        opts.setAutomaticReconnect(true);       // 自动重连
        opts.setCleanSession(true);
        if (!user.isEmpty()) opts.setUserName(user);
        if (!pass.isEmpty()) opts.setPassword(pass.toCharArray());

        /* 连接 Broker */
        client.setCallback(callback);
        client.connect(opts);
        Log.i(TAG, "MQTT connected → " + uri);
    }

    /**
     * 单例初始化
     */
    public static synchronized MQTTManager init(String uri, String user, String pass, String prefix) throws MqttException {
        if (INSTANCE == null) INSTANCE = new MQTTManager(uri, user, pass, prefix);
        return INSTANCE;
    }
    public static MQTTManager get() {
        return INSTANCE;
    }

    /* ============ 对外接口 ============ */
    /** 发布 JSON 字符串，自动添加前缀 */
    public void publish(String subTopic, String json) {
        try {
            client.publish(prefix + subTopic, new MqttMessage(json.getBytes()));
        } catch (Exception e) {
            Log.e(TAG, "publish error", e);
        }
    }

    /** 订阅主题（相对前缀） */
    public void subscribe(String subTopic, IMqttMessageListener listener) throws MqttException {
        client.subscribe(prefix + subTopic, listener);
    }

    /* ============ 回调 ============ */
    private final MqttCallback callback = new MqttCallback() {
        @Override public void connectionLost(Throwable cause) {
            Log.w(TAG, "MQTT lost: " + cause);
        }
        @Override public void messageArrived(String topic, MqttMessage message) { /* 统一在各自 subscribe 回调处理 */ }
        @Override public void deliveryComplete(org.eclipse.paho.client.mqttv3.IMqttDeliveryToken token) { /* optional */ }
    };
}
