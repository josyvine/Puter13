package com.puter.unofficial;

import android.app.Activity;
import android.app.Dialog;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Message;
import android.provider.MediaStore;
import android.util.Log;
import android.view.ViewGroup;
import android.webkit.ConsoleMessage;
import android.webkit.CookieManager;
import android.webkit.PermissionRequest;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.LinearLayout;

import androidx.core.content.FileProvider;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Handles the native file upload functionality (Camera, Gallery, File Picker)
 * for the WebView, enabling Base64 upload support for Puter AI interactions.
 * UPDATED: Added Console Message interception and moved to HTTPS Origin for persistence.
 * CORE FIX: Intercepts Puter Web OS desktop redirect (/app) to auto-dismiss login popup.
 * TIMING FIX: Synchronized WebRTC permission requests to eliminate thread race conditions.
 */
public class MyWebChromeClient extends WebChromeClient {

    private static final String TAG = "MyWebChromeClient";
    private ValueCallback<Uri[]> uploadMessage;
    private final Activity activity;
    private String currentPhotoPath;
    private Dialog authDialog; // Dialog to host the login popup WebView
    private boolean isAuthProcessing = false; // Prevents the "Blinking" reload loop
    private String lastAuthUrl = null;

    public MyWebChromeClient(Activity activity) {
        this.activity = activity;
    }

    /**
     * Capture JavaScript console logs and pipe them to Logcat.
     */
    @Override
    public boolean onConsoleMessage(ConsoleMessage consoleMessage) {
        String logMsg = String.format(Locale.getDefault(), 
            "[JS CONSOLE] %s -- From line %d of %s",
            consoleMessage.message(),
            consoleMessage.lineNumber(),
            consoleMessage.sourceId());

        switch (consoleMessage.messageLevel()) {
            case ERROR: 
                Log.e("PuterJS", logMsg); 
                ActionReportLogger.logHtmlGlitch("JS_CONSOLE_ERROR", logMsg);
                break;
            case WARNING: 
                Log.w("PuterJS", logMsg); 
                ActionReportLogger.logHtmlGlitch("JS_CONSOLE_WARNING", logMsg);
                break;
            default: 
                Log.d("PuterJS", logMsg); 
                ActionReportLogger.logAction("JS_CONSOLE_LOG", logMsg);
                break;
        }
        return true;
    }

    // --- SDK AUTH POPUP HANDLER WITH AUTO-CLOSE & FALLBACK BUTTON ---

    @Override
    public boolean onCreateWindow(WebView view, boolean isDialog, boolean isUserGesture, Message resultMsg) {
        if (activity == null) return false;
        WebView.WebViewTransport transport = (WebView.WebViewTransport) resultMsg.obj;
        isAuthProcessing = false; // Reset lock for new window

        triggerNativeLog("Auth Popup Requested by SDK", "native");

        // 1. Create a Layout to hold a Close Button + The Popup WebView
        LinearLayout dialogLayout = new LinearLayout(activity);
        dialogLayout.setOrientation(LinearLayout.VERTICAL);
        dialogLayout.setBackgroundColor(Color.WHITE);

        // 2. Create a Fail-Safe "Done" Button
        Button closeButton = new Button(activity);
        closeButton.setText("Close Window (Tap when Signed In)");
        closeButton.setBackgroundColor(Color.parseColor("#1a73e8"));
        closeButton.setTextColor(Color.WHITE);
        closeButton.setOnClickListener(v -> {
            triggerNativeLog("Manual Close Triggered by User", "warn");
            closeAuthAndRefresh();
        });

        dialogLayout.addView(closeButton, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 
                ViewGroup.LayoutParams.WRAP_CONTENT));

