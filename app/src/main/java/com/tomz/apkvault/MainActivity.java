
package com.tomz.apkvault;

import android.app.Activity;
import android.app.AlertDialog;
import android.os.Bundle;
import android.os.Build;
import android.content.Intent;
import android.net.Uri;
import android.provider.Settings;
import android.database.Cursor;
import android.provider.OpenableColumns;
import android.content.pm.PackageInfo;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.graphics.Typeface;
import android.view.View;
import android.widget.Toast;

import androidx.core.content.FileProvider;

import java.io.File;
import java.io.InputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.security.MessageDigest;
import java.util.Locale;

public class MainActivity extends Activity {

    private static final int PICK_APK = 100;
    private static final int INSTALL_SETTINGS = 101;
    private static final long MAX_APK_SIZE = 200L * 1024 * 1024;

    private TextView statusText;
    private TextView detailsText;
    private Button installButton;
    private Button chooseButton;

    private File selectedApk;
    private String selectedName = "";
    private String packageName = "";
    private String versionName = "";
    private boolean offerShown = false;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);

        buildInterface();

        // Muat APK bawaan ketika aplikasi dibuka.
        loadBundledApk();
    }

    private void buildInterface() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(40, 48, 40, 32);

        TextView title = new TextView(this);
        title.setText("APK Vault");
        title.setTextSize(28);
        title.setTypeface(null, Typeface.BOLD);

        TextView subtitle = new TextView(this);
        subtitle.setText("Local APK Manager");
        subtitle.setTextSize(15);

        statusText = new TextView(this);
        statusText.setTextSize(15);
        statusText.setText("Memeriksa APK bawaan...");

        detailsText = new TextView(this);
        detailsText.setTextSize(14);
        detailsText.setText(
            "APK tambahan akan diperiksa sebelum ditawarkan untuk instalasi."
        );

        installButton = new Button(this);
        installButton.setText("Lihat & Instal");
        installButton.setEnabled(false);

        chooseButton = new Button(this);
        chooseButton.setText("Pilih APK lain");

        root.addView(title);
        root.addView(subtitle);
        root.addView(statusText);
        root.addView(detailsText);
        root.addView(installButton);
        root.addView(chooseButton);

        setContentView(root);

        installButton.setOnClickListener(v -> requestInstall());

        chooseButton.setOnClickListener(v -> chooseApk());
    }

    private File getApkCacheDirectory() throws IOException {
        File dir = new File(getCacheDir(), "selected-apk");

        if (!dir.exists() && !dir.mkdirs()) {
            throw new IOException("Gagal membuat folder cache.");
        }

        return dir;
    }

    private void loadBundledApk() {
        new Thread(() -> {
            File copiedFile = null;

            try {
                File dir = getApkCacheDirectory();
                copiedFile = new File(dir, "bundled.apk");

                try (InputStream in = getAssets().open("file.apk")) {
                    copyAndCheckSize(in, copiedFile);
                }

                File resultFile = copiedFile;

                runOnUiThread(() ->
                    inspectAndDisplay(resultFile, "file.apk", true)
                );

            } catch (Exception e) {
                if (copiedFile != null) {
                    copiedFile.delete();
                }

                runOnUiThread(() -> {
                    statusText.setText("APK bawaan tidak tersedia.");
                    detailsText.setText(
                        "Pastikan file berada di " +
                        "app/src/main/assets/file.apk.\n\n" +
                        "Kamu juga dapat memilih APK secara manual."
                    );

                    installButton.setEnabled(false);
                    chooseButton.setEnabled(true);
                });
            }
        }).start();
    }

    private void chooseApk() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("application/vnd.android.package-archive");

        startActivityForResult(intent, PICK_APK);
    }

    @Override
    protected void onActivityResult(
            int requestCode, int resultCode, Intent data) {

        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == INSTALL_SETTINGS) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                    && getPackageManager().canRequestPackageInstalls()) {
                launchInstaller();
            } else {
                Toast.makeText(
                    this,
                    "Izin instalasi belum diaktifkan.",
                    Toast.LENGTH_LONG
                ).show();
            }
            return;
        }

        if (requestCode != PICK_APK
                || resultCode != RESULT_OK
                || data == null
                || data.getData() == null) {
            return;
        }

        Uri uri = data.getData();
        String displayName = getDisplayName(uri);

        if (displayName == null
                || !displayName.toLowerCase(Locale.ROOT).endsWith(".apk")) {
            showError("Pilih file dengan ekstensi .apk.");
            return;
        }

        new Thread(() -> {
            File destination = null;

            try {
                File dir = getApkCacheDirectory();
                destination = new File(dir, "selected.apk");

                try (InputStream in =
                        getContentResolver().openInputStream(uri)) {

                    if (in == null) {
                        throw new IOException("File tidak dapat dibaca.");
                    }

                    copyAndCheckSize(in, destination);
                }

                File resultFile = destination;

                runOnUiThread(() ->
                    inspectAndDisplay(resultFile, displayName, false)
                );

            } catch (Exception e) {
                if (destination != null) {
                    destination.delete();
                }

                runOnUiThread(() ->
                    showError("Pemeriksaan gagal: " + e.getMessage())
                );
            }
        }).start();
    }

    private void copyAndCheckSize(InputStream in, File destination)
            throws IOException {

        long total = 0;

        try (FileOutputStream out = new FileOutputStream(destination)) {
            byte[] buffer = new byte[8192];
            int count;

            while ((count = in.read(buffer)) != -1) {
                total += count;

                if (total > MAX_APK_SIZE) {
                    throw new IOException(
                        "Ukuran APK melebihi batas 200 MB."
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
    }

    private void inspectAndDisplay(
            File apkFile, String name, boolean showOffer) {

        try {
            PackageInfo info = getPackageManager()
                .getPackageArchiveInfo(apkFile.getAbsolutePath(), 0);

            if (info == null || info.applicationInfo == null) {
                apkFile.delete();
                showError("File tidak dapat dibaca sebagai APK valid.");
                return;
            }

            String hash = sha256(apkFile);

            selectedApk = apkFile;
            selectedName = name;
            packageName = info.packageName;
            versionName = info.versionName == null
                ? "Tidak diketahui"
                : info.versionName;

            String label = packageName;

            try {
                info.applicationInfo.sourceDir = apkFile.getAbsolutePath();
                info.applicationInfo.publicSourceDir =
                    apkFile.getAbsolutePath();

                CharSequence appLabel =
                    getPackageManager().getApplicationLabel(
                        info.applicationInfo
                    );

                if (appLabel != null && appLabel.length() > 0) {
                    label = appLabel.toString();
                }
            } catch (Exception ignored) {
                // Nama paket tetap dapat ditampilkan.
            }

            detailsText.setText(
                "Nama file: " + selectedName +
                "\nNama aplikasi: " + label +
                "\nPackage: " + packageName +
                "\nVersi: " + versionName +
                "\nUkuran: " + (apkFile.length() / 1024) + " KB" +
                "\nSHA-256:\n" + hash +
                "\n\nPemeriksaan ini bukan pemindai malware. " +
                "Pastikan sumber APK dapat dipercaya."
            );

            statusText.setText("APK berhasil diperiksa.");
            installButton.setEnabled(true);

            if (showOffer && !offerShown) {
                showInstallOffer();
            }

        } catch (Exception e) {
            selectedApk = null;
            installButton.setEnabled(false);
            showError("Tidak dapat memeriksa APK: " + e.getMessage());
        }
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
            // Tangani nama yang tidak tersedia.
        }

        return uri.getLastPathSegment();
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

    private void showInstallOffer() {
        if (offerShown || isFinishing()) {
            return;
        }

        offerShown = true;

        new AlertDialog.Builder(this)
            .setTitle("Aplikasi tambahan tersedia")
            .setMessage(
                "APK Vault menyertakan aplikasi tambahan.\n\n" +
                "Nama paket: " + packageName +
                "\nVersi: " + versionName +
                "\n\nPastikan kamu mengenali dan memercayai " +
                "aplikasi tersebut sebelum memasangnya."
            )
            .setNegativeButton("Nanti", (dialog, which) ->
                dialog.dismiss()
            )
            .setPositiveButton("Lihat & Instal", (dialog, which) ->
                requestInstall()
            )
            .setCancelable(true)
            .show();
    }

    private void requestInstall() {
        if (selectedApk == null || !selectedApk.isFile()) {
            showError("APK belum tersedia. Pilih atau muat APK terlebih dahulu.");
            return;
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                && !getPackageManager().canRequestPackageInstalls()) {

            Intent settings = new Intent(
                Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                Uri.parse("package:" + getPackageName())
            );

            startActivityForResult(settings, INSTALL_SETTINGS);
            return;
        }

        launchInstaller();
    }

    private void launchInstaller() {
        if (selectedApk == null || !selectedApk.isFile()) {
            showError("File APK tidak tersedia.");
            return;
        }

        try {
            Uri apkUri = FileProvider.getUriForFile(
                this,
                getPackageName() + ".fileprovider",
                selectedApk
            );

            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setDataAndType(
                apkUri,
                "application/vnd.android.package-archive"
            );
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);

            startActivity(intent);

        } catch (Exception e) {
            showError(
                "Tidak dapat membuka penginstal Android: " +
                e.getMessage()
            );
        }
    }

    private void showError(String message) {
        statusText.setText("Terjadi masalah.");
        Toast.makeText(
            this,
            message,
            Toast.LENGTH_LONG
        ).show();
    }
}
