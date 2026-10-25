package com.tomz.apkvault;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
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
import java.security.MessageDigest;

public class MainActivity extends Activity {
    private static final int REQUEST_PICK_APK = 1001;
    private static final long MAX_APK_BYTES = 200L * 1024L * 1024L;

    private TextView fileLabel;
    private TextView resultLabel;
    private Button installButton;
    private File selectedFile;
    private String selectedFileName = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        buildInterface();
        showWelcomePopup();
    }

    private void buildInterface() {
        ScrollView scroll = new ScrollView(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(24), dp(32), dp(24), dp(24));
        root.setGravity(Gravity.CENTER_HORIZONTAL);
        scroll.addView(root);

        TextView title = new TextView(this);
        title.setText("APK Vault");
        title.setTextSize(28);
        title.setGravity(Gravity.CENTER);
        title.setTextColor(0xFF172033);
        root.addView(title, matchWrap());

        TextView subtitle = new TextView(this);
        subtitle.setText("Periksa file APK sebelum membuka installer Android.");
        subtitle.setTextSize(14);
        subtitle.setGravity(Gravity.CENTER);
        subtitle.setTextColor(0xFF606B7A);
        LinearLayout.LayoutParams subParams = matchWrap();
        subParams.topMargin = dp(8);
        subParams.bottomMargin = dp(24);
        root.addView(subtitle, subParams);

        Button pickButton = new Button(this);
        pickButton.setText("Pilih File APK");
        root.addView(pickButton, matchWrap());
        pickButton.setOnClickListener(v -> pickApk());

        fileLabel = new TextView(this);
        fileLabel.setText("Belum ada file yang dipilih.");
        fileLabel.setTextSize(14);
        fileLabel.setTextColor(0xFF172033);
        LinearLayout.LayoutParams fileParams = matchWrap();
        fileParams.topMargin = dp(20);
        root.addView(fileLabel, fileParams);

        resultLabel = new TextView(this);
        resultLabel.setText("Hasil pemeriksaan akan tampil di sini.");
        resultLabel.setTextSize(13);
        resultLabel.setTextColor(0xFF3F4B5C);
        resultLabel.setPadding(0, dp(12), 0, dp(12));
        root.addView(resultLabel, matchWrap());

        installButton = new Button(this);
        installButton.setText("Lanjutkan ke Installer");
        installButton.setEnabled(false);
        root.addView(installButton, matchWrap());
        installButton.setOnClickListener(v -> requestInstall());

        TextView disclaimer = new TextView(this);
        disclaimer.setText("SHA-256 dan metadata membantu mengidentifikasi file, tetapi tidak membuktikan APK bebas malware. Instal hanya file dari sumber yang kamu percaya.");
        disclaimer.setTextSize(12);
        disclaimer.setTextColor(0xFF7B3440);
        LinearLayout.LayoutParams dParams = matchWrap();
        dParams.topMargin = dp(20);
        root.addView(disclaimer, dParams);

        setContentView(scroll);
    }

    private void showWelcomePopup() {
        new AlertDialog.Builder(this)
            .setTitle("APK Vault")
            .setMessage("Pilih file APK untuk menghitung SHA-256 dan membaca metadata sebelum membuka installer Android. APK tidak akan dipasang tanpa konfirmasi kamu.")
            .setPositiveButton("Pilih APK", (dialog, which) -> pickApk())
            .setNegativeButton("Nanti", (dialog, which) -> dialog.dismiss())
            .setCancelable(true)
            .show();
    }

    private void pickApk() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("application/vnd.android.package-archive");
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        try {
            startActivityForResult(intent, REQUEST_PICK_APK);
        } catch (ActivityNotFoundException e) {
            Toast.makeText(this, "Pemilih file tidak tersedia di perangkat ini.", Toast.LENGTH_LONG).show();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_PICK_APK && resultCode == RESULT_OK && data != null && data.getData() != null) {
            Uri uri = data.getData();
            try {
                selectedFile = copyToCache(uri);
                selectedFileName = getDisplayName(uri);
                inspectSelectedApk();
            } catch (Exception e) {
                selectedFile = null;
                installButton.setEnabled(false);
                resultLabel.setText("Gagal membaca APK: " + safeMessage(e));
                Toast.makeText(this, "Tidak dapat membaca file APK.", Toast.LENGTH_LONG).show();
            }
        }
    }

    private File copyToCache(Uri uri) throws Exception {
        File dir = new File(getCacheDir(), "selected-apk");
        if (!dir.exists() && !dir.mkdirs()) {
            throw new Exception("Tidak dapat membuat folder cache.");
        }
        File output = new File(dir, "selected.apk");
        long total = 0;
        try (InputStream in = getContentResolver().openInputStream(uri);
             FileOutputStream out = new FileOutputStream(output)) {
            if (in == null) throw new Exception("File tidak dapat dibuka.");
            byte[] buffer = new byte[8192];
            int read;
            while ((read = in.read(buffer)) != -1) {
                total += read;
                if (total > MAX_APK_BYTES) {
                    throw new Exception("Ukuran APK melebihi batas 200 MB.");
                }
                out.write(buffer, 0, read);
            }
            out.flush();
        }
        if (total == 0) throw new Exception("File kosong.");
        return output;
    }

    private String getDisplayName(Uri uri) {
        String name = uri.getLastPathSegment();
        if (name == null || name.trim().isEmpty()) name = "selected.apk";
        int slash = name.lastIndexOf('/');
        if (slash >= 0) name = name.substring(slash + 1);
        return name;
    }

    @SuppressWarnings("deprecation")
    private void inspectSelectedApk() throws Exception {
        if (selectedFile == null || !selectedFile.isFile()) throw new Exception("File tidak ditemukan.");
        long size = selectedFile.length();
        String hash = sha256(selectedFile);

        PackageInfo packageInfo;
        if (Build.VERSION.SDK_INT >= 33) {
            packageInfo = getPackageManager().getPackageArchiveInfo(
                selectedFile.getAbsolutePath(),
                PackageManager.PackageInfoFlags.of(PackageManager.GET_ACTIVITIES)
            );
        } else {
            packageInfo = getPackageManager().getPackageArchiveInfo(
                selectedFile.getAbsolutePath(), PackageManager.GET_ACTIVITIES
            );
        }

        StringBuilder info = new StringBuilder();
        info.append("Nama file: ").append(selectedFileName).append("\n");
        info.append("Ukuran: ").append(formatBytes(size)).append("\n");
        info.append("SHA-256:\n").append(hash).append("\n\n");

        if (packageInfo == null || packageInfo.applicationInfo == null) {
            info.append("Metadata: Android tidak dapat membaca metadata paket dari file ini. File mungkin bukan APK yang valid.");
            installButton.setEnabled(false);
        } else {
            ApplicationInfo appInfo = packageInfo.applicationInfo;
            appInfo.sourceDir = selectedFile.getAbsolutePath();
            appInfo.publicSourceDir = selectedFile.getAbsolutePath();
            CharSequence label;
            try {
                label = getPackageManager().getApplicationLabel(appInfo);
            } catch (Exception e) {
                label = "(nama aplikasi tidak tersedia)";
            }
            info.append("Nama aplikasi: ").append(label).append("\n");
            info.append("Nama paket: ").append(packageInfo.packageName).append("\n");
            info.append("Versi: ").append(packageInfo.versionName == null ? "(tidak tersedia)" : packageInfo.versionName).append("\n");
            if (Build.VERSION.SDK_INT >= 28) {
                info.append("Version code: ").append(packageInfo.getLongVersionCode()).append("\n");
            } else {
                info.append("Version code: ").append(packageInfo.versionCode).append("\n");
            }
            info.append("\nCatatan: metadata berhasil dibaca bukan berarti APK terbukti aman.");
            installButton.setEnabled(true);
        }
        fileLabel.setText("File dipilih: " + selectedFileName);
        resultLabel.setText(info.toString());
    }

    private String sha256(File file) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (InputStream in = new java.io.FileInputStream(file)) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = in.read(buffer)) != -1) digest.update(buffer, 0, read);
        }
        byte[] bytes = digest.digest();
        StringBuilder hex = new StringBuilder();
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
