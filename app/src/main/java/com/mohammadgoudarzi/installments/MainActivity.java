package com.mohammadgoudarzi.installments;

import android.app.Activity;
import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.webkit.JavascriptInterface;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.biometric.BiometricManager;
import androidx.biometric.BiometricPrompt;
import androidx.fragment.app.FragmentActivity;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Executor;

public class MainActivity extends FragmentActivity {

    private static final int REQUEST_CREATE_BACKUP = 4101;
    private static final int REQUEST_OPEN_BACKUP = 4102;
    private static final int REQUEST_NOTIFICATION_PERMISSION = 9001;

    private WebView webView;

    private String pendingBackupText = null;

    private SharedPreferences securityPrefs;
    private SharedPreferences notificationPrefs;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        securityPrefs = getSharedPreferences(
                "app_security",
                MODE_PRIVATE
        );

        notificationPrefs = getSharedPreferences(
                "installment_notifications",
                MODE_PRIVATE
        );

        if (securityPrefs.getBoolean("lock_enabled", false)) {
            showSecurityLock();
        } else {
            initializeApp();
        }
    }

    private void initializeApp() {

        webView = new WebView(this);

        WebSettings settings = webView.getSettings();

        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setAllowFileAccess(true);
        settings.setAllowContentAccess(true);

        settings.setBuiltInZoomControls(false);
        settings.setDisplayZoomControls(false);

        webView.setWebViewClient(new WebViewClient() {

            @Override
            public void onPageFinished(
                    WebView view,
                    String url
            ) {

                super.onPageFinished(view, url);

                injectNativeFeatures();

                requestNotificationPermission();

                scheduleNotificationAlarm();
            }
        });

        webView.addJavascriptInterface(
                new AndroidBridge(),
                "AndroidBridge"
        );

        webView.loadUrl(
                "file:///android_asset/index.html"
        );

        setContentView(webView);
    }

    /*
     * قفل برنامه با اثر انگشت
     * یا قفل خود گوشی
     */
    private void showSecurityLock() {

        BiometricManager biometricManager =
                BiometricManager.from(this);

        int authenticators =
                BiometricManager.Authenticators.BIOMETRIC_STRONG
                        |
                BiometricManager.Authenticators.DEVICE_CREDENTIAL;

        int result =
                biometricManager.canAuthenticate(
                        authenticators
                );

        if (
                result ==
                        BiometricManager.BIOMETRIC_SUCCESS
        ) {

            Executor executor =
                    androidx.core.content.ContextCompat
                            .getMainExecutor(this);

            BiometricPrompt biometricPrompt =
                    new BiometricPrompt(
                            this,
                            executor,
                            new BiometricPrompt.AuthenticationCallback() {

                                @Override
                                public void onAuthenticationSucceeded(
                                        @NonNull BiometricPrompt.AuthenticationResult result
                                ) {

                                    super.onAuthenticationSucceeded(
                                            result
                                    );

                                    initializeApp();
                                }

                                @Override
                                public void onAuthenticationError(
                                        int errorCode,
                                        @NonNull CharSequence errString
                                ) {

                                    super.onAuthenticationError(
                                            errorCode,
                                            errString
                                    );

                                    Toast.makeText(
                                            MainActivity.this,
                                            "برای ورود به برنامه احراز هویت لازم است.",
                                            Toast.LENGTH_LONG
                                    ).show();

                                    finish();
                                }
                            }
                    );

            BiometricPrompt.PromptInfo promptInfo =
                    new BiometricPrompt.PromptInfo.Builder()
                            .setTitle(
                                    "ورود به مدیریت اقساط"
                            )
                            .setSubtitle(
                                    "اثر انگشت یا قفل گوشی را وارد کنید"
                            )
                            .setAllowedAuthenticators(
                                    authenticators
                            )
                            .build();

            biometricPrompt.authenticate(
                    promptInfo
            );

        } else {

            Toast.makeText(
                    this,
                    "قفل صفحه گوشی فعال نیست.",
                    Toast.LENGTH_LONG
            ).show();

            initializeApp();
        }
    }

    /*
     * درخواست اجازه Notification
     * برای Android 13 به بالا
     */
    private void requestNotificationPermission() {

        if (
                Build.VERSION.SDK_INT >=
                        Build.VERSION_CODES.TIRAMISU
        ) {

            if (
                    checkSelfPermission(
                            "android.permission.POST_NOTIFICATIONS"
                    )
                            !=
                    android.content.pm.PackageManager.PERMISSION_GRANTED
            ) {

                requestPermissions(
                        new String[]{
                                "android.permission.POST_NOTIFICATIONS"
                        },
                        REQUEST_NOTIFICATION_PERMISSION
                );
            }
        }
    }

    /*
     * تنظیم Alarm
     */
    private void scheduleNotificationAlarm() {

        try {

            AlarmManager alarmManager =
                    (AlarmManager)
                            getSystemService(
                                    Context.ALARM_SERVICE
                            );

            if (alarmManager == null) {
                return;
            }

            Intent intent =
                    new Intent(
                            this,
                            NotificationReceiver.class
                    );

            PendingIntent pendingIntent =
                    PendingIntent.getBroadcast(
                            this,
                            7001,
                            intent,
                            PendingIntent.FLAG_UPDATE_CURRENT
                                    |
                            pendingIntentFlags()
                    );

            long interval =
                    24L
                            * 60L
                            * 60L
                            * 1000L;

            long firstRun =
                    System.currentTimeMillis()
                            + 60L * 1000L;

            if (
                    Build.VERSION.SDK_INT >=
                            Build.VERSION_CODES.M
            ) {

                alarmManager.setAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        firstRun,
                        pendingIntent
                );

            } else {

                alarmManager.setRepeating(
                        AlarmManager.RTC_WAKEUP,
                        firstRun,
                        interval,
                        pendingIntent
                );
            }

        } catch (Exception e) {

            e.printStackTrace();
        }
    }

    private int pendingIntentFlags() {

        if (
                Build.VERSION.SDK_INT >=
                        Build.VERSION_CODES.M
        ) {

            return PendingIntent.FLAG_IMMUTABLE;
        }

        return 0;
    }

    /*
     * امکانات Native
     */
    private void injectNativeFeatures() {

        String js =
                "javascript:(function(){"

                + "if(window.__nativeFeaturesInstalled)return;"

                + "window.__nativeFeaturesInstalled=true;"

                + "var style=document.createElement('style');"

                + "style.textContent='"

                + ".devMenuButton{"
                + "position:absolute;"
                + "left:10px;"
                + "top:10px;"
                + "width:40px;"
                + "height:40px;"
                + "border-radius:12px;"
                + "background:rgba(255,255,255,.18);"
                + "color:#fff;"
                + "font-size:25px;"
                + "font-weight:bold;"
                + "z-index:10;"
                + "}"

                + ".header{"
                + "position:relative;"
                + "}"

                + ".devMenu{"
                + "position:absolute;"
                + "left:10px;"
                + "top:55px;"
                + "z-index:30;"
                + "display:none;"
                + "background:#fff;"
                + "color:#17202a;"
                + "border-radius:14px;"
                + "box-shadow:0 6px 22px rgba(0,0,0,.22);"
                + "padding:6px;"
                + "min-width:220px;"
                + "}"

                + ".devMenu.open{"
                + "display:block;"
                + "}"

                + ".devMenuItem{"
                + "display:block;"
                + "width:100%;"
                + "padding:12px 14px;"
                + "border-radius:10px;"
                + "background:#fff;"
                + "text-align:right;"
                + "font-size:12px;"
                + "color:#334155;"
                + "}"

                + ".devMenuItem:active{"
                + "background:#f1f5f9;"
                + "}"

                + "';"

                + "document.head.appendChild(style);"

                + "var h=document.querySelector('.header');"

                + "if(h&&!document.getElementById('devMenuButton')){"

                + "var b=document.createElement('button');"

                + "b.id='devMenuButton';"
                + "b.className='devMenuButton';"
                + "b.type='button';"
                + "b.textContent='⋮';"

                + "var m=document.createElement('div');"
                + "m.id='devMenu';"
                + "m.className='devMenu';"

                + "var e=document.createElement('button');"

                + "e.type='button';"
                + "e.className='devMenuItem';"
                + "e.textContent='✉️ پیام به توسعه‌دهنده';"

                + "e.onclick=function(){"
                + "AndroidBridge.contactDeveloper();"
                + "m.classList.remove('open');"
                + "};"

                + "m.appendChild(e);"

                + "var s=document.createElement('button');"

                + "s.type='button';"
                + "s.className='devMenuItem';"
                + "s.textContent='🔐 فعال کردن قفل برنامه';"

                + "s.onclick=function(){"
                + "AndroidBridge.enableAppLock();"
                + "m.classList.remove('open');"
                + "};"

                + "m.appendChild(s);"

                + "var u=document.createElement('button');"

                + "u.type='button';"
                + "u.className='devMenuItem';"
                + "u.textContent='🔓 برداشتن قفل برنامه';"

                + "u.onclick=function(){"
                + "AndroidBridge.disableAppLock();"
                + "m.classList.remove('open');"
                + "};"

                + "m.appendChild(u);"

                + "var n=document.createElement('button');"

                + "n.type='button';"
                + "n.className='devMenuItem';"
                + "n.textContent='🔔 فعال‌سازی یادآوری اقساط';"

                + "n.onclick=function(){"
                + "AndroidBridge.enableNotifications();"
                + "m.classList.remove('open');"
                + "};"

                + "m.appendChild(n);"

                + "h.appendChild(b);"
                + "h.appendChild(m);"

                + "b.onclick=function(ev){"
                + "ev.stopPropagation();"
                + "m.classList.toggle('open');"
                + "};"

                + "document.addEventListener('click',function(ev){"
                + "if(!m.contains(ev.target)&&ev.target!==b)"
                + "m.classList.remove('open');"
                + "});"

                + "}"

                + "window.__nativeImportBackup=function(text){"

                + "try{"

                + "var backup=JSON.parse(text);"

                + "if(!backup||"
                + "backup.app!=='MohammadInstallments'||"
                + "!Array.isArray(backup.installments)){"

                + "alert('این فایل یک پشتیبان معتبر مدیریت اقساط نیست.');"
                + "return;"
                + "}"

                + "if(typeof askConfirm==='function'){"

                + "askConfirm("
                + "'اطلاعات فعلی با اطلاعات موجود در فایل پشتیبان جایگزین شود؟',"
                + "function(){"

                + "installments=backup.installments;"

                + "trash="
                + "Array.isArray(backup.trash)"
                + "?backup.trash"
                + ":[];"

                + "saveData();"
                + "render();"

                + "showToast('پشتیبان با موفقیت بازیابی شد.');"

                + "AndroidBridge.syncInstallments();"

                + "}"
                + ");"

                + "}else{"

                + "installments=backup.installments;"

                + "trash="
                + "Array.isArray(backup.trash)"
                + "?backup.trash"
                + ":[];"

                + "saveData();"
                + "render();"

                + "AndroidBridge.syncInstallments();"

                + "}"

                + "}catch(err){"

                + "alert('این فایل یک پشتیبان معتبر مدیریت اقساط نیست.');"

                + "}"

                + "};"

                + "var ex=document.getElementById('exportButton');"

                + "if(ex){"

                + "ex.onclick=function(){"

                + "var i=localStorage.getItem('mohammad_installments_v10');"

                + "var t=localStorage.getItem('mohammad_installments_trash_v10');"

                + "var backup={"

                + "app:'MohammadInstallments',"

                + "version:10,"

                + "exportedAt:new Date().toISOString(),"

                + "installments:i?JSON.parse(i):[],"

                + "trash:t?JSON.parse(t):[]"

                + "};"

                + "AndroidBridge.saveBackup("
                + "JSON.stringify(backup,null,2)"
                + ");"

                + "};"

                + "}"

                + "var im=document.getElementById('importButton');"

                + "if(im){"

                + "im.onclick=function(){"
                + "AndroidBridge.openBackupPicker();"
                + "};"

                + "}"

                + "})()";

        webView.evaluateJavascript(
                js,
                null
        );
    }

    /*
     * ذخیره پشتیبان
     */
    private void createBackupFile() {

        Intent intent =
                new Intent(
                        Intent.ACTION_CREATE_DOCUMENT
                );

        intent.addCategory(
                Intent.CATEGORY_OPENABLE
        );

        intent.setType(
                "application/json"
        );

        intent.putExtra(
                Intent.EXTRA_TITLE,
                "MohammadInstallments-backup.json"
        );

        startActivityForResult(
                intent,
                REQUEST_CREATE_BACKUP
        );
    }

    /*
     * انتخاب فایل پشتیبان
     */
    private void openBackupPicker() {

        Intent intent =
                new Intent(
                        Intent.ACTION_OPEN_DOCUMENT
                );

        intent.addCategory(
                Intent.CATEGORY_OPENABLE
        );

        intent.setType(
                "application/json"
        );

        intent.putExtra(
                Intent.EXTRA_MIME_TYPES,
                new String[]{
                        "application/json",
                        "text/json"
                }
        );

        startActivityForResult(
                intent,
                REQUEST_OPEN_BACKUP
        );
    }

    private void writeBackup(Uri uri) {

        if (pendingBackupText == null) {
            return;
        }

        try (
                OutputStream output =
                        getContentResolver()
                                .openOutputStream(uri)
        ) {

            if (output == null) {
                throw new IOException(
                        "Output stream is null"
                );
            }

            output.write(
                    pendingBackupText.getBytes(
                            StandardCharsets.UTF_8
                    )
            );

            output.flush();

            Toast.makeText(
                    this,
                    "پشتیبان با موفقیت ذخیره شد.",
                    Toast.LENGTH_SHORT
            ).show();

        } catch (Exception e) {

            Toast.makeText(
                    this,
                    "ذخیره پشتیبان انجام نشد.",
                    Toast.LENGTH_LONG
            ).show();

        } finally {

            pendingBackupText = null;
        }
    }

    private void readBackup(Uri uri) {

        try (
                InputStream input =
                        getContentResolver()
                                .openInputStream(uri);

                BufferedReader reader =
                        new BufferedReader(
                                new InputStreamReader(
                                        input,
                                        StandardCharsets.UTF_8
                                )
                        )
        ) {

            StringBuilder text =
                    new StringBuilder();

            String line;

            while (
                    (line = reader.readLine())
                            != null
            ) {

                text.append(line)
                        .append('\n');
            }

            String json =
                    text.toString();

            String jsArg =
                    org.json.JSONObject.quote(
                            json
                    );

            webView.evaluateJavascript(
                    "window.__nativeImportBackup("
                            + jsArg
                            + ");",
                    null
            );

        } catch (Exception e) {

            Toast.makeText(
                    this,
                    "خواندن فایل پشتیبان انجام نشد.",
                    Toast.LENGTH_LONG
            ).show();
        }
    }

    /*
     * فعال کردن قفل
     */
    private void setAppLock(boolean enabled) {

        securityPrefs.edit()
                .putBoolean(
                        "lock_enabled",
                        enabled
                )
                .apply();

        Toast.makeText(
                this,
                enabled
                        ? "قفل برنامه فعال شد."
                        : "قفل برنامه غیرفعال شد.",
                Toast.LENGTH_SHORT
        ).show();
    }

    /*
     * ذخیره اطلاعات اقساط برای Receiver
     */
    private void syncInstallments(String json) {

        if (json == null) {
            json = "[]";
        }

        notificationPrefs.edit()
                .putString(
                        "installments",
                        json
                )
                .apply();

        scheduleNotificationAlarm();
    }

    /*
     * فعال‌سازی Notification
     */
    private void enableNotifications() {

        requestNotificationPermission();

        scheduleNotificationAlarm();

        Toast.makeText(
                this,
                "یادآوری اقساط فعال شد.",
                Toast.LENGTH_SHORT
        ).show();
    }

    @Override
    protected void onActivityResult(
            int requestCode,
            int resultCode,
            Intent data
    ) {

        super.onActivityResult(
                requestCode,
                resultCode,
                data
        );

        if (
                resultCode != RESULT_OK ||
                data == null ||
                data.getData() == null
        ) {

            pendingBackupText = null;

            return;
        }

        Uri uri =
                data.getData();

        if (
                requestCode ==
                        REQUEST_CREATE_BACKUP
        ) {

            writeBackup(uri);

        } else if (
                requestCode ==
                        REQUEST_OPEN_BACKUP
        ) {

            readBackup(uri);
        }
    }

    /*
     * ارتباط Java و HTML
     */
    public class AndroidBridge {

        @JavascriptInterface
        public void saveBackup(
                String text
        ) {

            runOnUiThread(
                    () -> {

                        pendingBackupText =
                                text;

                        createBackupFile();
                    }
            );
        }

        @JavascriptInterface
        public void openBackupPicker() {

            runOnUiThread(
                    MainActivity.this::
                            openBackupPicker
            );
        }

        @JavascriptInterface
        public void enableAppLock() {

            runOnUiThread(
                    () -> {

                        setAppLock(true);

                        showSecurityLock();
                    }
            );
        }

        @JavascriptInterface
        public void disableAppLock() {

            runOnUiThread(
                    () -> {

                        setAppLock(false);
                    }
            );
        }

        @JavascriptInterface
        public void enableNotifications() {

            runOnUiThread(
                    MainActivity.this::
                            enableNotifications
            );
        }

        /*
         * نسخه اصلاح‌شده
         *
         * اطلاعات اقساط مستقیماً از localStorage
         * خوانده می‌شود.
         */
        @JavascriptInterface
        public void syncInstallments() {

            runOnUiThread(
                    () -> {

                        try {

                            webView.evaluateJavascript(
                                    "localStorage.getItem('mohammad_installments_v10')",
                                    value -> {

                                        if (
                                                value == null ||
                                                "null".equals(value)
                                        ) {

                                            MainActivity.this
                                                    .syncInstallments("[]");

                                            return;
                                        }

                                        String clean =
                                                value;

                                        try {

                                            Object parsed =
                                                    new org.json.JSONTokener(
                                                            value
                                                    ).nextValue();

                                            if (
                                                    parsed instanceof String
                                            ) {

                                                clean =
                                                        (String) parsed;
                                            }

                                        } catch (Exception ignored) {
                                        }

                                        MainActivity.this
                                                .syncInstallments(
                                                        clean
                                                );
                                    }
                            );

                        } catch (Exception e) {

                            e.printStackTrace();
                        }
                    }
            );
        }

        @JavascriptInterface
        public void contactDeveloper() {

            runOnUiThread(
                    () -> {

                        try {

                            Intent intent =
                                    new Intent(
                                            Intent.ACTION_SENDTO
                                    );

                            intent.setData(
                                    Uri.parse(
                                            "mailto:Mohammadgoodarzi7293@gmail.com"
                                    )
                            );

                            intent.putExtra(
                                    Intent.EXTRA_SUBJECT,
                                    "پیام درباره برنامه مدیریت اقساط"
                            );

                            startActivity(
                                    intent
                            );

                        } catch (Exception e) {

                            Toast.makeText(
                                    MainActivity.this,
                                    "برنامه ایمیل روی گوشی پیدا نشد.",
                                    Toast.LENGTH_LONG
                            ).show();
                        }
                    }
            );
        }
    }
}
