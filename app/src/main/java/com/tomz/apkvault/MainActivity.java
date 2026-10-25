
package com.tomz.apkvault;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
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
import java.util.Locale;

public class MainActivity extends Activity {

    private final int bgColor = Color.rgb(14, 17, 24);
    private final int cardColor = Color.rgb(25, 30, 41);
    private final int accentColor = Color.rgb(120, 104, 255);
    private final int textColor = Color.rgb(242, 244, 250);
    private final int mutedColor = Color.rgb(157, 166, 184);

    private LinearLayout root;
    private File apkFile;
    private TextView statusText;
    private TextView nameText;
    private TextView packageText;
    private TextView versionText;
    private TextView sizeText;
    private TextView hashText;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        buildInterface();
        loadApkInformation();

        root.post(() -> {
            if (!isFinishing()) {
                showInstallPrompt();
            }
        });
    }

    private void buildInterface() {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(bgColor);

        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(20), dp(24), dp(20), dp(28));
        root.setBackgroundColor(bgColor);

        scroll.addView(root);
        setContentView(scroll);

        TextView brand = new TextView(this);
        brand.setText("APK VAULT");
        brand.setTextColor(accentColor);
        brand.setTextSize(13);
        brand.setLetterSpacing(0.15f);
        brand.setTypeface(Typeface.DEFAULT_BOLD);
        root.addView(brand);

        TextView heading = new TextView(this);
        heading.setText("APK Manager");
        heading.setTextColor(textColor);
        heading.setTextSize(29);
        heading.setTypeface(Typeface.DEFAULT_BOLD);
        addView(heading, 0, 5, 0, 0);

        TextView subtitle = new TextView(this);
        subtitle.setText(
            "Informasi file, pemeriksaan hash, dan pemasangan manual."
        );
        subtitle.setTextColor(mutedColor);
        subtitle.setTextSize(14);
        addView(subtitle, 0, 0, 0, 22);

        LinearLayout fileCard = makeCard();
        root.addView(fileCard, matchWrap());

        TextView fileLabel = label("FILE APK");
        fileCard.addView(fileLabel);

        nameText = new TextView(this);
        nameText.setText("file.apk");
        nameText.setTextSize(21);
        nameText.setTypeface(Typeface.DEFAULT_BOLD);
        nameText.setTextColor(textColor);
        addViewTo(fileCard, nameText, 0, 10, 0, 5);

        statusText = label("Memeriksa file...");
        fileCard.addView(statusText);

        addDivider(fileCard);

        packageText = valueText();
        addViewTo(fileCard, packageText, 0, 12, 0, 0);

        versionText = valueText();
        addViewTo(fileCard, versionText, 0, 10, 0, 0);

        sizeText = valueText();
        addViewTo(fileCard, sizeText, 0, 10, 0, 0);

        TextView securityHeading = new TextView(this);
        securityHeading.setText("Pemeriksaan SHA-256");
        securityHeading.setTextColor(textColor);
        securityHeading.setTextSize(17);
        securityHeading.setTypeface(Typeface.DEFAULT_BOLD);
        addView(securityHeading, 0, 24, 0, 8);

        LinearLayout hashCard = makeCard();
        root.addView(hashCard, matchWrap());

        hashText = new TextView(this);
        hashText.setText("Hash belum tersedia");
        hashText.setTextColor(mutedColor);
        hashText.setTextSize(12);
        hashText.setTextIsSelectable(true);
        hashText.setTypeface(Typeface.MONOSPACE);
        hashCard.addView(hashText);

        Button copyButton = makeButton("Salin SHA-256", false);
        addView(copyButton, 0, 12, 0, 0);
        copyButton.setOnClickListener(v -> copyHash());

        Button installButton = makeButton("Lanjutkan ke pemasangan", true);
        addView(installButton, 0, 22, 0, 0);
        installButton.setOnClickListener(v -> requestInstall());

        TextView note = new TextView(this);
        note.setText(
            "Instal hanya APK dari sumber yang kamu percayai. " +
            "APK Vault tidak melewati pemeriksaan keamanan Android."
        );
        note.setTextColor(mutedColor);
        note.setTextSize(12);
        note.setGravity(Gravity.CENTER);
        addView(note, 0, 16, 0, 0);
    }

    private void loadApkInformation() {
        try {
            apkFile = copyBundledApk();

            if (apkFile == null || !apkFile.isFile()) {
                showUnavailable();
                return;
            }

            nameText.setText(apkFile.getName());
            statusText.setText("File tersedia");
            statusText.setTextColor(Color.rgb(104, 220, 160));

            sizeText.setText(
                "Ukuran: " + formatSize(apkFile.length())
            );

            hashText.setText(sha256(apkFile));

            PackageManager pm = getPackageManager();
            PackageInfo info;

            if (Build.VERSION.SDK_INT >= 33) {
                info = pm.getPackageArchiveInfo(
                    apkFile.getAbsolutePath(),
                    PackageManager.PackageInfoFlags.of(0)
                );
            } else {
                info = pm.getPackageArchiveInfo(
                    apkFile.getAbsolutePath(), 0
                );
            }

            if (info != null) {
                packageText.setText(
                    "Paket: " + info.packageName
                );

                String version = info.versionName;
                if (version == null || version.isEmpty()) {
                    version = "Tidak tersedia";
                }

                versionText.setText("Versi: " + version);
            } else {
                packageText.setText(
                    "Paket: metadata tidak dapat dibaca"
                );
                versionText.setText("Versi: tidak tersedia");
            }

        } catch (Exception e) {
            showUnavailable();
            hashText.setText("Gagal membaca file APK");
        }
    }

    private File copyBundledApk() throws Exception {
        File output = new File(getCacheDir(), "file.apk");

        try (InputStream input = getAssets().open("file.apk");
             FileOutputStream out = new FileOutputStream(output)) {

            byte[] buffer = new byte[8192];
            int count;

            while ((count = input.read(buffer)) != -1) {
                out.write(buffer, 0, count);
            }
        }

        return output;
    }

    private String sha256(File file) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");

        try (InputStream input =
                 new java.io.FileInputStream(file)) {

            byte[] buffer = new byte[8192];
            int count;

            while ((count = input.read(buffer)) != -1) {
                digest.update(buffer, 0, count);
            }
        }

        byte[] bytes = digest.digest();
        StringBuilder result = new StringBuilder();

        for (byte b : bytes) {
            result.append(
                String.format(Locale.US, "%02x", b & 0xff)
            );
        }

        return result.toString();
    }

    private void showInstallPrompt() {
        new AlertDialog.Builder(this)
            .setTitle("APK tersedia")
            .setMessage(
                "File APK tersedia untuk diperiksa.\n\n" +
                "Pastikan kamu mengenali sumber dan isi file " +
                "sebelum memasangnya."
            )
            .setNegativeButton("Nanti", (dialog, which) ->
                dialog.dismiss()
            )
            .setPositiveButton("Lihat informasi", (dialog, which) -> {
                // Informasi APK sudah ditampilkan pada layar utama.
            })
            .setCancelable(true)
            .show();
    }

    private void requestInstall() {
        if (apkFile == null || !apkFile.isFile()) {
            toast("File APK tidak tersedia.");
            return;
        }

        try {
            Uri uri = FileProvider.getUriForFile(
                this,
                getPackageName() + ".fileprovider",
                apkFile
            );

            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setDataAndType(
                uri,
                "application/vnd.android.package-archive"
            );
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);

            startActivity(intent);

        } catch (Exception e) {
            toast("Tidak dapat membuka pemasang: " + e.getMessage());
        }
    }

    private void copyHash() {
        String hash = hashText.getText().toString();

        if (hash.isEmpty() || hash.equals("Hash belum tersedia")) {
            toast("Hash belum tersedia.");
            return;
        }

        android.content.ClipboardManager clipboard =
            (android.content.ClipboardManager)
                getSystemService(CLIPBOARD_SERVICE);

        if (clipboard != null) {
            clipboard.setPrimaryClip(
                android.content.ClipData.newPlainText(
                    "SHA-256", hash
                )
            );
            toast("SHA-256 disalin.");
        }
    }

    private void showUnavailable() {
        if (statusText != null) {
            statusText.setText("File APK tidak tersedia");
            statusText.setTextColor(Color.rgb(255, 110, 110));
        }

        if (nameText != null) {
            nameText.setText("file.apk");
        }

        if (packageText != null) {
            packageText.setText("Paket: —");
        }

        if (versionText != null) {
            versionText.setText("Versi: —");
        }

        if (sizeText != null) {
            sizeText.setText("Ukuran: —");
        }

        if (hashText != null) {
            hashText.setText("Hash belum tersedia");
        }
    }

    private LinearLayout makeCard() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(16), dp(16), dp(16), dp(16));

        GradientDrawable shape = new GradientDrawable();
        shape.setColor(cardColor);
        shape.setCornerRadius(dp(18));
        shape.setStroke(dp(1), Color.rgb(45, 52, 68));
        card.setBackground(shape);

        return card;
    }

    private Button makeButton(String text, boolean primary) {
        Button button = new Button(this);
        button.setText(text);
        button.setTextSize(14);
        button.setAllCaps(false);
        button.setTypeface(Typeface.DEFAULT_BOLD);
        button.setMinHeight(dp(50));

        GradientDrawable shape = new GradientDrawable();
        shape.setCornerRadius(dp(14));

        if (primary) {
            shape.setColor(accentColor);
            button.setTextColor(Color.WHITE);
        } else {
            shape.setColor(cardColor);
            shape.setStroke(dp(1), accentColor);
            button.setTextColor(textColor);
        }

        button.setBackground(shape);
        return button;
    }

    private TextView label(String text) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setTextSize(11);
        view.setTypeface(Typeface.DEFAULT_BOLD);
        view.setTextColor(mutedColor);
        return view;
    }

    private TextView valueText() {
        TextView view = new TextView(this);
        view.setTextColor(textColor);
        view.setTextSize(13);
        view.setTextIsSelectable(true);
        return view;
    }

    private void addDivider(LinearLayout parent) {
        View divider = new View(this);
        divider.setBackgroundColor(Color.rgb(49, 56, 72));

        LinearLayout.LayoutParams params =
            new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(1)
            );

        params.topMargin = dp(16);
        params.bottomMargin = dp(4);
        parent.addView(divider, params);
    }

    private void addView(
        View view, int left, int top, int right, int bottom
    ) {
        LinearLayout.LayoutParams params =
            new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            );

        params.setMargins(dp(left), dp(top), dp(right), dp(bottom));
        root.addView(view, params);
    }

    private void addViewTo(
        LinearLayout parent, View view,
        int left, int top, int right, int bottom
    ) {
        LinearLayout.LayoutParams params =
            new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            );

        params.setMargins(dp(left), dp(top), dp(right), dp(bottom));
        parent.addView(view, params);
    }

    private LinearLayout.LayoutParams matchWrap() {
        return new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        );
    }

    private int dp(int value) {
        return (int) (
            value * getResources().getDisplayMetrics().density + 0.5f
        );
    }

    private String formatSize(long bytes) {
        if (bytes < 1024) {
            return bytes + " B";
        }

        if (bytes < 1024L * 1024L) {
            return String.format(
                Locale.US, "%.1f KB", bytes / 1024.0
            );
        }

        return String.format(
            Locale.US, "%.2f MB",
            bytes / (1024.0 * 1024.0)
        );
    }

    private void toast(String message) {
        Toast.makeText(
            this, message, Toast.LENGTH_LONG
        ).show();
    }
}
