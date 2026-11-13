package com.xixi.whale;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

public class MainActivity extends Activity {

    private SharedPreferences prefs;
    private final Handler ui = new Handler(Looper.getMainLooper());
    private static final String[] EMOJI = {"😀","😄","😂","🥺","😭","🤔","👍","🐟","🍚","❤️"};

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        prefs = getSharedPreferences("whale", MODE_PRIVATE);
        showList();
    }

    private int dp(float v) {
        return (int) (v * getResources().getDisplayMetrics().density);
    }

    private GradientDrawable round(int color, float radiusDp) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(dp(radiusDp));
        return g;
    }

    private TextView label(String text, int sizeSp, int color, boolean bold) {
        TextView t = new TextView(this);
        t.setText(text);
        t.setTextSize(sizeSp);
        t.setTextColor(color);
        if (bold) t.setTypeface(null, android.graphics.Typeface.BOLD);
        return t;
    }

    private void showList() {
        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setBackgroundColor(Color.parseColor("#F4F6FA"));

        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.VERTICAL);
        bar.setPadding(dp(20), dp(28), dp(20), dp(14));
        bar.setBackgroundColor(Color.parseColor("#3D6AF2"));
        bar.addView(label("鲸聊", 22, Color.WHITE, true));
        TextView sub = label("长按名字可以设置它的地址", 12, Color.parseColor("#D8E2FF"), false);
        sub.setPadding(0, dp(4), 0, 0);
        bar.addView(sub);
        page.addView(bar);

        page.addView(friendRow("大鲸", "在服务器那边的那一个", "#8B6CEF", 0));
        page.addView(friendRow("小鲸", "在你手机里的这一个", "#3D6AF2", 1));

        setContentView(page);
    }

    private View friendRow(final String name, final String desc, final String colorHex, final int idx) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(16), dp(14), dp(16), dp(14));
        row.setBackgroundColor(Color.WHITE);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(dp(12), dp(10), dp(12), 0);
        row.setLayoutParams(lp);

        TextView avatar = label(name.substring(0, 1), 18, Color.WHITE, true);
        avatar.setGravity(Gravity.CENTER);
        avatar.setBackground(round(Color.parseColor(colorHex), 24));
        LinearLayout.LayoutParams alp = new LinearLayout.LayoutParams(dp(48), dp(48));
        alp.setMargins(0, 0, dp(14), 0);
        avatar.setLayoutParams(alp);
        row.addView(avatar);

        LinearLayout texts = new LinearLayout(this);
        texts.setOrientation(LinearLayout.VERTICAL);
        texts.addView(label(name, 17, Color.parseColor("#1B2130"), true));
        TextView d = label(desc, 12, Color.parseColor("#8A93A6"), false);
        d.setPadding(0, dp(3), 0, 0);
        texts.addView(d);
        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        texts.setLayoutParams(tlp);
        row.addView(texts);

        row.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { showChat(name, idx); }
        });
        row.setOnLongClickListener(new View.OnLongClickListener() {
            public boolean onLongClick(View v) { editEndpoint(name, idx); return true; }
        });
        return row;
    }

    private void editEndpoint(final String name, final int idx) {
        final EditText input = new EditText(this);
        input.setInputType(InputType.TYPE_TEXT_VARIATION_URI);
        input.setText(prefs.getString("url_" + idx, ""));
        input.setHint("http://127.0.0.1:8899");
        new AlertDialog.Builder(this)
                .setTitle(name + " 的地址")
                .setMessage("填 http 开头的服务地址，留空就是没接上")
                .setView(input)
                .setPositiveButton("保存", new android.content.DialogInterface.OnClickListener() {
                    public void onClick(android.content.DialogInterface d, int w) {
                        prefs.edit().putString("url_" + idx, input.getText().toString().trim()).apply();
                        Toast.makeText(MainActivity.this, "记下了", Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void showChat(final String name, final int idx) {
        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setBackgroundColor(Color.parseColor("#F4F6FA"));

        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(dp(12), dp(24), dp(12), dp(12));
        bar.setBackgroundColor(Color.WHITE);
        TextView back = label("‹", 26, Color.parseColor("#3D6AF2"), true);
        back.setPadding(dp(8), 0, dp(12), 0);
        back.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { showList(); }
        });
        bar.addView(back);
        bar.addView(label(name, 18, Color.parseColor("#1B2130"), true));
        page.addView(bar);

        final ScrollView scroll = new ScrollView(this);
        final LinearLayout msgs = new LinearLayout(this);
        msgs.setOrientation(LinearLayout.VERTICAL);
        msgs.setPadding(dp(12), dp(12), dp(12), dp(12));
        scroll.addView(msgs);
        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f);
        page.addView(scroll, slp);

        final LinearLayout emojiBar = new LinearLayout(this);
        emojiBar.setOrientation(LinearLayout.HORIZONTAL);
        emojiBar.setBackgroundColor(Color.WHITE);
        emojiBar.setVisibility(View.GONE);
        emojiBar.setPadding(dp(8), dp(6), dp(8), dp(6));
        for (final String e : EMOJI) {
            TextView t = label(e, 22, Color.BLACK, false);
            t.setPadding(dp(8), dp(2), dp(8), dp(2));
            emojiBar.addView(t);
        }
        page.addView(emojiBar);

        LinearLayout inputBar = new LinearLayout(this);
        inputBar.setOrientation(LinearLayout.HORIZONTAL);
        inputBar.setGravity(Gravity.CENTER_VERTICAL);
        inputBar.setPadding(dp(10), dp(8), dp(10), dp(16));
        inputBar.setBackgroundColor(Color.WHITE);

        TextView emojiBtn = label("☺", 22, Color.parseColor("#3D6AF2"), false);
        emojiBtn.setPadding(dp(8), dp(2), dp(10), dp(2));
        inputBar.addView(emojiBtn);

        final EditText input = new EditText(this);
        input.setHint("说点什么");
        input.setTextSize(15);
        input.setSingleLine(true);
        input.setBackground(round(Color.parseColor("#F0F2F7"), 18));
        input.setPadding(dp(14), dp(8), dp(14), dp(8));
        LinearLayout.LayoutParams ilp = new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        inputBar.addView(input, ilp);

        final Button send = new Button(this);
        send.setText("发送");
        send.setTextSize(14);
        send.setAllCaps(false);
        send.setTextColor(Color.WHITE);
        send.setBackground(round(Color.parseColor("#3D6AF2"), 18));
        LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, dp(38));
        blp.setMargins(dp(8), 0, 0, 0);
        inputBar.addView(send, blp);

        page.addView(inputBar);
        setContentView(page);

        emojiBtn.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                emojiBar.setVisibility(emojiBar.getVisibility() == View.VISIBLE ? View.GONE : View.VISIBLE);
            }
        });

        for (int i = 0; i < emojiBar.getChildCount(); i++) {
            final TextView t = (TextView) emojiBar.getChildAt(i);
            t.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) {
                    input.append(t.getText().toString());
                }
            });
        }

        bubble(msgs, scroll, "我是" + name + "，有什么就说吧。", false);

        send.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                String text = input.getText().toString().trim();
                if (text.length() == 0) return;
                input.setText("");
                bubble(msgs, scroll, text, true);
                final String url = prefs.getString("url_" + idx, "");
                if (url == null || url.length() == 0) {
                    bubble(msgs, scroll, "（还没接上" + name + "，长按好友名字填地址）", false);
                    return;
                }
                bubble(msgs, scroll, "……", false);
                final View thinking = msgs.getChildAt(msgs.getChildCount() - 1);
                new Thread(new Runnable() {
                    public void run() {
                        final String reply = post(url, name, text);
                        ui.post(new Runnable() {
                            public void run() {
                                msgs.removeView(thinking);
                                bubble(msgs, scroll, reply, false);
                            }
                        });
                    }
                }).start();
            }
        });
    }

    private String post(String base, String name, String text) {
        HttpURLConnection conn = null;
        try {
            URL u = new URL(base.replaceAll("/+$", "") + "/chat");
            conn = (HttpURLConnection) u.openConnection();
            conn.setRequestMethod("POST");
            conn.setConnectTimeout(8000);
            conn.setReadTimeout(60000);
            conn.setDoOutput(true);
            conn.setRequestProperty("Content-Type", "application/json; charset=utf-8");
            JSONObject body = new JSONObject();
            body.put("name", name);
            body.put("text", text);
            OutputStream os = conn.getOutputStream();
            os.write(body.toString().getBytes("UTF-8"));
            os.flush();
            os.close();
            int code = conn.getResponseCode();
            BufferedReader br = new BufferedReader(new InputStreamReader(
                    code >= 400 ? conn.getErrorStream() : conn.getInputStream(), "UTF-8"));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = br.readLine()) != null) sb.append(line);
            br.close();
            if (code >= 400) return "（出错：" + code + "）";
            JSONObject resp = new JSONObject(sb.toString());
            String r = resp.optString("reply", "");
            return r.length() == 0 ? "（没说话）" : r;
        } catch (Exception e) {
            return "（连不上" + name + "：" + e.getClass().getSimpleName() + "）";
        } finally {
            if (conn != null) conn.disconnect();
        }
    }

    private void bubble(LinearLayout box, final ScrollView scroll, String text, boolean mine) {
        LinearLayout holder = new LinearLayout(this);
        holder.setOrientation(LinearLayout.HORIZONTAL);
        holder.setGravity(mine ? Gravity.END : Gravity.START);
        LinearLayout.LayoutParams hp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        hp.setMargins(0, dp(4), 0, dp(4));
        holder.setLayoutParams(hp);

        TextView t = new TextView(this);
        t.setText(text);
        t.setTextSize(15);
        t.setTextColor(mine ? Color.WHITE : Color.parseColor("#1B2130"));
        t.setPadding(dp(14), dp(10), dp(14), dp(10));
        t.setBackground(round(mine ? Color.parseColor("#3D6AF2") : Color.WHITE, 14));
        t.setMaxWidth((int) (getResources().getDisplayMetrics().widthPixels * 0.75));
        holder.addView(t);
        box.addView(holder);

        scroll.post(new Runnable() {
            public void run() { scroll.fullScroll(View.FOCUS_DOWN); }
        });
    }
}
