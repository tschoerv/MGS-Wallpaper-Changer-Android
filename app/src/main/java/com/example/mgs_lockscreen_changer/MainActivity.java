package com.example.mgs_lockscreen_changer;


import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.Switch;
import android.widget.CheckBox;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import java.util.Objects;
import java.util.concurrent.locks.Lock;


public class MainActivity extends AppCompatActivity {
    Button btnStartService, btnStopService;
    EditText eUpdateInterval, eBirthBlock, eVisibleCrop;
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
        Intent serviceIntent = new Intent(this, MyService.class);
        serviceIntent.putExtra("_updateInterval", String.valueOf(eUpdateInterval.getText()));
        serviceIntent.putExtra("_visibleCrop", String.valueOf(eVisibleCrop.getText()));
        serviceIntent.putExtra("_birthBlock", String.valueOf(eBirthBlock.getText()));
        serviceIntent.putExtra("_bothLockAndHome", bothLockAndHome);
        serviceIntent.putExtra("_LockOrHome", LockOrHome);

        ContextCompat.startForegroundService(this, serviceIntent);
    }

    public void stopService() {
        Intent serviceIntent = new Intent(this, MyService.class);
        stopService(serviceIntent);
    }

    @Override
    protected void onResume() {
        super.onResume();

        // Fetching the stored data
        // from the SharedPreference
        SharedPreferences sh = getSharedPreferences("userPref", Context.MODE_PRIVATE);

        // Setting the fetched data
        // in the EditTexts
        eUpdateInterval.setText(sh.getString("_updateInterval", ""));
        eVisibleCrop.setText(sh.getString("_visibleCrop", ""));
        eBirthBlock.setText(sh.getString("_birthBlock", ""));

        if (Objects.equals(sh.getString("_bothLockAndHome", ""), "true")) {
            bothCheckbox.setChecked(true);
        }
        if (Objects.equals(sh.getString("_lockOrHome", ""), "true")) {
            switchLH.setChecked(true);
        }
    }

    // Store the data in the SharedPreference
    // in the onPause() method
    // When the user closes the application
    // onPause() will be called
    // and data will be stored
    @Override
    protected void onPause() {
        super.onPause();

        // Creating a shared pref object
        // with a file name "MySharedPref"
        // in private mode
        SharedPreferences sharedPreferences = getSharedPreferences("userPref", MODE_PRIVATE);
        SharedPreferences.Editor myEdit = sharedPreferences.edit();

        // write all the data entered by the user in SharedPreference and apply
        myEdit.putString("_updateInterval", eUpdateInterval.getText().toString());
        myEdit.putString("_visibleCrop", eVisibleCrop.getText().toString());
        myEdit.putString("_birthBlock", eBirthBlock.getText().toString());
        myEdit.putString("_bothLockAndHome", bothLockAndHome);
        myEdit.putString("_lockOrHome", LockOrHome);
        myEdit.apply();
    }
}