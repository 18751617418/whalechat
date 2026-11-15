package com.xixi.whale;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.util.Base64;
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
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

public class MainActivity extends Activity {

    private SharedPreferences prefs;
    private boolean inChat = false;
    private static final int REQ_PICK = 1001;
    private LinearLayout emojiBarRef;
    private String chatBaseUrl = "";
    private String chatName = "";
    private LinearLayout chatMsgsRef;
    private ScrollView chatScrollRef;
    private final Handler ui = new Handler(Looper.getMainLooper());
    private static final String DEFAULT_URL = "http://192.168.1.5:8899";
    private static final String[] EMOJI = {"😀","😂","🥺","😭","👍","❤️"};

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        prefs = getSharedPreferences("whale", MODE_PRIVATE);
        showList();
    }

    @Override
    public void onBackPressed() {
        if (inChat) {
            showList();
            return;
        }
        super.onBackPressed();
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

        inChat = false;
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
        String lastMsg = prefs.getString("last_" + idx, "");
        TextView d = label(lastMsg.length() > 0 ? lastMsg : desc, 12,
                lastMsg.length() > 0 ? Color.parseColor("#5A6478") : Color.parseColor("#8A93A6"), false);
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
        input.setText(prefs.getString("url_" + idx, DEFAULT_URL));
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
        chatScrollRef = scroll;
        final LinearLayout msgs = new LinearLayout(this);
        chatMsgsRef = msgs;
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
        emojiBarRef = emojiBar;
        chatBaseUrl = prefs.getString("url_" + idx, DEFAULT_URL);
        chatName = name;

        TextView addBtn = label("＋", 20, Color.parseColor("#3D6AF2"), true);
        addBtn.setPadding(dp(8), dp(2), dp(8), dp(2));
        addBtn.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { pickEmoji(); }
        });
        emojiBar.addView(addBtn);
        loadCustomEmoji(emojiBar);

        android.widget.HorizontalScrollView hsv = new android.widget.HorizontalScrollView(this);
        hsv.setHorizontalScrollBarEnabled(false);
        hsv.addView(emojiBar);
        page.addView(hsv);

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
        inChat = true;
        setContentView(page);

        emojiBtn.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                emojiBar.setVisibility(emojiBar.getVisibility() == View.VISIBLE ? View.GONE : View.VISIBLE);
            }
        });

        for (int i = 0; i < emojiBar.getChildCount(); i++) {
            View child = emojiBar.getChildAt(i);
            if (!(child instanceof TextView)) continue;
            final TextView t = (TextView) child;
            if ("＋".equals(t.getText().toString())) continue;
            t.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) {
                    input.append(t.getText().toString());
                }
            });
        }

        bubble(msgs, scroll, "我是" + name + "，有什么就说吧。", false);

        final String baseUrl0 = prefs.getString("url_" + idx, DEFAULT_URL);
        if (baseUrl0 != null && baseUrl0.length() > 0) {
            new Thread(new Runnable() {
                public void run() {
                    final String json = get(baseUrl0, "/history");
                    ui.post(new Runnable() {
                        public void run() {
                            try {
                                org.json.JSONObject root = new org.json.JSONObject(json);
                                org.json.JSONArray arr = root.optJSONArray("messages");
                                if (arr != null && arr.length() > 0) {
                                    msgs.removeAllViews();
                                    for (int i = 0; i < arr.length(); i++) {
                                        org.json.JSONObject m = arr.getJSONObject(i);
                                        String t = m.optString("text", "");
                                        if (t.length() > 0) {
                                            bubble(msgs, scroll, t, !m.optBoolean("mine", false));
                                        }
                                    }
                                }
                            } catch (Exception ignored) {
                            }
                        }
                    });
                }
            }).start();
        }

        send.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                String text = input.getText().toString().trim();
                if (text.length() == 0) return;
                input.setText("");
                bubble(msgs, scroll, text, true);
                prefs.edit().putString("last_" + idx, text).apply();
                final String url = prefs.getString("url_" + idx, DEFAULT_URL);
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
                                prefs.edit().putString("last_" + idx,
                                        reply.length() > 24 ? reply.substring(0, 24) + "…" : reply).apply();
                            }
                        });
                    }
                }).start();
            }
        });
    }


    private void pickEmoji() {
        Intent i = new Intent(Intent.ACTION_GET_CONTENT);
        i.setType("image/*");
        i.addCategory(Intent.CATEGORY_OPENABLE);
        try {
            startActivityForResult(Intent.createChooser(i, "选一张当表情"), REQ_PICK);
        } catch (Exception e) {
            Toast.makeText(this, "打不开相册", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        if (req != REQ_PICK || res != RESULT_OK || data == null || data.getData() == null) return;
        try {
            InputStream is = getContentResolver().openInputStream(data.getData());
            File f = new File(getFilesDir(), "emoji_" + System.currentTimeMillis() + ".jpg");
            FileOutputStream os = new FileOutputStream(f);
            byte[] buf = new byte[8192];
            int n;
            while ((n = is.read(buf)) > 0) os.write(buf, 0, n);
            os.close();
            is.close();
            String list = prefs.getString("emoji_files", "");
            String next = list.length() == 0 ? f.getAbsolutePath() : list + "|" + f.getAbsolutePath();
            prefs.edit().putString("emoji_files", next).apply();
            if (emojiBarRef != null) addEmojiView(emojiBarRef, f);
            Toast.makeText(this, "加上了", Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Toast.makeText(this, "存不下来", Toast.LENGTH_SHORT).show();
        }
    }

    private void loadCustomEmoji(LinearLayout bar) {
        String list = prefs.getString("emoji_files", "");
        if (list.length() == 0) return;
        for (String path : list.split("\\|")) {
            File f = new File(path);
            if (f.exists()) addEmojiView(bar, f);
        }
    }

    private void addEmojiView(LinearLayout bar, final File f) {
        ImageView iv = new ImageView(this);
        try {
            Bitmap b = BitmapFactory.decodeFile(f.getAbsolutePath());
            if (b == null) return;
            int size = dp(34);
            Bitmap scaled = Bitmap.createScaledBitmap(b, size, size, true);
            iv.setImageBitmap(scaled);
        } catch (Exception e) {
            return;
        }
        iv.setPadding(dp(4), dp(2), dp(4), dp(2));
        iv.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { sendEmojiFile(f); }
        });
        bar.addView(iv, bar.getChildCount() - 1);
    }

    private void sendEmojiFile(final File f) {
        if (chatMsgsRef == null) return;
        bubbleImage(chatMsgsRef, chatScrollRef, f);
        final String base = chatBaseUrl;
        final String name = chatName;
        new Thread(new Runnable() {
            public void run() {
                final String reply = postImage(base, name, f);
                ui.post(new Runnable() {
                    public void run() { bubble(chatMsgsRef, chatScrollRef, reply, false); }
                });
            }
        }).start();
    }

    private String postImage(String base, String name, File f) {
        HttpURLConnection conn = null;
        try {
            byte[] bytes = new byte[(int) f.length()];
            InputStream is = new java.io.FileInputStream(f);
            int off = 0, n;
            while (off < bytes.length && (n = is.read(bytes, off, bytes.length - off)) > 0) off += n;
            is.close();
            String b64 = Base64.encodeToString(bytes, Base64.NO_WRAP);
            URL u = new URL(base.replaceAll("/+$", "") + "/chat");
            conn = (HttpURLConnection) u.openConnection();
            conn.setRequestMethod("POST");
            conn.setConnectTimeout(8000);
            conn.setReadTimeout(120000);
            conn.setDoOutput(true);
            conn.setRequestProperty("Content-Type", "application/json; charset=utf-8");
            JSONObject body = new JSONObject();
            body.put("name", name);
            body.put("image", b64);
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
            if (code >= 400) return "（图片没发出去：" + code + "）";
            String r = new JSONObject(sb.toString()).optString("reply", "");
            return r.length() == 0 ? "（它看到这张表情了）" : r;
        } catch (Exception e) {
            return "（表情没发出去：" + e.getClass().getSimpleName() + "）";
        } finally {
            if (conn != null) conn.disconnect();
        }
    }

    private String get(String base, String path) {
        HttpURLConnection conn = null;
        try {
            URL u = new URL(base.replaceAll("/+$", "") + path);
            conn = (HttpURLConnection) u.openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(8000);
            conn.setReadTimeout(30000);
            int code = conn.getResponseCode();
            if (code >= 400) return "{}";
            BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream(), "UTF-8"));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = br.readLine()) != null) sb.append(line);
            br.close();
            return sb.toString();
        } catch (Exception e) {
            return "{}";
        } finally {
            if (conn != null) conn.disconnect();
        }
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

    private void bubbleImage(LinearLayout box, final ScrollView scroll, File f) {
        LinearLayout holder = new LinearLayout(this);
        holder.setOrientation(LinearLayout.HORIZONTAL);
        holder.setGravity(Gravity.END);
        LinearLayout.LayoutParams hp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        hp.setMargins(0, dp(4), 0, dp(4));
        holder.setLayoutParams(hp);
        ImageView iv = new ImageView(this);
        try {
            Bitmap b = BitmapFactory.decodeFile(f.getAbsolutePath());
            if (b != null) iv.setImageBitmap(b);
        } catch (Exception ignored) {
        }
        int max = (int) (getResources().getDisplayMetrics().widthPixels * 0.55);
        iv.setAdjustViewBounds(true);
        iv.setMaxWidth(max);
        iv.setMaxHeight(max);
        holder.addView(iv);
        box.addView(holder);
        scroll.post(new Runnable() {
            public void run() { scroll.fullScroll(View.FOCUS_DOWN); }
        });
    }

    private void bubble(LinearLayout box, final ScrollView scroll, String text, boolean mine) {
        LinearLayout holder = new LinearLayout(this);
        holder.setOrientation(LinearLayout.HORIZONTAL);
        holder.setGravity(mine ? Gravity.END : Gravity.START);
        LinearLayout.LayoutParams hp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        hp.setMargins(0, dp(4), 0, dp(4));
        holder.setLayoutParams(hp);

        if (!mine) {
            TextView av = label(chatName.length() > 0 ? chatName.substring(0, 1) : "鲸",
                    13, Color.WHITE, true);
            av.setGravity(Gravity.CENTER);
            av.setBackground(round(chatName.equals("大鲸")
                    ? Color.parseColor("#8B6CEF") : Color.parseColor("#3D6AF2"), 19));
            LinearLayout.LayoutParams alp = new LinearLayout.LayoutParams(dp(38), dp(38));
            alp.setMargins(0, 0, dp(8), 0);
            av.setLayoutParams(alp);
            holder.addView(av);
        }

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
