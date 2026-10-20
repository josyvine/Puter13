package com.puter.unofficial;

import android.annotation.SuppressLint;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.webkit.PermissionRequest;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;

import org.json.JSONObject;

/**
 * Native controller managing the background 1x1 pixel WebView running headless_live_audio.html.
 * Bridges native Java calls to the browser AudioContext/WebSocket pipeline for Gemini Live.
 */
public class HeadlessAudioBridge {
    private static final String TAG = "HeadlessAudioBridge";
    private static final String HEADLESS_ASSET_URL = "file:///android_asset/headless_live_audio.html";

    public interface LiveAudioEventListener {
        void onSessionConnected();
        void onTranscriptReceived(String text);
        void onAudioChunkReceived(String base64Pcm);
        void onCommentaryFinished();
        default void onSessionDisconnected() {}
        void onTargetCoordinatesReceived(String targetId, float left, float top, float right, float bottom);
        void onError(String errorMessage);
    }

    private final Context context;
    private final WebView webView;
    private final Handler mainHandler;
    private LiveAudioEventListener listener;
    private boolean isSessionActive = false;

    @SuppressLint("SetJavaScriptEnabled")
    public HeadlessAudioBridge(Context context, WebView webView) {
        this.context = context;
        this.webView = webView;
        this.mainHandler = new Handler(Looper.getMainLooper());

        setupWebView();
    }

    public void setEventListener(LiveAudioEventListener listener) {
        this.listener = listener;
    }

    public boolean isSessionActive() {
        return isSessionActive;
    }

    @SuppressLint("SetJavaScriptEnabled")
    private void setupWebView() {
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setMediaPlaybackRequiresUserGesture(false);
        settings.setAllowFileAccess(true);
        settings.setAllowContentAccess(true);

        // Grant WebRTC microphone capture permission automatically to avoid silent getUserMedia drop
        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onPermissionRequest(final PermissionRequest request) {
                mainHandler.post(() -> {
                    if (request != null) {
                        request.grant(request.getResources());
                    }
                });
            }
        });

        webView.loadUrl(HEADLESS_ASSET_URL);
    }

    public void startCommentarySession(String scriptText, String personaPrompt) {
        mainHandler.post(() -> {
            isSessionActive = true;
            String scriptEscaped = scriptText != null ? JSONObject.quote(scriptText) : "\"\"";
            String personaEscaped = personaPrompt != null ? JSONObject.quote(personaPrompt) : "\"\"";
            String js = String.format("window.startCommentarySession(%s, %s);", scriptEscaped, personaEscaped);
            webView.evaluateJavascript(js, null);
        });
    }

    public void stopCommentarySession() {
        mainHandler.post(() -> {
            isSessionActive = false;
            webView.evaluateJavascript("window.stopCommentarySession();", null);
            dispatchDisconnected();
        });
    }

    public void groundTargetWithVision(String base64Jpg, String targetId, String desc) {
        mainHandler.post(() -> {
            String b64 = JSONObject.quote(base64Jpg != null ? base64Jpg : "");
            String tId = JSONObject.quote(targetId != null ? targetId : "");
            String d = JSONObject.quote(desc != null ? desc : "");
            String js = String.format("window.groundTargetWithVision(%s, %s, %s);", b64, tId, d);
            webView.evaluateJavascript(js, null);
        });
    }

    // Callbacks from WebAppInterface dispatched to listener
    public void dispatchConnected() {
        mainHandler.post(() -> {
            isSessionActive = true;
            if (listener != null) listener.onSessionConnected();
        });
    }

    public void dispatchTranscript(String text) {
        mainHandler.post(() -> {
            if (listener != null) listener.onTranscriptReceived(text);
        });
    }

    public void dispatchAudioChunk(String base64Pcm) {
        mainHandler.post(() -> {
            if (listener != null) listener.onAudioChunkReceived(base64Pcm);
        });
    }

    public void dispatchFinished() {
        mainHandler.post(() -> {
            isSessionActive = false;
            if (listener != null) listener.onCommentaryFinished();
        });
    }

    public void dispatchDisconnected() {
        mainHandler.post(() -> {
            isSessionActive = false;
            if (listener != null) listener.onSessionDisconnected();
        });
    }

    public void dispatchTargetCoordinates(String targetId, float left, float top, float right, float bottom) {
        mainHandler.post(() -> {
            if (listener != null) listener.onTargetCoordinatesReceived(targetId, left, top, right, bottom);
        });
    }

    public void dispatchError(String error) {
        mainHandler.post(() -> {
            isSessionActive = false;
            if (listener != null) listener.onError(error);
        });
    }
}