package com.gbpusd.signals;

import android.app.*;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.os.IBinder;
import org.json.*;
import java.io.*;
import java.net.*;
import java.util.*;

public class SignalService extends Service {
    private volatile boolean running = false;
    private Thread worker;
    private long lastTs = 0;
    private int nid = 100;

    @Override
    public int onStartCommand(Intent i, int f, int id) {
        NotificationManager nm = getSystemService(NotificationManager.class);
        nm.createNotificationChannel(new NotificationChannel("bg", "Surveillance", NotificationManager.IMPORTANCE_LOW));
        nm.createNotificationChannel(new NotificationChannel("signals", "Signaux", NotificationManager.IMPORTANCE_HIGH));
        Notification n = new Notification.Builder(this, "bg")
                .setContentTitle("GBPUSD Signals actif")
                .setContentText("Surveillance GBP/USD en cours")
                .setSmallIcon(android.R.drawable.ic_dialog_info).build();
        if (Build.VERSION.SDK_INT >= 29)
            startForeground(1, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC);
        else startForeground(1, n);
        if (worker == null) {
            running = true;
            worker = new Thread(this::loop);
            worker.start();
        }
        return START_STICKY;
    }

    private void loop() {
        while (running) {
            try { check(); } catch (Exception e) { }
            try { Thread.sleep(60000); } catch (InterruptedException e) { return; }
        }
    }

    private void check() throws Exception {
        URL u = new URL("https://query1.finance.yahoo.com/v8/finance/chart/GBPUSD=X?interval=5m&range=5d");
        HttpURLConnection c = (HttpURLConnection) u.openConnection();
        c.setRequestProperty("User-Agent", "Mozilla/5.0");
        c.setConnectTimeout(15000);
        c.setReadTimeout(15000);
        BufferedReader r = new BufferedReader(new InputStreamReader(c.getInputStream()));
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = r.readLine()) != null) sb.append(line);
        r.close();

        JSONObject res = new JSONObject(sb.toString()).getJSONObject("chart").getJSONArray("result").getJSONObject(0);
        JSONArray ts = res.getJSONArray("timestamp");
        JSONObject q = res.getJSONObject("indicators").getJSONArray("quote").getJSONObject(0);
        JSONArray cl = q.getJSONArray("close"), hi = q.getJSONArray("high"), lo = q.getJSONArray("low");

        List<Double> C = new ArrayList<>(), H = new ArrayList<>(), L = new ArrayList<>();
        List<Long> T = new ArrayList<>();
        for (int k = 0; k < ts.length(); k++) {
            if (cl.isNull(k) || hi.isNull(k) || lo.isNull(k)) continue;
            C.add(cl.getDouble(k)); H.add(hi.getDouble(k)); L.add(lo.getDouble(k)); T.add(ts.getLong(k));
        }
        int n = C.size();
        if (n < 210) return;

        double[] e20 = ema(C, 20), e50 = ema(C, 50), e200 = ema(C, 200);
        int a = n - 2, p = n - 3;
        boolean buy = e20[p] <= e50[p] && e20[a] > e50[a] && C.get(a) > e200[a];
        boolean sell = e20[p] >= e50[p] && e20[a] < e50[a] && C.get(a) < e200[a];
        if (!buy && !sell) return;
        if (T.get(a) == lastTs) return;
        lastTs = T.get(a);

        double atr = 0;
        for (int k = a - 13; k <= a; k++) {
            double pc = C.get(k - 1);
            atr += Math.max(H.get(k) - L.get(k), Math.max(Math.abs(H.get(k) - pc), Math.abs(L.get(k) - pc)));
        }
        atr /= 14;
        double px = C.get(a);
        double sl = buy ? px - 1.5 * atr : px + 1.5 * atr;
        double tp = buy ? px + 3 * atr : px - 3 * atr;
        double slPips = 1.5 * atr / 0.0001;
        String txt = String.format(Locale.US, "@ %.5f | SL %.5f | TP %.5f | risque %.1f pips", px, sl, tp, slPips);
        notify(buy ? "ACHAT GBPUSD" : "VENTE GBPUSD", txt);
    }

    private double[] ema(List<Double> v, int per) {
        double[] e = new double[v.size()];
        double k = 2.0 / (per + 1);
        e[0] = v.get(0);
        for (int i = 1; i < e.length; i++) e[i] = v.get(i) * k + e[i - 1] * (1 - k);
        return e;
    }

    private void notify(String title, String text) {
        Notification n = new Notification.Builder(this, "signals")
                .setContentTitle(title).setContentText(text)
                .setSmallIcon(android.R.drawable.ic_dialog_info).setAutoCancel(true).build();
        getSystemService(NotificationManager.class).notify(nid++, n);
    }

    @Override public void onDestroy() { running = false; if (worker != null) worker.interrupt(); super.onDestroy(); }
    @Override public IBinder onBind(Intent i) { return null; }
}
