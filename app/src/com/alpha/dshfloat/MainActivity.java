// DSH 悬浮窗 · 把 DeepSeek Harness 手机页面置顶成悬浮窗
// https://github.com/xiangshu2026/dsh-float

package com.alpha.dshfloat;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.ClipboardManager;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

public class MainActivity extends Activity {

    static final String PREF = "dshfloat";
    private TextView status;
    private EditText urlBox;
    private EditText secretBox;
    private CheckBox ballMode;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        SharedPreferences sp = getSharedPreferences(PREF, MODE_PRIVATE);

        ScrollView sc = new ScrollView(this);
        sc.setBackgroundColor(0xFF14161A);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(20), dp(28), dp(20), dp(28));
        sc.addView(root);

        TextView title = new TextView(this);
        title.setText("DSH 悬浮窗");
        title.setTextColor(0xFFEDEFF3);
        title.setTextSize(24);
        title.setGravity(Gravity.START);
        root.addView(title);

        TextView sub = new TextView(this);
        sub.setText("在任意界面上置顶显示 DSH 页面，边打字边操控手机");
        sub.setTextColor(0xFF9AA1AE);
        sub.setTextSize(13);
        sub.setPadding(0, dp(6), 0, dp(18));
        root.addView(sub);

        status = new TextView(this);
        status.setTextSize(13);
        status.setPadding(dp(12), dp(10), dp(12), dp(10));
        status.setBackground(round(0xFF1E2027, 12));
        root.addView(status);

        label(root, "页面地址（含 token 的完整地址最稳）");
        urlBox = new EditText(this);
        urlBox.setText(sp.getString("url", "http://127.0.0.1:19387/"));
        urlBox.setTextColor(0xFFEDEFF3);
        urlBox.setTextSize(13);
        urlBox.setInputType(InputType.TYPE_TEXT_VARIATION_URI);
        urlBox.setBackground(round(0xFF0F1115, 10));
        urlBox.setPadding(dp(12), dp(12), dp(12), dp(12));
        root.addView(urlBox);

        Button paste = new Button(this);
        paste.setText("从剪贴板粘贴地址");
        style(paste, true);
        paste.setOnClickListener(v -> {
            ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            if (cm != null && cm.hasPrimaryClip() && cm.getPrimaryClip().getItemCount() > 0) {
                CharSequence t = cm.getPrimaryClip().getItemAt(0).coerceToText(this);
                if (t != null) { urlBox.setText(t.toString().trim()); toast("已粘贴"); }
            } else toast("剪贴板是空的");
        });
        root.addView(paste);

        label(root, "签名密钥（可留空；填了才能长期免 token）");
        secretBox = new EditText(this);
        secretBox.setText(sp.getString("secret", Auth.DEFAULT_SECRET));
        secretBox.setTextColor(0xFFEDEFF3);
        secretBox.setTextSize(12);
        secretBox.setBackground(round(0xFF0F1115, 10));
        secretBox.setPadding(dp(12), dp(12), dp(12), dp(12));
        root.addView(secretBox);

        ballMode = new CheckBox(this);
        ballMode.setText("启动后收成悬浮球（点球展开）");
        ballMode.setTextColor(0xFFEDEFF3);
        ballMode.setTextSize(13);
        ballMode.setChecked(sp.getBoolean("ball", false));
        ballMode.setPadding(0, dp(12), 0, dp(4));
        root.addView(ballMode);

        label(root, "操作");
        Button grant = new Button(this);
        grant.setText("① 授予悬浮窗权限");
        style(grant, false);
        grant.setOnClickListener(v -> {
            if (Build.VERSION.SDK_INT >= 23) {
                try {
                    Intent i = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + getPackageName()));
                    startActivity(i);
                } catch (Exception e) { toast("打不开权限页，请到 设置→应用→显示在其他应用上层 手动开"); }
            }
        });
        root.addView(grant);

        Button start = new Button(this);
        start.setText("② 启动悬浮窗");
        style(start, true);
        start.setOnClickListener(v -> {
            String u = urlBox.getText().toString().trim();
            if (!u.startsWith("http")) { toast("地址要以 http 开头"); return; }
            SharedPreferences.Editor ed = getSharedPreferences(PREF, MODE_PRIVATE).edit();
            ed.putString("url", u);
            ed.putString("secret", secretBox.getText().toString().trim());
            ed.putBoolean("ball", ballMode.isChecked());
            ed.apply();
            if (Build.VERSION.SDK_INT >= 23 && !Settings.canDrawOverlays(this)) {
                toast("还没给悬浮窗权限，先点①");
                return;
            }
            Intent i = new Intent(this, FloatService.class);
            i.setAction("SHOW");
            i.putExtra("url", u);
            i.putExtra("secret", secretBox.getText().toString().trim());
            i.putExtra("ball", ballMode.isChecked());
            if (Build.VERSION.SDK_INT >= 26) startForegroundService(i); else startService(i);
            toast("已启动，按 Home 键试效果");
        });
        root.addView(start);

        Button stop = new Button(this);
        stop.setText("停止悬浮窗");
        style(stop, false);
        stop.setOnClickListener(v -> {
            stopService(new Intent(this, FloatService.class));
            toast("已停止");
        });
        root.addView(stop);

        TextView tip = new TextView(this);
        tip.setTextSize(12);
        tip.setTextColor(0xFF9AA1AE);
        tip.setPadding(0, dp(16), 0, 0);
        tip.setText("提示：\n" +
                "• 若页面提示 authentication required（401），说明缺 token——在手机浏览器打开 DSH 页面后，长按地址栏复制完整网址，粘贴到上面即可。\n" +
                "• 「签名密钥」留空也能用（靠地址里的 ?token=）。想长期免登录，就填自己 DSH 的密钥：~/.dsh/.credentials.yaml → client-connection/browser-session → payload.secret。每个安装的密钥都不同，抄别人的无效。\n" +
                "• 悬浮窗可拖动顶部条移动，右下角斜纹可拉伸大小。\n" +
                "• 顶栏按钮：一 收成球 / ↻ 刷新 / ⌨ 输入 / A- A+ 缩放 / ◐ 透明度 / ✕ 关闭。\n" +
                "• 首次输入文字时如果输入法遮住内容，点 ⌨ 用弹窗输入，最稳。");
        root.addView(tip);

        TextView ver = new TextView(this);
        ver.setTextSize(11);
        ver.setTextColor(0xFF5C6370);
        ver.setPadding(0, dp(18), 0, 0);
        ver.setText("DSH Float v1.0 · DSH Float · Alpha");
        root.addView(ver);

        setContentView(sc);
    }

    @Override
    protected void onResume() {
        super.onResume();
        boolean ok = Build.VERSION.SDK_INT < 23 || Settings.canDrawOverlays(this);
        status.setText(ok ? "✅ 悬浮窗权限：已授予" : "❌ 悬浮窗权限：未授予（点下面的①）");
        status.setTextColor(ok ? 0xFF4CD964 : 0xFFFF6B6B);
    }

    private void label(LinearLayout p, String t) {
        TextView v = new TextView(this);
        v.setText(t);
        v.setTextColor(0xFF9AA1AE);
        v.setTextSize(12);
        v.setPadding(0, dp(18), 0, dp(6));
        p.addView(v);
    }

    private void style(Button b, boolean accent) {
        b.setAllCaps(false);
        b.setTextSize(15);
        b.setTextColor(accent ? 0xFFFFFFFF : 0xFFEDEFF3);
        b.setBackground(round(accent ? 0xFFFB7299 : 0xFF262933, 14));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(48));
        lp.topMargin = dp(10);
        b.setLayoutParams(lp);
    }

    private GradientDrawable round(int color, int r) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(dp(r));
        return d;
    }

    private int dp(int v) { return (int) (v * getResources().getDisplayMetrics().density + 0.5f); }
    private void toast(String s) { Toast.makeText(this, s, Toast.LENGTH_SHORT).show(); }
}
