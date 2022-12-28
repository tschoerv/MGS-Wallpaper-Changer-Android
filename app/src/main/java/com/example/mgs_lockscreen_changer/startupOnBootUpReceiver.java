package com.example.mgs_lockscreen_changer;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;

import androidx.core.content.ContextCompat;

public class startupOnBootUpReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {

        if(Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) {

            SharedPreferences sh = context.getSharedPreferences("userPref", Context.MODE_PRIVATE);

            Intent serviceIntent = new Intent(context, MyService.class);
            serviceIntent.putExtra("_updateInterval", sh.getString("_updateInterval", ""));
            serviceIntent.putExtra("_visibleCrop",sh.getString("_visibleCrop", ""));
            serviceIntent.putExtra("_birthBlock", sh.getString("_birthBlock", ""));
            serviceIntent.putExtra("_bothLockAndHome", sh.getString("_bothLockAndHome", ""));
            serviceIntent.putExtra("_LockOrHome", sh.getString("_lockOrHome", ""));
            ContextCompat.startForegroundService(context, serviceIntent);
        }
    }
}
