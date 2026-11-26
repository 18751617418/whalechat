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
    private static final int REQ_PHOTO = 1003;
    private LinearLayout emojiPanelRef;
    private ScrollView emojiScrollRef;
    private EditText emojiInputRef;
    private String chatBaseUrl = "";
    private int chatIdx = 0;
    private String chatName = "";
    private LinearLayout chatMsgsRef;
    private ScrollView chatScrollRef;
    private final Handler ui = new Handler(Looper.getMainLooper());
    private int pollBaseline = -1;
    private final java.util.Set<String> localReplies = new java.util.HashSet<>();
    private static final String DEFAULT_URL = "http://192.168.1.5:8899";
    private static final String BIG_WHALE_URL = "http://101.37.189.214:8899";

    private String defaultUrlFor(int idx) {
        String want = idx == 0 ? BIG_WHALE_URL : DEFAULT_URL;
        String saved = prefs.getString("url_" + idx, "");
        // 老版本留下的旧地址自动换成新的，省得手动改
        if (!saved.equals(want) && (saved.length() == 0
                || saved.equals(DEFAULT_URL) || saved.equals("http://192.168.1.5:8899"))) {
            prefs.edit().putString("url_" + idx, want).apply();
            return want;
        }
        return saved.length() == 0 ? want : saved;
    }
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

    private void autoFixLocalUrl() {
        new Thread(new Runnable() {
            public void run() {
                String saved = prefs.getString("url_1", DEFAULT_URL);
                String prefix = "192.168.1.";
                int idx = saved.indexOf("//");
                if (idx > 0) {
                    String host = saved.substring(idx + 2);
                    int colon = host.indexOf(':');
                    if (colon > 0) host = host.substring(0, colon);
                    int dot = host.lastIndexOf('.');
                    if (dot > 0) prefix = host.substring(0, dot + 1);
                }
                final String found = scanLan(prefix);
                if (found.length() == 0) return;
                String cur = prefs.getString("url_1", DEFAULT_URL);
                if (!found.equals(cur)) {
                    prefs.edit().putString("url_1", found).apply();
                    ui.post(new Runnable() {
                        public void run() {
                            Toast.makeText(MainActivity.this, "找到新地址：" + found, Toast.LENGTH_SHORT).show();
                            showList();
                        }
                    });
                }
            }
        }).start();
    }

    private String scanLan(String prefix) {
        java.util.concurrent.ExecutorService pool = java.util.concurrent.Executors.newFixedThreadPool(24);
        final java.util.concurrent.atomic.AtomicReference<String> hit =
                new java.util.concurrent.atomic.AtomicReference<>("");
        for (int i = 1; i <= 254; i++) {
            final String ip = prefix + i;
            pool.submit(new Runnable() {
                public void run() {
                    if (hit.get().length() > 0) return;
                    java.net.Socket sock = new java.net.Socket();
                    try {
                        sock.connect(new java.net.InetSocketAddress(ip, 8899), 220);
                        hit.compareAndSet("", "http://" + ip + ":8899");
                    } catch (Exception ignored) {
                    } finally {
                        try { sock.close(); } catch (Exception ignored) {}
                    }
                }
            });
        }
        pool.shutdown();
        try {
            pool.awaitTermination(6, java.util.concurrent.TimeUnit.SECONDS);
        } catch (Exception ignored) {
        }
        return hit.get();
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

        autoFixLocalUrl();
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

        View avatar;
        File avFile = avatarFile(idx);
        if (avFile.exists()) {
            ImageView iv = new ImageView(this);
            try {
                Bitmap b = BitmapFactory.decodeFile(avFile.getAbsolutePath());
                iv.setImageBitmap(b);
            } catch (Exception ignored) {
            }
            LinearLayout.LayoutParams alp = new LinearLayout.LayoutParams(dp(48), dp(48));
            alp.setMargins(0, 0, dp(14), 0);
            iv.setLayoutParams(alp);
            iv.setScaleType(ImageView.ScaleType.CENTER_CROP);
            avatar = iv;
        } else {
            TextView tv = label(name.substring(0, 1), 18, Color.WHITE, true);
            tv.setGravity(Gravity.CENTER);
            tv.setBackground(round(Color.parseColor(colorHex), 24));
            LinearLayout.LayoutParams alp = new LinearLayout.LayoutParams(dp(48), dp(48));
            alp.setMargins(0, 0, dp(14), 0);
            tv.setLayoutParams(alp);
            avatar = tv;
        }
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
            public boolean onLongClick(View v) { showFriendMenu(name, idx); return true; }
        });
        return row;
    }

    private static final int REQ_AVATAR = 1002;
    private static final int REQ_FILE = 1003;
    private int avatarIdx = 0;

    private void showFriendMenu(final String name, final int idx) {
        new AlertDialog.Builder(this)
                .setTitle(name)
                .setItems(new String[]{"换个头像", "改地址"}, new android.content.DialogInterface.OnClickListener() {
                    public void onClick(android.content.DialogInterface d, int which) {
                        if (which == 0) {
                            avatarIdx = idx;
                            Intent i = new Intent(Intent.ACTION_GET_CONTENT);
                            i.setType("image/*");
                            i.addCategory(Intent.CATEGORY_OPENABLE);
                            try {
                                startActivityForResult(Intent.createChooser(i, "选一张当头像"), REQ_AVATAR);
                            } catch (Exception e) {
                                Toast.makeText(MainActivity.this, "打不开相册", Toast.LENGTH_SHORT).show();
                            }
                        } else {
                            editEndpoint(name, idx);
                        }
                    }
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private File avatarFile(int idx) {
        return new File(getFilesDir(), "avatar_" + idx + ".jpg");
    }

    private void editEndpoint(final String name, final int idx) {
        final EditText input = new EditText(this);
        input.setInputType(InputType.TYPE_TEXT_VARIATION_URI);
        input.setText(prefs.getString("url_" + idx, defaultUrlFor(idx)));
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

        final LinearLayout emojiPanel = new LinearLayout(this);
        emojiPanel.setOrientation(LinearLayout.VERTICAL);
        emojiPanel.setBackgroundColor(Color.WHITE);
        emojiPanel.setPadding(dp(6), dp(6), dp(6), dp(6));
        emojiPanelRef = emojiPanel;
        chatBaseUrl = prefs.getString("url_" + idx, defaultUrlFor(idx));
        chatName = name;
        chatIdx = idx;

        final ScrollView emojiScroll = new ScrollView(this);
        emojiScroll.setVerticalScrollBarEnabled(false);
        emojiScroll.addView(emojiPanel);
        emojiScroll.setVisibility(View.GONE);
        emojiScrollRef = emojiScroll;
        LinearLayout.LayoutParams esp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(210));
        // 面板等输入栏建好后再挂上去，位置在工具条下面

        LinearLayout inputBar = new LinearLayout(this);
        inputBar.setOrientation(LinearLayout.HORIZONTAL);
        inputBar.setGravity(Gravity.CENTER_VERTICAL);
        inputBar.setPadding(dp(10), dp(8), dp(10), dp(16));
        inputBar.setBackgroundColor(Color.WHITE);

        TextView emojiBtn = label("☺", 22, Color.parseColor("#3D6AF2"), false);
        emojiBtn.setPadding(dp(14), dp(2), dp(14), dp(2));

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

        LinearLayout toolBar = new LinearLayout(this);
        toolBar.setOrientation(LinearLayout.HORIZONTAL);
        toolBar.setGravity(Gravity.CENTER_VERTICAL);
        toolBar.setBackgroundColor(Color.WHITE);
        toolBar.setPadding(dp(10), 0, dp(10), dp(14));
        toolBar.addView(emojiBtn);

        TextView plusBtn = label("＋", 22, Color.parseColor("#3D6AF2"), false);
        plusBtn.setPadding(dp(14), dp(2), dp(14), dp(2));
        plusBtn.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { pickEmoji(); }
        });
        toolBar.addView(plusBtn);

        TextView fileBtn = label("文", 18, Color.parseColor("#3D6AF2"), true);
        fileBtn.setPadding(dp(14), dp(2), dp(14), dp(2));
        fileBtn.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                Intent i = new Intent(Intent.ACTION_GET_CONTENT);
                i.setType("*/*");
                i.addCategory(Intent.CATEGORY_OPENABLE);
                try {
                    startActivityForResult(Intent.createChooser(i, "选一个文件"), REQ_FILE);
                } catch (Exception e) {
                    Toast.makeText(MainActivity.this, "打不开文件选择", Toast.LENGTH_SHORT).show();
                }
            }
        });
        toolBar.addView(fileBtn);

        TextView picBtn = label("图", 18, Color.parseColor("#3D6AF2"), false);
        picBtn.setPadding(dp(16), dp(2), dp(14), dp(2));
        picBtn.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { pickPhoto(); }
        });
        toolBar.addView(picBtn);
        page.addView(toolBar);
        page.addView(emojiScroll, esp);
        inChat = true;
        setContentView(page);

        emojiBtn.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                emojiScroll.setVisibility(emojiScroll.getVisibility() == View.VISIBLE
                        ? View.GONE : View.VISIBLE);
            }
        });

        emojiInputRef = input;
        renderEmojiPanel();

        bubble(msgs, scroll, "我是" + name + "，有什么就说吧。", false);

        final String baseUrl0 = prefs.getString("url_" + idx, defaultUrlFor(idx));
        loadLocalHistory(msgs, scroll, idx, baseUrl0, name);
        startRemotePoll(msgs, scroll, baseUrl0);

        send.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                String text = input.getText().toString().trim();
                if (text.length() == 0) return;
                input.setText("");
                timeTag(msgs, System.currentTimeMillis());
                bubble(msgs, scroll, text, true);
                appendLocal(idx, true, text, null);
                prefs.edit().putString("last_" + idx, text).apply();
                final String url = prefs.getString("url_" + idx, defaultUrlFor(idx));
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
                                timeTag(msgs, System.currentTimeMillis());
                                bubble(msgs, scroll, reply, false);
                                localReplies.add(reply);
                                appendLocal(idx, false, reply, null);
                                prefs.edit().putString("last_" + idx,
                                        reply.length() > 24 ? reply.substring(0, 24) + "…" : reply).apply();
                            }
                        });
                    }
                }).start();
            }
        });
    }


    private void appendLocal(int idx, boolean mine, String text, String imgPath) {
        try {
            File f = new File(getFilesDir(), "chatv3_" + idx + ".jsonl");
            org.json.JSONObject o = new org.json.JSONObject();
            o.put("mine", mine);
            o.put("t", System.currentTimeMillis());
            if (imgPath != null) o.put("img", imgPath); else o.put("text", text);
            FileOutputStream os = new FileOutputStream(f, true);
            os.write((o.toString() + "\n").getBytes("UTF-8"));
            os.flush();
            os.close();
        } catch (Exception ignored) {
        }
    }

    private java.util.List<String> readLines(File f) {
        java.util.List<String> out = new java.util.ArrayList<>();
        try {
            BufferedReader br = new BufferedReader(
                    new InputStreamReader(new java.io.FileInputStream(f), "UTF-8"));
            String line;
            while ((line = br.readLine()) != null) {
                if (line.trim().length() > 0) out.add(line);
            }
            br.close();
        } catch (Exception ignored) {
        }
        return out;
    }

    private void loadLocalHistory(final LinearLayout msgs, final ScrollView scroll,
                                  final int idx, final String base, final String name) {
        File f = new File(getFilesDir(), "chatv3_" + idx + ".jsonl");
        if (!f.exists()) return;
        for (String line : readLines(f)) {
            try {
                org.json.JSONObject o = new org.json.JSONObject(line);
                boolean mine = o.optBoolean("mine", false);
                String img = o.optString("img", "");
                if (img.length() > 0) {
                    File imf = new File(img);
                    if (imf.exists()) bubbleEmoji(msgs, scroll, imf);
                } else {
                    String t = o.optString("text", "");
                    if (t.length() > 0) {
                        timeTag(msgs, o.optLong("t", 0L));
                        bubble(msgs, scroll, t, mine);
                    }
                }
            } catch (Exception ignored) {
            }
        }
    }


    private void pickPhoto() {
        Intent i = new Intent(Intent.ACTION_GET_CONTENT);
        i.setType("image/*");
        i.addCategory(Intent.CATEGORY_OPENABLE);
        try {
            startActivityForResult(Intent.createChooser(i, "选一张发过去"), REQ_PHOTO);
        } catch (Exception e) {
            Toast.makeText(this, "打不开相册", Toast.LENGTH_SHORT).show();
        }
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
        if (res != RESULT_OK || data == null || data.getData() == null) return;
        if (req == REQ_FILE) {
            try {
                Uri uri = data.getData();
                String name = "file";
                android.database.Cursor c = getContentResolver().query(uri, null, null, null, null);
                if (c != null) {
                    int i = c.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME);
                    if (i >= 0 && c.moveToFirst()) name = c.getString(i);
                    c.close();
                }
                InputStream is = getContentResolver().openInputStream(uri);
                ByteArrayOutputStream bos = new ByteArrayOutputStream();
                byte[] buf = new byte[8192];
                int n;
                while ((n = is.read(buf)) > 0) bos.write(buf, 0, n);
                is.close();
                final String fname = name;
                final String b64 = Base64.encodeToString(bos.toByteArray(), Base64.NO_WRAP);
                bubble(chatMsgsRef, chatScrollRef, "[文件] " + fname, true);
                appendLocal(chatIdx, true, "[文件] " + fname, null);
                new Thread(new Runnable() {
                    public void run() {
                        final String reply = postFile(chatBaseUrl, fname, b64);
                        ui.post(new Runnable() {
                            public void run() {
                                bubble(chatMsgsRef, chatScrollRef, reply, false);
                                appendLocal(chatIdx, false, reply, null);
                            }
                        });
                    }
                }).start();
            } catch (Exception e) {
                Toast.makeText(this, "文件读不出来", Toast.LENGTH_SHORT).show();
            }
            return;
        }
        if (req == REQ_AVATAR) {
            try {
                InputStream is = getContentResolver().openInputStream(data.getData());
                File f = avatarFile(avatarIdx);
                FileOutputStream os = new FileOutputStream(f);
                byte[] buf = new byte[8192];
                int n;
                while ((n = is.read(buf)) > 0) os.write(buf, 0, n);
                os.close();
                is.close();
                Toast.makeText(this, "头像换好了", Toast.LENGTH_SHORT).show();
                showList();
            } catch (Exception e) {
                Toast.makeText(this, "存不下来", Toast.LENGTH_SHORT).show();
            }
            return;
        }
        if (req == REQ_PHOTO) {
            try {
                InputStream is = getContentResolver().openInputStream(data.getData());
                File f = new File(getFilesDir(), "photo_" + System.currentTimeMillis() + ".jpg");
                FileOutputStream os = new FileOutputStream(f);
                byte[] buf = new byte[8192];
                int n;
                while ((n = is.read(buf)) > 0) os.write(buf, 0, n);
                os.close();
                is.close();
                sendEmojiFile(f);
            } catch (Exception e) {
                Toast.makeText(this, "图片没读出来", Toast.LENGTH_SHORT).show();
            }
            return;
        }
        if (req != REQ_PICK) return;
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
            renderEmojiPanel();
            Toast.makeText(this, "加上了", Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Toast.makeText(this, "存不下来", Toast.LENGTH_SHORT).show();
        }
    }

    private LinearLayout.LayoutParams cellParams() {
        int w = (getResources().getDisplayMetrics().widthPixels - dp(12)) / 7;
        return new LinearLayout.LayoutParams(w, dp(56));
    }

    private void renderEmojiPanel() {
        if (emojiPanelRef == null || emojiInputRef == null) return;
        emojiPanelRef.removeAllViews();
        java.util.List<View> cells = new java.util.ArrayList<>();
        for (final String e : EMOJI) {
            TextView t = label(e, 24, Color.BLACK, false);
            t.setGravity(Gravity.CENTER);
            t.setLayoutParams(cellParams());
            t.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) { emojiInputRef.append(e); }
            });
            cells.add(t);
        }
        String list = prefs.getString("emoji_files", "");
        if (list.length() > 0) {
            for (String path : list.split("\\|")) {
                File f = new File(path);
                if (!f.exists()) continue;
                ImageView iv = new ImageView(this);
                try {
                    Bitmap b = BitmapFactory.decodeFile(f.getAbsolutePath());
                    if (b == null) continue;
                    int size = dp(40);
                    iv.setImageBitmap(Bitmap.createScaledBitmap(b, size, size, true));
                } catch (Exception ex) {
                    continue;
                }
                iv.setLayoutParams(cellParams());
                final File ff = f;
                iv.setOnClickListener(new View.OnClickListener() {
                    public void onClick(View v) { sendEmojiFile(ff); }
                });
                iv.setOnLongClickListener(new View.OnLongClickListener() {
                    public boolean onLongClick(View v) {
                        removeEmojiFile(ff);
                        return true;
                    }
                });
                cells.add(iv);
            }
        }
        int perRow = 7;
        LinearLayout row = null;
        for (int i = 0; i < cells.size(); i++) {
            if (i % perRow == 0) {
                row = new LinearLayout(this);
                row.setOrientation(LinearLayout.HORIZONTAL);
                emojiPanelRef.addView(row);
            }
            row.addView(cells.get(i));
        }
    }

    private void removeEmojiFile(File f) {
        String list = prefs.getString("emoji_files", "");
        StringBuilder sb = new StringBuilder();
        if (list.length() > 0) {
            for (String path : list.split("\\|")) {
                if (path.equals(f.getAbsolutePath())) continue;
                if (sb.length() > 0) sb.append("|");
                sb.append(path);
            }
        }
        prefs.edit().putString("emoji_files", sb.toString()).apply();
        try {
            f.delete();
        } catch (Exception ignored) {
        }
        renderEmojiPanel();
        Toast.makeText(this, "删掉了", Toast.LENGTH_SHORT).show();
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
        bubbleEmoji(chatMsgsRef, chatScrollRef, f);
        appendLocal(chatIdx, true, "", f.getAbsolutePath());
        String sents = prefs.getString("sent_images", "");
        prefs.edit().putString("sent_images",
                sents.length() == 0 ? f.getAbsolutePath() : sents + "|" + f.getAbsolutePath()).apply();
        final String base = chatBaseUrl;
        final String name = chatName;
        new Thread(new Runnable() {
            public void run() {
                final String reply = postImage(base, name, f);
                ui.post(new Runnable() {
                    public void run() {
                        bubble(chatMsgsRef, chatScrollRef, reply, false);
                        appendLocal(chatIdx, false, reply, null);
                        prefs.edit().putString("last_" + chatIdx,
                                reply.length() > 24 ? reply.substring(0, 24) + "…" : reply).apply();
                    }
                });
            }
        }).start();
    }

    private String postFile(String base, String filename, String b64) {
        HttpURLConnection conn = null;
        try {
            URL u = new URL(base.replaceAll("/+$", "") + "/chat");
            conn = (HttpURLConnection) u.openConnection();
            conn.setRequestMethod("POST");
            conn.setConnectTimeout(8000);
            conn.setReadTimeout(180000);
            conn.setDoOutput(true);
            conn.setRequestProperty("Content-Type", "application/json; charset=utf-8");
            JSONObject body = new JSONObject();
            body.put("name", chatName);
            body.put("filename", filename);
            body.put("file", b64);
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
            if (code >= 400) return "（文件没发出去：" + code + "）";
            String r = new JSONObject(sb.toString()).optString("reply", "");
            return r.length() == 0 ? "（它收到文件了）" : r;
        } catch (Exception e) {
            return "（文件没发出去：" + e.getClass().getSimpleName() + "）";
        } finally {
            if (conn != null) conn.disconnect();
        }
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
            conn.setReadTimeout(180000);
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
            conn.setReadTimeout(180000);
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
            if (e instanceof java.net.SocketTimeoutException) {
                return "（" + name + "还在想，没赶上时间）";
            }
            return "（连不上" + name + "：" + e.getClass().getSimpleName() + "）";
        } finally {
            if (conn != null) conn.disconnect();
        }
    }

    private void bubbleEmoji(LinearLayout box, final ScrollView scroll, File f) {
        bubbleImageSized(box, scroll, f, dp(110));
    }

    private void bubbleImage(LinearLayout box, final ScrollView scroll, File f) {
        int max = (int) (getResources().getDisplayMetrics().widthPixels * 0.55);
        bubbleImageSized(box, scroll, f, max);
    }

    private void bubbleImageSized(LinearLayout box, final ScrollView scroll, File f, int limit) {
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
        iv.setAdjustViewBounds(true);
        iv.setMaxWidth(limit);
        iv.setMaxHeight(limit);
        holder.addView(iv);
        box.addView(holder);
        scroll.post(new Runnable() {
            public void run() { scroll.fullScroll(View.FOCUS_DOWN); }
        });
    }

    private void startRemotePoll(final LinearLayout msgs, final ScrollView scroll,
                                 final String base) {
        if (base == null || base.length() == 0) return;
        pollBaseline = -1;
        final String root = base.replaceAll("/+$", "");
        final Runnable loop = new Runnable() {
            public void run() {
                new Thread(new Runnable() {
                    public void run() {
                        final String body = httpGet(root + "/history");
                        ui.post(new Runnable() {
                            public void run() { renderRemote(msgs, scroll, body, root); }
                        });
                    }
                }).start();
                ui.postDelayed(this, 5000);
            }
        };
        ui.postDelayed(loop, 2000);
    }

    private void renderRemote(LinearLayout msgs, ScrollView scroll, String body, String root) {
        if (body == null) return;
        java.util.List<org.json.JSONObject> items = new java.util.ArrayList<>();
        try {
            org.json.JSONArray arr = new org.json.JSONObject(body).optJSONArray("messages");
            if (arr == null) return;
            for (int i = 0; i < arr.length(); i++) {
                org.json.JSONObject o = arr.optJSONObject(i);
                if (o != null && !o.optBoolean("mine", true)) items.add(o);
            }
        } catch (Exception e) {
            return;
        }
        if (pollBaseline < 0) {
            pollBaseline = items.size();
            return;
        }
        for (int i = pollBaseline; i < items.size(); i++) {
            org.json.JSONObject o = items.get(i);
            String t = o.optString("text", "");
            String fp = o.optString("file", "");
            if (fp.length() > 0) {
                attachBubble(msgs, scroll, o.optString("name", "文件"), fp, root);
            } else if (t.length() > 0 && !localReplies.contains(t)) {
                timeTag(msgs, System.currentTimeMillis());
                bubble(msgs, scroll, t, false);
            }
        }
        if (items.size() > pollBaseline) pollBaseline = items.size();
    }

    private void attachBubble(LinearLayout box, final ScrollView scroll, String name,
                              final String fp, final String root) {
        timeTag(box, System.currentTimeMillis());
        TextView t = new TextView(this);
        t.setText("📎 " + name);
        t.setTextSize(15);
        t.setTextColor(Color.parseColor("#1B2130"));
        t.setPadding(dp(14), dp(10), dp(14), dp(10));
        t.setBackground(round(Color.parseColor("#E8EEFF"), 14));
        t.setMaxWidth((int) (getResources().getDisplayMetrics().widthPixels * 0.75));
        t.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                try {
                    String url = root + "/file?p=" + java.net.URLEncoder.encode(fp, "UTF-8");
                    startActivity(new Intent(Intent.ACTION_VIEW, android.net.Uri.parse(url)));
                } catch (Exception e) {
                    Toast.makeText(MainActivity.this, "打不开这个文件", Toast.LENGTH_SHORT).show();
                }
            }
        });
        box.addView(t);
        scroll.post(new Runnable() {
            public void run() { scroll.fullScroll(View.FOCUS_DOWN); }
        });
    }

    private String httpGet(String url) {
        HttpURLConnection conn = null;
        try {
            conn = (HttpURLConnection) new java.net.URL(url).openConnection();
            conn.setConnectTimeout(6000);
            conn.setReadTimeout(8000);
            if (conn.getResponseCode() >= 400) return null;
            BufferedReader br = new BufferedReader(
                    new InputStreamReader(conn.getInputStream(), "UTF-8"));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = br.readLine()) != null) sb.append(line);
            br.close();
            return sb.toString();
        } catch (Exception e) {
            return null;
        } finally {
            if (conn != null) conn.disconnect();
        }
    }

    private void timeTag(LinearLayout box, long ts) {
        if (ts <= 0L) return;
        TextView t = new TextView(this);
        t.setText(new java.text.SimpleDateFormat("MM-dd HH:mm",
                java.util.Locale.CHINA).format(new java.util.Date(ts)));
        t.setTextSize(11);
        t.setTextColor(Color.parseColor("#8A93A6"));
        t.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, dp(6), 0, dp(2));
        t.setLayoutParams(lp);
        box.addView(t);
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
            File avFile = avatarFile(chatIdx);
            if (avFile.exists()) {
                ImageView iv = new ImageView(this);
                try {
                    iv.setImageBitmap(BitmapFactory.decodeFile(avFile.getAbsolutePath()));
                } catch (Exception ignored) {
                }
                iv.setScaleType(ImageView.ScaleType.CENTER_CROP);
                LinearLayout.LayoutParams alp = new LinearLayout.LayoutParams(dp(38), dp(38));
                alp.setMargins(0, 0, dp(8), 0);
                iv.setLayoutParams(alp);
                holder.addView(iv);
            } else {
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
        }

        TextView t = new TextView(this);
        t.setText(text);
        t.setTextSize(15);
        t.setTextColor(mine ? Color.WHITE : Color.parseColor("#1B2130"));
        t.setPadding(dp(14), dp(10), dp(14), dp(10));
        t.setBackground(round(mine ? Color.parseColor("#3D6AF2") : Color.WHITE, 14));
        t.setAutoLinkMask(android.text.util.Linkify.WEB_URLS);
        t.setMovementMethod(android.text.method.LinkMovementMethod.getInstance());
        t.setLinkTextColor(mine ? Color.WHITE : Color.parseColor("#3D6AF2"));
        t.setMaxWidth((int) (getResources().getDisplayMetrics().widthPixels * 0.75));
        holder.addView(t);
        box.addView(holder);

        scroll.post(new Runnable() {
            public void run() { scroll.fullScroll(View.FOCUS_DOWN); }
        });
    }
}
