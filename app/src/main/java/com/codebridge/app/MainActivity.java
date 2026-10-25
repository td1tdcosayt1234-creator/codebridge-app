package com.codebridge.app;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
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
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.google.android.gms.ads.AdError;
import com.google.android.gms.ads.AdRequest;
import com.google.android.gms.ads.FullScreenContentCallback;
import com.google.android.gms.ads.LoadAdError;
import com.google.android.gms.ads.MobileAds;
import com.google.android.gms.ads.rewarded.RewardedAd;
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback;

/**
 * CodeBridge Android app — server-based client of dash.roun.sryze.cc.
 * Tasks / History / Profile / Earn with native AdMob rewarded earn flow.
 */
public class MainActivity extends Activity {

    private static final String DASH = "https://dash.roun.sryze.cc";
    private static final String UA_MARK = "CodeBridgeApp/1.0";
    private static final String[] LABELS = {"Tasks", "History", "Profile", "Earn"};
    private static final int[] ICONS = {R.drawable.ic_tasks, R.drawable.ic_history,
            R.drawable.ic_profile, R.drawable.ic_earn};
    private static final String[] PATHS = {"/tasks", "/builds", "/settings", "/earn"};

    // TODO: flip USE_TEST to false after the AdMob app review approves CodeBridge,
    // then bump versionCode/versionName in app/build.gradle.kts.
    private static final boolean USE_TEST = true;
    private static final String TEST_REWARDED = "ca-app-pub-3940256099942544/5224354917";
    private static final String PROD_REWARDED = "ca-app-pub-3123261757380037/5119738300";

    private static final int BG = 0xFF0B1020;
    private static final int NAV_BG = 0xFF101A33;
    private static final int HAIRLINE = 0xFF18243E;
    private static final int PILL = 0xFF1B2A4D;
    private static final int ACCENT = 0xFF22D3EE;
    private static final int IDLE = 0xFF8A94A6;

    private WebView web;
    private ProgressBar bar;
    private SwipeRefreshLayout swipe;
    private LinearLayout[] navCols;
    private ImageView[] navIcons;
    private TextView[] navLabels;
    private LinearLayout splash;
    private int rippleRes;
    private boolean splashGone = false;
    private int selected = 0;

    private RewardedAd rewarded;
    private volatile boolean adLoading = false;
    /** Set when the user requested an ad while none was cached yet. */
    private volatile boolean pendingShow = false;

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

        root.addView(header());
        root.addView(hairline());

        swipe = new SwipeRefreshLayout(this);
        swipe.setColorSchemeColors(ACCENT);
        swipe.setOnRefreshListener(new SwipeRefreshLayout.OnRefreshListener() {
            @Override
            public void onRefresh() {
                reload();
            }
        });
        root.addView(swipe, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        FrameLayout frame = new FrameLayout(this);
        frame.setBackgroundColor(BG);

        web = new WebView(this);
        frame.addView(web, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        bar = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        bar.getProgressDrawable().setColorFilter(ACCENT, android.graphics.PorterDuff.Mode.SRC_ATOP);
        FrameLayout.LayoutParams barParams = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(3));
        barParams.gravity = Gravity.TOP;
        bar.setVisibility(View.GONE);
        frame.addView(bar, barParams);

        if (saved == null) {
            splash = buildSplash();
            frame.addView(splash, new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        }

        swipe.addView(frame, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        root.addView(nav());

        setContentView(root);
        setupWeb();

        // Google Mobile Ads SDK; cache the first rewarded ad once ready.
        MobileAds.initialize(this, status -> maybeLoadAd());

        if (saved != null) {
            web.restoreState(saved);
            splashGone = true;
        } else {
            web.loadUrl(DASH + PATHS[selected]);
        }
        highlight();
    }

    private LinearLayout header() {
        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setBackgroundColor(NAV_BG);
        header.setPadding(dp(16), dp(11), dp(8), dp(11));

        TextView star = new TextView(this);
        star.setText("✦");
        star.setTextColor(ACCENT);
        star.setTextSize(15);
        header.addView(star);

        TextView title = new TextView(this);
        title.setText("  CodeBridge");
        title.setTextColor(0xFFFFFFFF);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setTextSize(16);
        header.addView(title);

        TextView url = new TextView(this);
        url.setText("  dash.roun.sryze.cc");
        url.setTextColor(IDLE);
        url.setTextSize(10);
        url.setLetterSpacing(0.02f);
        header.addView(url);

        TextView refresh = new TextView(this);
        refresh.setText("⟳");
        refresh.setTextColor(ACCENT);
        refresh.setTextSize(20);
        refresh.setPadding(dp(12), dp(6), dp(12), dp(6));
        android.util.TypedValue rv = new android.util.TypedValue();
        getTheme().resolveAttribute(android.R.attr.selectableItemBackground, rv, true);
        refresh.setBackgroundResource(rv.resourceId);
        refresh.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                reload();
            }
        });
        header.addView(refresh, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return header;
    }

