package com.webforge.shell;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.DownloadManager;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.util.Log;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.view.WindowManager;
import android.webkit.CookieManager;
import android.webkit.DownloadListener;
import android.webkit.GeolocationPermissions;
import android.webkit.JavascriptInterface;
import android.webkit.JsPromptResult;
import android.webkit.JsResult;
import android.webkit.PermissionRequest;
import android.webkit.URLUtil;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONObject;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public class MainActivity extends Activity {

    private static final String TAG = "WebForgeShell";
    private static final String VIRTUAL_HOST = "webforge.app";
    private static final String VIRTUAL_HOME_URL = "https://" + VIRTUAL_HOST + "/index.html";
    private static final int REQ_FILE_CHOOSER = 4101;
    private static final int REQ_PERMISSIONS = 4102;

    private FrameLayout rootLayout;
    private WebView webView;
    private ProgressBar loadingBar;
    private FrameLayout splashOverlay;
    private FrameLayout customVideoContainer;
    private View customVideoView;
    private WebChromeClient.CustomViewCallback customViewCallback;
    private LinearLayout floatingBar;

    private ValueCallback<Uri[]> filePathCallback;
    private final Handler handler = new Handler(Looper.getMainLooper());

    private String sourceMode = "html";
    private String startUrl = "";
    private String appName = "WebForge App";
    private String themeColor = "#5B3DF5";
    private String statusBarColor = "#0C0A20";
    private String navBarColor = "#0C0A20";
    private String orientationMode = "auto";
    private boolean isFullscreen = false;
    private boolean pullToRefresh = true;
    private boolean keepScreenOn = false;
    private boolean enableZoom = false;
    private boolean desktopMode = false;
    private boolean showProgressBar = true;
    private boolean showFloatingBar = false;
    private boolean openExternalLinks = false;
    private String backBehavior = "history";
    private String customUserAgent = "";
    private boolean splashEnabled = true;
    private long splashDurationMs = 1500L;
    private String splashTitle = "";
    private String splashSubtitle = "";
    private String splashBgColor = "#0C0A20";
    private String splashAccentColor = "#00C6FF";
    private boolean showSplashSpinner = true;

    private long lastBackPressMs = 0L;
    private long createTimeMs = 0L;
    private boolean splashDismissed = false;
    private float pullStartY = -1f;
    private String homeTargetUrl = VIRTUAL_HOME_URL;

    @SuppressLint({"SetJavaScriptEnabled", "ClickableViewAccessibility"})
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        try {
            requestWindowFeature(Window.FEATURE_NO_TITLE);
        } catch (Throwable ignored) {
        }
        super.onCreate(savedInstanceState);
        createTimeMs = System.currentTimeMillis();

        try {
            loadConfig();
            applyOrientation(orientationMode);

            int splashBgInt = safeParseColor(splashBgColor, 0xFF0C0A20);
            int accentInt = safeParseColor(splashAccentColor, 0xFF00C6FF);

            rootLayout = new FrameLayout(this);
            rootLayout.setBackgroundColor(splashBgInt);

            webView = new WebView(this);
            webView.setBackgroundColor(splashBgInt);
            webView.setLayoutParams(new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
            ));
            configureWebSettings();

            if (pullToRefresh) {
                webView.setOnTouchListener(new View.OnTouchListener() {
                    @Override
                    public boolean onTouch(View v, MotionEvent event) {
                        if (event.getAction() == MotionEvent.ACTION_DOWN && webView.getScrollY() == 0) {
                            pullStartY = event.getRawY();
                        } else if (event.getAction() == MotionEvent.ACTION_UP && pullStartY > 0f) {
                            float delta = event.getRawY() - pullStartY;
                            pullStartY = -1f;
                            if (delta > 280f && webView.getScrollY() == 0) {
                                webView.reload();
                                Toast.makeText(MainActivity.this, "↻ Refreshing...", Toast.LENGTH_SHORT).show();
                            }
                        } else if (event.getAction() == MotionEvent.ACTION_CANCEL) {
                            pullStartY = -1f;
                        }
                        return false;
                    }
                });
            }

            rootLayout.addView(webView);

            loadingBar = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
            loadingBar.setMax(100);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                loadingBar.setProgressTintList(ColorStateList.valueOf(accentInt));
            }
            loadingBar.setVisibility(showProgressBar ? View.VISIBLE : View.GONE);
            rootLayout.addView(loadingBar, new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, dpToPx(3), Gravity.TOP
            ));

            customVideoContainer = new FrameLayout(this);
            customVideoContainer.setBackgroundColor(Color.BLACK);
            customVideoContainer.setVisibility(View.GONE);
            rootLayout.addView(customVideoContainer, new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT
            ));

            if (showFloatingBar) {
                floatingBar = buildFloatingBar(accentInt);
                FrameLayout.LayoutParams barLp = new FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                        Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL
                );
                barLp.bottomMargin = dpToPx(18);
                rootLayout.addView(floatingBar, barLp);
            }

            if (splashEnabled) {
                splashOverlay = buildSplashOverlay(splashBgInt, accentInt);
                rootLayout.addView(splashOverlay, new FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT
                ));
                handler.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        dismissSplash();
                    }
                }, Math.max(500L, splashDurationMs));
            } else {
                splashDismissed = true;
            }

            setContentView(rootLayout);
            applyWindowStyling();
            rootLayout.post(new Runnable() {
                @Override
                public void run() {
                    applyFullscreenInternal(isFullscreen);
                }
            });

            homeTargetUrl = ("url".equalsIgnoreCase(sourceMode) && startUrl != null && !startUrl.trim().isEmpty())
                    ? normalizeUrl(startUrl.trim())
                    : VIRTUAL_HOME_URL;

            webView.loadUrl(homeTargetUrl);
            requestMissingManifestPermissions();
        } catch (Throwable err) {
            Log.e(TAG, "Initialization error", err);
            showSafeFallbackError(err);
        }
    }

    private void showSafeFallbackError(Throwable err) {
        try {
            ScrollView scroll = new ScrollView(this);
            scroll.setBackgroundColor(0xFF0C0A20);
            LinearLayout box = new LinearLayout(this);
            box.setOrientation(LinearLayout.VERTICAL);
            box.setPadding(dpToPx(24), dpToPx(36), dpToPx(24), dpToPx(24));

            TextView title = new TextView(this);
            title.setText(appName + " — Runtime Diagnostics");
            title.setTextColor(0xFF00C6FF);
            title.setTextSize(18f);
            title.setTypeface(Typeface.DEFAULT_BOLD);
            box.addView(title);

            TextView msg = new TextView(this);
            String detail = err.getClass().getSimpleName() + ": " + String.valueOf(err.getMessage());
            msg.setText(detail);
            msg.setTextColor(0xFFF4F6FF);
            msg.setTextSize(14f);
            msg.setTextIsSelectable(true);
            msg.setPadding(0, dpToPx(12), 0, dpToPx(12));
            box.addView(msg);

            scroll.addView(box);
            setContentView(scroll);
        } catch (Throwable ignored) {
        }
    }

    private void loadConfig() {
        try {
            InputStream is = getAssets().open("app_config.json");
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            byte[] buf = new byte[4096];
            int n;
            while ((n = is.read(buf)) != -1) {
                baos.write(buf, 0, n);
            }
            is.close();
            JSONObject obj = new JSONObject(new String(baos.toByteArray(), StandardCharsets.UTF_8));
            sourceMode = obj.optString("sourceMode", "html");
            startUrl = obj.optString("startUrl", "");
            appName = obj.optString("appName", "WebForge App");
            themeColor = obj.optString("themeColor", "#5B3DF5");
            statusBarColor = obj.optString("statusBarColor", "#0C0A20");
            navBarColor = obj.optString("navBarColor", "#0C0A20");
            orientationMode = obj.optString("orientation", "auto");
            isFullscreen = obj.optBoolean("fullscreen", false);
            pullToRefresh = obj.optBoolean("pullToRefresh", true);
            keepScreenOn = obj.optBoolean("keepScreenOn", false);
            enableZoom = obj.optBoolean("enableZoom", false);
            desktopMode = obj.optBoolean("desktopMode", false);
            showProgressBar = obj.optBoolean("showProgressBar", true);
            showFloatingBar = obj.optBoolean("showFloatingBar", false);
            openExternalLinks = obj.optBoolean("openExternalLinks", false);
            backBehavior = obj.optString("backBehavior", "history");
            customUserAgent = obj.optString("customUserAgent", "");
            splashEnabled = obj.optBoolean("splashEnabled", true);
            splashDurationMs = obj.optLong("splashDurationMs", 1500L);
            splashTitle = obj.optString("splashTitle", appName);
            splashSubtitle = obj.optString("splashSubtitle", "");
            splashBgColor = obj.optString("splashBgColor", "#0C0A20");
            splashAccentColor = obj.optString("splashAccentColor", "#00C6FF");
            showSplashSpinner = obj.optBoolean("showSplashSpinner", true);
        } catch (Throwable ignored) {
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private void configureWebSettings() {
        WebSettings ws = webView.getSettings();
        ws.setJavaScriptEnabled(true);
        ws.setJavaScriptCanOpenWindowsAutomatically(true);
        ws.setSupportMultipleWindows(false);
        ws.setDomStorageEnabled(true);
        ws.setDatabaseEnabled(true);
        ws.setAllowFileAccess(true);
        ws.setAllowContentAccess(true);
        ws.setAllowFileAccessFromFileURLs(true);
        ws.setAllowUniversalAccessFromFileURLs(true);
        ws.setMediaPlaybackRequiresUserGesture(false);
        ws.setUseWideViewPort(true);
        ws.setLoadWithOverviewMode(true);
        ws.setSupportZoom(enableZoom);
        ws.setBuiltInZoomControls(enableZoom);
        ws.setDisplayZoomControls(false);
        ws.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
        ws.setCacheMode(WebSettings.LOAD_DEFAULT);

        if (customUserAgent != null && !customUserAgent.trim().isEmpty()) {
            ws.setUserAgentString(customUserAgent.trim());
        } else if (desktopMode) {
            ws.setUserAgentString(
                    "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36"
            );
        }

        try {
            CookieManager.getInstance().setAcceptCookie(true);
            CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true);
        } catch (Throwable ignored) {
        }

        webView.addJavascriptInterface(new WebForgeBridge(), "WebForgeNative");

        webView.setDownloadListener(new DownloadListener() {
            @Override
            public void onDownloadStart(String url, String userAgent, String contentDisposition, String mimeType, long contentLength) {
                try {
                    DownloadManager.Request req = new DownloadManager.Request(Uri.parse(url));
                    req.setMimeType(mimeType);
                    if (userAgent != null) req.addRequestHeader("User-Agent", userAgent);
                    String fileName = URLUtil.guessFileName(url, contentDisposition, mimeType);
                    req.setTitle(fileName);
                    req.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
                    req.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName);
                    DownloadManager dm = (DownloadManager) getSystemService(DOWNLOAD_SERVICE);
                    if (dm != null) {
                        dm.enqueue(req);
                        Toast.makeText(MainActivity.this, "Downloading " + fileName, Toast.LENGTH_SHORT).show();
                        return;
                    }
                } catch (Throwable ignored) {
                }
                openExternalIntent(url);
            }
        });

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                try {
                    Uri uri = request != null ? request.getUrl() : null;
                    if (uri != null && VIRTUAL_HOST.equalsIgnoreCase(uri.getHost())) {
                        String rawPath = uri.getPath();
                        String rel = (rawPath == null || rawPath.isEmpty() || "/".equals(rawPath))
                                ? "index.html"
                                : rawPath.replaceFirst("^/+", "");
                        if (rel.contains("..")) {
                            return null;
                        }
                        WebResourceResponse resp = openAssetResponse("www/" + rel);
                        if (resp != null) return resp;
                        if (!rel.contains(".")) {
                            return openAssetResponse("www/index.html");
                        }
                    }
                } catch (Throwable ignored) {
                }
                return super.shouldInterceptRequest(view, request);
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                Uri uri = request.getUrl();
                String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.US);
                if (!"http".equals(scheme) && !"https".equals(scheme) && !"file".equals(scheme)
                        && !"about".equals(scheme) && !"data".equals(scheme) && !"blob".equals(scheme)) {
                    openExternalIntent(uri.toString());
                    return true;
                }
                if (openExternalLinks && ("http".equals(scheme) || "https".equals(scheme))) {
                    String targetHost = uri.getHost();
                    String homeHost = Uri.parse(homeTargetUrl).getHost();
                    if (targetHost != null && !VIRTUAL_HOST.equalsIgnoreCase(targetHost)
                            && (homeHost == null || !targetHost.equalsIgnoreCase(homeHost))) {
                        openExternalIntent(uri.toString());
                        return true;
                    }
                }
                return false;
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                if (showProgressBar && loadingBar != null) {
                    loadingBar.setVisibility(View.GONE);
                }
                if (splashEnabled && !splashDismissed) {
                    long elapsed = System.currentTimeMillis() - createTimeMs;
                    long remaining = Math.max(120L, Math.min(splashDurationMs, Math.max(500L, splashDurationMs) - elapsed));
                    handler.postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            dismissSplash();
                        }
                    }, remaining);
                }
            }

            @Override
            public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
                if (request != null && request.isForMainFrame() && "url".equalsIgnoreCase(sourceMode)) {
                    dismissSplash();
                    String safeHome = homeTargetUrl.replace("'", "\\'");
                    String offlineHtml = "<!DOCTYPE html><html><head><meta charset='utf-8'><meta name='viewport' content='width=device-width,initial-scale=1.0'>"
                            + "<style>body{margin:0;background:#0C0A20;color:#F4F6FF;font-family:system-ui,sans-serif;display:flex;align-items:center;justify-content:center;min-height:100vh;text-align:center;padding:24px}"
                            + ".box{max-width:340px;background:#1C1840;border:1px solid #5B3DF5;border-radius:22px;padding:28px}"
                            + "button{margin-top:14px;width:100%;padding:14px;border:none;border-radius:14px;font-weight:700;font-size:15px;color:#0C0A20;background:linear-gradient(90deg,#5B3DF5,#00C6FF)}</style></head>"
                            + "<body><div class='box'><h2>" + escapeHtml(appName) + "</h2>"
                            + "<p style='color:#A8AED8'>تعذر تحميل الصفحة — تحقق من الاتصال بالإنترنت.<br>Unable to reach page.</p>"
                            + "<button onclick=\"location.href='" + safeHome + "'\">إعادة المحاولة / Retry</button></div></body></html>";
                    view.loadDataWithBaseURL(null, offlineHtml, "text/html", "utf-8", null);
                }
            }
        });

        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onProgressChanged(WebView view, int newProgress) {
                if (showProgressBar && loadingBar != null) {
                    loadingBar.setProgress(newProgress);
                    loadingBar.setVisibility(newProgress >= 100 ? View.GONE : View.VISIBLE);
                }
            }

            @Override
            public boolean onShowFileChooser(WebView webView, ValueCallback<Uri[]> callback, FileChooserParams fileChooserParams) {
                if (filePathCallback != null) {
                    filePathCallback.onReceiveValue(null);
                }
                filePathCallback = callback;
                try {
                    Intent intent = fileChooserParams.createIntent();
                    startActivityForResult(intent, REQ_FILE_CHOOSER);
                } catch (Exception e) {
                    filePathCallback = null;
                    return false;
                }
                return true;
            }

            @Override
            public void onGeolocationPermissionsShowPrompt(String origin, GeolocationPermissions.Callback callback) {
                callback.invoke(origin, true, false);
            }

            @Override
            public void onPermissionRequest(PermissionRequest request) {
                try {
                    request.grant(request.getResources());
                } catch (Throwable ignored) {
                }
            }

            @Override
            public boolean onJsAlert(WebView view, String url, String message, JsResult result) {
                Toast.makeText(MainActivity.this, message, Toast.LENGTH_SHORT).show();
                result.confirm();
                return true;
            }

            @Override
            public boolean onJsConfirm(WebView view, String url, String message, JsResult result) {
                result.confirm();
                return true;
            }

            @Override
            public boolean onJsPrompt(WebView view, String url, String message, String defaultValue, JsPromptResult result) {
                result.confirm(defaultValue);
                return true;
            }

            @Override
            public void onShowCustomView(View view, CustomViewCallback callback) {
                if (customVideoView != null) {
                    callback.onCustomViewHidden();
                    return;
                }
                customVideoView = view;
                customViewCallback = callback;
                customVideoContainer.addView(view, new FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT
                ));
                customVideoContainer.setVisibility(View.VISIBLE);
                setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE);
                applyFullscreenInternal(true);
            }

            @Override
            public void onHideCustomView() {
                if (customVideoView == null) return;
                customVideoContainer.removeView(customVideoView);
                customVideoContainer.setVisibility(View.GONE);
                customVideoView = null;
                if (customViewCallback != null) {
                    customViewCallback.onCustomViewHidden();
                    customViewCallback = null;
                }
                applyOrientation(orientationMode);
                applyFullscreenInternal(isFullscreen);
            }
        });
    }

    private WebResourceResponse openAssetResponse(String assetPath) {
        try {
            InputStream is = getAssets().open(assetPath);
            String mime = guessMimeType(assetPath);
            Map<String, String> headers = new HashMap<>();
            headers.put("Access-Control-Allow-Origin", "*");
            headers.put("Cache-Control", "no-cache");
            return new WebResourceResponse(mime, "UTF-8", 200, "OK", headers, is);
        } catch (Throwable e) {
            return null;
        }
    }

    private String guessMimeType(String path) {
        String p = path.toLowerCase(Locale.US);
        if (p.endsWith(".html") || p.endsWith(".htm")) return "text/html";
        if (p.endsWith(".css")) return "text/css";
        if (p.endsWith(".js") || p.endsWith(".mjs")) return "application/javascript";
        if (p.endsWith(".json") || p.endsWith(".webmanifest")) return "application/json";
        if (p.endsWith(".svg")) return "image/svg+xml";
        if (p.endsWith(".png")) return "image/png";
        if (p.endsWith(".jpg") || p.endsWith(".jpeg")) return "image/jpeg";
        if (p.endsWith(".webp")) return "image/webp";
        if (p.endsWith(".gif")) return "image/gif";
        if (p.endsWith(".woff2")) return "font/woff2";
        if (p.endsWith(".woff")) return "font/woff";
        if (p.endsWith(".ttf")) return "font/ttf";
        if (p.endsWith(".mp4")) return "video/mp4";
        if (p.endsWith(".mp3")) return "audio/mpeg";
        return "application/octet-stream";
    }

    private FrameLayout buildSplashOverlay(int bgInt, int accentInt) {
        FrameLayout overlay = new FrameLayout(this);
        overlay.setBackgroundColor(bgInt);
        overlay.setClickable(true);

        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setGravity(Gravity.CENTER);
        col.setPadding(dpToPx(24), dpToPx(24), dpToPx(24), dpToPx(24));

        ImageView logo = new ImageView(this);
        try {
            InputStream is = getAssets().open("splash_icon.png");
            Bitmap bmp = BitmapFactory.decodeStream(is);
            is.close();
            if (bmp != null) {
                logo.setImageBitmap(bmp);
            }
        } catch (Throwable ignored) {
        }
        int iconSz = dpToPx(104);
        LinearLayout.LayoutParams iconLp = new LinearLayout.LayoutParams(iconSz, iconSz);
        iconLp.bottomMargin = dpToPx(18);
        col.addView(logo, iconLp);

        String displayTitle = (splashTitle != null && !splashTitle.trim().isEmpty()) ? splashTitle : appName;
        TextView titleView = new TextView(this);
        titleView.setText(displayTitle);
        titleView.setTextColor(isLightColor(bgInt) ? 0xFF0C0A20 : Color.WHITE);
        titleView.setTextSize(22f);
        titleView.setTypeface(Typeface.DEFAULT_BOLD);
        titleView.setGravity(Gravity.CENTER);
        col.addView(titleView);

        if (splashSubtitle != null && !splashSubtitle.trim().isEmpty()) {
            TextView subView = new TextView(this);
            subView.setText(splashSubtitle);
            subView.setTextColor(isLightColor(bgInt) ? 0xFF444868 : 0xFFA8AED8);
            subView.setTextSize(14f);
            subView.setGravity(Gravity.CENTER);
            LinearLayout.LayoutParams subLp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
            );
            subLp.topMargin = dpToPx(8);
            col.addView(subView, subLp);
        }

        if (showSplashSpinner) {
            ProgressBar spinner = new ProgressBar(this);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                spinner.setIndeterminateTintList(ColorStateList.valueOf(accentInt));
            }
            LinearLayout.LayoutParams spLp = new LinearLayout.LayoutParams(dpToPx(32), dpToPx(32));
            spLp.topMargin = dpToPx(22);
            col.addView(spinner, spLp);
        }

        overlay.addView(col, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER
        ));
        return overlay;
    }

    private LinearLayout buildFloatingBar(int accentInt) {
        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0xE61C1840);
        bg.setCornerRadius(dpToPx(24));
        bg.setStroke(dpToPx(1), accentInt);
        bar.setBackground(bg);
        bar.setPadding(dpToPx(8), dpToPx(4), dpToPx(8), dpToPx(4));

        bar.addView(makeBarBtn("◀", accentInt, new View.OnClickListener() {
            @Override public void onClick(View v) { if (webView != null && webView.canGoBack()) webView.goBack(); }
        }));
        bar.addView(makeBarBtn("▶", accentInt, new View.OnClickListener() {
            @Override public void onClick(View v) { if (webView != null && webView.canGoForward()) webView.goForward(); }
        }));
        bar.addView(makeBarBtn("⌂", Color.WHITE, new View.OnClickListener() {
            @Override public void onClick(View v) { if (webView != null) webView.loadUrl(homeTargetUrl); }
        }));
        bar.addView(makeBarBtn("↻", accentInt, new View.OnClickListener() {
            @Override public void onClick(View v) { if (webView != null) webView.reload(); }
        }));
        bar.addView(makeBarBtn("⟳", 0xFF34E0A1, new View.OnClickListener() {
            @Override public void onClick(View v) { toggleOrientationLive(); }
        }));
        bar.addView(makeBarBtn("⛶", Color.WHITE, new View.OnClickListener() {
            @Override public void onClick(View v) {
                isFullscreen = !isFullscreen;
                applyFullscreenInternal(isFullscreen);
            }
        }));
        return bar;
    }

    private TextView makeBarBtn(String label, int color, View.OnClickListener listener) {
        TextView tv = new TextView(this);
        tv.setText(label);
        tv.setTextColor(color);
        tv.setTextSize(16f);
        tv.setGravity(Gravity.CENTER);
        tv.setMinWidth(dpToPx(40));
        tv.setMinHeight(dpToPx(38));
        tv.setOnClickListener(listener);
        return tv;
    }

    private void toggleOrientationLive() {
        int current = getResources().getConfiguration().orientation;
        if (current == Configuration.ORIENTATION_LANDSCAPE) {
            orientationMode = "portrait";
        } else {
            orientationMode = "landscape";
        }
        applyOrientation(orientationMode);
    }

    private void dismissSplash() {
        if (splashDismissed || splashOverlay == null) return;
        splashDismissed = true;
        try {
            splashOverlay.animate().alpha(0f).setDuration(260L).withEndAction(new Runnable() {
                @Override
                public void run() {
                    splashOverlay.setVisibility(View.GONE);
                }
            }).start();
        } catch (Throwable e) {
            splashOverlay.setVisibility(View.GONE);
        }
    }

    private void applyOrientation(String mode) {
        try {
            if ("portrait".equalsIgnoreCase(mode)) {
                setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
            } else if ("landscape".equalsIgnoreCase(mode)) {
                setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
            } else {
                setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED);
            }
        } catch (Throwable ignored) {
        }
    }

    private void applyWindowStyling() {
        try {
            Window w = getWindow();
            w.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
            int statusColor = safeParseColor(statusBarColor, 0xFF0C0A20);
            int navColor = safeParseColor(navBarColor, 0xFF0C0A20);
            w.setStatusBarColor(statusColor);
            w.setNavigationBarColor(navColor);
            if (keepScreenOn) {
                w.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                WindowInsetsController c = w.getInsetsController();
                if (c != null) {
                    int lightStatus = isLightColor(statusColor) ? WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS : 0;
                    c.setSystemBarsAppearance(lightStatus, WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS);
                }
            }
            applyFullscreenInternal(isFullscreen);
        } catch (Throwable ignored) {
        }
    }

    private void applyFullscreenInternal(boolean enable) {
        try {
            Window w = getWindow();
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                WindowInsetsController controller = w.getInsetsController();
                if (controller != null) {
                    if (enable) {
                        controller.hide(WindowInsets.Type.statusBars() | WindowInsets.Type.navigationBars());
                        controller.setSystemBarsBehavior(WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
                    } else {
                        controller.show(WindowInsets.Type.statusBars() | WindowInsets.Type.navigationBars());
                    }
                }
            } else {
                int flags = View.SYSTEM_UI_FLAG_LAYOUT_STABLE;
                if (enable) {
                    flags |= View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                            | View.SYSTEM_UI_FLAG_FULLSCREEN
                            | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                            | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                            | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION;
                }
                w.getDecorView().setSystemUiVisibility(flags);
            }
        } catch (Throwable ignored) {
        }
    }

    private void requestMissingManifestPermissions() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return;
        java.util.ArrayList<String> needed = new java.util.ArrayList<>();
        String[] candidates = new String[]{
                Manifest.permission.CAMERA,
                Manifest.permission.RECORD_AUDIO,
                Manifest.permission.ACCESS_FINE_LOCATION,
                "android.permission.POST_NOTIFICATIONS"
        };
        try {
            String[] declared = getPackageManager().getPackageInfo(getPackageName(), PackageManager.GET_PERMISSIONS).requestedPermissions;
            if (declared == null) return;
            java.util.HashSet<String> set = new java.util.HashSet<>(java.util.Arrays.asList(declared));
            for (String p : candidates) {
                if (set.contains(p) && checkSelfPermission(p) != PackageManager.PERMISSION_GRANTED) {
                    needed.add(p);
                }
            }
            if (!needed.isEmpty()) {
                requestPermissions(needed.toArray(new String[0]), REQ_PERMISSIONS);
            }
        } catch (Throwable ignored) {
        }
    }

    private void openExternalIntent(String url) {
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
            startActivity(intent);
        } catch (Throwable ignored) {
        }
    }

    private String normalizeUrl(String url) {
        if (url.startsWith("http://") || url.startsWith("https://") || url.startsWith("file://")) {
            return url;
        }
        return "https://" + url;
    }

    private boolean isLightColor(int color) {
        double luma = (0.299 * Color.red(color) + 0.587 * Color.green(color) + 0.114 * Color.blue(color)) / 255.0;
        return luma > 0.65;
    }

    private int safeParseColor(String hex, int fallback) {
        try {
            return Color.parseColor(hex);
        } catch (Throwable e) {
            return fallback;
        }
    }

    private int dpToPx(int dp) {
        return Math.round(dp * getResources().getDisplayMetrics().density);
    }

    private String escapeHtml(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == REQ_FILE_CHOOSER && filePathCallback != null) {
            Uri[] results = WebChromeClient.FileChooserParams.parseResult(resultCode, data);
            filePathCallback.onReceiveValue(results);
            filePathCallback = null;
        } else {
            super.onActivityResult(requestCode, resultCode, data);
        }
    }

    @Override
    public void onBackPressed() {
        if (customVideoView != null && webView != null && webView.getWebChromeClient() != null) {
            webView.getWebChromeClient().onHideCustomView();
            return;
        }
        if (webView != null && webView.canGoBack()) {
            webView.goBack();
            return;
        }
        if ("confirm".equalsIgnoreCase(backBehavior)) {
            long now = System.currentTimeMillis();
            if (now - lastBackPressMs < 2000L) {
                super.onBackPressed();
            } else {
                lastBackPressMs = now;
                Toast.makeText(this, "اضغط مرة أخرى للخروج / Press back again to exit", Toast.LENGTH_SHORT).show();
            }
        } else {
            super.onBackPressed();
        }
    }

    public class WebForgeBridge {
        @JavascriptInterface
        public void toast(final String msg) {
            runOnUiThread(new Runnable() {
                @Override public void run() { Toast.makeText(MainActivity.this, msg, Toast.LENGTH_SHORT).show(); }
            });
        }

        @JavascriptInterface
        public void vibrate(long ms) {
            try {
                Vibrator v = (Vibrator) getSystemService(VIBRATOR_SERVICE);
                if (v != null) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        v.vibrate(VibrationEffect.createOneShot(ms, VibrationEffect.DEFAULT_AMPLITUDE));
                    } else {
                        v.vibrate(ms);
                    }
                }
            } catch (Throwable ignored) {
            }
        }

        @JavascriptInterface
        public void share(String title, String text) {
            try {
                Intent i = new Intent(Intent.ACTION_SEND);
                i.setType("text/plain");
                i.putExtra(Intent.EXTRA_SUBJECT, title);
                i.putExtra(Intent.EXTRA_TEXT, text);
                startActivity(Intent.createChooser(i, title));
            } catch (Throwable ignored) {
            }
        }

        @JavascriptInterface
        public void openExternal(String url) {
            openExternalIntent(url);
        }

        @JavascriptInterface
        public void setOrientation(final String mode) {
            runOnUiThread(new Runnable() {
                @Override public void run() {
                    orientationMode = mode;
                    applyOrientation(mode);
                }
            });
        }

        @JavascriptInterface
        public void setFullscreen(final boolean enable) {
            runOnUiThread(new Runnable() {
                @Override public void run() {
                    isFullscreen = enable;
                    applyFullscreenInternal(enable);
                }
            });
        }

        @JavascriptInterface
        public void reload() {
            runOnUiThread(new Runnable() {
                @Override public void run() {
                    if (webView != null) webView.reload();
                }
            });
        }
    }
}
