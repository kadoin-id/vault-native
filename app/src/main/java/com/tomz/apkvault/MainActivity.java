package com.tomz.apkvault;

import android.app.Activity;
import android.os.Bundle;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.graphics.Typeface;
import android.graphics.Color;
import android.util.TypedValue;
import android.content.ClipData;
import android.os.Build;
import android.widget.Toast;

import androidx.core.content.FileProvider;

import java.io.File;
import java.io.InputStream;
import java.io.FileOutputStream;
import java.security.MessageDigest;
import java.util.Locale;

public class MainActivity extends Activity {

    private TextView infoText;
    private File apkFile;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        apkFile = new File(getCacheDir(), "file.apk");

        buildInterface();
        loadApk();
    }

    private void buildInterface() {
        ScrollView scroll = new ScrollView(this);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(24), dp(28), dp(24), dp(24));
        root.setBackgroundColor(Color.rgb(247, 248, 252));

        scroll.addView(root);

        TextView title = new TextView(this);
        title.setText("APK Vault");
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 28);
        title.setTypeface(null, Typeface.BOLD);
        title.setTextColor(Color.rgb(30, 35, 55));
        root.addView(title);

        TextView subtitle = new TextView(this);
        subtitle.setText("APK information and installer");
        subtitle.setTextSize(14);
        subtitle.setTextColor(Color.DKGRAY);
        subtitle.setPadding(0, dp(6), 0, dp(24));
        root.addView(subtitle);

        infoText = new TextView(this);
        infoText.setTextSize(14);
        infoText.setTextColor(Color.rgb(45, 50, 65));
        infoText.setPadding(dp(16), dp(16), dp(16), dp(16));
        infoText.setBackgroundColor(Color.WHITE);
        root.addView(infoText);

        Button refreshButton = new Button(this);
        refreshButton.setText("Muat Ulang Informasi");
        root.addView(refreshButton);

        refreshButton.setOnClickListener(v -> loadApk());

        Button installButton = new Button(this);
        installButton.setText("Instal APK");
        root.addView(installButton);

        installButton.setOnClickListener(v -> requestInstall());

        setContentView(scroll);
    }

    private void loadApk() {
        try {
            copyBundledApk();

            PackageManager pm = getPackageManager();
            PackageInfo packageInfo = null;

            if (Build.VERSION.SDK_INT >= 33) {
                packageInfo = pm.getPackageArchiveInfo(
                    apkFile.getAbsolutePath(),
                    PackageManager.PackageInfoFlags.of(0)
                );
            } else {
                packageInfo = pm.getPackageArchiveInfo(
                    apkFile.getAbsolutePath(), 0
                );
            }

            String packageName = "Tidak diketahui";
            String versionName = "Tidak diketahui";
            long versionCode = 0;

            if (packageInfo != null) {
                packageName = packageInfo.packageName;
                versionName = packageInfo.versionName == null
                    ? "Tidak diketahui"
                    : packageInfo.versionName;

                if (Build.VERSION.SDK_INT >= 28) {
                    versionCode = packageInfo.getLongVersionCode();
                } else {
                    versionCode = packageInfo.versionCode;
                }
            }

            String hash = calculateSha256(apkFile);

            String info =
                "Status: APK tersedia\n\n" +
                "Nama file: file.apk\n" +
                "Ukuran: " + formatSize(apkFile.length()) + "\n" +
                "Package: " + packageName + "\n" +
                "Versi: " + versionName + "\n" +
                "Version code: " + versionCode + "\n\n" +
                "SHA-256:\n" + hash;

            infoText.setText(info);

        } catch (Exception e) {
            infoText.setText(
                "Gagal membaca APK.\n\n" + errorMessage(e)
            );
        }
    }

    private void copyBundledApk() throws Exception {
        File parent = apkFile.getParentFile();

        if (parent != null && !parent.exists()) {
            if (!parent.mkdirs()) {
                throw new Exception("Gagal membuat folder cache.");
            }
        }

        try (
            InputStream input = getAssets().open("file.apk");
            FileOutputStream output = new FileOutputStream(apkFile)
        ) {
            byte[] buffer = new byte[8192];
            int count;

            while ((count = input.read(buffer)) != -1) {
                output.write(buffer, 0, count);
            }

            output.flush();
        }

        if (!apkFile.exists() || apkFile.length() == 0) {
            throw new Exception("File APK kosong atau tidak ditemukan.");
        }
    }

    private String calculateSha256(File file) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");

        try (InputStream input =
                 new java.io.FileInputStream(file)) {

            byte[] buffer = new byte[8192];
            int count;

            while ((count = input.read(buffer)) != -1) {
                digest.update(buffer, 0, count);
            }
        }

        StringBuilder hex = new StringBuilder();

        for (byte b : digest.digest()) {
            hex.append(
                String.format(Locale.US, "%02x", b & 0xff)
            );
        }

        return hex.toString();
    }

    private void requestInstall() {
        try {
            if (!apkFile.exists() || apkFile.length() == 0) {
                copyBundledApk();
            }

            Uri apkUri = FileProvider.getUriForFile(
                this,
                getPackageName() + ".fileprovider",
                apkFile
            );

            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setDataAndType(
                apkUri,
                "application/vnd.android.package-archive"
            );
            intent.addFlags(
                Intent.FLAG_GRANT_READ_URI_PERMISSION
            );
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            intent.setClipData(
                ClipData.newRawUri("APK", apkUri)
            );

            startActivity(intent);

        } catch (Exception e) {
            Toast.makeText(
                this,
                "Gagal membuka installer: " + errorMessage(e),
                Toast.LENGTH_LONG
            ).show();
        }
    }

    private String formatSize(long bytes) {
        if (bytes < 1024) {
            return bytes + " byte";
        }

        if (bytes < 1024 * 1024) {
            return String.format(
                Locale.US, "%.1f KB", bytes / 1024.0
            );
        }

        return String.format(
            Locale.US,
            "%.2f MB",
            bytes / (1024.0 * 1024.0)
        );
    }

    private String errorMessage(Exception e) {
        String message = e.getMessage();

        return message == null
            ? e.getClass().getSimpleName()
            : message;
    }

    private int dp(int value) {
        return (int) (
            value * getResources().getDisplayMetrics().density
            + 0.5f
        );
    }
}
