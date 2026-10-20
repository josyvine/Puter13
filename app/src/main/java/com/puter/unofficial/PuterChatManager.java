package com.puter.unofficial;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.puter.unofficial.models.ChatMessage;
import com.puter.unofficial.models.GenerationConfig;
import com.puter.unofficial.models.StagedFile;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.List;
import java.util.concurrent.TimeUnit;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;

/**
 * Pure Java OkHttp client for Puter AI chat completions and SSE streaming over HTTP.
 * Fully replaces the in-browser puter.ai.chat(...) JavaScript calls with native networking.
 */
public class PuterChatManager {
    private static final String TAG = "PuterChatManager";
    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");
    private static final String PUTER_API_URL = "https://api.puter.com/drivers/call";
    private static final String PUTER_V2_CHAT_URL = "https://api.puter.com/v2/ai/chat";

    private final Context context;
    private final OkHttpClient client;
    private final Handler mainHandler;
    private Call activeCall;

    public interface PuterChatListener {
        void onChunk(String chunkText);
        void onImageReceived(String imageUrlOrBase64);
        void onComplete(String fullText);
        void onError(String errorMessage);
    }

    public interface PuterLiveModelsListener {
        void onModelsFetched(String rawJson);
        void onError(String errorMessage);
    }

    public PuterChatManager(Context context) {
        this.context = context.getApplicationContext();
        this.client = new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .build();
        this.mainHandler = new Handler(Looper.getMainLooper());
    }

    public void stopCurrentGeneration() {
        if (activeCall != null && !activeCall.isCanceled()) {
            activeCall.cancel();
            activeCall = null;
        }
    }

    public void fetchLiveModels(String authToken, PuterLiveModelsListener listener) {
        new Thread(() -> {
            try {
                JSONObject rootPayload = new JSONObject();
                rootPayload.put("interface", "puter-chat-completion");
                rootPayload.put("driver", "ai");
                rootPayload.put("test_mode", false);
                rootPayload.put("method", "list-models");
                rootPayload.put("args", new JSONObject());

                Request.Builder reqBuilder = new Request.Builder()
                        .url(PUTER_API_URL)
                        .post(RequestBody.create(rootPayload.toString(), JSON))
                        .addHeader("Accept", "application/json");

                if (authToken != null && !authToken.trim().isEmpty()) {
                    reqBuilder.addHeader("Authorization", "Bearer " + authToken.trim());
                }

                Response resp = client.newCall(reqBuilder.build()).execute();
                if (resp.isSuccessful() && resp.body() != null) {
                    String bodyStr = resp.body().string();
                    mainHandler.post(() -> listener.onModelsFetched(bodyStr));
                    return;
                }

                // Fallback attempt to v2 endpoint
                Request.Builder fallbackBuilder = new Request.Builder()
                        .url("https://api.puter.com/v2/ai/models")
                        .get()
                        .addHeader("Accept", "application/json");
                if (authToken != null && !authToken.trim().isEmpty()) {
                    fallbackBuilder.addHeader("Authorization", "Bearer " + authToken.trim());
                }

                Response fallbackResp = client.newCall(fallbackBuilder.build()).execute();
                if (fallbackResp.isSuccessful() && fallbackResp.body() != null) {
                    String fallbackBody = fallbackResp.body().string();
                    mainHandler.post(() -> listener.onModelsFetched(fallbackBody));
                } else {
                    String err = (resp.body() != null) ? resp.body().string() : "HTTP " + resp.code();
                    mainHandler.post(() -> listener.onError(err));
                }
            } catch (Exception e) {
                mainHandler.post(() -> listener.onError(e.getMessage()));
            }
        }).start();
    }

