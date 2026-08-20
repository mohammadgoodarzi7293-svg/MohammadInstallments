package com.mohammadgoudarzi.installments;

import android.os.Bundle;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import androidx.fragment.app.FragmentActivity;

public class ChecksActivity extends FragmentActivity {

    private WebView webView;
    private PremiumManager premiumManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        premiumManager = new PremiumManager(this);

        webView = new WebView(this);

        WebSettings settings = webView.getSettings();

        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setAllowFileAccess(true);
        settings.setAllowContentAccess(true);

        settings.setBuiltInZoomControls(false);
        settings.setDisplayZoomControls(false);

        webView.setWebViewClient(new WebViewClient());

        webView.addJavascriptInterface(
                new ChecksBridge(),
                "AndroidBridge"
        );

        webView.loadUrl(
                "file:///android_asset/checks.html"
        );

        setContentView(webView);
    }

    private class ChecksBridge {

        @android.webkit.JavascriptInterface
        public boolean isPremium() {
            return premiumManager.isPremium();
        }

        @android.webkit.JavascriptInterface
        public boolean canAddCheck(int currentCount) {
            return premiumManager.canAddCheck(currentCount);
        }
    }

    @Override
    public void onBackPressed() {

        if (webView != null && webView.canGoBack()) {
            webView.goBack();
        } else {
            super.onBackPressed();
        }
    }
}
