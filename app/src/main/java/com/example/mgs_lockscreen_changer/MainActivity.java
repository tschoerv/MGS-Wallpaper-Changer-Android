package com.example.mgs_lockscreen_changer;

import android.app.AlertDialog;
import android.app.WallpaperManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Rect;
import android.icu.text.SimpleDateFormat;
import android.os.Build;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SwitchCompat;
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
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;


public class MainActivity extends AppCompatActivity {
    Button btnStartService, btnStopService;
    EditText eUpdateInterval, eBirthBlock, eVisibleCrop;
    TextView tLastUpdate;
    SwitchCompat switchLH, bothCheckbox;
    String bothLockAndHome = "false";
    String LockOrHome = "false";



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



        bothCheckbox.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isChecked) {
                bothLockAndHome = "true";
            } else {
                bothLockAndHome = "false";
            }
        });

        switchLH.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isChecked) {
                LockOrHome = "true";
            } else {
                LockOrHome = "false";
            }
        });

        tLastUpdate.setOnClickListener(v -> {
            SharedPreferences sh = getSharedPreferences("userPref", Context.MODE_PRIVATE);
            String lastUpdate = getString(R.string.last_wallpaper_update, sh.getString("_lastUpdate", ""));
            tLastUpdate.setText(lastUpdate);
        });

        btnStartService.setOnClickListener(v -> startService());
        btnStopService.setOnClickListener(v -> new AlertDialog.Builder(MainActivity.this)
                .setTitle("MGS Wallpaper Changer")
                .setMessage("Do you really want to stop the service?")
                .setIcon(android.R.drawable.ic_dialog_alert)
                .setPositiveButton(android.R.string.yes, (dialog, whichButton) -> stopService())
                .setNegativeButton(android.R.string.no, null).show());
    }

    public void startService() {
        if(checkWorkerState()){
            Toast.makeText(this, "Service already running", Toast.LENGTH_SHORT).show();
        }
        else {
            double updateInterval = Double.parseDouble(eUpdateInterval.getText().toString()) * 60 * 60 * 24 * 1000;
            int updateIntervalMillis = (int) updateInterval;
            String updateIntervalString = Integer.toString(updateIntervalMillis);

            double flexInterval = updateInterval * 0.1;
            int flexIntervalMillis = (int) flexInterval;

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

            WorkManager.getInstance(this).enqueueUniquePeriodicWork(
                    "updateWorker",
                    ExistingPeriodicWorkPolicy.KEEP,
                    mRequest
            );

            setWallpaperTask("https://seeder.mutant.garden/api/mutant/" + eBirthBlock.getText().toString() + "/raster/now");

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
        } catch (ExecutionException | InterruptedException e) {
            e.printStackTrace();
            return false;
        }
    }

    @Override
    protected void onResume() {
        super.onResume();

        SharedPreferences sh = getSharedPreferences("userPref", Context.MODE_PRIVATE);

        eUpdateInterval.setText(sh.getString("_updateIntervalDec", ""));
        eVisibleCrop.setText(sh.getString("_visibleCrop", ""));
        eBirthBlock.setText(sh.getString("_birthBlock", ""));
        String lastUpdate = getString(R.string.last_wallpaper_update, sh.getString("_lastUpdate",""));
        tLastUpdate.setText(lastUpdate);

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

    private void setWallpaperTask(String _imgURL) {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        executor.execute(() -> {
        try {
            InputStream inputStream = new URL(_imgURL).openStream();
            Bitmap bitmap = BitmapFactory.decodeStream(inputStream);
            inputStream.close();

            Bitmap imageWithBG = Bitmap.createBitmap(bitmap.getWidth(), bitmap.getHeight(),bitmap.getConfig());  // Create another image the same size
            imageWithBG.eraseColor(Color.WHITE);  // set the background to white
            Canvas canvas = new Canvas(imageWithBG);  // create a canvas to draw on the new image
            canvas.drawBitmap(bitmap, 0f, 0f, null); // draw downloaded image on the background
            bitmap.recycle();  // clear out old image

            // Set the image as the wallpaper
            WallpaperManager wallpaperManager = WallpaperManager.getInstance(getApplicationContext());

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                //Set wallpaper on both screens
                if (Objects.equals(bothLockAndHome, "true")) {
                    wallpaperManager.setBitmap(imageWithBG, new Rect(Integer.parseInt(eVisibleCrop.getText().toString()), 0, imageWithBG.getWidth(), imageWithBG.getHeight()), true );
                    saveLastUpdateTime();
                }
                //Set wallpaper on Lockscreen
                else if (Objects.equals(bothLockAndHome, "false") && Objects.equals(LockOrHome, "false")){
                    wallpaperManager.setBitmap(imageWithBG, new Rect(Integer.parseInt(eVisibleCrop.getText().toString()), 0, imageWithBG.getWidth(), imageWithBG.getHeight()), true, WallpaperManager.FLAG_LOCK );
                    saveLastUpdateTime();

                }
                //Set wallpaper on Home-screen
                else if (Objects.equals(bothLockAndHome, "false") && Objects.equals(LockOrHome, "true")){
                    wallpaperManager.setBitmap(imageWithBG, new Rect(Integer.parseInt(eVisibleCrop.getText().toString()), 0, imageWithBG.getWidth(), imageWithBG.getHeight()), true, WallpaperManager.FLAG_SYSTEM );
                    saveLastUpdateTime();
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        });
        executor.shutdown();
    }



    private void saveLastUpdateTime() {
        SimpleDateFormat s;
        String format = null;
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
            s = new SimpleDateFormat("dd-MM-yyyy HH:mm:ss");
            format = s.format(new Date());
        }

        SharedPreferences sharedPreferences = getSharedPreferences("userPref", MODE_PRIVATE);
        SharedPreferences.Editor myEdit = sharedPreferences.edit();

        myEdit.putString("_lastUpdate", format);
        myEdit.apply();
    }
}