    public void sendChatCompletion(List<ChatMessage> history, String userPrompt, List<StagedFile> attachments,
                                   String modelId, GenerationConfig config, PuterChatListener listener) {
        stopCurrentGeneration();

        new Thread(() -> {
            try {
                String authToken = AuthManager.getInstance(context).getAuthToken();
                boolean isImageGen = isImageModel(modelId);

                JSONArray messagesArray = new JSONArray();

                // Add history
                if (history != null) {
                    for (ChatMessage msg : history) {
                        JSONObject m = new JSONObject();
                        m.put("role", msg.getRole().equalsIgnoreCase(ChatMessage.ROLE_USER) ? "user" : "assistant");
                        m.put("content", msg.getText());
                        messagesArray.put(m);
                    }
                }

                // Add current user message with staged files if any
                JSONObject currentMsg = new JSONObject();
                currentMsg.put("role", "user");

                if (attachments != null && !attachments.isEmpty()) {
                    JSONArray contentBlocks = new JSONArray();
                    JSONObject textBlock = new JSONObject();
                    textBlock.put("type", "text");
                    textBlock.put("text", userPrompt);
                    contentBlocks.put(textBlock);

                    for (StagedFile file : attachments) {
                        JSONObject imgBlock = new JSONObject();
                        imgBlock.put("type", "image_url");
                        JSONObject urlObj = new JSONObject();
                        urlObj.put("url", file.getBase64DataUri());
                        imgBlock.put("image_url", urlObj);
                        contentBlocks.put(imgBlock);
                    }
                    currentMsg.put("content", contentBlocks);
                } else {
                    currentMsg.put("content", userPrompt);
                }
                messagesArray.put(currentMsg);

                // Build Driver Call Payload
                JSONObject rootPayload = new JSONObject();
                rootPayload.put("interface", "puter-chat-completion");
                rootPayload.put("driver", isImageGen ? "txt2img" : "ai-chat");
                rootPayload.put("test_mode", false);
                rootPayload.put("method", "complete");

                JSONObject argsObj = new JSONObject();
                argsObj.put("messages", messagesArray);
                argsObj.put("model", modelId);
                argsObj.put("stream", !isImageGen);

                if (config != null) {
                    if (config.getSystemInstructions() != null && !config.getSystemInstructions().isEmpty()) {
                        argsObj.put("system_message", config.getSystemInstructions());
                    }
                    if (config.getTemperature() > 0) {
                        argsObj.put("temperature", (double) config.getTemperature());
                    }
                    if (isImageGen && config.getAspectRatio() != null) {
                        argsObj.put("aspect_ratio", config.getAspectRatio());
                    }
                }
                rootPayload.put("args", argsObj);

                Request.Builder reqBuilder = new Request.Builder()
                        .url(PUTER_API_URL)
                        .post(RequestBody.create(rootPayload.toString(), JSON))
                        .addHeader("Accept", !isImageGen ? "text/event-stream, application/json" : "application/json");

                if (authToken != null && !authToken.trim().isEmpty()) {
                    reqBuilder.addHeader("Authorization", "Bearer " + authToken);
                }

                Request request = reqBuilder.build();
                activeCall = client.newCall(request);

                activeCall.enqueue(new Callback() {
                    @Override
                    public void onFailure(Call call, java.io.IOException e) {
                        if (call.isCanceled()) return;
                        postError(listener, "Network error: " + e.getMessage());
                    }

                    @Override
                    public void onResponse(Call call, Response response) {
                        if (!response.isSuccessful()) {
                            // Try fallback to v2 chat endpoint if drivers/call returned 404/400
                            executeV2Fallback(messagesArray, modelId, config, authToken, isImageGen, listener);
                            return;
                        }

                        ResponseBody body = response.body();
                        if (body == null) {
                            postError(listener, "Empty server response");
                            return;
                        }

                        if (isImageGen) {
                            try {
                                String respStr = body.string();
                                JSONObject json = new JSONObject(respStr);
                                String imgResult = "";
                                if (json.has("result")) {
                                    Object res = json.get("result");
                                    if (res instanceof JSONObject) {
                                        JSONObject rObj = (JSONObject) res;
                                        imgResult = rObj.optString("image_url", rObj.optString("image", ""));
                                    } else {
                                        imgResult = res.toString();
                                    }
                                } else if (json.has("image_url")) {
                                    imgResult = json.getString("image_url");
                                } else if (json.has("image")) {
                                    imgResult = json.getString("image");
                                }

                                if (!imgResult.isEmpty()) {
                                    final String finalImg = imgResult;
                                    mainHandler.post(() -> listener.onImageReceived(finalImg));
                                } else {
                                    postError(listener, "Could not extract image from response: " + respStr);
                                }
                            } catch (Exception e) {
                                postError(listener, "Failed to parse image response: " + e.getMessage());
                            }
                        } else {
                            // Stream SSE chunks
                            try {
                                BufferedReader reader = new BufferedReader(new InputStreamReader(body.byteStream()));
                                String line;
                                StringBuilder fullAccumulator = new StringBuilder();

                                while ((line = reader.readLine()) != null) {
                                    if (line.startsWith("data:")) {
                                        String data = line.substring(5).trim();
                                        if (data.equals("[DONE]")) break;

                                        String deltaText = extractDeltaText(data);
                                        if (deltaText != null && !deltaText.isEmpty()) {
                                            fullAccumulator.append(deltaText);
                                            mainHandler.post(() -> listener.onChunk(deltaText));
                                        }
                                    }
                                }

                                mainHandler.post(() -> listener.onComplete(fullAccumulator.toString()));
                            } catch (Exception e) {
                                if (!call.isCanceled()) {
                                    postError(listener, "Stream reading interrupted: " + e.getMessage());
                                }
                            }
                        }
                    }
                });

            } catch (Exception e) {
                postError(listener, "Chat preparation failed: " + e.getMessage());
            }
        }).start();
    }

