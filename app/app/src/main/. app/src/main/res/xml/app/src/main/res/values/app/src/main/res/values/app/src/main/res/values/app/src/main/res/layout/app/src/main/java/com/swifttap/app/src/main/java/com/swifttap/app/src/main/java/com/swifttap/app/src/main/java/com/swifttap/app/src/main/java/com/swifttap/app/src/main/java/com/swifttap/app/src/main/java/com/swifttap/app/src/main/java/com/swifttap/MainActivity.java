package com.swifttap;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

public class MainActivity extends Activity {
    private AppSettings settings;
    private TextView status, stats;
    private EditText etTps, etHold;
    private Button btnPanel;
    private final Handler ui = new Handler(Looper.getMainLooper());

    private final Runnable refresh = new Runnable() {
        @Override
        public void run() {
            updateUi();
            ui.postDelayed(this, 500);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        settings = new AppSettings(this);

        status = findViewById(R.id.status);
        stats = findViewById(R.id.stats);
        etTps = findViewById(R.id.etTps);
        etHold = findViewById(R.id.etHold);
        btnPanel = findViewById(R.id.btnPanel);

        etTps.setText(String.valueOf(settings.tps()));
        etHold.setText(String.valueOf(settings.holdMs()));

        findViewById(R.id.btnAccess).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                open(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
            }
        });

        findViewById(R.id.btnBattery).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                open(new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS));
            }
        });

        btnPanel.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                saveInputs();
                TapService s = TapService.instance;
                if (s == null) {
                    Toast.makeText(MainActivity.this,
                            "Pehle Accessibility me SwiftTap ON karo", Toast.LENGTH_LONG).show();
                    return;
                }
                if (s.isPanelShown()) s.hidePanel();
                else s.showPanel();
                updateUi();
            }
        });
    }

    private void open(Intent i) {
        try {
            startActivity(i);
        } catch (ActivityNotFoundException e) {
            Toast.makeText(this, "Settings screen not available on this phone", Toast.LENGTH_LONG).show();
        }
    }

    private int parse(EditText e, int def) {
        try {
            return Integer.parseInt(e.getText().toString().trim());
        } catch (NumberFormatException x) {
            return def;
        }
    }

    private void saveInputs() {
        settings.setRate(parse(etTps, settings.tps()), parse(etHold, settings.holdMs()));
        etTps.setText(String.valueOf(settings.tps()));
        etHold.setText(String.valueOf(settings.holdMs()));
    }

    private void updateUi() {
        TapService s = TapService.instance;
        if (s == null || s.scheduler() == null) {
            status.setText("Service OFF\nStep 1: Accessibility me SwiftTap ko ON karo.");
            btnPanel.setText("2. Show floating panel");
            stats.setText("Service is off.");
            return;
        }
        String err = s.lastError;
        status.setText("Service ON" + (err != null ? "\nLast error: " + err : ""));
        btnPanel.setText(s.isPanelShown() ? "Hide floating panel" : "2. Show floating panel");
        stats.setText(s.scheduler().report());
    }

    @Override
    protected void onResume() {
        super.onResume();
        ui.post(refresh);
    }

    @Override
    protected void onPause() {
        ui.removeCallbacks(refresh);
        saveInputs();
        super.onPause();
    }
}
