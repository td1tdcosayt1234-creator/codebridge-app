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
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.ads.AdError;
import com.google.android.gms.ads.AdRequest;
import com.google.android.gms.ads.FullScreenContentCallback;
import com.google.android.gms.ads.LoadAdError;
import com.google.android.gms.ads.MobileAds;
import com.google.android.gms.ads.rewarded.RewardedAd;
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback;

/**
 * CodeBridge Android app.
 * Server-based client of dash.roun.sryze.cc: Tasks, History, Profile, Earn.
 *
 * Earn is ad-based: native AdMob rewarded ads. On /earn, the page's sponsor
 * window.open() is intercepted and replaced by the rewarded flow; the page's
 * own timer + POST /api/earn (start -> complete) stays untouched.
 */
public class MainActivity extends Activity {

    private static final String DASH = "https://dash.roun.sryze.cc";
    private static final String UA_MARK = "CodeBridgeApp/1.0";
    private static final String[] LABELS = {"Tasks", "History", "Profile", "Earn"};
    private static final String[] ICONS = {"📋", "🕘", "👤", "⭐"};
    private static final String[] PATHS = {"/tasks", "/builds", "/settings", "/earn"};

    // TODO: flip USE_TEST to false after the AdMob app review approves CodeBridge,
    // then bump versionCode/versionName in AndroidManifest.xml.
    private static final boolean USE_TEST = true;
    private static final String TEST_REWARDED = "ca-app-pub-3940256099942544/5224354917";
    private static final String PROD_REWARDED = "ca-app-pub-3123261757380037/5119738300";

    private static final int BG = 0xFF0B1020;
    private static final int NAV_BG = 0xFF101A33;
    private static final int ACCENT = 0xFF22D3EE;
    private static final int IDLE = 0xFF8A94A6;

    private WebView web;
    private ProgressBar bar;
    private TextView[] tabs;
    private int selected = 0;

    private RewardedAd rewarded;
    private volatile boolean adLoading = false;

    /** JS bridge used by the injected window.open hook on /earn. */
    private class Bridge {
        @JavascriptInterface
        public void showRewarded() {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    showAd();
                }
            });
        }
    }

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

        // Google Mobile Ads SDK; cache the first rewarded ad once ready.
        MobileAds.initialize(this, status -> maybeLoadAd());

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
        nav.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(60)));

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
        try {
            bar.setVisibility(View.VISIBLE);
        } catch (Exception ignored) {
        }
        web.loadUrl(DASH + PATHS[i]);
        if (i == PATHS.length - 1) {
            maybeLoadAd(); // precache rewarded before the Earn tab opens
        }
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
        web.addJavascriptInterface(new Bridge(), "CodeBridge");

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
                if (url != null && url.contains("/earn")) {
                    injectSponsorHook();
                }
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

    /** Swap the earn page's sponsor window.open by the native rewarded ad. */
    private void injectSponsorHook() {
        String js = "(function(){if(window.__cbHook)return;window.__cbHook=1;"
                + "var om=window.open;window.open=function(u){try{var s=String(u);"
                + "if(s.indexOf('uplcm')>-1){if(window.CodeBridge)window.CodeBridge.showRewarded();return null;}}"
                + "catch(e){}return om.apply(window,arguments);};})();";
        web.evaluateJavascript(js, null);
        maybeLoadAd();
    }

    private void showAd() {
        if (rewarded != null) {
            final RewardedAd ad = rewarded;
            rewarded = null; // one live ShowingSurface at a time
            ad.setFullScreenContentCallback(new FullScreenContentCallback() {
                @Override
                public void onAdDismissedFullScreenContent() {
                    maybeLoadAd();
                }

                @Override
                public void onAdFailedToShowFullScreenContent(AdError error) {
                    maybeLoadAd();
                }
            });
            ad.show(this, rewardItem -> {
                int coins = rewardItem.getAmount();
                web.post(() -> web.evaluateJavascript(
                        "if(window.__cbReward)window.__cbReward(" + coins + ");", null));
                maybeLoadAd();
            });
        } else {
            maybeLoadAd();
            Toast.makeText(this, "Ad load hote ektu time lagbe — abar tap korun", Toast.LENGTH_SHORT).show();
        }
    }

    private void maybeLoadAd() {
        if (rewarded != null || adLoading) return;
        adLoading = true;
        String unit = USE_TEST ? TEST_REWARDED : PROD_REWARDED;
        RewardedAd.load(this, unit, new AdRequest.Builder().build(), new RewardedAdLoadCallback() {
            @Override
            public void onAdLoaded(RewardedAd ad) {
                rewarded = ad;
                adLoading = false;
            }

            @Override
            public void onAdFailedToLoad(LoadAdError error) {
                adLoading = false;
            }
        });
    }

    /** Site hosts stay in-app; sponsor (Monetag) swallowed; others open browser. */
    private boolean external(String url) {
        Uri u = Uri.parse(url);
        String scheme = u.getScheme() == null ? "" : u.getScheme();
        if (scheme.equals("http") || scheme.equals("https")) {
            String host = u.getHost() == null ? "" : u.getHost();
            if (host.contains("uplcm.com")) return true; // replaced by rewarded ad
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
