package com.example.mgs_lockscreen_changer;


import android.annotation.SuppressLint;
import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.Switch;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import java.util.Objects;


public class MainActivity extends AppCompatActivity {
    Button btnStartService, btnStopService;
    EditText eUpdateInterval, eBirthBlock, eVisibleCrop;
    TextView tLastUpdate;
    @SuppressLint("UseSwitchCompatOrMaterialCode")
    Switch switchLH, bothCheckbox;
    String bothLockAndHome = "false";
    String LockOrHome = "false";


    @SuppressLint("MissingInflatedId")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        btnStartService = findViewById(R.id.buttonStartService);
        btnStopService = findViewById(R.id.buttonStopService);

        eUpdateInterval = findViewById(R.id.editUpdateInterval);
        eVisibleCrop = findViewById(R.id.editVisibleCrop);
        eBirthBlock = findViewById(R.id.editBirthBlock);

        tLastUpdate = findViewById(R.id.lastUpdate);

        switchLH = findViewById(R.id.switch1);
        bothCheckbox = findViewById(R.id.bothCheckbox);

        bothCheckbox.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if (isChecked == true) {
                    bothLockAndHome = "true";
                } else {
                    bothLockAndHome = "false";
                }
            }
        });

        switchLH.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if (isChecked == true) {
                    LockOrHome = "true";
                } else {
                    LockOrHome = "false";
                }
            }
        });

        tLastUpdate.setOnClickListener(new View.OnClickListener(){
            @SuppressLint("SetTextI18n")
            @Override
            public void onClick(View v) {
                SharedPreferences sh = getSharedPreferences("userPref", Context.MODE_PRIVATE);
                tLastUpdate.setText("last wallpaper update: " + sh.getString("_lastUpdate",""));
            }
        });

        btnStartService.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startService();
            }
        });
        btnStopService.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                stopService();
            }
        });
    }

    public void startService() {
        Double updateInterval = Double.valueOf(eUpdateInterval.getText().toString()).doubleValue()* 60 * 60 * 24 * 1000;
        Integer updateIntervalMillis = updateInterval.intValue();
        String updateIntervalString = Integer.toString(updateIntervalMillis);

        SharedPreferences sharedPreferences = getSharedPreferences("userPref", MODE_PRIVATE);
        SharedPreferences.Editor myEdit = sharedPreferences.edit();
        myEdit.putString("_updateInterval", updateIntervalString);
        myEdit.apply();

        Intent serviceIntent = new Intent(this, MyService.class);
        serviceIntent.putExtra("_updateInterval", updateIntervalString);
        serviceIntent.putExtra("_visibleCrop", String.valueOf(eVisibleCrop.getText()));
        serviceIntent.putExtra("_birthBlock", String.valueOf(eBirthBlock.getText()));
        serviceIntent.putExtra("_bothLockAndHome", bothLockAndHome);
        serviceIntent.putExtra("_LockOrHome", LockOrHome);

        startService(serviceIntent);
    }

    public void stopService() {
        Intent serviceIntent = new Intent(this, MyService.class);
        stopService(serviceIntent);
        cancelAlarm();
    }



    private void cancelAlarm() {
        AlarmManager alarmManager = (AlarmManager) getSystemService(ALARM_SERVICE);
        Intent intent = new Intent(this, MyAlarm.class);
        PendingIntent pendingIntent = PendingIntent.getBroadcast(this, 0, intent, PendingIntent.FLAG_MUTABLE);

        alarmManager.cancel(pendingIntent);
    }

    @SuppressLint("SetTextI18n")
    @Override
    protected void onResume() {
        super.onResume();

        SharedPreferences sh = getSharedPreferences("userPref", Context.MODE_PRIVATE);

        eUpdateInterval.setText(sh.getString("_updateIntervalDec", ""));
        eVisibleCrop.setText(sh.getString("_visibleCrop", ""));
        eBirthBlock.setText(sh.getString("_birthBlock", ""));
        tLastUpdate.setText("last wallpaper update: " + sh.getString("_lastUpdate",""));

        if (Objects.equals(sh.getString("_bothLockAndHome", ""), "true")) {
            bothCheckbox.setChecked(true);
        }
        if (Objects.equals(sh.getString("_lockOrHome", ""), "true")) {
            switchLH.setChecked(true);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();

        SharedPreferences sharedPreferences = getSharedPreferences("userPref", MODE_PRIVATE);
        SharedPreferences.Editor myEdit = sharedPreferences.edit();

        myEdit.putString("_updateIntervalDec", eUpdateInterval.getText().toString());
        myEdit.putString("_visibleCrop", eVisibleCrop.getText().toString());
        myEdit.putString("_birthBlock", eBirthBlock.getText().toString());
        myEdit.putString("_bothLockAndHome", bothLockAndHome);
        myEdit.putString("_lockOrHome", LockOrHome);

        myEdit.apply();
    }
}