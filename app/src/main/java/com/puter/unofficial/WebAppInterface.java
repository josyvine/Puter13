package com.puter.unofficial;

import android.app.Activity;
import android.content.ContentValues;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.util.Base64;
import android.util.Log;
import android.webkit.JavascriptInterface;
import android.widget.Toast;

import java.io.OutputStream;

/**
 * JavaScript interface bridge specifically bound to headless_live_audio.html.
 * Handles Gemini Live Bidi WebSockets, live audio streams, transcripts, and vision tool events.
 */
public class WebAppInterface {

    private static final String TAG = "WebAppInterface";
    private final Context context;
    private final SharedPreferences prefs;
    private final Handler mainHandler;
    private HeadlessAudioBridge audioBridge;
    private MainActivity mainActivity;

    public WebAppInterface(Context context) {
        this.context = context;
        this.prefs = context.getSharedPreferences(AppConstants.PREF_NAME, Context.MODE_PRIVATE);
        this.mainHandler = new Handler(Looper.getMainLooper());
    }

    public void setAudioBridge(HeadlessAudioBridge bridge) {
        this.audioBridge = bridge;
    }

    public void setMainActivity(MainActivity activity) {
        this.mainActivity = activity;
    }

    @JavascriptInterface
    public String getGeminiApiKey() {
        String key = prefs.getString(AppConstants.KEY_GEMINI_API_KEY, "");
        if (key == null || key.trim().isEmpty()) {
            key = "AIzaSyBbwPg-mPKe4FNtfI8j07REFFjV6C-xVMw";
        }
        return key;
    }

    @JavascriptInterface
    public String getModelId() {
        if (mainActivity != null) {
            String m = mainActivity.getActiveModelId();
            if (m != null && m.contains("gemini")) return m;
        }
        return "gemini-2.0-flash-exp";
    }

    @JavascriptInterface
    public String getVoiceName() {
        return prefs.getString("gemini_voice_name", "Puck");
    }

    @JavascriptInterface
    public void onLiveSessionConnected() {
        Log.d(TAG, "Live WebSocket session connected");
        if (audioBridge != null) {
            audioBridge.dispatchConnected();
        }
        if (mainActivity != null) {
            mainActivity.runOnUiThread(() -> mainActivity.onLiveSessionConnected());
        }
    }

    @JavascriptInterface
    public void onLiveAudioChunkReceived(String base64Pcm) {
        if (audioBridge != null) {
            audioBridge.dispatchAudioChunk(base64Pcm);
        }
    }

    @JavascriptInterface
    public void onLiveCommentaryText(String text) {
        if (audioBridge != null) {
            audioBridge.dispatchTranscript(text);
        }
        if (mainActivity != null) {
            mainActivity.runOnUiThread(() -> mainActivity.onLiveTranscriptReceived(text));
        }
    }

    @JavascriptInterface
    public void onLiveCommentaryFinished() {
        Log.d(TAG, "Live commentary turn completed");
        if (audioBridge != null) {
            audioBridge.dispatchFinished();
        }
        if (mainActivity != null) {
            mainActivity.runOnUiThread(() -> mainActivity.onLiveSessionFinished());
        }
    }

    @JavascriptInterface
    public void onLiveError(String err) {
        Log.e(TAG, "Live commentary error: " + err);
        if (audioBridge != null) {
            audioBridge.dispatchError(err);
        }
        if (mainActivity != null) {
            mainActivity.runOnUiThread(() -> mainActivity.onLiveError(err));
        }
    }

    @JavascriptInterface
    public void onTargetCoordinatesReceived(String targetId, float left, float top, float right, float bottom) {
        Log.d(TAG, "Target coordinates received: " + targetId + " -> [" + left + ", " + top + ", " + right + ", " + bottom + "]");
        if (audioBridge != null) {
            audioBridge.dispatchTargetCoordinates(targetId, left, top, right, bottom);
        }
    }

    @JavascriptInterface
    public void logDiagnostic(String msg, String tag) {
        Log.d("PuterLiveDiagnostic", "[" + tag + "] " + msg);
    }

    @JavascriptInterface
    public void showToast(String msg) {
        mainHandler.post(() -> Toast.makeText(context, msg, Toast.LENGTH_SHORT).show());
    }

    @JavascriptInterface
    public void saveImageToGallery(String base64Data, String filename) {
        new Thread(() -> {
            try {
                String cleanBase64 = base64Data.replaceFirst("^data:image/[a-zA-Z]+;base64,", "");
                byte[] decoded = Base64.decode(cleanBase64, Base64.DEFAULT);
                Bitmap bitmap = BitmapFactory.decodeByteArray(decoded, 0, decoded.length);

                String name = (filename != null && !filename.isEmpty()) ? filename : "Puter_" + System.currentTimeMillis() + ".jpg";
                ContentValues values = new ContentValues();
                values.put(MediaStore.Images.Media.DISPLAY_NAME, name);
                values.put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg");
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    values.put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/PuterAI");
                }

                Uri uri = context.getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);
                if (uri != null) {
                    try (OutputStream out = context.getContentResolver().openOutputStream(uri)) {
                        bitmap.compress(Bitmap.CompressFormat.JPEG, 95, out);
                    }
                    mainHandler.post(() -> Toast.makeText(context, "Image saved to Gallery", Toast.LENGTH_SHORT).show());
                }
            } catch (Exception e) {
                mainHandler.post(() -> Toast.makeText(context, "Save failed: " + e.getMessage(), Toast.LENGTH_SHORT).show());
            }
        }).start();
    }
}