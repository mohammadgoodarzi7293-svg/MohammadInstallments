package com.mohammadgoudarzi.installments;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
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
import java.util.Calendar;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.Executor;

public class MainActivity extends FragmentActivity {

    private static final int REQUEST_CREATE_BACKUP = 4101;
    private static final int REQUEST_OPEN_BACKUP = 4102;
    private static final int REQUEST_NOTIFICATION_PERMISSION = 9001;

    private static final int NOTIFICATION_ALARM_REQUEST_CODE = 7001;

    private static final String NOTIFICATION_PREFS =
            "installment_notifications";

    private static final String REMINDER_ENABLED =
            "reminder_enabled";

    private static final String REMINDER_DAYS =
            "reminder_days";

    private static final String REMINDER_TIME =
            "reminder_time";

    private static final String INSTALLMENTS_KEY =
            "installments";

    private static final String POSTED_NOTIFICATION_IDS =
            "posted_notification_ids";

    private WebView webView;

    /*
     * =========================================================
     * Premium Manager
     * =========================================================
     */
    private PremiumManager premiumManager;

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

        /*
         * PremiumManager
         */
        premiumManager = new PremiumManager(this);

        notificationPrefs = getSharedPreferences(
                NOTIFICATION_PREFS,
                MODE_PRIVATE
        );

        /*
         * تنظیمات پیش‌فرض یادآوری
         */
        if (!notificationPrefs.contains(REMINDER_ENABLED)) {

            notificationPrefs.edit()
                    .putBoolean(
                            REMINDER_ENABLED,
                            true
                    )
                    .putInt(
                            REMINDER_DAYS,
                            2
                    )
                    .putString(
                            REMINDER_TIME,
                            "16:00"
                    )
                    .apply();
        }

