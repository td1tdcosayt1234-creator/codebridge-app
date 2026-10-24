package com.codebridge.app;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.CookieManager;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

/**
 * CodeBridge Android app.
 * Server-based client of dash.roun.sryze.cc: Tasks, History, Profile, Earn.
 * The WebView appends a "CodeBridgeApp/1.0" marker to its User-Agent — the
 * server uses it to allow ad earning only from inside this app.
 */
public class MainActivity extends Activity {

    private static final String DASH = "https://dash.roun.sryze.cc";
    private static final String UA_MARK = "CodeBridgeApp/1.0";
    private static final String[] LABELS = {"Tasks", "History", "Profile", "Earn"};
    private static final String[] ICONS = {"📋", "🕘", "👤", "⭐"};
    private static final String[] PATHS = {"/tasks", "/builds", "/settings", "/earn"};

    private static final int BG = 0xFF0B1020;
    private static final int NAV_BG = 0xFF101A33;
    private static final int ACCENT = 0xFF22D3EE;
    private static final int IDLE = 0xFF8A94A6;

    private WebView web;
    private ProgressBar bar;
    private TextView[] tabs;
    private int selected = 0;

    @Override
    protected void onCreate(Bundle saved) {
        super.onCreate(saved);
        getWindow().setStatusBarColor(BG);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(BG);

        FrameLayout content = new FrameLayout(this);

        web = new WebView(this);
        content.addView(web, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        bar = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        FrameLayout.LayoutParams barParams = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(3));
        barParams.gravity = Gravity.TOP;
        bar.setVisibility(View.GONE);
        content.addView(bar, barParams);

        root.addView(content, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        root.addView(nav());

        setContentView(root);
        setupWeb();

        if (saved != null) {
            web.restoreState(saved);
        } else {
            web.loadUrl(DASH + PATHS[selected]);
            highlight();
        }
    }

    private LinearLayout nav() {
        LinearLayout nav = new LinearLayout(this);
        nav.setOrientation(LinearLayout.HORIZONTAL);
        nav.setBackgroundColor(NAV_BG);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(60));
        nav.setLayoutParams(lp);

        tabs = new TextView[PATHS.length];
        for (int i = 0; i < PATHS.length; i++) {
            final int index = i;
            LinearLayout col = new LinearLayout(this);
            col.setOrientation(LinearLayout.VERTICAL);
            col.setGravity(Gravity.CENTER);
            col.setPadding(0, dp(6), 0, dp(6));
            col.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    open(index);
                }
            });

            TextView ic = new TextView(this);
            ic.setText(ICONS[i]);
            ic.setTextSize(19);
            ic.setGravity(Gravity.CENTER);
            col.addView(ic, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

            TextView label = new TextView(this);
            label.setText(LABELS[i]);
            label.setTextSize(11);
            label.setTypeface(Typeface.DEFAULT_BOLD);
            label.setGravity(Gravity.CENTER);
            label.setTextColor(IDLE);
            col.addView(label, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

            nav.addView(col, new LinearLayout.LayoutParams(
                    0, ViewGroup.LayoutParams.MATCH_PARENT, 1f));
            tabs[i] = label;
        }
        return nav;
    }

    private void open(int i) {
        selected = i;
        highlight();
        bar.setVisibility(View.VISIBLE);
        web.loadUrl(DASH + PATHS[i]);
    }

    private void highlight() {
        for (int i = 0; i < tabs.length; i++) {
            tabs[i].setTextColor(i == selected ? ACCENT : IDLE);
        }
    }

    private void setupWeb() {
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setLoadWithOverviewMode(true);
        s.setUseWideViewPort(true);
        s.setSupportZoom(false);
        s.setBuiltInZoomControls(false);
        s.setDisplayZoomControls(false);
        s.setMediaPlaybackRequiresUserGesture(true);

        // Server-side earn gate reads this UA marker.
        String ua = s.getUserAgentString();
        s.setUserAgentString(ua == null ? UA_MARK : ua + " " + UA_MARK);

        CookieManager cm = CookieManager.getInstance();
        cm.setAcceptCookie(true);
        cm.setAcceptThirdPartyCookies(web, true);

        web.setBackgroundColor(BG);
        web.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest req) {
                return external(req.getUrl().toString());
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                return external(url);
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                CookieManager.getInstance().flush();
            }
        });
        web.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onProgressChanged(WebView view, int progress) {
                bar.setProgress(progress);
                bar.setVisibility(progress >= 100 ? View.GONE : View.VISIBLE);
            }
        });
    }

    /** Site hosts stay in-app; everything external opens the browser. */
    private boolean external(String url) {
        Uri u = Uri.parse(url);
        String scheme = u.getScheme() == null ? "" : u.getScheme();
        if (scheme.equals("http") || scheme.equals("https")) {
            String host = u.getHost() == null ? "" : u.getHost();
            if (host.equals("roun.sryze.cc") || host.endsWith(".roun.sryze.cc")) {
                return false; // keep in-app
            }
            try {
                startActivity(new Intent(Intent.ACTION_VIEW, u));
            } catch (Exception ignored) {
            }
            return true;
        }
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, u));
        } catch (Exception ignored) {
        }
        return true;
    }

    @Override
    public void onBackPressed() {
        if (web.canGoBack()) {
            web.goBack();
        } else {
            super.onBackPressed();
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle out) {
        super.onSaveInstanceState(out);
        web.saveState(out);
    }

    @Override
    protected void onPause() {
        super.onPause();
        CookieManager.getInstance().flush();
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }
}
