
package com.tomz.apkvault;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.core.content.FileProvider;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.IOException;
import java.security.MessageDigest;

public class MainActivity extends Activity {

    private static final String ASSET_APK = "file.apk";
    private File bundledApk;

    private TextView statusText;
    private TextView detailsText;
    private Button installButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        createInterface();
        inspectBundledApk();
    }

    private void createInterface() {
        int padding = dp(20);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(padding, padding, padding, padding);
        root.setBackgroundColor(0xFFF6F7FB);

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);

        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);

        TextView title = new TextView(this);
        title.setText("APK Vault");
        title.setTextSize(28);
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        title.setTextColor(0xFF172033);

        TextView subtitle = new TextView(this);
        subtitle.setText("Bundled APK installer");
        subtitle.setTextSize(14);
        subtitle.setTextColor(0xFF64748B);

        statusText = new TextView(this);
        statusText.setText("Memeriksa file APK...");
        statusText.setTextSize(15);
        statusText.setTextColor(0xFF334155);
        statusText.setPadding(0, dp(22), 0, dp(12));

        detailsText = new TextView(this);
        detailsText.setTextSize(14);
        detailsText.setTextColor(0xFF334155);
        detailsText.setPadding(dp(14), dp(14), dp(14), dp(14));
        detailsText.setBackgroundColor(0xFFFFFFFF);
        detailsText.setText("Informasi APK akan muncul di sini.");

        installButton = new Button(this);
        installButton.setText("Periksa dan pasang APK");
        installButton.setEnabled(false);
        installButton.setOnClickListener(v -> inspectBundledApk());

        Button refreshButton = new Button(this);
        refreshButton.setText("Periksa ulang");
        refreshButton.setOnClickListener(v -> inspectBundledApk());

        TextView notice = new TextView(this);
        notice.setText(
            "APK sumber: assets/file.apk\n\n" +
            "Pemasangan dilakukan melalui pemasang Android. " +
            "Perangkat akan meminta konfirmasi pengguna."
        );
        notice.setTextSize(12);
        notice.setTextColor(0xFF64748B);
        notice.setPadding(0, dp(18), 0, dp(8));

        content.addView(title);
        content.addView(subtitle);
        content.addView(statusText);
        content.addView(detailsText);

        LinearLayout.LayoutParams buttonParams =
            new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            );
        buttonParams.topMargin = dp(16);

        content.addView(installButton, buttonParams);
        content.addView(refreshButton, buttonParams);
        content.addView(notice);

        scroll.addView(content);
        root.addView(scroll, new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            0,
            1
        ));

        setContentView(root);
    }

    private void inspectBundledApk() {
        installButton.setEnabled(false);
        statusText.setText("Memeriksa APK yang disertakan...");

        new Thread(() -> {
            File copiedFile = null;

            try {
                copiedFile = copyBundledApk();
                String hash = calculateSha256(copiedFile);

                PackageInfo info = getPackageManager()
                    .getPackageArchiveInfo(copiedFile.getAbsolutePath(), 0);

                if (info == null) {
                    throw new IOException(
                        "File ditemukan, tetapi bukan APK Android yang valid."
                    );
                }

                String packageName = info.packageName;
                String version = "Tidak diketahui";

                if (Build.VERSION.SDK_INT >= 28) {
                    version = String.valueOf(info.getLongVersionCode());
                } else {
                    version = String.valueOf(info.versionCode);
                }

                String result =
                    "Nama file: file.apk\n\n" +
                    "Package: " + packageName + "\n\n" +
                    "Versi kode: " + version + "\n\n" +
                    "Ukuran: " + formatBytes(copiedFile.length()) + "\n\n" +
                    "SHA-256:\n" + hash;

                File finalFile = copiedFile;

                runOnUiThread(() -> {
                    bundledApk = finalFile;
                    detailsText.setText(result);
                    statusText.setText("APK ditemukan dan lolos pemeriksaan dasar.");
                    installButton.setEnabled(true);
                    installButton.setOnClickListener(
                        v -> confirmInstallation()
                    );
                });

            } catch (Exception e) {
                String message = e.getMessage();

                runOnUiThread(() -> {
                    bundledApk = null;
                    detailsText.setText(
                        message == null ? "Terjadi kesalahan." : message
                    );
                    statusText.setText("APK belum siap digunakan.");
                    installButton.setEnabled(false);
                    Toast.makeText(
                        MainActivity.this,
                        "Pemeriksaan APK gagal.",
                        Toast.LENGTH_LONG
                    ).show();
                });
            }
        }).start();
    }

    private File copyBundledApk() throws IOException {
        File destination = new File(getCacheDir(), ASSET_APK);

        try (InputStream input = getAssets().open(ASSET_APK);
             FileOutputStream output = new FileOutputStream(destination)) {

            byte[] buffer = new byte[16 * 1024];
            int count;

            while ((count = input.read(buffer)) != -1) {
                output.write(buffer, 0, count);
            }

            output.flush();
        }

        if (!destination.exists() || destination.length() == 0) {
            throw new IOException("File APK kosong atau gagal disalin.");
        }

        return destination;
    }

    private String calculateSha256(File file) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");

        try (InputStream input = new java.io.FileInputStream(file)) {
            byte[] buffer = new byte[16 * 1024];
            int count;

            while ((count = input.read(buffer)) != -1) {
                digest.update(buffer, 0, count);
            }
        }

        StringBuilder hex = new StringBuilder();

        for (byte b : digest.digest()) {
            hex.append(String.format("%02x", b & 0xff));
        }

        return hex.toString();
    }

    private void confirmInstallation() {
        if (bundledApk == null || !bundledApk.exists()) {
            showError("File APK tidak tersedia. Periksa ulang terlebih dahulu.");
            return;
        }

        new AlertDialog.Builder(this)
            .setTitle("Konfirmasi pemasangan")
            .setMessage(
                "APK yang disertakan akan dibuka menggunakan pemasang Android.\n\n" +
                "Lanjutkan hanya jika kamu memercayai sumber APK ini."
            )
            .setNegativeButton("Batal", null)
            .setPositiveButton("Lanjutkan", (dialog, which) -> {
                if (Build.VERSION.SDK_INT >= 26 &&
                    !getPackageManager().canRequestPackageInstalls()) {

                    new AlertDialog.Builder(this)
                        .setTitle("Izin pemasangan diperlukan")
                        .setMessage(
                            "Izinkan APK Vault memasang aplikasi dari sumber ini " +
                            "pada pengaturan Android berikutnya."
                        )
                        .setNegativeButton("Batal", null)
                        .setPositiveButton("Buka pengaturan", (d, w) -> {
                            Intent settingsIntent = new Intent(
                                Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                                Uri.parse("package:" + getPackageName())
                            );
                            startActivity(settingsIntent);
                        })
                        .show();

                    return;
                }

                launchInstaller();
            })
            .show();
    }

    private void launchInstaller() {
        try {
            Uri apkUri = FileProvider.getUriForFile(
                this,
                getPackageName() + ".fileprovider",
                bundledApk
            );

            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setDataAndType(
                apkUri,
                "application/vnd.android.package-archive"
            );
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            intent.setClipData(ClipData.newRawUri("APK", apkUri));

            startActivity(intent);

        } catch (Exception e) {
            showError(
                "Tidak dapat membuka pemasang Android: " + e.getMessage()
            );
        }
    }

    private String formatBytes(long bytes) {
        if (bytes < 1024) {
            return bytes + " B";
        }

        if (bytes < 1024 * 1024) {
            return String.format("%.2f KB", bytes / 1024.0);
        }

        return String.format(
            "%.2f MB",
            bytes / (1024.0 * 1024.0)
        );
    }

    private int dp(int value) {
        return (int) (value * getResources()
            .getDisplayMetrics().density + 0.5f);
    }

    private void showError(String message) {
        new AlertDialog.Builder(this)
            .setTitle("Tidak dapat melanjutkan")
            .setMessage(message)
            .setPositiveButton("OK", null)
            .show();
    }
}
   for (byte b : bytes) hex.append(String.format("%02x", b & 0xff));
        return hex.toString();
    }

    private void requestInstall() {
        if (selectedFile == null || !selectedFile.exists()) {
            Toast.makeText(this, "Pilih APK terlebih dahulu.", Toast.LENGTH_SHORT).show();
            return;
        }
        if (Build.VERSION.SDK_INT >= 26 && !getPackageManager().canRequestPackageInstalls()) {
            new AlertDialog.Builder(this)
                .setTitle("Izinkan instalasi untuk APK Vault")
                .setMessage("Android memerlukan izin khusus agar APK Vault dapat membuka installer untuk file yang kamu pilih. Pada layar pengaturan, aktifkan izin hanya jika kamu ingin melanjutkan.")
                .setNegativeButton("Batal", (d, w) -> d.dismiss())
                .setPositiveButton("Buka Pengaturan", (d, w) -> {
                    Intent settings = new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                        Uri.parse("package:" + getPackageName()));
                    startActivity(settings);
                })
                .show();
            return;
        }

        new AlertDialog.Builder(this)
            .setTitle("Lanjutkan instalasi?")
            .setMessage("APK Vault akan membuka installer Android. Tinjau nama aplikasi dan izin yang diminta sebelum menekan Instal.")
            .setNegativeButton("Batal", (d, w) -> d.dismiss())
            .setPositiveButton("Buka Installer", (d, w) -> launchSystemInstaller())
            .show();
    }

    private void launchSystemInstaller() {
        try {
            Uri apkUri = FileProvider.getUriForFile(this, getPackageName() + ".fileprovider", selectedFile);
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setDataAndType(apkUri, "application/vnd.android.package-archive");
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
        } catch (Exception e) {
            Toast.makeText(this, "Tidak dapat membuka installer: " + safeMessage(e), Toast.LENGTH_LONG).show();
        }
    }

    private String formatBytes(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.1f KB", bytes / 1024.0);
        return String.format("%.1f MB", bytes / (1024.0 * 1024.0));
    }

    private String safeMessage(Exception e) {
        String message = e.getMessage();
        return message == null ? e.getClass().getSimpleName() : message;
    }

    private LinearLayout.LayoutParams matchWrap() {
        return new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        );
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }
}
