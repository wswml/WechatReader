package com.nous.wechatreader;

import android.text.TextUtils;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * 线程安全的消息追加写入器。
 * 输出：/storage/emulated/0/Download/WechatReader/messages.log
 */
public class MessageWriter {

    private static final String TAG = "WechatReader-Writer";
    private static final String LOG_DIR  = "/storage/emulated/0/Download/WechatReader";
    private static final String LOG_FILE = "messages.log";
    private static final long MAX_FILE_SIZE = 10 * 1024 * 1024L; // 10 MB
    private static final long MIN_INTERVAL_MS = 100;

    private static final SimpleDateFormat SDF =
            new SimpleDateFormat("MM-dd HH:mm:ss", Locale.getDefault());
    private static final Object LOCK = new Object();

    private static long sLastWrite = 0;
    private static String sLastMessage = "";

    public static void write(String message, long timestamp) {
        if (TextUtils.isEmpty(message)) return;

        long now = System.currentTimeMillis();

        // 防抖：同一消息 100ms 内不重复写入
        if (now - sLastWrite < MIN_INTERVAL_MS
                && message.equals(sLastMessage)) {
            return;
        }

        synchronized (LOCK) {
            try {
                File dir = new File(LOG_DIR);
                if (!dir.exists() && !dir.mkdirs()) {
                    Log.e(TAG, "无法创建目录");
                    return;
                }
                // 让 Termux 可读
                dir.setReadable(true, false);

                File file = new File(dir, LOG_FILE);
                boolean isNew = !file.exists();

                try (FileOutputStream fos = new FileOutputStream(file, true);
                     OutputStreamWriter w = new OutputStreamWriter(
                             fos, StandardCharsets.UTF_8)) {

                    if (isNew) {
                        w.write("===== 微信消息日志 =====\n\n");
                    }

                    w.write("[");
                    w.write(SDF.format(new Date(timestamp)));
                    w.write("] ");
                    w.write(message);
                    w.write("\n");
                    w.flush();
                }

                // 让 Termux 能读
                file.setReadable(true, false);

                sLastWrite = now;
                sLastMessage = message;

                // 超 10MB 截断
                if (file.length() > MAX_FILE_SIZE) {
                    truncate(file);
                }

            } catch (Exception e) {
                Log.e(TAG, "写入失败", e);
            }
        }
    }

    private static void truncate(File file) {
        try {
            File bak = new File(LOG_DIR, "messages_old.log");
            // 先删旧备份（renameTo 在 Android 上不会覆盖已有文件）
            if (bak.exists() && !bak.delete()) {
                Log.w(TAG, "无法删除旧备份文件");
            }
            if (!file.renameTo(bak)) {
                // rename 失败时直接删原文件，下一条消息重建
                Log.w(TAG, "截断失败，直接删除日志文件");
                file.delete();
            }
        } catch (Exception ignored) {}
    }
}
