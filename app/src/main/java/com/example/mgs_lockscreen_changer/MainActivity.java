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
import android.os.Handler;
import android.os.Looper;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
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

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
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
    EditText eUpdateInterval, eBirthBlock, eCropLeft, eCropRight;
    TextView tLastUpdate;
    SwitchCompat switchLH, bothCheckbox;
    String bothLockAndHome = "false";
    String LockOrHome = "false";
    ImageView imageViewUncropped;



    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        btnStartService = findViewById(R.id.buttonStartService);
        btnStopService = findViewById(R.id.buttonStopService);

        eUpdateInterval = findViewById(R.id.editUpdateInterval);
        eCropLeft = findViewById(R.id.editCropLeft);
        eCropRight = findViewById(R.id.editCropRight);
        eBirthBlock = findViewById(R.id.editBirthBlock);

        tLastUpdate = findViewById(R.id.lastUpdate);

        switchLH = findViewById(R.id.switch1);
        bothCheckbox = findViewById(R.id.bothCheckbox);

        imageViewUncropped = findViewById(R.id.imageView);


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
        btnStopService.setOnClickListener(v -> {if(checkWorkerState()){new AlertDialog.Builder(MainActivity.this)
                .setTitle("MGS Wallpaper Changer")
                .setMessage("Do you really want to stop the service?")
                .setIcon(android.R.drawable.ic_dialog_alert)
                .setPositiveButton(android.R.string.yes, (dialog, whichButton) -> stopService())
                .setNegativeButton(android.R.string.no, null).show();}});
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
            data.putString("_cropLeft", String.valueOf(eCropLeft.getText()));
            data.putString("_cropRight", String.valueOf(eCropRight.getText()));
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
        loadImageIntoImageView();

        SharedPreferences sh = getSharedPreferences("userPref", Context.MODE_PRIVATE);

        eUpdateInterval.setText(sh.getString("_updateIntervalDec", ""));
        eCropLeft.setText(sh.getString("_cropLeft", ""));
        eCropRight.setText(sh.getString("_cropRight", ""));
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
        myEdit.putString("_cropLeft", eCropLeft.getText().toString());
        myEdit.putString("_cropRight", eCropRight.getText().toString());
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

                // Determine the amount to crop from the left and right sides
                int cropLeft = 0;
                if(eCropLeft != null && !eCropLeft.getText().toString().isEmpty()){
                    cropLeft = Integer.parseInt(eCropLeft.getText().toString());
                }

                int cropRight = 0;
                if(eCropRight != null && !eCropRight.getText().toString().isEmpty()){
                    cropRight = Integer.parseInt(eCropRight.getText().toString());
                }

                // Ensure cropLeft and cropRight are within the bitmap's width
                cropLeft = Math.min(cropLeft, bitmap.getWidth());
                cropRight = Math.min(cropRight, bitmap.getWidth() - cropLeft);

                // Create a source rectangle from cropLeft to the width minus cropRight
                Rect src = new Rect(cropLeft, 0, bitmap.getWidth() - cropRight, bitmap.getHeight());

                // Create another image of the size of the src rectangle with a white background
                Bitmap imageWithBG = Bitmap.createBitmap(src.width(), src.height(), bitmap.getConfig());
                imageWithBG.eraseColor(Color.WHITE);
                Canvas canvas = new Canvas(imageWithBG);
                // Draw only the portion of the bitmap defined by src, scaled to fit the new bitmap
                canvas.drawBitmap(bitmap, src, new Rect(0, 0, src.width(), src.height()), null);


                // Convert the bitmap to a file
                File wallpaperFile = saveBitmapToFile(imageWithBG);

                WallpaperManager wallpaperManager = WallpaperManager.getInstance(getApplicationContext());

                // Set the image as the wallpaper based on user choice
                try (InputStream fileInputStream = new FileInputStream(wallpaperFile)) {
                    if (Objects.equals(bothLockAndHome, "true")) {
                        wallpaperManager.setStream(fileInputStream);
                    } else if (Objects.equals(LockOrHome, "true")) {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                            wallpaperManager.setStream(fileInputStream, null, true, WallpaperManager.FLAG_SYSTEM);
                        }
                    } else {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                            wallpaperManager.setStream(fileInputStream, null, true, WallpaperManager.FLAG_LOCK);
                        }
                    }
                    saveLastUpdateTime();
                }

            } catch (IOException e) {
                e.printStackTrace();
            }
        });
        executor.shutdown();
    }



    private File saveBitmapToFile(Bitmap bitmap) throws IOException {
        // Create a file in the app's cache directory
        File cacheDir = getApplicationContext().getCacheDir();
        File wallpaperFile = new File(cacheDir, "wallpaper.png");

        try (FileOutputStream out = new FileOutputStream(wallpaperFile)) {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out); // PNG is a lossless format, the compression factor (100) is ignored
        }

        return wallpaperFile;
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

    private void loadImageIntoImageView() {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        Handler handler = new Handler(Looper.getMainLooper());

        executor.execute(() -> {
            File cacheDir = getApplicationContext().getCacheDir();
            File wallpaperFile = new File(cacheDir, "wallpaper.png");

            if (wallpaperFile.exists()) {
                Bitmap bitmap = BitmapFactory.decodeFile(wallpaperFile.getAbsolutePath());
                handler.post(() -> {
                    // Ensure ImageView and Activity are still valid
                    if (imageViewUncropped != null && !isFinishing()) {
                        imageViewUncropped.setImageBitmap(bitmap);
                    }
                });
            }
        });
        executor.shutdown();
    }
}