        // 3. Create and configure the new WebView for the popup
        final WebView popupWebView = new WebView(activity);
        dialogLayout.addView(popupWebView, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 
                ViewGroup.LayoutParams.MATCH_PARENT));

        WebSettings webSettings = popupWebView.getSettings();
        webSettings.setJavaScriptEnabled(true);
        webSettings.setDomStorageEnabled(true);
        webSettings.setJavaScriptCanOpenWindowsAutomatically(true);
        webSettings.setSupportMultipleWindows(true);

        String userAgent = popupWebView.getSettings().getUserAgentString().replace("; wv", "");
        webSettings.setUserAgentString(userAgent);

        CookieManager.getInstance().setAcceptCookie(true);
        CookieManager.getInstance().setAcceptThirdPartyCookies(popupWebView, true);

        // 4. Monitor the popup for URL changes and post-login desktop routing
        popupWebView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                String url = request.getUrl().toString();
                triggerNativeLog("Popup Navigating: " + url, "info");
                lastAuthUrl = url;

                AuthManager auth = AuthManager.getInstance(activity);
                if (auth.isAuthCallback(url)) {
                    if (!isAuthProcessing) {
                        isAuthProcessing = true;
                        triggerNativeLog("Auth completion marker found (/app or token). Finalizing session...", "native");
                        view.postDelayed(() -> {
                            CookieManager.getInstance().flush();
                            closeAuthAndRefresh();
                        }, 800);
                    }
                    return false;
                }
                return false;
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                CookieManager.getInstance().flush();
                lastAuthUrl = url;
                triggerNativeLog("Popup Page Loaded: " + url, "info");

                AuthManager auth = AuthManager.getInstance(activity);
                if (url != null && auth.isAuthCallback(url)) {
                    if (!isAuthProcessing) {
                        isAuthProcessing = true;
                        triggerNativeLog("Auth callback recognized in onPageFinished. Closing dialog...", "native");
                        view.postDelayed(() -> {
                            CookieManager.getInstance().flush();
                            closeAuthAndRefresh();
                        }, 500);
                    }
                }
            }
        });

        // 5. Handle official window.close() call from Puter SDK
        popupWebView.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onCloseWindow(WebView window) {
                if (!isAuthProcessing) {
                    isAuthProcessing = true;
                    triggerNativeLog("SDK issued window.close() command", "native");
                    closeAuthAndRefresh();
                }
            }
        });

        // 6. Show the Dialog
        authDialog = new Dialog(activity, android.R.style.Theme_DeviceDefault_NoActionBar);
        authDialog.setContentView(dialogLayout);
        authDialog.show();

        transport.setWebView(popupWebView);
        resultMsg.sendToTarget();

        return true;
    }

    private void triggerNativeLog(String msg, String type) {
        Log.d("PuterPopupTrace", msg);
        ActionReportLogger.logAction("AUTH_POPUP", "[" + type + "] " + msg);
    }

    /**
     * Finalizes authentication, marks session in AuthManager, dismisses popup, and refreshes UI.
     */
    private void closeAuthAndRefresh() {
        CookieManager.getInstance().flush(); 

        if (activity != null) {
            AuthManager auth = AuthManager.getInstance(activity);
            if (lastAuthUrl != null) {
                auth.handleAuthCallback(lastAuthUrl);
            }
            auth.setLoggedIn(true);
        }

        if (authDialog != null && authDialog.isShowing()) {
            authDialog.dismiss();
            authDialog = null;
        }

        if (activity instanceof MainActivity) {
            activity.runOnUiThread(() -> {
                ((MainActivity) activity).reloadWebView();
            });
        }
    }

    @Override
    public void onCloseWindow(WebView window) {
        if (authDialog != null && authDialog.isShowing()) {
            authDialog.dismiss();
            authDialog = null;
        }
        super.onCloseWindow(window);
    }

    // --- HARDWARE PERMISSIONS GATE OVERRIDE ---

    @Override
    public void onPermissionRequest(final PermissionRequest request) {
        Log.d(TAG, "onPermissionRequest: Processing hardware capture access permission synchronously.");
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            if (activity != null) {
                activity.runOnUiThread(() -> {
                    try {
                        String[] requestedResources = request.getResources();
                        request.grant(requestedResources);
                        Log.d(TAG, "onPermissionRequest: Successfully granted resources: " + java.util.Arrays.toString(requestedResources));
                    } catch (Exception e) {
                        Log.e(TAG, "onPermissionRequest: Failed to grant permission. Error: " + e.getMessage());
                        request.deny();
                    }
                });
            } else {
                try {
                    request.grant(request.getResources());
                } catch (Exception ignored) {}
            }
        }
    }

    // --- FILE UPLOAD LOGIC ---

    @Override
    public boolean onShowFileChooser(WebView webView, ValueCallback<Uri[]> filePathCallback, FileChooserParams fileChooserParams) {
        if (activity == null) return false;
        if (uploadMessage != null) {
            uploadMessage.onReceiveValue(null);
        }
        uploadMessage = filePathCallback;

        Intent contentIntent = new Intent(Intent.ACTION_GET_CONTENT);
        contentIntent.addCategory(Intent.CATEGORY_OPENABLE);
        contentIntent.setType("*/*");

        Intent takePictureIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        if (takePictureIntent.resolveActivity(activity.getPackageManager()) != null) {
            File photoFile = null;
            try {
                photoFile = createImageFile();
                takePictureIntent.putExtra("PhotoPath", currentPhotoPath);
            } catch (IOException ex) {
                Log.e("MyWebChromeClient", "Error creating file", ex);
            }
            if (photoFile != null) {
                currentPhotoPath = "file:" + photoFile.getAbsolutePath();
                Uri photoURI = FileProvider.getUriForFile(activity, activity.getPackageName() + ".fileprovider", photoFile);
                takePictureIntent.putExtra(MediaStore.EXTRA_OUTPUT, photoURI);
            } else {
                takePictureIntent = null;
            }
        }

        Intent chooserIntent = new Intent(Intent.ACTION_CHOOSER);
        chooserIntent.putExtra(Intent.EXTRA_INTENT, contentIntent);
        chooserIntent.putExtra(Intent.EXTRA_TITLE, "Upload File");
        if (takePictureIntent != null) {
            chooserIntent.putExtra(Intent.EXTRA_INITIAL_INTENTS, new Intent[] { takePictureIntent });
        }

        activity.startActivityForResult(chooserIntent, 1);
        return true;
    }

    private File createImageFile() throws IOException {
        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
        String imageFileName = "JPEG_" + timeStamp + "_";
        File storageDir = activity.getExternalFilesDir(null);
        return File.createTempFile(imageFileName, ".jpg", storageDir);
    }

    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (uploadMessage == null) return;
        Uri[] results = null;

        if (resultCode == Activity.RESULT_OK) {
            if (data == null || data.getData() == null) {
                if (currentPhotoPath != null) {
                    results = new Uri[]{Uri.parse(currentPhotoPath)};
                }
            } else {
                String dataString = data.getDataString();
                if (dataString != null) {
                    results = new Uri[]{Uri.parse(dataString)};
                }
            }
        }
        uploadMessage.onReceiveValue(results);
        uploadMessage = null;
    }
}