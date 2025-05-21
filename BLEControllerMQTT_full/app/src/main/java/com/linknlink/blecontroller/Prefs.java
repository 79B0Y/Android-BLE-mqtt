package com.linknlink.blecontroller;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.XmlResourceParser;
import android.util.Log;

import org.xmlpull.v1.XmlPullParser;

/**
 * 读取 / 保存 MQTT 配置
 * • 第一次启动：从 res/xml/mqtt_config.xml 加载
 * • 运行期：MQTTConfigReceiver 可调用 Prefs.set() 动态修改
 */
public final class Prefs {

    private static final String TAG = "Prefs";
    private static final String SP_NAME = "mqtt_cfg";

    /** XML 属性名 */
    private static final String ATTR_HOST = "host";
    private static final String ATTR_USER = "username";
    private static final String ATTR_PASS = "password";
    private static final String ATTR_PREFIX = "topic_prefix";

    /* 单例 SharedPreferences */
    private static SharedPreferences sp;

    /** 在 Application / Service 启动时调用一次 */
    public static void init(Context ctx) {
        if (sp != null) return;               // 已初始化
        sp = ctx.getSharedPreferences(SP_NAME, Context.MODE_PRIVATE);

        /* 如果尚未写入 host，加载 xml 默认值 */
        if (!sp.contains(ATTR_HOST)) {
            loadDefaultFromXml(ctx);
        }
    }

    /* ---------- Getter ---------- */
    public static String host()   { return sp.getString(ATTR_HOST,   "tcp://127.0.0.1:1883"); }
    public static String user()   { return sp.getString(ATTR_USER,   ""); }
    public static String pass()   { return sp.getString(ATTR_PASS,   ""); }
    public static String prefix() { return sp.getString(ATTR_PREFIX, "ble/"); }

    /* ---------- Setter (供 MQTTConfigReceiver 调用) ---------- */
    public static void set(String key, String value) {
        sp.edit().putString(key, value).apply();
    }

    /* ============ Load Default XML ============ */
    private static void loadDefaultFromXml(Context ctx) {
        try {
            XmlResourceParser parser = ctx.getResources().getXml(R.xml.mqtt_config);
            while (parser.next() != XmlPullParser.END_DOCUMENT) {
                if (parser.getEventType() == XmlPullParser.START_TAG
                        && "mqtt".equals(parser.getName())) {

                    SharedPreferences.Editor ed = sp.edit();
                    ed.putString(ATTR_HOST,   parser.getAttributeValue(null, ATTR_HOST));
                    ed.putString(ATTR_USER,   parser.getAttributeValue(null, ATTR_USER));
                    ed.putString(ATTR_PASS,   parser.getAttributeValue(null, ATTR_PASS));
                    ed.putString(ATTR_PREFIX, parser.getAttributeValue(null, ATTR_PREFIX));
                    ed.apply();
                    break;
                }
            }
            parser.close();
            Log.i(TAG, "Default MQTT config loaded from XML");
        } catch (Exception e) {
            Log.e(TAG, "loadDefaultFromXml", e);
        }
    }

    /* 工具类禁止实例化 */
    private Prefs() {}
}
