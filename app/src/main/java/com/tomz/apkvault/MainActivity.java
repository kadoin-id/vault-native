
package com.tomz.apkvault;

import android.app.Activity;
import android.os.Bundle;
import android.os.Build;
import android.content.Intent;
import android.content.ClipData;
import android.net.Uri;
import android.provider.Settings;
import android.database.Cursor;
import android.provider.OpenableColumns;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.view.View;
import android.graphics.Typeface;
import android.widget.Toast;

import androidx.core.content.FileProvider;

import java.io.File;
import java.io.InputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.security.MessageDigest;

public class MainActivity extends Activity {

    private static final int PICK_APK = 100;
    private static final int INSTALL_SETTINGS = 101;
    private static final long MAX_APK_SIZE = 200L * 1024 * 1024;

    private TextView info;
    private Button installButton;
    private File selectedApk;
    private String selectedName = "";

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(36, 40, 36, 30);

        TextView title = new TextView(this);
        title.setText("APK Vault");
        title.setTextSize(28);
        title.setTypeface(null, Typeface.BOLD);

        TextView subtitle = new TextView(this);
        subtitle.setText("Local APK Manager");
        subtitle.setTextSize(15);

        Button chooseButton = new Button(this);
        chooseButton.setText("Pilih file APK");

        installButton = new Button(this);
        installButton.setText("Lanjutkan ke penginstal Android");
        installButton.setEnabled(false);

        info = new TextView(this);
        info.setTextSize(15);
        info.setText(
            "Pilih APK untuk memeriksa file sebelum instalasi.\n\n" +
            "Pemeriksaan dasar tidak menjamin APK bebas malware."
        );

        root.addView(title);
        root.addView(subtitle);
        root.addView(chooseButton);
        root.addView(info);
        root.addView(installButton);

        setContentView(root);

        chooseButton.setOnClickListener(v -> chooseApk());
        installButton.setOnClickListener(v -> requestInstall());
    }

    private void chooseApk() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        startActivityForResult(intent, PICK_APK);
    }

    @Override
    protected void onActivityResult(
            int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode != PICK_APK || resultCode != RESULT_OK
                || data == null || data.getData() == null) {
            return;
        }

        Uri uri = data.getData();
        selectedName = getDisplayName(uri);

        // Jangan menerima file hanya berdasarkan nama.
        if (!selectedName.toLowerCase().endsWith(".apk")) {
            resetSelection("Pilih file dengan ekstensi .apk.");
            return;
        }

        try {
            File dir = new File(getCacheDir(), "selected-apk");
            if (!dir.exists() && !dir.mkdirs()) {
                throw new IOException("Gagal membuat folder cache.");
            }

            File destination = new File(dir, "selected.apk");
            long size = copyAndCheckSize(uri, destination);

            PackageInfo packageInfo =
                getPackageManager().getPackageArchiveInfo(
                    destination.getAbsolutePath(), 0
                );

            if (packageInfo == null ||
                    packageInfo.applicationInfo == null) {
                destination.delete();
                resetSelection("File tidak terbaca sebagai APK valid.");
                return;
            }

            selectedApk = destination;

            String hash = sha256(destination);

            info.setText(
                "File: " + selectedName +
                "\nUkuran: " + (size / 1024) + " KB" +
                "\nPaket: " + packageInfo.packageName +
                "\nVersi: " + packageInfo.versionName +
                "\nSHA-256: " + hash +
                "\n\nStatus: Struktur paket dapat dibaca." +
                "\n\nPeringatan: hash dan metadata bukan pemindai malware. " +
                "Pastikan sumber APK tepercaya sebelum melanjutkan."
            );

            installButton.setEnabled(true);

        } catch (Exception e) {
            resetSelection("Pemeriksaan gagal: " + e.getMessage());
        }
    }

    private long copyAndCheckSize(Uri uri, File destination)
            throws IOException {
        long total = 0;

        try (InputStream in = getContentResolver().openInputStream(uri);
             FileOutputStream out = new FileOutputStream(destination)) {

            if (in == null) {
                throw new IOException("File tidak dapat dibaca.");
            }

            byte[] buffer = new byte[8192];
            int count;

            while ((count = in.read(buffer)) != -1) {
                total += count;

                if (total > MAX_APK_SIZE) {
                    throw new IOException(
                        "Ukuran file melebihi batas 200 MB."
                    );
                }

                out.write(buffer, 0, count);
            }

            out.flush();
        } catch (IOException e) {
            destination.delete();
            throw e;
        }

        if (total == 0) {
            destination.delete();
            throw new IOException("File kosong.");
        }

        return total;
    }

    private String getDisplayName(Uri uri) {
        try (Cursor cursor = getContentResolver().query(
                uri, null, null, null, null)) {

            if (cursor != null && cursor.moveToFirst()) {
                int index = cursor.getColumnIndex(
                    OpenableColumns.DISPLAY_NAME
                );

                if (index >= 0) {
                    return cursor.getString(index);
                }
            }
        } catch (Exception ignored) {
            // Nama alternatif di bawah.
        }

        return uri.getLastPathSegment() == null
            ? "unknown"
            : uri.getLastPathSegment();
    }

    private String sha256(File file) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");

        try (InputStream in = new java.io.FileInputStream(file)) {
            byte[] buffer = new byte[8192];
            int count;

            while ((count = in.read(buffer)) != -1) {
                digest.update(buffer, 0, count);
            }
        }

        StringBuilder result = new StringBuilder();

        for (byte b : digest.digest()) {
            result.append(String.format("%02x", b & 0xff));
        }

        return result.toString();
    }

    private void requestInstall() {
        if (selectedApk == null || !selectedApk.isFile()) {
            resetSelection("Pilih APK dan lakukan pemeriksaan dahulu.");
            return;
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
                !getPackageManager().canRequestPackageInstalls()) {

            Intent settings = new Intent(
                Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                Uri.parse("package:" + getPackageName())
            );

            startActivityForResult(settings, INSTALL_SETTINGS);

            Toast.makeText(
                this,
                "Aktifkan izin instalasi untuk APK Vault, lalu kembali.",
                Toast.LENGTH_LONG
            ).show();

            return;
        }

        try {
            Uri apkUri = FileProvider.getUriForFile(
                this,
                getPackageName() + ".provider",
                selectedApk
            );

            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setDataAndType(
                apkUri,
                "application/vnd.android.package-archive"
            );
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

            startActivity(intent);

        } catch (Exception e) {
            Toast.makeText(
                this,
                "Tidak dapat membuka penginstal: " + e.getMessage(),
                Toast.LENGTH_LONG
            ).show();
        }
    }

    private void resetSelection(String message) {
        selectedApk = null;
        installButton.setEnabled(false);
        info.setText(message);
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
    }
}
