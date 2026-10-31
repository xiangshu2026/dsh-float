// DSH 悬浮窗 · 把 DeepSeek Harness 手机页面置顶成悬浮窗
// https://github.com/xiangshu2026/dsh-float

package com.alpha.dshfloat;

import android.util.Base64;
import android.webkit.CookieManager;

import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/** 用 DSH 的持久签名密钥自签浏览器 cookie，绕开一次性 token 过期问题。 */
public final class Auth {

    /**
     * 默认密钥：**故意留空**。
     *
     * 每个 DSH 安装的签名密钥都不同，写死在代码里的密钥只对作者那台机器有效，
     * 对别人必然是 401。请从自己的
     *   ~/.dsh/.credentials.yaml -> client-connection/browser-session -> payload.secret
     * 取出后填到 App 首页的「签名密钥」输入框。
     *
     * 留空也能用——那就走地址里自带的 ?token=xxx（缺点是 DSH 每次重启 token 会变，
     * 需要重新复制地址）。填了密钥则可以长期免 token。
     */
    public static final String DEFAULT_SECRET = "";

    /** 密钥是否可用（base64url 解出来必须是 32 字节，见 DSH 的 canonicalSecret）。 */
    public static boolean usable(String secret) {
        if (secret == null) return false;
        String s = secret.trim();
        if (s.isEmpty()) return false;
        try {
            return b64urlDecode(s).length == 32;
        } catch (Throwable t) {
            return false;
        }
    }

    private Auth() {}

    /** 同时写入「带端口」与「不带端口」两种 authority 的 cookie（Host 头写法不同都能命中） */
    public static void install(String url, String secret) {
        if (!usable(secret)) {
            android.util.Log.i("DSHFloat", "install skipped: no usable secret, rely on ?token= in url");
            return;
        }
        try {
            URL u = new URL(url);
            int port = u.getPort() > 0 ? u.getPort() : 80;
            String withPort = u.getHost() + ":" + port;
            String noPort = u.getHost();
            CookieManager cm = CookieManager.getInstance();
            cm.setAcceptCookie(true);
            setOne(cm, url, withPort, secret);
            if (!noPort.equals(withPort)) setOne(cm, url, noPort, secret);
            cm.flush();
            String back = cm.getCookie(url);
            android.util.Log.i("DSHFloat", "install host=" + noPort
                    + " port=" + port
                    + " withPort=" + cookieName(withPort)
                    + " noPort=" + cookieName(noPort)
                    + " storeLen=" + (back == null ? 0 : back.length()));
        } catch (Throwable t) {
            android.util.Log.e("DSHFloat", "auth install failed", t);
        }
    }

    private static void setOne(CookieManager cm, String url, String authority, String secret) {
        String cookie = cookieName(authority) + "=" + encodeCookie(authority, secret) + "; Path=/";
        cm.setCookie("http://" + authority + "/", cookie);
        cm.setCookie("https://" + authority + "/", cookie);
        cm.setCookie(url, cookie);
    }

    public static String cookieName(String authority) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return "dsh-auth-" + b64url(md.digest(bytes(authority)));
        } catch (Throwable t) {
            return "dsh-auth-";
        }
    }

    public static String encodeCookie(String authority, String secret) {
        android.util.Log.i("DSHFloat", "encodeCookie secretLen=" + (secret == null ? -1 : secret.length())
                + " keyBytes=" + (secret == null ? -1 : b64urlDecode(secret).length));
        long now = System.currentTimeMillis();
        long exp = now + 30L * 24 * 3600 * 1000;
        String payload = "{\"version\":1,\"authority\":\"" + authority
                + "\",\"issuedAt\":" + now + ",\"expiresAt\":" + exp + "}";
        String body = b64url(bytes(payload));
        return "v1." + body + "." + b64url(hmac(secret, body));
    }

    private static byte[] hmac(String secret, String body) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(b64urlDecode(secret), "HmacSHA256"));
            return mac.doFinal(bytes(body));
        } catch (Throwable t) {
            return new byte[32];
        }
    }

    private static byte[] bytes(String s) { return s.getBytes(StandardCharsets.UTF_8); }

    private static String b64url(byte[] data) {
        return Base64.encodeToString(data, Base64.URL_SAFE | Base64.NO_PADDING | Base64.NO_WRAP);
    }

    private static byte[] b64urlDecode(String s) {
        return Base64.decode(s, Base64.URL_SAFE | Base64.NO_PADDING | Base64.NO_WRAP);
    }
}
