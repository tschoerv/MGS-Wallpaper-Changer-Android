package com.example.mgs_lockscreen_changer;


import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.app.WallpaperManager;
import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Rect;
import android.icu.text.SimpleDateFormat;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.transition.Transition;
import androidx.work.Constraints;
import androidx.work.Data;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkInfo;
import androidx.work.WorkManager;

import com.google.common.util.concurrent.ListenableFuture;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.Date;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;


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
                new AlertDialog.Builder(MainActivity.this)
                        .setTitle("MGS Wallpaper Changer")
                        .setMessage("Do you really want to stop the service?")
                        .setIcon(android.R.drawable.ic_dialog_alert)
                        .setPositiveButton(android.R.string.yes, new DialogInterface.OnClickListener() {

                            public void onClick(DialogInterface dialog, int whichButton) {
                                stopService();
                            }})
                        .setNegativeButton(android.R.string.no, null).show();
            }
        });
    }

    public void startService() {
        if(checkWorkerState() == true){
            Toast.makeText(this, "Service already running", Toast.LENGTH_SHORT).show();
        }
        else {
            Double updateInterval = Double.valueOf(eUpdateInterval.getText().toString()) * 60 * 60 * 24 * 1000;
            Integer updateIntervalMillis = updateInterval.intValue();
            String updateIntervalString = Integer.toString(updateIntervalMillis);

            Double flexInterval = updateInterval * 0.1;
            Integer flexIntervalMillis = flexInterval.intValue();

            SharedPreferences sharedPreferences = getSharedPreferences("userPref", MODE_PRIVATE);
            SharedPreferences.Editor myEdit = sharedPreferences.edit();
            myEdit.putString("_updateInterval", updateIntervalString);
            myEdit.apply();

            Constraints.Builder builder = new Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED);

            // Passing params
            Data.Builder data = new Data.Builder();
            data.putString("_updateInterval", updateIntervalString);
            data.putString("_visibleCrop", String.valueOf(eVisibleCrop.getText()));
            data.putString("_birthBlock", String.valueOf(eBirthBlock.getText()));
            data.putString("_bothLockAndHome", bothLockAndHome);
            data.putString("_LockOrHome", LockOrHome);

            final PeriodicWorkRequest mRequest = new PeriodicWorkRequest.Builder(MyWorker.class, updateIntervalMillis, TimeUnit.MILLISECONDS, flexIntervalMillis, TimeUnit.MILLISECONDS)
                    .setInputData(data.build())
                    .setConstraints(builder.build())
                    .build();


            WorkManager.getInstance(MainActivity.this).enqueueUniquePeriodicWork("updateWorker",
                    ExistingPeriodicWorkPolicy.KEEP, mRequest);

            new MainActivity.SetWallpaperTask().execute("https://seeder.mutant.garden/api/mutant/" + eBirthBlock.getText().toString() + "/raster/now");

            Toast.makeText(this, "Service started", Toast.LENGTH_SHORT).show();
        }
    }

    public void stopService() {
        WorkManager.getInstance(MainActivity.this). cancelUniqueWork("updateWorker");
        Toast.makeText(this, "Service stopped", Toast.LENGTH_SHORT).show();
    }
    public boolean checkWorkerState() {

        ListenableFuture<List<WorkInfo>> status = WorkManager.getInstance(MainActivity.this).getWorkInfosForUniqueWork("updateWorker");
        try {
            boolean running = false;
            List<WorkInfo> workInfoList = status.get();
            for (WorkInfo workInfo : workInfoList) {
                WorkInfo.State state = workInfo.getState();
                running = state == WorkInfo.State.RUNNING | state == WorkInfo.State.ENQUEUED;
            }
            return running;
        } catch (ExecutionException e) {
            e.printStackTrace();
            return false;
        } catch (InterruptedException e) {
            e.printStackTrace();
            return false;
        }
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

    private class SetWallpaperTask extends AsyncTask<String, Void, Bitmap> {
        @Override
        protected Bitmap doInBackground(String... params) {
            // Get the image URL
            String imageUrl = params[0];

            try {
                InputStream inputStream = new URL(imageUrl).openStream();
                Bitmap bitmap = BitmapFactory.decodeStream(inputStream);
                inputStream.close();

                Bitmap imageWithBG = Bitmap.createBitmap(bitmap.getWidth(), bitmap.getHeight(),bitmap.getConfig());  // Create another image the same size
                imageWithBG.eraseColor(Color.WHITE);  // set the background to white
                Canvas canvas = new Canvas(imageWithBG);  // create a canvas to draw on the new image
                canvas.drawBitmap(bitmap, 0f, 0f, null); // draw downloaded image on the background
                bitmap.recycle();  // clear out old image

                return imageWithBG;

            } catch (IOException ex) {
                ex.printStackTrace();
            }
            return null;
        }

        @Override
        protected void onPostExecute(Bitmap bitmap) {
            // Set the image as the wallpaper
            WallpaperManager wallpaperManager = WallpaperManager.getInstance(MainActivity.this);
            try {
                if (bitmap.getWidth() == 0 || bitmap.getHeight() == 0){
                    //Toast.makeText(MyService.this, "failed to download image", Toast.LENGTH_SHORT).show();
                }
                else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    //Set wallpaper on Lockscreen
                    if (Objects.equals(bothLockAndHome, "true")) {
                        wallpaperManager.setBitmap(bitmap, new Rect(Integer.parseInt(eVisibleCrop.getText().toString()), 0, bitmap.getWidth(), bitmap.getHeight()), true );
                        saveLastUpdateTime();
                    }
                    else if (Objects.equals(bothLockAndHome, "false") && Objects.equals(LockOrHome, "false")){
                        wallpaperManager.setBitmap(bitmap, new Rect(Integer.parseInt(eVisibleCrop.getText().toString()), 0, bitmap.getWidth(), bitmap.getHeight()), true, WallpaperManager.FLAG_LOCK );
                        saveLastUpdateTime();

                    }
                    else if (Objects.equals(bothLockAndHome, "false") && Objects.equals(LockOrHome, "true")){
                        wallpaperManager.setBitmap(bitmap, new Rect(Integer.parseInt(eVisibleCrop.getText().toString()), 0, bitmap.getWidth(), bitmap.getHeight()), true, WallpaperManager.FLAG_SYSTEM );
                        saveLastUpdateTime();
                    }
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

    }
    private void saveLastUpdateTime() {
        SimpleDateFormat s;
        String format = null;
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
            s = new SimpleDateFormat("dd-MM-yyyy HH:mm:ss");
            format = s.format(new Date());
        }

        SharedPreferences sharedPreferences = MainActivity.this.getSharedPreferences("userPref", MODE_PRIVATE);
        SharedPreferences.Editor myEdit = sharedPreferences.edit();

        myEdit.putString("_lastUpdate", format);
        myEdit.apply();
    }
}