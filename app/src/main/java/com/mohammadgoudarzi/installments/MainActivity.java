package com.mohammadgoudarzi.installments;

import android.Manifest;
import android.app.Activity;
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
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Executor;

public class MainActivity extends Activity {

    private static final int REQUEST_CREATE_BACKUP = 4101;
    private static final int REQUEST_OPEN_BACKUP = 4102;
    private static final int REQUEST_NOTIFICATION_PERMISSION = 5001;

    private static final String PREFS_NAME =
            "mohammad_installments_settings";

    private static final String PREF_APP_LOCK =
            "app_lock_enabled";

    private static final String PREF_BIOMETRIC =
            "biometric_enabled";

    private WebView webView;

    private String pendingBackupText = null;

    private SharedPreferences settingsPrefs;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        settingsPrefs =
                getSharedPreferences(
                        PREFS_NAME,
                        MODE_PRIVATE
                );

        requestNotificationPermission();

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

    @Override
    protected void onResume() {
        super.onResume();

        if (settingsPrefs != null &&
                settingsPrefs.getBoolean(
                        PREF_APP_LOCK,
                        false
                )) {

            if (webView != null) {
                webView.postDelayed(
                        () -> authenticateUser(),
                        300
                );
            }
        }
    }

    /*
     * درخواست مجوز اعلان
     */
    private void requestNotificationPermission() {

        if (
                Build.VERSION.SDK_INT >=
                Build.VERSION_CODES.TIRAMISU
        ) {

            if (
                    ContextCompat.checkSelfPermission(
                            this,
                            Manifest.permission.POST_NOTIFICATIONS
                    )
                    != PackageManager.PERMISSION_GRANTED
            ) {

                ActivityCompat.requestPermissions(
                        this,
                        new String[]{
                                Manifest.permission.POST_NOTIFICATIONS
                        },
                        REQUEST_NOTIFICATION_PERMISSION
                );
            }
        }
    }

    /*
     * احراز هویت با اثر انگشت / قفل گوشی
     */
    private void authenticateUser() {

        BiometricManager biometricManager =
                BiometricManager.from(this);

        int result =
                biometricManager.canAuthenticate(
                        BiometricManager.Authenticators.BIOMETRIC_STRONG |
                        BiometricManager.Authenticators.DEVICE_CREDENTIAL
                );

        if (
                result !=
                BiometricManager.BIOMETRIC_SUCCESS
        ) {

            return;
        }

        Executor executor =
                ContextCompat.getMainExecutor(this);

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

                                if (
                                        errorCode !=
                                        BiometricPrompt.ERROR_USER_CANCELED
                                ) {

                                    finish();
                                }
                            }