        if (
                securityPrefs.getBoolean(
                        "lock_enabled",
                        false
                )
        ) {

            showSecurityLock();

        } else {

            initializeApp();
        }
    }

    /*
     * =========================================================
     * راه‌اندازی برنامه
     * =========================================================
     */
    private void initializeApp() {

        webView = new WebView(this);

        WebSettings settings =
                webView.getSettings();

        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setAllowFileAccess(true);
        settings.setAllowContentAccess(true);

        settings.setBuiltInZoomControls(false);
        settings.setDisplayZoomControls(false);

        webView.setWebViewClient(
                new WebViewClient() {

                    @Override
                    public void onPageFinished(
                            WebView view,
                            String url
                    ) {

                        super.onPageFinished(
                                view,
                                url
                        );

                        injectNativeFeatures();

                        requestNotificationPermission();

                        /*
                         * اطلاعات فعلی اقساط
                         * از WebView گرفته می‌شود.
                         */
                        syncInstallmentsFromWebView();

                        /*
                         * آلارم روزانه ساخته می‌شود.
                         */
                        scheduleNotificationAlarm();
                    }
                }
        );

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
     * =========================================================
     * قفل برنامه
     * =========================================================
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
                            new BiometricPrompt
                                    .AuthenticationCallback() {

                                @Override
                                public void
                                onAuthenticationSucceeded(
                                        @NonNull
                                        BiometricPrompt.AuthenticationResult result
                                ) {

                                    super
                                            .onAuthenticationSucceeded(
                                                    result
                                            );

                                    initializeApp();
                                }

                                @Override
                                public void
                                onAuthenticationError(
                                        int errorCode,
                                        @NonNull
                                        CharSequence errString
                                ) {

                                    super
                                            .onAuthenticationError(
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
     * =========================================================
     * اجازه Notification
     * =========================================================
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
                    PackageManager.PERMISSION_GRANTED
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
     * =========================================================
     * بررسی اجازه Alarm دقیق
     * =========================================================
     */
    private boolean canScheduleExactAlarms() {

        if (
                Build.VERSION.SDK_INT >=
                        Build.VERSION_CODES.S
        ) {

            AlarmManager alarmManager =
                    (AlarmManager)
                            getSystemService(
                                    Context.ALARM_SERVICE
                            );

            return alarmManager != null
                    &&
                    alarmManager.canScheduleExactAlarms();
        }

        return true;
    }

    /*
     * =========================================================
     * درخواست اجازه Alarm دقیق
     * =========================================================
     */
    private void requestExactAlarmPermission() {

        if (
                Build.VERSION.SDK_INT >=
                        Build.VERSION_CODES.S
        ) {

            try {

                Intent intent =
                        new Intent(
                                Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM
                        );

                intent.setData(
                        Uri.parse(
                                "package:"
                                        + getPackageName()
                        )
                );

                startActivity(intent);

            } catch (Exception e) {

                e.printStackTrace();
            }
        }
    }

    /*
     * =========================================================
     * لغو Alarm قبلی
     * =========================================================
     */
    private void cancelNotificationAlarm() {

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
                            NOTIFICATION_ALARM_REQUEST_CODE,
                            intent,
                            PendingIntent.FLAG_UPDATE_CURRENT
                                    |
                            pendingIntentFlags()
                    );

            alarmManager.cancel(
                    pendingIntent
            );

            pendingIntent.cancel();

        } catch (Exception e) {

            e.printStackTrace();
        }
    }

    /*
     * =========================================================
     * ساخت Alarm روزانه
     * =========================================================
     */
    private void scheduleNotificationAlarm() {

        try {

            boolean enabled =
                    notificationPrefs.getBoolean(
                            REMINDER_ENABLED,
                            true
                    );

            if (!enabled) {

                cancelNotificationAlarm();

                return;
            }

            AlarmManager alarmManager =
                    (AlarmManager)
                            getSystemService(
                                    Context.ALARM_SERVICE
                            );

            if (alarmManager == null) {
                return;
            }

            if (!canScheduleExactAlarms()) {

                requestExactAlarmPermission();

                return;
            }

            String time =
                    notificationPrefs.getString(
                            REMINDER_TIME,
                            "16:00"
                    );

            int hour = 16;
            int minute = 0;

            try {

                String[] parts =
                        time.split(":");

                if (parts.length >= 2) {

                    hour =
                            Integer.parseInt(
                                    parts[0]
                            );

                    minute =
                            Integer.parseInt(
                                    parts[1]
                            );
                }

            } catch (Exception ignored) {

                hour = 16;
                minute = 0;
            }

            if (hour < 0 || hour > 23) {
                hour = 16;
            }

            if (minute < 0 || minute > 59) {
                minute = 0;
            }

            cancelNotificationAlarm();

            Calendar calendar =
                    Calendar.getInstance();

            calendar.set(
                    Calendar.HOUR_OF_DAY,
                    hour
            );

            calendar.set(
                    Calendar.MINUTE,
                    minute
            );

            calendar.set(
                    Calendar.SECOND,
                    0
            );

            calendar.set(
                    Calendar.MILLISECOND,
                    0
            );

            if (
                    calendar.getTimeInMillis()
                            <=
                    System.currentTimeMillis()
            ) {

                calendar.add(
                        Calendar.DAY_OF_YEAR,
                        1
                );
            }

            Intent intent =
                    new Intent(
                            this,
                            NotificationReceiver.class
                    );

            PendingIntent pendingIntent =
                    PendingIntent.getBroadcast(
                            this,
                            NOTIFICATION_ALARM_REQUEST_CODE,
                            intent,
                            PendingIntent.FLAG_UPDATE_CURRENT
                                    |
                            pendingIntentFlags()
                    );

            long triggerAt =
                    calendar.getTimeInMillis();

            if (
                    Build.VERSION.SDK_INT >=
                            Build.VERSION_CODES.M
            ) {

                alarmManager.setExactAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        triggerAt,
                        pendingIntent
                );

            } else {

                alarmManager.setExact(
                        AlarmManager.RTC_WAKEUP,
                        triggerAt,
                        pendingIntent
                );
            }

        } catch (SecurityException e) {

            e.printStackTrace();

            requestExactAlarmPermission();

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
     * =========================================================
     * پاک کردن سابقه اعلان‌ها
     * =========================================================
     */
    private void clearNotificationHistory() {

        try {

            Set<String> keys =
                    new HashSet<>();

            for (
                    java.util.Map.Entry<String, ?>
                            entry :
                    notificationPrefs.getAll().entrySet()
            ) {

                String key =
                        entry.getKey();

                if (
                        key.startsWith("notified_")
                ) {

                    keys.add(key);
                }
            }

            SharedPreferences.Editor editor =
                    notificationPrefs.edit();

            for (String key : keys) {

                editor.remove(key);
            }

            editor.apply();

        } catch (Exception e) {

            e.printStackTrace();
        }
    }

    /*
     * =========================================================
     * ذخیره اطلاعات اقساط
     * =========================================================
     */
    private void syncInstallments(String json) {

        if (
                json == null ||
                json.trim().isEmpty()
        ) {

            json = "[]";
        }

        notificationPrefs.edit()
                .putString(
                        INSTALLMENTS_KEY,
                        json
                )
                .apply();

        NotificationReceiver
                .cancelAllPostedNotifications(this);

        clearNotificationHistory();

        scheduleNotificationAlarm();
    }

    /*
     * =========================================================
     * گرفتن اقساط از LocalStorage
     * =========================================================
     */
    private void syncInstallmentsFromWebView() {

        if (webView == null) {
            return;
        }

        try {

            webView.evaluateJavascript(
                    "localStorage.getItem('mohammad_installments_v10')",
                    value -> {

                        if (
                                value == null ||
                                "null".equals(value)
                        ) {

                            syncInstallments("[]");

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
                                        (String)
                                                parsed;
                            }

                        } catch (Exception ignored) {
                        }

                        syncInstallments(
                                clean
                        );
                    }
            );

        } catch (Exception e) {

            e.printStackTrace();
        }
    }

    /*
     * =========================================================
     * فعال کردن یادآوری
     * =========================================================
     */
    private void enableNotifications() {

        requestNotificationPermission();

        if (!canScheduleExactAlarms()) {

            requestExactAlarmPermission();

            Toast.makeText(
                    this,
                    "لطفاً اجازه آلارم دقیق را فعال کنید.",
                    Toast.LENGTH_LONG
            ).show();

            return;
        }

        notificationPrefs.edit()
                .putBoolean(
                        REMINDER_ENABLED,
                        true
                )
                .apply();

        scheduleNotificationAlarm();

        Toast.makeText(
                this,
                "یادآوری اقساط فعال شد.",
                Toast.LENGTH_SHORT
        ).show();
    }

    /*
     * =========================================================
     * امکانات Native
     * =========================================================
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
                + "n.textContent='🔔 تنظیم یادآوری اقساط';"

                + "n.onclick=function(){"
                + "if(window.__openReminderSettings){"
                + "window.__openReminderSettings();"
                + "}"
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

                /*
                 * =====================================================
                 * Backup Import
                 * =====================================================
                 */
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

                /*
                 * =====================================================
                 * Export
                 * =====================================================
                 */
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

                /*
                 * =====================================================
                 * Import
                 * =====================================================
                 */
                + "var im=document.getElementById('importButton');"

                + "if(im){"

                + "im.onclick=function(){"
                + "AndroidBridge.openBackupPicker();"
                + "};"

                + "}"

                /*
                 * =====================================================
                 * اتصال خودکار saveData به سیستم اعلان
                 * =====================================================
                 */
                + "if(typeof window.saveData==='function'&&!window.__originalSaveData){"

                + "window.__originalSaveData=window.saveData;"

                + "window.saveData=function(){"

                + "var result=window.__originalSaveData.apply(this,arguments);"

                + "try{"

                + "if(window.AndroidBridge){"

                + "AndroidBridge.syncInstallments();"

                + "}"

                + "}catch(e){}"

                + "return result;"

                + "};"

                + "}"

                + "})()";

        webView.evaluateJavascript(
                js,
                null
        );
    }

    /*
     * =========================================================
     * ساخت Backup
     * =========================================================
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
     * =========================================================
     * انتخاب Backup
     * =========================================================
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

    /*
     * =========================================================
     * ذخیره Backup
     * =========================================================
     */
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

    /*
     * =========================================================
     * خواندن Backup
     * =========================================================
     */
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
     * =========================================================
     * قفل برنامه
     * =========================================================
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
     * =========================================================
     * نتیجه Backup
     * =========================================================
     */
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
     * =========================================================
     * ارتباط Java و HTML
     * =========================================================
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
         * =====================================================
         * تغییر تنظیمات یادآوری
         * =====================================================
         */
        @JavascriptInterface
        public void reminderSettingsChanged(
                String settingsJson
        ) {

            runOnUiThread(
                    () -> {

                        try {

                            org.json.JSONObject settings =
                                    new org.json.JSONObject(
                                            settingsJson
                                    );

                            boolean enabled =
                                    settings.optBoolean(
                                            "enabled",
                                            true
                                    );

                            int daysBefore =
                                    settings.optInt(
                                            "daysBefore",
                                            2
                                    );

                            String time =
                                    settings.optString(
                                            "time",
                                            "16:00"
                                    );

                            if (
                                    daysBefore != 1
                                            &&
                                    daysBefore != 2
                            ) {

                                daysBefore = 2;
                            }

                            if (
                                    time == null
                                            ||
                                    time.trim().isEmpty()
                            ) {

                                time = "16:00";
                            }

                            notificationPrefs.edit()
                                    .putBoolean(
                                            REMINDER_ENABLED,
                                            enabled
                                    )
                                    .putInt(
                                            REMINDER_DAYS,
                                            daysBefore
                                    )
                                    .putString(
                                            REMINDER_TIME,
                                            time
                                    )
                                    .apply();

                            NotificationReceiver
                                    .cancelAllPostedNotifications(
                                            MainActivity.this
                                    );

                            clearNotificationHistory();

                            cancelNotificationAlarm();

                            if (enabled) {

                                scheduleNotificationAlarm();
                            }

                        } catch (Exception e) {

                            e.printStackTrace();
                        }
                    }
            );
        }

        /*
         * =====================================================
         * همگام‌سازی اقساط
         * =====================================================
         */
        @JavascriptInterface
        public void syncInstallments() {

            runOnUiThread(
                    MainActivity.this::
                            syncInstallmentsFromWebView
            );
        }

        /*
         * =====================================================
         * PREMIUM
         * =====================================================
         */

        /*
         * آیا Premium فعال است؟
         *
         * JavaScript:
         *
         * AndroidBridge.isPremium()
         */
        @JavascriptInterface
        public boolean isPremium() {

            return premiumManager != null
                    &&
                    premiumManager.isPremium();
        }

        /*
         * تعداد قسط مجاز
         *
         * Free = 1
         * Premium = نامحدود
         */
        @JavascriptInterface
        public int getInstallmentLimit() {

            if (premiumManager == null) {
                return PremiumManager.FREE_INSTALLMENT_LIMIT;
            }

            return premiumManager
                    .getInstallmentLimit();
        }

        /*
         * تعداد چک مجاز
         *
         * Free = 1
         * Premium = نامحدود
         */
        @JavascriptInterface
        public int getCheckLimit() {

            if (premiumManager == null) {
                return PremiumManager.FREE_CHECK_LIMIT;
            }

            return premiumManager
                    .getCheckLimit();
        }

        /*
         * بررسی امکان افزودن قسط
         *
         * currentInstallmentCount:
         * تعداد فعلی اقساط موجود
         */
        @JavascriptInterface
        public boolean canAddInstallment(
                int currentInstallmentCount
        ) {

            if (premiumManager == null) {
                return false;
            }

            return premiumManager
                    .canAddInstallment(
                            currentInstallmentCount
                    );
        }

        /*
         * بررسی امکان افزودن چک
         *
         * currentCheckCount:
         * تعداد فعلی چک‌ها
         */
        @JavascriptInterface
        public boolean canAddCheck(
                int currentCheckCount
        ) {

            if (premiumManager == null) {
                return false;
            }

            return premiumManager
                    .canAddCheck(
                            currentCheckCount
                    );
        }

        /*
         * آیا سقف اقساط رایگان پر شده؟
         */
        @JavascriptInterface
        public boolean installmentLimitReached(
                int currentInstallmentCount
        ) {

            if (premiumManager == null) {
                return true;
            }

            return premiumManager
                    .installmentLimitReached(
                            currentInstallmentCount
                    );
        }

        /*
         * آیا سقف چک رایگان پر شده؟
         */
        @JavascriptInterface
        public boolean checkLimitReached(
                int currentCheckCount
        ) {

            if (premiumManager == null) {
                return true;
            }

            return premiumManager
                    .checkLimitReached(
                            currentCheckCount
                    );
        }

        /*
         * فعال کردن Premium
         *
         * فعلاً برای تست داخلی.
         */
        @JavascriptInterface
        public void activatePremium() {

            runOnUiThread(
                    () -> {

                        if (premiumManager != null) {

                            premiumManager
                                    .activatePremium();
                        }

                        Toast.makeText(
                                MainActivity.this,
                                "Premium فعال شد.",
                                Toast.LENGTH_SHORT
                        ).show();
                    }
            );
        }

        /*
         * غیرفعال کردن Premium
         *
         * فعلاً برای تست داخلی.
         */
        @JavascriptInterface
        public void deactivatePremium() {

            runOnUiThread(
                    () -> {

                        if (premiumManager != null) {

                            premiumManager
                                    .deactivatePremium();
                        }

                        Toast.makeText(
                                MainActivity.this,
                                "Premium غیرفعال شد.",
                                Toast.LENGTH_SHORT
                        ).show();
                    }
            );
        }

        /*
         * =====================================================
         * پیام به توسعه‌دهنده
         * =====================================================
         */
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
