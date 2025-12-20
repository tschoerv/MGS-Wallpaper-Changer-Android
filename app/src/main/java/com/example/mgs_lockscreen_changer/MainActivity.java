package com.example.mgs_lockscreen_changer;

import android.app.AlertDialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.work.Constraints;
import androidx.work.Data;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkInfo;
import androidx.work.WorkManager;

import com.google.common.util.concurrent.ListenableFuture;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;


public class MainActivity extends AppCompatActivity {
    Button btnStartService, btnStopService;
    EditText eUpdateInterval, eBirthBlock, eCropLeft, eCropRight;
    TextView tLastUpdate;
    RadioGroup wallpaperModeGroup;
    RadioButton radioLock, radioHome, radioBoth;
    String wallpaperMode = "lock";
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

        wallpaperModeGroup = findViewById(R.id.wallpaperModeGroup);
        radioLock = findViewById(R.id.radioLock);
        radioHome = findViewById(R.id.radioHome);
        radioBoth = findViewById(R.id.radioBoth);

        imageViewUncropped = findViewById(R.id.imageView);


        wallpaperModeGroup.setOnCheckedChangeListener((group, checkedId) -> {
            if (checkedId == R.id.radioHome) {
                wallpaperMode = "home";
            } else if (checkedId == R.id.radioBoth) {
                wallpaperMode = "both";
            } else {
                wallpaperMode = "lock";
            }
        });

        tLastUpdate.setOnClickListener(v -> {
            updateLastUpdateText();
        });