    private View hairline() {
        View line = new View(this);
        line.setBackgroundColor(HAIRLINE);
        line.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Math.max(1, dp(1))));
        return line;
    }

    private LinearLayout buildSplash() {
        LinearLayout s = new LinearLayout(this);
        s.setOrientation(LinearLayout.VERTICAL);
        s.setGravity(Gravity.CENTER);
        s.setBackgroundColor(BG);

        TextView logo = new TextView(this);
        logo.setText("✦ CodeBridge");
        logo.setTextColor(ACCENT);
        logo.setTextSize(26);
        logo.setTypeface(Typeface.DEFAULT_BOLD);
        logo.setLetterSpacing(0.03f);
        s.addView(logo);

        TextView sub = new TextView(this);
        sub.setText("dash.roun.sryze.cc");
        sub.setTextColor(IDLE);
        sub.setTextSize(11);
        sub.setLetterSpacing(0.08f);
        s.addView(sub, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        ProgressBar spin = new ProgressBar(this);
        spin.getIndeterminateDrawable().setColorFilter(ACCENT, android.graphics.PorterDuff.Mode.SRC_ATOP);
        LinearLayout.LayoutParams spinLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        spinLp.topMargin = dp(22);
        s.addView(spin, spinLp);
        return s;
    }

    private LinearLayout nav() {
        LinearLayout nav = new LinearLayout(this);
        nav.setOrientation(LinearLayout.HORIZONTAL);
        nav.setBackgroundColor(NAV_BG);
        nav.setPadding(dp(4), dp(7), dp(4), dp(9));

        navCols = new LinearLayout[PATHS.length];
        navIcons = new ImageView[PATHS.length];
        navLabels = new TextView[PATHS.length];

        for (int i = 0; i < PATHS.length; i++) {
            final int index = i;
            LinearLayout col = new LinearLayout(this);
            col.setOrientation(LinearLayout.VERTICAL);
            col.setGravity(Gravity.CENTER);
            col.setPadding(0, dp(7), 0, dp(7));

            ImageView ic = new ImageView(this);
            ic.setImageResource(ICONS[i]);
            ic.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            col.addView(ic, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));

            TextView label = new TextView(this);
            label.setText(LABELS[i].toUpperCase());
            label.setTextSize(10);
            label.setLetterSpacing(0.06f);
            label.setGravity(Gravity.CENTER);
            LinearLayout.LayoutParams labelLp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            labelLp.topMargin = dp(4);
            col.addView(label, labelLp);

            // touch feedback
            android.util.TypedValue tv = new android.util.TypedValue();
            getTheme().resolveAttribute(android.R.attr.selectableItemBackground, tv, true);
            rippleRes = tv.resourceId;
            col.setBackgroundResource(rippleRes);

            col.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    open(index);
                }
            });

            nav.addView(col, new LinearLayout.LayoutParams(
                    0, ViewGroup.LayoutParams.MATCH_PARENT, 1f));
            navCols[i] = col;
            navIcons[i] = ic;
            navLabels[i] = label;
        }
        return nav;
    }

    private void reload() {
        try {
            bar.setVisibility(View.VISIBLE);
        } catch (Exception ignored) {
        }
        web.reload();
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

    /** Keep the bottom nav in sync with whatever page the WebView shows. */
    private void syncFromUrl(String url) {
        if (url == null) return;
        String clean = url.replaceFirst("https?://dash\\.roun\\.sryze\\.cc", "").split("\\?")[0];
        for (int i = 0; i < PATHS.length; i++) {
            if (clean.startsWith(PATHS[i])) {
                if (selected != i) {
                    selected = i;
                    highlight();
                }
                return;
            }
        }
    }

    private void highlight() {
        for (int i = 0; i < navCols.length; i++) {
            boolean sel = i == selected;
            navIcons[i].setColorFilter(sel ? ACCENT : IDLE, android.graphics.PorterDuff.Mode.SRC_ATOP);
            navLabels[i].setTextColor(sel ? ACCENT : IDLE);
            navLabels[i].setTypeface(sel ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT);
            navCols[i].setBackgroundResource(sel ? R.drawable.nav_pill : rippleRes);
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
                if (swipe != null) swipe.setRefreshing(false);
                hideSplash();
                syncFromUrl(url);
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

    private void hideSplash() {
        if (splashGone || splash == null) return;
        splashGone = true;
        splash.setVisibility(View.GONE);
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
            // Ad not cached yet — remember the intent; it auto-opens the
            // moment the load finishes, so the user never taps twice.
            pendingShow = true;
            maybeLoadAd();
            Toast.makeText(this, "Ad load hochhe — ektu pore auto khule jabe", Toast.LENGTH_SHORT).show();
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
                if (pendingShow) {
                    pendingShow = false;
                    runOnUiThread(() -> showAd());
                }
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