                            @Override
                            public void onAuthenticationFailed() {

                                super.onAuthenticationFailed();
                            }
                        }
                );

        BiometricPrompt.PromptInfo promptInfo =
                new BiometricPrompt.PromptInfo.Builder()
                        .setTitle(
                                "قفل مدیریت اقساط"
                        )
                        .setSubtitle(
                                "برای ورود به برنامه احراز هویت کنید"
                        )
                        .setAllowedAuthenticators(
                                BiometricManager.Authenticators.BIOMETRIC_STRONG |
                                BiometricManager.Authenticators.DEVICE_CREDENTIAL
                        )
                        .build();

        biometricPrompt.authenticate(
                promptInfo
        );
    }

    /*
     * فعال کردن امکانات Native
     */
    private void injectNativeFeatures() {

        String js =
                "javascript:(function(){"

                + "if(window.__nativeFeaturesInstalled)return;"

                + "window.__nativeFeaturesInstalled=true;"

                /*
                 * استایل منو
                 */

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
                + "border:0;"
                + "}"

                + ".devMenuItem:active{"
                + "background:#f1f5f9;"
                + "}"

                + "';"

                + "document.head.appendChild(style);"

                /*
                 * پیدا کردن Header
                 */

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

                /*
                 * تماس با توسعه دهنده
                 */

                + "var e=document.createElement('button');"
                + "e.type='button';"
                + "e.className='devMenuItem';"
                + "e.textContent='✉️ پیام به توسعه‌دهنده';"

                + "e.onclick=function(){"
                + "AndroidBridge.contactDeveloper();"
                + "m.classList.remove('open');"
                + "};"

                /*
                 * تنظیمات قفل
                 */

                + "var lock=document.createElement('button');"
                + "lock.type='button';"
                + "lock.className='devMenuItem';"
                + "lock.textContent='🔐 قفل برنامه';"

                + "lock.onclick=function(){"
                + "AndroidBridge.configureLock();"
                + "m.classList.remove('open');"
                + "};"

                /*
                 * وضعیت اعلان
                 */

                + "var notification=document.createElement('button');"
                + "notification.type='button';"
                + "notification.className='devMenuItem';"
                + "notification.textContent='🔔 فعال‌سازی یادآوری اقساط';"

                + "notification.onclick=function(){"
                + "AndroidBridge.requestNotifications();"
                + "m.classList.remove('open');"
                + "};"

                + "m.appendChild(e);"
                + "m.appendChild(lock);"
                + "m.appendChild(notification);"

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
                 * بازیابی
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

                + "AndroidBridge.updateNotificationData("
                + "JSON.stringify(installments)"
                + ");"

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

                + "AndroidBridge.updateNotificationData("
                + "JSON.stringify(installments)"
                + ");"

                + "}"

                + "}catch(err){"

                + "alert('این فایل یک پشتیبان معتبر مدیریت اقساط نیست.');"

                + "}"

                + "};"

                /*
                 * پشتیبان گیری
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
                 * بازیابی
                 */

                + "var im=document.getElementById('importButton');"

                + "if(im){"

                + "im.onclick=function(){"
                + "AndroidBridge.openBackupPicker();"
                + "};"

                + "}"

                /*
                 * انتقال اطلاعات اقساط به Android
                 */

                + "if(typeof saveData==='function'){"

                + "var originalSaveData=saveData;"

                + "window.saveData=function(){"

                + "var result=originalSaveData.apply(this,arguments);"

                + "try{"

                + "var data=localStorage.getItem('mohammad_installments_v10');"

                + "if(data){"

                + "AndroidBridge.updateNotificationData(data);"

                + "}"

                + "}catch(err){}"

                + "return result;"

                + "};"

                + "}"

                /*
                 * ارسال اطلاعات فعلی هنگام شروع
                 */

                + "try{"

                + "var initialData="
                + "localStorage.getItem('mohammad_installments_v10');"

                + "if(initialData){"

                + "AndroidBridge.updateNotificationData(initialData);"

                + "}"

                + "}catch(err){}"

                + "})()";

        webView.evaluateJavascript(
                js,
                null
        );
    }

    /*
     * ساخت فایل پشتیبان
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

    /*
     * ذخیره پشتیبان
     */

    private void writeBackup(Uri uri) {

        if (
                pendingBackupText == null
        )
            return;

        try (
                OutputStream output =
                        getContentResolver()
                                .openOutputStream(uri)
        ) {

            if (output == null)
                throw new IOException(
                        "Output stream is null"
                );

            output.write(
                    pendingBackupText
                            .getBytes(
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
     * خواندن پشتیبان
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
                    JSONObject.quote(
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
     * دریافت نتیجه انتخاب فایل
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
     * رابط JavaScript
     */

    public class AndroidBridge {

        /*
         * پشتیبان گیری
         */

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

        /*
         * بازیابی
         */

        @JavascriptInterface
        public void openBackupPicker() {

            runOnUiThread(
                    MainActivity.this::
                            openBackupPicker
            );
        }

        /*
         * تماس با توسعه دهنده
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

        /*
         * تنظیم قفل برنامه
         */

        @JavascriptInterface
        public void configureLock() {

            runOnUiThread(
                    () -> {

                        boolean enabled =
                                settingsPrefs.getBoolean(
                                        PREF_APP_LOCK,
                                        false
                                );

                        settingsPrefs.edit()
                                .putBoolean(
                                        PREF_APP_LOCK,
                                        !enabled
                                )
                                .apply();

                        if (!enabled) {

                            settingsPrefs.edit()
                                    .putBoolean(
                                            PREF_BIOMETRIC,
                                            true
                                    )
                                    .apply();

                            Toast.makeText(
                                    MainActivity.this,
                                    "قفل برنامه فعال شد.",
                                    Toast.LENGTH_LONG
                            ).show();

                            authenticateUser();

                        } else {

                            Toast.makeText(
                                    MainActivity.this,
                                    "قفل برنامه غیرفعال شد.",
                                    Toast.LENGTH_LONG
                            ).show();
                        }

                    }
            );
        }

        /*
         * درخواست اعلان
         */

        @JavascriptInterface
        public void requestNotifications() {

            runOnUiThread(
                    () -> {

                        requestNotificationPermission();

                        Toast.makeText(
                                MainActivity.this,
                                "یادآوری اقساط فعال شد.",
                                Toast.LENGTH_LONG
                        ).show();

                    }
            );
        }

        /*
         * دریافت اطلاعات اقساط
         * و ذخیره برای NotificationReceiver
         */

        @JavascriptInterface
        public void updateNotificationData(
                String data
        ) {

            try {

                SharedPreferences prefs =
                        getSharedPreferences(
                                "installment_notifications",
                                MODE_PRIVATE
                        );

                prefs.edit()
                        .putString(
                                "installments",
                                data
                        )
                        .apply();

                scheduleNotificationCheck();

            } catch (Exception e) {

                e.printStackTrace();

            }
        }
    }

    /*
     * زمان‌بندی بررسی اقساط
     */

    private void scheduleNotificationCheck() {

        try {

            AlarmManager alarmManager =
                    (AlarmManager)
                            getSystemService(
                                    Context.ALARM_SERVICE
                            );

            if (alarmManager == null)
                return;

            Intent intent =
                    new Intent(
                            this,
                            NotificationReceiver.class
                    );

            PendingIntent pendingIntent =
                    PendingIntent.getBroadcast(
                            this,
                            4810,
                            intent,
                            PendingIntent.FLAG_UPDATE_CURRENT |
                            pendingIntentFlags()
                    );

            long triggerTime =
                    System.currentTimeMillis()
                            + (60L * 60L * 1000L);

            if (
                    Build.VERSION.SDK_INT >=
                    Build.VERSION_CODES.M
            ) {

                alarmManager.setAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        triggerTime,
                        pendingIntent
                );

            } else {

                alarmManager.set(
                        AlarmManager.RTC_WAKEUP,
                        triggerTime,
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
    }