        btnStartService.setOnClickListener(v -> startService());
        btnStopService.setOnClickListener(v -> {
            if (checkWorkerState()) {
                new AlertDialog.Builder(MainActivity.this)
                        .setTitle("MGS Wallpaper Changer")
                        .setMessage("Do you really want to stop the service?")
                        .setIcon(android.R.drawable.ic_dialog_alert)
                        .setPositiveButton(android.R.string.yes, (dialog, whichButton) -> stopService())
                        .setNegativeButton(android.R.string.no, null)
                        .show();
            }
        });
    }

    public void startService() {
        String intervalInput = eUpdateInterval.getText().toString().trim();
        String birthBlock = eBirthBlock.getText().toString().trim();
        int[] cropValues = validateCropInputs();

        if (intervalInput.isEmpty() || birthBlock.isEmpty()) {
            Toast.makeText(this, "Please fill in the interval and birth block.", Toast.LENGTH_SHORT).show();
            return;
        }
        if (cropValues == null) {
            return;
        }

        double updateIntervalDays;
        try {
            updateIntervalDays = Double.parseDouble(intervalInput);
        } catch (NumberFormatException e) {
            Toast.makeText(this, "Update interval must be a number.", Toast.LENGTH_SHORT).show();
            return;
        }

        long updateIntervalMillis = (long) (updateIntervalDays * TimeUnit.DAYS.toMillis(1));
        if (updateIntervalMillis < PeriodicWorkRequest.MIN_PERIODIC_INTERVAL_MILLIS) {
            updateIntervalMillis = PeriodicWorkRequest.MIN_PERIODIC_INTERVAL_MILLIS;
        }

        long flexIntervalMillis = Math.max((long) (updateIntervalMillis * 0.1), PeriodicWorkRequest.MIN_PERIODIC_FLEX_MILLIS);

        boolean applyToBoth = wallpaperMode.equals("both");
        boolean applyToHome = wallpaperMode.equals("home");

        SharedPreferences sharedPreferences = getSharedPreferences("userPref", MODE_PRIVATE);
        sharedPreferences.edit()
                .putString("_updateInterval", String.valueOf(updateIntervalMillis))
                .putString("_wallpaperMode", wallpaperMode)
                .apply();

        Constraints constraints = new Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build();

        Data data = new Data.Builder()
                .putString("_updateInterval", String.valueOf(updateIntervalMillis))
                .putString("_cropLeft", eCropLeft.getText().toString())
                .putString("_cropRight", eCropRight.getText().toString())
                .putString("_birthBlock", birthBlock)
                .putString("wallpaperMode", wallpaperMode)
                // legacy keys for backward compatibility with older worker fields
                .putBoolean("_bothLockAndHome", applyToBoth)
                .putBoolean("_lockOrHome", applyToHome)
                .build();

        PeriodicWorkRequest request = new PeriodicWorkRequest.Builder(MyWorker.class, updateIntervalMillis, TimeUnit.MILLISECONDS, flexIntervalMillis, TimeUnit.MILLISECONDS)
                .setInputData(data)
                .setConstraints(constraints)
                .build();

        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
                "updateWorker",
                ExistingPeriodicWorkPolicy.UPDATE,
                request
        );

        triggerImmediateUpdate(birthBlock, cropValues[0], cropValues[1], applyToBoth, applyToHome);
        updateButtons(true);
        updateLastUpdateText();

        Toast.makeText(this, "Wallpaper schedule saved", Toast.LENGTH_SHORT).show();
    }

    public void stopService() {
        WorkManager.getInstance(MainActivity.this).cancelUniqueWork("updateWorker");
        updateButtons(false);
        Toast.makeText(this, "Service stopped", Toast.LENGTH_SHORT).show();
    }

    public boolean checkWorkerState() {

        ListenableFuture<List<WorkInfo>> status = WorkManager.getInstance(MainActivity.this).getWorkInfosForUniqueWork("updateWorker");
        try {
            List<WorkInfo> workInfoList = status.get();
            for (WorkInfo workInfo : workInfoList) {
                WorkInfo.State state = workInfo.getState();
                if (state == WorkInfo.State.RUNNING || state == WorkInfo.State.ENQUEUED) {
                    return true;
                }
            }
            return false;
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
        updateLastUpdateText();

        wallpaperMode = sh.getString("_wallpaperMode", null);
        if (wallpaperMode == null) {
            boolean legacyBoth = Boolean.parseBoolean(sh.getString("_bothLockAndHome", "false"));
            boolean legacyHome = Boolean.parseBoolean(sh.getString("_lockOrHome", "false"));
            wallpaperMode = legacyBoth ? "both" : legacyHome ? "home" : "lock";
        }
        if ("home".equals(wallpaperMode)) {
            wallpaperModeGroup.check(radioHome.getId());
        } else if ("both".equals(wallpaperMode)) {
            wallpaperModeGroup.check(radioBoth.getId());
        } else {
            wallpaperModeGroup.check(radioLock.getId());
        }

        updateButtons(checkWorkerState());
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
        myEdit.putString("_wallpaperMode", wallpaperMode);

        myEdit.apply();
    }


    private void triggerImmediateUpdate(String birthBlock, int cropLeft, int cropRight, boolean applyToBoth, boolean applyToHome) {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        Handler handler = new Handler(Looper.getMainLooper());

        executor.execute(() -> {
            try {
                WallpaperUpdater.updateWallpaper(
                        getApplicationContext(),
                        buildImageUrl(birthBlock),
                        cropLeft,
                        cropRight,
                        applyToBoth,
                        applyToHome
                );
                handler.post(() -> {
                    loadImageIntoImageView();
                    updateLastUpdateText();
                });
            } catch (IOException e) {
                e.printStackTrace();
                handler.post(() -> Toast.makeText(MainActivity.this, "Failed to update wallpaper: " + e.getMessage(), Toast.LENGTH_LONG).show());
            }
        });
        executor.shutdown();
    }


    private int[] validateCropInputs() {
        int left = parseNonNegativeInt(eCropLeft.getText().toString());
        int right = parseNonNegativeInt(eCropRight.getText().toString());
        if (left + right > 1000) {
            Toast.makeText(this, "Total crop (left + right) must be 1000 pixels or less.", Toast.LENGTH_SHORT).show();
            return null;
        }
        return new int[]{left, right};
    }

    private int parseNonNegativeInt(String value) {
        try {
            return Math.max(Integer.parseInt(value.trim()), 0);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private String buildImageUrl(String birthBlock) {
        return "https://seeder.mutant.garden/api/mutant/" + birthBlock + "/raster/now";
    }

    private void loadImageIntoImageView() {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        Handler handler = new Handler(Looper.getMainLooper());

        executor.execute(() -> {
            File wallpaperFile = WallpaperUpdater.getCachedWallpaperFile(getApplicationContext());

            if (wallpaperFile.exists()) {
                Bitmap bitmap = BitmapFactory.decodeFile(wallpaperFile.getAbsolutePath());
                handler.post(() -> {
                    // Ensure ImageView and Activity are still valid
                    if (imageViewUncropped != null && !isFinishing()) {
                        imageViewUncropped.setImageBitmap(bitmap);
                    }
                });
            } else {
                handler.post(() -> {
                    if (imageViewUncropped != null && !isFinishing()) {
                        imageViewUncropped.setImageResource(R.mipmap.ic_launcher);
                    }
                });
            }
        });
        executor.shutdown();
    }

    private void updateLastUpdateText() {
        SharedPreferences sh = getSharedPreferences("userPref", Context.MODE_PRIVATE);
        String stored = sh.getString("_lastUpdate", "");
        String displayValue = stored == null || stored.isEmpty() ? "not yet" : stored;
        tLastUpdate.setText(getString(R.string.last_wallpaper_update, displayValue));
    }

    private void updateButtons(boolean running) {
        btnStartService.setVisibility(running ? View.GONE : View.VISIBLE);
        btnStopService.setVisibility(running ? View.VISIBLE : View.GONE);
    }
}
