package com.gbpusd.signals;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

public class MainActivity extends Activity {
    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        if (Build.VERSION.SDK_INT >= 33) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 1);
        }
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.VERTICAL);
        l.setPadding(48, 96, 48, 48);
        TextView t = new TextView(this);
        t.setTextSize(18);
        t.setText("GBPUSD Signals (M5)\nEMA 20/50 + filtre EMA 200\nSL 1.5xATR, TP 3xATR\n\nSignaux uniquement, aucun ordre n'est passé.");
        Button start = new Button(this);
        start.setText("Démarrer la surveillance");
        start.setOnClickListener(v -> startForegroundService(new Intent(this, SignalService.class)));
        Button stop = new Button(this);
        stop.setText("Arrêter");
        stop.setOnClickListener(v -> stopService(new Intent(this, SignalService.class)));
        l.addView(t);
        l.addView(start);
        l.addView(stop);
        setContentView(l);
    }
}
