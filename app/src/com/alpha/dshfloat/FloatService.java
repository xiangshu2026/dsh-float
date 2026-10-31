// DSH 悬浮窗 · 把 DeepSeek Harness 手机页面置顶成悬浮窗
// https://github.com/xiangshu2026/dsh-float

package com.alpha.dshfloat;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.IBinder;
import android.text.InputType;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.webkit.CookieManager;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.ValueCallback;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

public class FloatService extends Service {

    private WindowManager wm;
    private WindowManager.LayoutParams lp;
    private FrameLayout root;
    private WebView web;
    private LinearLayout panel;      // 悬浮窗主体
    private FrameLayout ball;       // 收起后的小球
    private TextView title;
    private String url = "http://127.0.0.1:19387/";
    private String secret = Auth.DEFAULT_SECRET;
    private float alpha = 1f;
    private float scale = 1f;
    private int winW, winH;

    @Override public IBinder onBind(Intent i) { return null; }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && intent.getStringExtra("url") != null) url = intent.getStringExtra("url");
        if (intent != null && intent.getStringExtra("secret") != null) secret = intent.getStringExtra("secret");
        startForeground(1, buildNotification());
        if (wm == null) { wm = (WindowManager) getSystemService(Context.WINDOW_SERVICE); buildUi(); }
        if (intent != null && "HIDE".equals(intent.getAction())) showBall(true);
        return START_STICKY;
    }

    private Notification buildNotification() {
        String ch = "dshfloat";
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            if (nm.getNotificationChannel(ch) == null) {
                NotificationChannel c = new NotificationChannel(ch, "DSH 悬浮窗", NotificationManager.IMPORTANCE_MIN);
                c.setShowBadge(false);
                nm.createNotificationChannel(c);
            }
        }
        Intent open = new Intent(this, MainActivity.class);
        PendingIntent pi = PendingIntent.getActivity(this, 0, open,
                Build.VERSION.SDK_INT >= 23 ? PendingIntent.FLAG_IMMUTABLE : 0);
        Notification.Builder b = Build.VERSION.SDK_INT >= 26
                ? new Notification.Builder(this, ch) : new Notification.Builder(this);
        b.setContentTitle("DSH 悬浮窗运行中").setContentText("点此返回设置")
                .setSmallIcon(android.R.drawable.ic_dialog_info).setContentIntent(pi).setOngoing(true);
        return b.build();
    }

    private int dp(float v) { return (int) (v * getResources().getDisplayMetrics().density + 0.5f); }

    private GradientDrawable round(int color, int r, int strokeC, int strokeW) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(dp(r));
        if (strokeW > 0) d.setStroke(dp(strokeW), strokeC);
        return d;
    }

    private void buildUi() {
        int type = Build.VERSION.SDK_INT >= 26
                ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                : WindowManager.LayoutParams.TYPE_PHONE;

        // 按屏幕自适应：占屏宽 85%、屏高 62%。
        // 注意：Service 里 getResources().getDisplayMetrics() 拿到的屏宽不准（实测偏小），
        // 必须用 WindowManager 的 getRealMetrics 才是真实物理像素。
        android.util.DisplayMetrics dm = new android.util.DisplayMetrics();
        try { wm.getDefaultDisplay().getRealMetrics(dm); } catch (Throwable t) { dm = getResources().getDisplayMetrics(); }
        int screenW = dm.widthPixels;
        int screenH = dm.heightPixels;
        winW = Math.max(dp(280), (int) (screenW * 0.85f));
        winH = Math.max(dp(320), (int) (screenH * 0.62f));

        lp = new WindowManager.LayoutParams(
                winW, winH, type,
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                        | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT);
        lp.gravity = Gravity.TOP | Gravity.START;
        lp.x = Math.max(0, screenW - winW - dp(8));
        lp.y = dp(90);
        lp.softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE;

        root = new FrameLayout(this);

        // ===== 悬浮窗主体 =====
        panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setBackground(round(0xF214161A, 16, 0x55FB7299, 1));

        // 顶栏
        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setBackground(round(0xF2181A20, 16, 0, 0));
        bar.setPadding(dp(10), dp(6), dp(4), dp(6));

        title = new TextView(this);
        title.setText("DSH");
        title.setTextColor(0xFFFFFFFF);
        title.setTextSize(13);
        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        title.setLayoutParams(tlp);
        bar.addView(title);

        bar.addView(barBtn("☰", v -> toggleSidebar()));
        bar.addView(barBtn("一", v -> showBall(true)));
        bar.addView(barBtn("↻", v -> web.reload()));
        bar.addView(barBtn("A-", v -> setScale(scale - 0.1f)));
        bar.addView(barBtn("A+", v -> setScale(scale + 0.1f)));
        bar.addView(barBtn("◐", v -> { alpha = alpha > 0.8f ? 0.55f : 1f; lp.alpha = alpha; wm.updateViewLayout(root, lp); }));
        bar.addView(barBtn("✕", v -> stopSelf()));

        panel.addView(bar, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(34)));

        // WebView
        web = new WebView(this);
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setUseWideViewPort(true);
        s.setLoadWithOverviewMode(true);
        s.setSupportZoom(true);
        s.setBuiltInZoomControls(true);
        s.setDisplayZoomControls(false);
        s.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
        s.setCacheMode(WebSettings.LOAD_NO_CACHE);
        s.setUserAgentString(s.getUserAgentString());
        CookieManager.getInstance().setAcceptCookie(true);
        CookieManager.getInstance().setAcceptThirdPartyCookies(web, true);
        web.setWebViewClient(new WebViewClient() {
            @Override public void onPageFinished(WebView v, String u) {
                title.setText(v.getTitle() != null && v.getTitle().length() > 0 ? v.getTitle() : "DSH");
                android.util.Log.i("DSHFloat", "onPageFinished " + u);
                injectTidy(v);   // 注入手机精简样式
            }
            @Override public void onReceivedHttpError(WebView v,
                    android.webkit.WebResourceRequest req, android.webkit.WebResourceResponse err) {
                android.util.Log.w("DSHFloat", "HTTP_ERROR " + err.getStatusCode() + " " + req.getUrl());
            }
            /** 每次请求都确保带上自签 cookie（首帧也不漏） */
            @Override public android.webkit.WebResourceResponse shouldInterceptRequest(
                    WebView v, android.webkit.WebResourceRequest req) {
                try {
                    android.net.Uri uri = req.getUrl();
                    if (uri != null && uri.getHost() != null && uri.getHost().equals(hostOf(url))) {
                        java.util.Map<String, String> hdrs = req.getRequestHeaders();
                        String ck = hdrs == null ? null : hdrs.get("Cookie");
                        if (ck == null && hdrs != null) ck = hdrs.get("cookie");
                        android.util.Log.i("DSHFloat", "REQ " + uri
                                + " hasCookieHeader=" + (ck != null)
                                + " cookieHeader=" + ck
                                + " storeCookie=" + CookieManager.getInstance().getCookie(uri.toString()));
                    }
                } catch (Throwable ignored) {}
                return null;
            }
        });
        web.setWebChromeClient(new WebChromeClient());
        web.setBackgroundColor(0xFF14161A);
        CookieManager.getInstance().setAcceptCookie(true);

        panel.addView(web, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        // 底部工具条
        LinearLayout foot = new LinearLayout(this);
        foot.setOrientation(LinearLayout.HORIZONTAL);
        foot.setGravity(Gravity.CENTER_VERTICAL);
        foot.setPadding(dp(6), dp(4), dp(6), dp(6));
        foot.setBackground(round(0xF2181A20, 12, 0, 0));

        foot.addView(smallBtn("⌨ 输入", v -> askInput()));
        foot.addView(smallBtn("↑ 上", v -> sendKey(19)));
        foot.addView(smallBtn("↓ 下", v -> sendKey(20)));
        foot.addView(smallBtn("回车", v -> sendKey(66)));
        panel.addView(foot, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(36)));

        // 缩放手柄
        TextView grip = new TextView(this);
        grip.setBackground(round(0x66FB7299, 6, 0, 0));
        FrameLayout.LayoutParams glp = new FrameLayout.LayoutParams(dp(22), dp(22));
        glp.gravity = Gravity.BOTTOM | Gravity.END;
        grip.setLayoutParams(glp);
        grip.setOnTouchListener(new View.OnTouchListener() {
            float sx, sy; int sw, sh;
            @Override public boolean onTouch(View v, MotionEvent e) {
                switch (e.getAction()) {
                    case MotionEvent.ACTION_DOWN: sx = e.getRawX(); sy = e.getRawY(); sw = lp.width; sh = lp.height; return true;
                    case MotionEvent.ACTION_MOVE:
                        lp.width = Math.max(dp(200), sw + (int) (e.getRawX() - sx));
                        lp.height = Math.max(dp(240), sh + (int) (e.getRawY() - sy));
                        wm.updateViewLayout(root, lp); return true;
                }
                return false;
            }
        });

        root.addView(panel, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        root.addView(grip);

        // 顶部拖动
        bar.setOnTouchListener(new View.OnTouchListener() {
            float sx, sy; int ox, oy; long down;
            @Override public boolean onTouch(View v, MotionEvent e) {
                switch (e.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        sx = e.getRawX(); sy = e.getRawY(); ox = lp.x; oy = lp.y; down = System.currentTimeMillis(); return true;
                    case MotionEvent.ACTION_MOVE:
                        lp.x = ox + (int) (e.getRawX() - sx);
                        lp.y = oy + (int) (e.getRawY() - sy);
                        wm.updateViewLayout(root, lp); return true;
                    case MotionEvent.ACTION_UP:
                        if (System.currentTimeMillis() - down < 200) { /* 轻点不处理 */ }
                        return true;
                }
                return false;
            }
        });

        // ===== 悬浮球 =====
        ball = new FrameLayout(this);
        TextView dot = new TextView(this);
        dot.setText("D");
        dot.setTextColor(0xFFFFFFFF);
        dot.setTextSize(18);
        dot.setGravity(Gravity.CENTER);
        dot.setBackground(round(0xF2FB7299, 28, 0x66FFFFFF, 2));
        ball.addView(dot, new FrameLayout.LayoutParams(dp(56), dp(56)));
        ball.setVisibility(View.GONE);
        ball.setOnTouchListener(new View.OnTouchListener() {
            float sx, sy; int ox, oy; long down; boolean moved;
            @Override public boolean onTouch(View v, MotionEvent e) {
                switch (e.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        sx = e.getRawX(); sy = e.getRawY(); ox = lp.x; oy = lp.y;
                        down = System.currentTimeMillis(); moved = false; return true;
                    case MotionEvent.ACTION_MOVE:
                        if (Math.abs(e.getRawX() - sx) > dp(6) || Math.abs(e.getRawY() - sy) > dp(6)) moved = true;
                        lp.x = ox + (int) (e.getRawX() - sx);
                        lp.y = oy + (int) (e.getRawY() - sy);
                        wm.updateViewLayout(root, lp); return true;
                    case MotionEvent.ACTION_UP:
                        if (!moved && System.currentTimeMillis() - down < 300) showBall(false);
                        return true;
                }
                return false;
            }
        });

        root.addView(ball, new FrameLayout.LayoutParams(dp(56), dp(56)));

        try {
            wm.addView(root, lp);
        } catch (Exception e) {
            toast("悬浮窗创建失败：" + e.getMessage());
            stopSelf();
            return;
        }

        // 关键：flush() 是异步的，必须「先写 cookie → 等它同步到网络进程 → 再加载页面」
        web.postDelayed(() -> {
            try {
                web.clearCache(true);
                web.clearHistory();
                Auth.install(url, secret);
                CookieManager.getInstance().flush();
                android.util.Log.i("DSHFloat", "step1 cookie written");
            } catch (Throwable t) {
                android.util.Log.e("DSHFloat", "step1 failed", t);
            }
        }, 300);

        web.postDelayed(() -> {
            try {
                Auth.install(url, secret);
                CookieManager.getInstance().flush();
                String stored = CookieManager.getInstance().getCookie(url);
                android.util.Log.i("DSHFloat", "step2 storeLen=" + (stored == null ? 0 : stored.length()));
            } catch (Throwable t) {
                android.util.Log.e("DSHFloat", "step2 failed", t);
            }
        }, 1200);

        // 第三次才真正加载，给网络进程充分时间接收 cookie
        web.postDelayed(() -> {
            try {
                String stored = CookieManager.getInstance().getCookie(url);
                android.util.Log.i("DSHFloat", "step3 loadUrl storeLen=" + (stored == null ? 0 : stored.length()));
                web.loadUrl(bust(url));
            } catch (Throwable t) {
                android.util.Log.e("DSHFloat", "step3 failed", t);
            }
        }, 2200);
    }

    /** 切换会话列表：调用 DSH 原生的「打开/收起侧边栏」按钮 */
    private void toggleSidebar() {
        try {
            String js = "(function(){try{" +
                    "var b=document.body;var on=b.classList.toggle('alpha-sidebar-open');" +
                    "return on?'open':'closed';" +
                    "}catch(e){return 'err:'+e.message;}})()";
            web.evaluateJavascript(js, r -> android.util.Log.i("DSHFloat", "toggleSidebar => " + r));
        } catch (Throwable t) {
            android.util.Log.e("DSHFloat", "toggleSidebar failed", t);
        }
    }

    /** 注入手机精简样式：单栏、大字、藏思考过程、输入框贴底 */
    private void injectTidy(WebView v) {
        try {
            final String css = jsLiteral(TidyCss.CSS);
            // 错误页 / 白屏页可能还没有 documentElement，直接 appendChild 会抛
            // "Cannot read properties of null (reading 'appendChild')"。
            // 所以先探测 DOM 是否可用，不可用就等 250ms 重试，最多 8 次。
            final WebView wv = v;
            final int[] tries = { 0 };
            final Runnable[] push = new Runnable[1];
            push[0] = new Runnable() {
                @Override public void run() {
                    try {
                        String js = "(function(a){try{"
                                + "var root=document.head||document.documentElement||document.body;"
                                + "if(!root){return 'wait:'+a;}"
                                + "var id='alpha-mobile-tidy';"
                                + "var el=document.getElementById(id);"
                                + "if(!el){el=document.createElement('style');el.id=id;root.appendChild(el);}"
                                + "el.textContent=" + css + ";"
                                + "return 'ok';"
                                + "}catch(e){return 'err:'+e.message;}})(" + tries[0] + ")";
                        wv.evaluateJavascript(js, new ValueCallback<String>() {
                            @Override public void onReceiveValue(String r) {
                                android.util.Log.i("DSHFloat", "tidy try" + tries[0] + " => " + r);
                                if (r != null && r.contains("wait:") && tries[0] < 8) {
                                    tries[0]++;
                                    wv.postDelayed(push[0], 250);
                                }
                            }
                        });
                    } catch (Throwable t) {
                        android.util.Log.e("DSHFloat", "injectTidy retry failed", t);
                    }
                }
            };
            push[0].run();
        } catch (Throwable t) {
            android.util.Log.e("DSHFloat", "injectTidy failed", t);
        }
    }

    /** 把 Java 字符串安全地转成 JS 字符串字面量 */
    private static String jsLiteral(String s) {
        StringBuilder sb = new StringBuilder("'");
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '\\': sb.append("\\\\"); break;
                case '\'': sb.append("\\'"); break;
                case '\n': sb.append("\\n"); break;
                case '\r': sb.append("\\r"); break;
                case '\t': sb.append("\\t"); break;
                default: sb.append(c);
            }
        }
        return sb.append("'").toString();
    }

    /** 追加防缓存参数，避免旧的 401 响应被 WebView 复用 */
    private static String bust(String u) {
        return u + (u.contains("?") ? "&" : "?") + "_=" + System.currentTimeMillis();
    }

    private static String hostOf(String u) {
        try { return new java.net.URL(u).getHost(); } catch (Throwable t) { return null; }
    }

    private void showBall(boolean toBall) {
        if (toBall) {
            lp.width = dp(56); lp.height = dp(56);
            panel.setVisibility(View.GONE); ball.setVisibility(View.VISIBLE);
        } else {
            lp.width = winW; lp.height = winH;
            panel.setVisibility(View.VISIBLE); ball.setVisibility(View.GONE);
        }
        try { wm.updateViewLayout(root, lp); } catch (Exception ignored) {}
    }

    private void setScale(float s) {
        scale = Math.max(0.5f, Math.min(2.0f, s));
        web.setInitialScale((int) (100 * scale));
        toast("缩放 " + Math.round(scale * 100) + "%");
    }

    private void sendKey(int code) {
        try {
            Runtime.getRuntime().exec(new String[]{"input", "keyevent", String.valueOf(code)});
        } catch (Exception ignored) {}
    }

    /** 弹窗输入：应对输入法遮挡 & 中文输入 */
    private void askInput() {
        final EditText et = new EditText(this);
        et.setHint("输入内容后点确定，会送进页面");
        et.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        et.setTextColor(0xFFFFFFFF);
        et.setHintTextColor(0xFF9AA1AE);
        et.setBackground(round(0xFF0F1115, 10, 0x33FFFFFF, 1));
        et.setPadding(dp(14), dp(14), dp(14), dp(14));
        et.setMinLines(2);

        new android.app.AlertDialog.Builder(this)
                .setTitle("输入到 DSH")
                .setView(et)
                .setPositiveButton("确定", (d, w) -> {
                    String t = et.getText().toString();
                    if (t.length() > 0) {
                        // 用 JS 注入到当前焦点元素或页面输入框
                        String js = "(function(){var el=document.activeElement;" +
                                "if(!el||(el.tagName!=='INPUT'&&el.tagName!=='TEXTAREA'&&!el.isContentEditable)){" +
                                "el=document.querySelector('textarea')||document.querySelector('input[type=text]')||document.querySelector('[contenteditable=true]');}" +
                                "if(el){el.focus();" +
                                "if(el.isContentEditable){el.textContent=el.textContent+" + jsStr(t) + ";}else{el.value=el.value+" + jsStr(t) + ";" +
                                "el.dispatchEvent(new Event('input',{bubbles:true}));}" +
                                "return 'ok';}return 'no';})()";
                        web.evaluateJavascript(js, r -> toast(r != null && r.contains("ok") ? "已输入" : "没找到输入框"));
                    }
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private String jsStr(String s) {
        StringBuilder sb = new StringBuilder("'");
        for (char c : s.toCharArray()) {
            if (c == '\\') sb.append("\\\\");
            else if (c == '\'') sb.append("\\'");
            else if (c == '\n') sb.append("\\n");
            else if (c == '\r') continue;
            else sb.append(c);
        }
        return sb.append("'").toString();
    }

    private TextView barBtn(String txt, View.OnClickListener l) {
        TextView t = new TextView(this);
        t.setText(txt);
        t.setTextColor(0xFFFFFFFF);
        t.setTextSize(14);
        t.setGravity(Gravity.CENTER);
        t.setPadding(dp(8), 0, dp(8), 0);
        t.setOnClickListener(l);
        return t;
    }

    private TextView smallBtn(String txt, View.OnClickListener l) {
        TextView t = new TextView(this);
        t.setText(txt);
        t.setTextColor(0xFFEDEFF3);
        t.setTextSize(12);
        t.setGravity(Gravity.CENTER);
        t.setBackground(round(0xFF262933, 10, 0, 0));
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, dp(28), 1f);
        p.setMargins(dp(3), 0, dp(3), 0);
        t.setLayoutParams(p);
        t.setOnClickListener(l);
        return t;
    }

    private void toast(String s) { Toast.makeText(this, s, Toast.LENGTH_SHORT).show(); }

    @Override
    public void onDestroy() {
        try { if (web != null) { web.destroy(); web = null; } } catch (Exception ignored) {}
        try { if (root != null && wm != null) wm.removeView(root); } catch (Exception ignored) {}
        root = null;
        super.onDestroy();
    }
}