    private void executeV2Fallback(JSONArray messages, String modelId, GenerationConfig config,
                                   String authToken, boolean isImageGen, PuterChatListener listener) {
        try {
            JSONObject payload = new JSONObject();
            payload.put("messages", messages);
            payload.put("model", modelId);
            payload.put("stream", !isImageGen);
            if (config != null && config.getSystemInstructions() != null) {
                payload.put("system_message", config.getSystemInstructions());
            }

            Request.Builder b = new Request.Builder()
                    .url(PUTER_V2_CHAT_URL)
                    .post(RequestBody.create(payload.toString(), JSON));

            if (authToken != null && !authToken.isEmpty()) {
                b.addHeader("Authorization", "Bearer " + authToken);
            }

            activeCall = client.newCall(b.build());
            activeCall.enqueue(new Callback() {
                @Override
                public void onFailure(Call call, java.io.IOException e) {
                    if (!call.isCanceled()) postError(listener, e.getMessage());
                }

                @Override
                public void onResponse(Call call, Response response) {
                    try {
                        ResponseBody body = response.body();
                        if (body == null) {
                            postError(listener, "Null response from Puter V2");
                            return;
                        }
                        BufferedReader reader = new BufferedReader(new InputStreamReader(body.byteStream()));
                        String line;
                        StringBuilder full = new StringBuilder();
                        while ((line = reader.readLine()) != null) {
                            if (line.startsWith("data:")) {
                                String d = line.substring(5).trim();
                                if ("[DONE]".equals(d)) break;
                                String text = extractDeltaText(d);
                                if (text != null && !text.isEmpty()) {
                                    full.append(text);
                                    mainHandler.post(() -> listener.onChunk(text));
                                }
                            }
                        }
                        mainHandler.post(() -> listener.onComplete(full.toString()));
                    } catch (Exception e) {
                        postError(listener, e.getMessage());
                    }
                }
            });
        } catch (Exception ex) {
            postError(listener, ex.getMessage());
        }
    }

    private String extractDeltaText(String jsonStr) {
        try {
            JSONObject obj = new JSONObject(jsonStr);
            if (obj.has("text")) return obj.getString("text");
            if (obj.has("delta")) {
                JSONObject delta = obj.getJSONObject("delta");
                if (delta.has("content")) return delta.getString("content");
                if (delta.has("text")) return delta.getString("text");
            }
            if (obj.has("choices")) {
                JSONArray choices = obj.getJSONArray("choices");
                if (choices.length() > 0) {
                    JSONObject c = choices.getJSONObject(0);
                    if (c.has("delta") && c.getJSONObject("delta").has("content")) {
                        return c.getJSONObject("delta").getString("content");
                    }
                    if (c.has("text")) return c.getString("text");
                }
            }
            if (obj.has("message") && obj.getJSONObject("message").has("content")) {
                return obj.getJSONObject("message").getString("content");
            }
        } catch (Exception ignored) {}
        return null;
    }

    public static boolean isImageModel(String modelId) {
        if (modelId == null) return false;
        String m = modelId.toLowerCase();
        return m.contains("dall-e") || m.contains("flux") || m.contains("imagen") || m.contains("midjourney") || m.contains("txt2img");
    }

    private void postError(PuterChatListener listener, String err) {
        mainHandler.post(() -> listener.onError(err));
    }
}
