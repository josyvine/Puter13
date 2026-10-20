package com.puter.unofficial;

import android.Manifest;
import android.app.Dialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Vibrator;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.speech.tts.TextToSpeech;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Base64;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.webkit.CookieManager;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;
import androidx.coordinatorlayout.widget.CoordinatorLayout;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.puter.unofficial.adapters.ChatAdapter;
import com.puter.unofficial.adapters.ModelAccordionAdapter;
import com.puter.unofficial.adapters.StagedAttachmentsAdapter;
import com.puter.unofficial.models.AIModelDefinition;
import com.puter.unofficial.models.ChatMessage;
import com.puter.unofficial.models.ChatSession;
import com.puter.unofficial.models.GroundingSource;
import com.puter.unofficial.models.StagedFile;
import com.puter.unofficial.FileUtils;
import com.puter.unofficial.ImageUtils;
import com.puter.unofficial.NativeMarkdownParser;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 100% Pure Native Android Java Activity for Puter Unofficial.
 * Directly adopts the exact UI, components, interactions, and state logic of index.html.
 */
public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MainActivity";

    // Native Views from activity_chat_native.xml
    private CoordinatorLayout chatRootLayout;
    private TextView tvAppHeaderTitle;
    private ImageView btnHeaderMenu;
    private LinearLayout settingsBar;
    private LinearLayout modelSelectTrigger;
    private TextView selectedModelText;
    private ImageView ivTriggerChevron;
    private LinearLayout aspectRatioBar;
    private Spinner spnAspectRatio;
    private RecyclerView rvChatTimeline;
    private LinearLayout ecgLoaderContainer;
    private ImageView ivEcgIcon;
    private TextView tvEcgStatus;
    private ImageButton btnStopGeneration;
    private LinearLayout chatControlsContainer;
    private RecyclerView rvStagedAttachments;
    private ImageView btnAttach;
    private EditText etMessageInput;
    private ImageView btnVoiceInput;
    private FrameLayout btnVoiceAgent;
    private ImageView voiceAgentIcon;
    private TextView orbitStar1, orbitStar2, orbitStar3;
    private ImageButton btnSendMessage;

    // Floating Accordion and Dropdown Cards
    private CardView accordionMenuCard;
    private RecyclerView rvAccordionModels;
    private ModelAccordionAdapter modelAccordionAdapter;

    private CardView puterDropdownMenuCard;
    private TextView menuAuthOption;
    private TextView menuModelSourceToggle;
    private TextView menuViewHistory;
    private TextView menuNewSession;
    private TextView menuToggleSettings;
    private TextView menuHelp;
    private TextView menuSettingsHub;
    private TextView menuBrowseWebsite;
    private TextView menuTradersZone;
    private EditText etMenuReplyLanguage;
    private CheckBox cbMenuUrlContext;
    private TextView tvMenuLastTokens;
    private TextView tvMenuLastCost;
    private TextView tvMenuGlobalTokens;
    private TextView tvMenuGlobalCost;
    private LinearLayout llDynamicSessionsContainer;
    private LinearLayout llDynamicScrapedContainer;

    // Context Menu Card
    private CardView customContextMenuCard;
    private TextView ctxCancelGen;
    private TextView ctxCopyChat;
    private TextView ctxReadAloud;
    private TextView ctxDeleteChat;
    private TextView ctxRefreshRelay;
    private TextView ctxToggleMode;
    private TextView ctxToggleProvider;
    private TextView ctxEndSession;
    private ChatMessage contextTargetMessage;

    // Headless WebView & Live Voice Overlay HUD
    private WebView headlessAudioWebView;

    private TextView tvLiveTranscriptTicker;
    private TextView tvLiveVoiceStatus;
    private View viewOrbCore;
    private View viewOrbGlow;

    // Adapters & Managers
    private ChatAdapter chatAdapter;
    private StagedAttachmentsAdapter stagedAttachmentsAdapter;
    private ChatSessionManager sessionManager;
    private PuterChatManager puterChatManager;
    private GeminiService geminiService;
    private HeadlessAudioBridge headlessAudioBridge;
    private WebAppInterface webAppInterface;
    private SharedPreferences prefs;

    // State Variables
    private ChatSession currentSession;
    private String activeModelId = AppConstants.DEFAULT_MODEL;
    private String activeProvider = "Puter";
    private boolean isLiveMode = false;
    private boolean isAutoMode = false;
    private boolean isGenerating = false;
    private TextToSpeech textToSpeech;
    private SpeechRecognizer speechRecognizer;
    private Vibrator vibrator;
    private final List<AIModelDefinition> allModelsList = new ArrayList<>();
    private final Map<String, List<AIModelDefinition>> modelsByProvider = new HashMap<>();

    // Activity Result Launchers
    private ActivityResultLauncher<String> galleryLauncher;
    private ActivityResultLauncher<String> documentLauncher;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_chat_native);

        prefs = getSharedPreferences(AppConstants.PREF_NAME, MODE_PRIVATE);
        sessionManager = ChatSessionManager.getInstance(this);
        puterChatManager = new PuterChatManager(this);
        geminiService = new GeminiService(this);
        vibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);

        activeProvider = prefs.getString(AppConstants.KEY_ACTIVE_PROVIDER, "Puter");
        isLiveMode = prefs.getBoolean(AppConstants.KEY_USE_LIVE_MODELS, false);
        isAutoMode = prefs.getBoolean(AppConstants.KEY_AUTO_MODE, false);

        NativeMarkdownParser.init(this);

        initViews();
        setupHeadlessAudioWebView();
        setupTTS();
        setupResultLaunchers();
        loadModels();
        setupChatTimeline();
        setupStagedAttachments();
        setupInputDock();
        setupHeaderAndDropdownActions();
        loadInitialSession();

        checkAndRequestPermissions();
    }

    private void initViews() {
        chatRootLayout = findViewById(R.id.chatRootLayout);
        tvAppHeaderTitle = findViewById(R.id.tvAppHeaderTitle);
        btnHeaderMenu = findViewById(R.id.btnHeaderMenu);
        settingsBar = findViewById(R.id.settingsBar);
        modelSelectTrigger = findViewById(R.id.modelSelectTrigger);
        selectedModelText = findViewById(R.id.selectedModelText);
        ivTriggerChevron = findViewById(R.id.ivTriggerChevron);
        aspectRatioBar = findViewById(R.id.aspectRatioContainer);
        spnAspectRatio = findViewById(R.id.spnAspectRatio);
        rvChatTimeline = findViewById(R.id.rvChatTimeline);
        ecgLoaderContainer = findViewById(R.id.ecgLoader);
        ivEcgIcon = findViewById(R.id.ivEcgIcon);
        tvEcgStatus = findViewById(R.id.loaderText);
        btnStopGeneration = findViewById(R.id.btnStopGeneration);
        chatControlsContainer = findViewById(R.id.chatControlsContainer);
        rvStagedAttachments = findViewById(R.id.thumbnailArea);
        btnAttach = findViewById(R.id.btnUpload);
        etMessageInput = findViewById(R.id.puterChatInput);
        btnVoiceInput = findViewById(R.id.btnMic);
        btnVoiceAgent = findViewById(R.id.flVoiceAgentBtn);
        voiceAgentIcon = findViewById(R.id.voiceAgentIcon);
        orbitStar1 = findViewById(R.id.orbitStar1);
        orbitStar2 = findViewById(R.id.orbitStar2);
        orbitStar3 = findViewById(R.id.orbitStar3);
        btnSendMessage = findViewById(R.id.btnSend);

        // Accordion Card
        accordionMenuCard = findViewById(R.id.accordionMenuCard);
        rvAccordionModels = findViewById(R.id.rvAccordionModels);

        // Dropdown Menu Card
        puterDropdownMenuCard = findViewById(R.id.puterDropdownMenuCard);
        menuAuthOption = findViewById(R.id.menuAuthOption);
        menuModelSourceToggle = findViewById(R.id.menuModelSourceToggle);
        menuViewHistory = findViewById(R.id.menuViewHistory);
        menuNewSession = findViewById(R.id.menuNewSession);
        menuToggleSettings = findViewById(R.id.menuToggleSettings);
        menuHelp = findViewById(R.id.menuHelp);
        menuSettingsHub = findViewById(R.id.menuSettingsHub);
        menuBrowseWebsite = findViewById(R.id.menuBrowseWebsite);
        menuTradersZone = findViewById(R.id.menuTradersZone);
        etMenuReplyLanguage = findViewById(R.id.etMenuReplyLanguage);
        cbMenuUrlContext = findViewById(R.id.cbMenuUrlContext);
        tvMenuLastTokens = findViewById(R.id.tvMenuLastTokens);
        tvMenuLastCost = findViewById(R.id.tvMenuLastCost);
        tvMenuGlobalTokens = findViewById(R.id.tvMenuGlobalTokens);
        tvMenuGlobalCost = findViewById(R.id.tvMenuGlobalCost);
        llDynamicSessionsContainer = findViewById(R.id.llDynamicSessionsContainer);
        llDynamicScrapedContainer = findViewById(R.id.llDynamicScrapedContainer);

        // Context Menu Card
        customContextMenuCard = findViewById(R.id.customContextMenuCard);
        ctxCancelGen = findViewById(R.id.ctxCancelGen);
        ctxCopyChat = findViewById(R.id.ctxCopyChat);
        ctxReadAloud = findViewById(R.id.ctxReadAloud);
        ctxDeleteChat = findViewById(R.id.ctxDeleteChat);
        ctxRefreshRelay = findViewById(R.id.ctxRefreshRelay);
        ctxToggleMode = findViewById(R.id.ctxToggleMode);
        ctxToggleProvider = findViewById(R.id.ctxToggleProvider);
        ctxEndSession = findViewById(R.id.ctxEndSession);

        // Headless WebView
        headlessAudioWebView = findViewById(R.id.headlessAudioWebView);


        setupAspectRatioSpinner();
    }

    private void setupAspectRatioSpinner() {
        if (spnAspectRatio == null) return;
        String[] ratios = new String[]{
                "1:1 Square (Profile/Logo)",
                "16:9 Landscape (YouTube/Wallpapers)",
                "9:16 Portrait (Reels/Shorts)",
                "4:3 Classic",
                "3:4 Portrait",
                "21:9 Cinematic"
        };
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, ratios);
        spnAspectRatio.setAdapter(adapter);
    }

    private void setupHeadlessAudioWebView() {
        if (headlessAudioWebView == null) return;
        webAppInterface = new WebAppInterface(this);
        webAppInterface.setMainActivity(this);
        headlessAudioBridge = new HeadlessAudioBridge(this, headlessAudioWebView);
        webAppInterface.setAudioBridge(headlessAudioBridge);
        headlessAudioWebView.getSettings().setJavaScriptEnabled(true);
        headlessAudioWebView.getSettings().setDomStorageEnabled(true);
        headlessAudioWebView.addJavascriptInterface(webAppInterface, AppConstants.JS_BRIDGE_NAME);

        headlessAudioBridge.setEventListener(new HeadlessAudioBridge.LiveAudioEventListener() {
            @Override
            public void onSessionConnected() {
                runOnUiThread(() -> {
                    if (tvLiveVoiceStatus != null) tvLiveVoiceStatus.setText(R.string.voice_status_listening);
                    toggleStarAnimation(true);
                });
            }

            @Override
            public void onTranscriptReceived(String text) {
                runOnUiThread(() -> {
                    if (tvLiveTranscriptTicker != null) tvLiveTranscriptTicker.setText(text);
                });
            }

            @Override
            public void onAudioChunkReceived(String base64Pcm) {}

            @Override
            public void onCommentaryFinished() {
                runOnUiThread(() -> {
                    if (tvLiveVoiceStatus != null) tvLiveVoiceStatus.setText(R.string.voice_status_speaking);
                    toggleStarAnimation(false);
                });
            }

            @Override
            public void onTargetCoordinatesReceived(String targetId, float left, float top, float right, float bottom) {
                runOnUiThread(() -> {
                    Toast.makeText(MainActivity.this, "Target: " + targetId, Toast.LENGTH_SHORT).show();
                });
            }

            @Override
            public void onError(String errorMessage) {
                runOnUiThread(() -> {
                    if (tvLiveVoiceStatus != null) tvLiveVoiceStatus.setText(errorMessage);
                    toggleStarAnimation(false);
                });
            }
        });
        headlessAudioWebView.loadUrl(AppConstants.HEADLESS_AUDIO_URL);
    }

    public String getActiveModelId() {
        return activeModelId;
    }

    public void onLiveSessionConnected() {
        if (tvLiveVoiceStatus != null) tvLiveVoiceStatus.setText(R.string.voice_status_listening);
        toggleStarAnimation(true);
    }

    public void onLiveTranscriptReceived(String text) {
        if (tvLiveTranscriptTicker != null) tvLiveTranscriptTicker.setText(text);
    }

    public void onLiveSessionFinished() {
        if (tvLiveVoiceStatus != null) tvLiveVoiceStatus.setText(R.string.voice_status_speaking);
        toggleStarAnimation(false);
    }

    public void onLiveError(String err) {
        if (tvLiveVoiceStatus != null) tvLiveVoiceStatus.setText(err);
        toggleStarAnimation(false);
    }

    private void setupTTS() {
        textToSpeech = new TextToSpeech(this, status -> {
            if (status == TextToSpeech.SUCCESS) {
                String targetLang = prefs.getString(AppConstants.KEY_TARGET_LANGUAGE, "");
                if (!targetLang.isEmpty()) {
                    setTtsLanguage(targetLang);
                } else {
                    textToSpeech.setLanguage(Locale.US);
                }
            }
        });
    }

    private void setTtsLanguage(String languageName) {
        if (textToSpeech == null || languageName == null) return;
        Locale found = null;
        for (Locale loc : Locale.getAvailableLocales()) {
            if (loc.getDisplayLanguage().equalsIgnoreCase(languageName) ||
                    loc.getLanguage().equalsIgnoreCase(languageName)) {
                found = loc;
                break;
            }
        }
        if (found != null) {
            textToSpeech.setLanguage(found);
        } else {
            textToSpeech.setLanguage(Locale.US);
        }
    }

    private void setupResultLaunchers() {
        galleryLauncher = registerForActivityResult(new ActivityResultContracts.GetContent(), uri -> {
            if (uri != null) stageFileFromUri(uri);
        });

        documentLauncher = registerForActivityResult(new ActivityResultContracts.GetContent(), uri -> {
            if (uri != null) stageFileFromUri(uri);
        });
    }

    private void stageFileFromUri(Uri uri) {
        try {
            String name = FileUtils.getFileName(this, uri);
            String mime = FileUtils.getMimeType(this, uri);
            String dataUri = FileUtils.fileToDataUri(this, uri);
            Bitmap thumb = null;
            if (mime != null && mime.startsWith("image/")) {
                thumb = ImageUtils.getThumbnail(this, uri, 120);
            }
            StagedFile file = new StagedFile(uri, name, mime, 0, dataUri, thumb);
            stagedAttachmentsAdapter.addAttachment(file);
            rvStagedAttachments.setVisibility(View.VISIBLE);
            updateSendButtonState();
        } catch (Exception e) {
            Toast.makeText(this, "Failed to attach: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void setupChatTimeline() {
        chatAdapter = new ChatAdapter(new ChatAdapter.OnChatMessageActionListener() {
            @Override
            public void onCopyMessage(ChatMessage message) {
                copyText(message.getText());
            }

            @Override
            public void onSpeakMessage(ChatMessage message) {
                speakText(message.getText());
            }

            @Override
            public void onSpeakAssistantClicked(ChatMessage message, View speakContainer, TextView s1, TextView s2, TextView s3) {
                speakText(message.getText());
                toggleChatStarAnimation(true, s1, s2, s3);
                // Stop animation after speech finishes (or reasonable delay)
                new Handler(Looper.getMainLooper()).postDelayed(() -> toggleChatStarAnimation(false, s1, s2, s3), 3000);
            }

            @Override
            public void onRegenerateMessage(ChatMessage message) {
                regenerateTurn(message);
            }

            @Override
            public void onDeleteMessage(ChatMessage message) {
                chatAdapter.removeMessage(message);
                if (currentSession != null) {
                    currentSession.getMessages().remove(message);
                    sessionManager.saveSession(currentSession);
                }
            }

            @Override
            public void onEditMessage(ChatMessage message) {
                etMessageInput.setText(message.getText());
                etMessageInput.setSelection(etMessageInput.getText().length());
            }

            @Override
            public void onQuoteAndReattach(ChatMessage message) {
                quoteMessageIntoInput(message);
            }

            @Override
            public void onPreviewCode(String language, String code) {
                showCodePreviewFullscreen(code);
            }

            @Override
            public void onPreviewImage(String imageUrlOrBase64) {
                showImagePreviewViewer(imageUrlOrBase64);
            }

            @Override
            public void onLongPressMessage(ChatMessage message, View view) {
                showUniversalContextMenu(message, view);
            }
        });

        LinearLayoutManager lm = new LinearLayoutManager(this);
        lm.setStackFromEnd(true);
        rvChatTimeline.setLayoutManager(lm);
        rvChatTimeline.setAdapter(chatAdapter);
    }

    private void setupStagedAttachments() {
        stagedAttachmentsAdapter = new StagedAttachmentsAdapter((position, file) -> {
            stagedAttachmentsAdapter.removeAttachment(position);
            if (stagedAttachmentsAdapter.getItemCount() == 0) {
                rvStagedAttachments.setVisibility(View.GONE);
            }
            updateSendButtonState();
        });
        rvStagedAttachments.setLayoutManager(new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false));
        rvStagedAttachments.setAdapter(stagedAttachmentsAdapter);
    }

    private void setupInputDock() {
        etMessageInput.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                updateSendButtonState();
            }
            @Override public void afterTextChanged(Editable s) {}
        });

        btnAttach.setOnClickListener(v -> showAttachmentPickerDialog());

        btnSendMessage.setOnClickListener(v -> {
            if (isGenerating) {
                stopCurrentGeneration();
            } else {
                dispatchSendMessage();
            }
        });

        btnStopGeneration.setOnClickListener(v -> stopCurrentGeneration());

        btnVoiceInput.setOnClickListener(v -> startSpeechToText());

        btnVoiceAgent.setOnClickListener(v -> launchLiveVoiceAgent());
    }

    private void setupHeaderAndDropdownActions() {
        btnHeaderMenu.setOnClickListener(v -> {
            if (puterDropdownMenuCard.getVisibility() == View.VISIBLE) {
                puterDropdownMenuCard.setVisibility(View.GONE);
            } else {
                refreshDropdownUI();
                puterDropdownMenuCard.setVisibility(View.VISIBLE);
                if (accordionMenuCard != null) accordionMenuCard.setVisibility(View.GONE);
            }
        });

        modelSelectTrigger.setOnClickListener(v -> {
            if (accordionMenuCard.getVisibility() == View.VISIBLE) {
                accordionMenuCard.setVisibility(View.GONE);
                ivTriggerChevron.setRotation(0);
            } else {
                accordionMenuCard.setVisibility(View.VISIBLE);
                ivTriggerChevron.setRotation(180);
                if (puterDropdownMenuCard != null) puterDropdownMenuCard.setVisibility(View.GONE);
            }
        });

        menuAuthOption.setOnClickListener(v -> {
            toggleAuthMirror();
            puterDropdownMenuCard.setVisibility(View.GONE);
        });

        menuModelSourceToggle.setOnClickListener(v -> {
            toggleModelSource();
            puterDropdownMenuCard.setVisibility(View.GONE);
        });

        menuViewHistory.setOnClickListener(v -> {
            puterDropdownMenuCard.setVisibility(View.GONE);
            showHistoryDialog();
        });

        menuNewSession.setOnClickListener(v -> {
            createNewSession();
            puterDropdownMenuCard.setVisibility(View.GONE);
        });

        menuToggleSettings.setOnClickListener(v -> {
            if (settingsBar.getVisibility() == View.VISIBLE) {
                settingsBar.setVisibility(View.GONE);
                menuToggleSettings.setText("Show Settings");
            } else {
                settingsBar.setVisibility(View.VISIBLE);
                menuToggleSettings.setText("Hide Settings");
            }
            puterDropdownMenuCard.setVisibility(View.GONE);
        });

        menuHelp.setOnClickListener(v -> {
            puterDropdownMenuCard.setVisibility(View.GONE);
            showHelpDialog();
        });

        menuSettingsHub.setOnClickListener(v -> {
            puterDropdownMenuCard.setVisibility(View.GONE);
            showSettingsHubDialog();
        });

        menuBrowseWebsite.setOnClickListener(v -> {
            puterDropdownMenuCard.setVisibility(View.GONE);
            showBrowserDialog();
        });

        menuTradersZone.setOnClickListener(v -> {
            puterDropdownMenuCard.setVisibility(View.GONE);
            showCryptoBotDialog();
        });

        etMenuReplyLanguage.setText(prefs.getString(AppConstants.KEY_TARGET_LANGUAGE, ""));
        etMenuReplyLanguage.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                prefs.edit().putString(AppConstants.KEY_TARGET_LANGUAGE, s.toString().trim()).apply();
                setTtsLanguage(s.toString().trim());
            }
            @Override public void afterTextChanged(Editable s) {}
        });

        cbMenuUrlContext.setChecked(prefs.getBoolean(AppConstants.KEY_URL_CONTEXT_ENABLED, false));
        cbMenuUrlContext.setOnCheckedChangeListener((btn, isChecked) -> {
            prefs.edit().putBoolean(AppConstants.KEY_URL_CONTEXT_ENABLED, isChecked).apply();
            Toast.makeText(this, isChecked ? "URL Context Enabled" : "URL Context Disabled", Toast.LENGTH_SHORT).show();
        });

        setupContextMenuActions();
    }

    private void setupContextMenuActions() {
        if (customContextMenuCard == null) return;
        ctxCancelGen.setOnClickListener(v -> {
            stopCurrentGeneration();
            customContextMenuCard.setVisibility(View.GONE);
        });
        ctxCopyChat.setOnClickListener(v -> {
            if (contextTargetMessage != null) copyText(contextTargetMessage.getText());
            customContextMenuCard.setVisibility(View.GONE);
        });
        ctxReadAloud.setOnClickListener(v -> {
            if (contextTargetMessage != null) speakText(contextTargetMessage.getText());
            customContextMenuCard.setVisibility(View.GONE);
        });
        ctxDeleteChat.setOnClickListener(v -> {
            if (contextTargetMessage != null) {
                chatAdapter.removeMessage(contextTargetMessage);
                if (currentSession != null) {
                    currentSession.getMessages().remove(contextTargetMessage);
                    sessionManager.saveSession(currentSession);
                }
            }
            customContextMenuCard.setVisibility(View.GONE);
        });
        ctxRefreshRelay.setOnClickListener(v -> {
            Toast.makeText(this, "Nostr Relay Refreshed", Toast.LENGTH_SHORT).show();
            customContextMenuCard.setVisibility(View.GONE);
        });
        ctxToggleMode.setOnClickListener(v -> {
            isAutoMode = !isAutoMode;
            prefs.edit().putBoolean(AppConstants.KEY_AUTO_MODE, isAutoMode).apply();
            Toast.makeText(this, isAutoMode ? "Auto Mode Enabled" : "Normal Mode Enabled", Toast.LENGTH_SHORT).show();
            customContextMenuCard.setVisibility(View.GONE);
        });
        ctxToggleProvider.setOnClickListener(v -> {
            activeProvider = "Gemini".equalsIgnoreCase(activeProvider) ? "Puter" : "Gemini";
            prefs.edit().putString(AppConstants.KEY_ACTIVE_PROVIDER, activeProvider).apply();
            Toast.makeText(this, "Switched active engine provider to: " + activeProvider, Toast.LENGTH_SHORT).show();
            loadModels();
            customContextMenuCard.setVisibility(View.GONE);
        });
        ctxEndSession.setOnClickListener(v -> {
            createNewSession();
            customContextMenuCard.setVisibility(View.GONE);
        });
    }

    private void refreshDropdownUI() {
        boolean loggedIn = AuthManager.getInstance(this).isLoggedIn();
        menuAuthOption.setText(loggedIn ? "Sign Out" : "Sign In");

        boolean isLiveMode = prefs.getBoolean(AppConstants.KEY_USE_LIVE_MODELS, false);
        menuModelSourceToggle.setText(isLiveMode ? "Use Local Models" : "Enable Live Models");

        long totalTokens = prefs.getLong(AppConstants.KEY_USAGE_TOTAL_TOKENS, 0);
        float totalCost = prefs.getFloat(AppConstants.KEY_USAGE_TOTAL_COST, 0.0f);
        tvMenuGlobalTokens.setText("Global Total: " + totalTokens + " tokens");
        tvMenuGlobalCost.setText(String.format(Locale.US, "Global Cost: $%.4f", totalCost));

        refreshDynamicSessionsInMenu();
    }

    private void refreshDynamicSessionsInMenu() {
        if (llDynamicSessionsContainer == null) return;
        llDynamicSessionsContainer.removeAllViews();
        List<ChatSession> sessions = sessionManager.getAllSessions();
        if (sessions == null || sessions.isEmpty()) {
            TextView tv = new TextView(this);
            tv.setText("No saved sessions");
            tv.setTextColor(Color.parseColor("#888888"));
            tv.setTextSize(12);
            tv.setPadding(32, 16, 32, 16);
            llDynamicSessionsContainer.addView(tv);
            return;
        }

        for (ChatSession s : sessions) {
            TextView tv = new TextView(this);
            tv.setText(s.getTitle());
            boolean isActive = currentSession != null && s.getId().equals(currentSession.getId());
            tv.setTextColor(isActive ? Color.parseColor("#1a73e8") : Color.parseColor("#333333"));
            tv.setTextSize(13);
            tv.setPadding(32, 20, 32, 20);
            tv.setBackgroundResource(android.R.drawable.list_selector_background);
            tv.setOnClickListener(v -> {
                loadSessionIntoUI(s);
                puterDropdownMenuCard.setVisibility(View.GONE);
            });
            tv.setOnLongClickListener(v -> {
                new AlertDialog.Builder(this)
                        .setTitle("Delete Session")
                        .setMessage("Delete session \"" + s.getTitle() + "\"?")
                        .setPositiveButton("Delete", (d, w) -> {
                            sessionManager.deleteSession(s.getId());
                            if (currentSession != null && currentSession.getId().equals(s.getId())) {
                                loadInitialSession();
                            } else {
                                refreshDynamicSessionsInMenu();
                            }
                        })
                        .setNegativeButton("Cancel", null)
                        .show();
                return true;
            });
            llDynamicSessionsContainer.addView(tv);
        }
    }

    private void updateSendButtonState() {
        boolean hasText = etMessageInput.getText().toString().trim().length() > 0;
        boolean hasAttachments = stagedAttachmentsAdapter.getItemCount() > 0;

        if (isGenerating) {
            btnSendMessage.setVisibility(View.VISIBLE);
            btnSendMessage.setImageResource(R.drawable.ic_stop);
            btnSendMessage.setBackgroundTintList(ContextCompat.getColorStateList(this, R.color.stop_red));
            btnVoiceInput.setVisibility(View.GONE);
            btnVoiceAgent.setVisibility(View.GONE);
        } else if (hasText || hasAttachments) {
            btnSendMessage.setVisibility(View.VISIBLE);
            btnSendMessage.setImageResource(R.drawable.ic_send);
            btnSendMessage.setBackgroundTintList(ContextCompat.getColorStateList(this, R.color.puter_blue));
            btnVoiceInput.setVisibility(View.GONE);
            btnVoiceAgent.setVisibility(View.GONE);
        } else {
            btnSendMessage.setVisibility(View.GONE);
            btnVoiceInput.setVisibility(View.VISIBLE);
            btnVoiceAgent.setVisibility(View.VISIBLE);
        }
    }

    private void dispatchSendMessage() {
        String prompt = etMessageInput.getText().toString().trim();
        List<StagedFile> attachments = new ArrayList<>(stagedAttachmentsAdapter.getItems());

        if (prompt.isEmpty() && attachments.isEmpty()) return;

        etMessageInput.setText("");
        stagedAttachmentsAdapter.clear();
        rvStagedAttachments.setVisibility(View.GONE);
        updateSendButtonState();

        // User message
        ChatMessage userMsg = new ChatMessage(
                "msg_user_" + System.currentTimeMillis(),
                ChatMessage.ROLE_USER,
                prompt,
                System.currentTimeMillis()
        );
        userMsg.setAttachments(attachments);
        chatAdapter.addMessage(userMsg);
        currentSession.addMessage(userMsg);
        sessionManager.autoUpdateTitleIfNeeded(currentSession, prompt);
        rvChatTimeline.smoothScrollToPosition(chatAdapter.getItemCount() - 1);

        // Pending bot message
        ChatMessage assistantMsg = new ChatMessage(
                "msg_bot_" + System.currentTimeMillis(),
                ChatMessage.ROLE_BOT,
                "",
                System.currentTimeMillis()
        );
        assistantMsg.setModelUsed(activeModelId);
        assistantMsg.setStreaming(true);
        chatAdapter.addMessage(assistantMsg);
        currentSession.addMessage(assistantMsg);

        // Check Reply Language directive
        String targetLang = prefs.getString(AppConstants.KEY_TARGET_LANGUAGE, "");
        String activeSystemMessage = prefs.getString(AppConstants.KEY_CUSTOM_INSTRUCTION, "");
        if (!targetLang.isEmpty()) {
            String systemDirective = "IMPORTANT:\nALWAYS respond ONLY in " + targetLang + ".\n\nNEVER use English.\n\nEven if user speaks English, translate internally and answer only in " + targetLang + ". Do not transliterate. Write strictly in the native script characters of the language.";
            activeSystemMessage = activeSystemMessage.isEmpty() ? systemDirective : activeSystemMessage + "\n\n" + systemDirective;
        }
        currentSession.getConfig().setSystemInstructions(activeSystemMessage);

        // Aspect ratio handling for Image Models
        boolean isImageGen = PuterChatManager.isImageModel(activeModelId);
        String ratio = "1:1";
        if (spnAspectRatio != null && spnAspectRatio.getSelectedItem() != null) {
            String selected = spnAspectRatio.getSelectedItem().toString();
            if (selected.contains("16:9")) ratio = "16:9";
            else if (selected.contains("9:16")) ratio = "9:16";
            else if (selected.contains("4:3")) ratio = "4:3";
            else if (selected.contains("3:4")) ratio = "3:4";
            else if (selected.contains("21:9")) ratio = "21:9";
        }
        currentSession.getConfig().setAspectRatio(ratio);

        String finalPrompt = prompt;
        if (isImageGen) {
            finalPrompt = prompt + ". Aspect ratio: " + ratio + " cinematic landscape.";
        }

        // ECG loader
        isGenerating = true;
        updateSendButtonState();
        tvEcgStatus.setText(isImageGen ? "AI is generating image..." : "AI is answering...");
        ecgLoaderContainer.setVisibility(View.VISIBLE);
        try {
            Animation pulseAnim = AnimationUtils.loadAnimation(this, R.anim.ecg_trace_animation);
            if (ivEcgIcon != null) ivEcgIcon.startAnimation(pulseAnim);
        } catch (Exception ignored) {}

        // Check URL Context
        boolean urlContextEnabled = prefs.getBoolean(AppConstants.KEY_URL_CONTEXT_ENABLED, false);
        final String effectivePrompt = finalPrompt;

        if (urlContextEnabled && prompt.matches(".*https?://\\S+.*")) {
            new Thread(() -> {
                String enriched = fetchUrlContextIfPresent(effectivePrompt);
                runOnUiThread(() -> routeGeneration(enriched, attachments, assistantMsg));
            }).start();
        } else {
            routeGeneration(effectivePrompt, attachments, assistantMsg);
        }
    }

    private void routeGeneration(String prompt, List<StagedFile> attachments, ChatMessage assistantMsg) {
        if ("Gemini".equalsIgnoreCase(activeProvider) || activeModelId.toLowerCase(Locale.US).contains("gemini")) {
            dispatchGeminiGeneration(prompt, attachments, assistantMsg);
        } else {
            dispatchPuterGeneration(prompt, attachments, assistantMsg);
        }
    }

    private String fetchUrlContextIfPresent(String text) {
        try {
            java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("(https?://[a-zA-Z0-9.-]+(?:/[^\\s]*)?)");
            java.util.regex.Matcher matcher = pattern.matcher(text);
            if (matcher.find()) {
                String foundUrl = matcher.group(1);
                okhttp3.Request req = new okhttp3.Request.Builder().url(foundUrl).get().build();
                okhttp3.OkHttpClient fastClient = new okhttp3.OkHttpClient.Builder()
                        .connectTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
                        .readTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
                        .build();
                okhttp3.Response resp = fastClient.newCall(req).execute();
                if (resp.isSuccessful() && resp.body() != null) {
                    String body = resp.body().string();
                    if (body.length() > 30000) body = body.substring(0, 30000) + "...[Truncated]";
                    return text + "\n\n[Web Page Context for " + foundUrl + "]:\n" + body;
                }
            }
        } catch (Exception ignored) {}
        return text;
    }

    private void dispatchPuterGeneration(String prompt, List<StagedFile> attachments, ChatMessage assistantMsg) {
        List<ChatMessage> history = new ArrayList<>(currentSession.getMessages());
        if (history.size() >= 2) {
            history = history.subList(0, history.size() - 2);
        }

        puterChatManager.sendChatCompletion(
                history, prompt, attachments, activeModelId, currentSession.getConfig(),
                new PuterChatManager.PuterChatListener() {
                    @Override
                    public void onChunk(String chunkText) {
                        runOnUiThread(() -> {
                            assistantMsg.setText(assistantMsg.getText() + chunkText);
                            chatAdapter.notifyItemChanged(chatAdapter.getItemCount() - 1);
                            rvChatTimeline.smoothScrollToPosition(chatAdapter.getItemCount() - 1);
                        });
                    }

                    @Override
                    public void onImageReceived(String imageUrlOrBase64) {
                        runOnUiThread(() -> {
                            assistantMsg.setGeneratedImageUrl(imageUrlOrBase64);
                            chatAdapter.notifyItemChanged(chatAdapter.getItemCount() - 1);
                            finishGeneration();
                        });
                    }

                    @Override
                    public void onComplete(String fullText) {
                        runOnUiThread(() -> {
                            assistantMsg.setText(fullText);
                            assistantMsg.setStreaming(false);
                            chatAdapter.notifyItemChanged(chatAdapter.getItemCount() - 1);
                            finishGeneration();
                        });
                    }

                    @Override
                    public void onError(String errorMessage) {
                        runOnUiThread(() -> {
                            assistantMsg.setText("Error: " + errorMessage);
                            assistantMsg.setStreaming(false);
                            chatAdapter.notifyItemChanged(chatAdapter.getItemCount() - 1);
                            finishGeneration();
                        });
                    }
                }
        );
    }

    private void dispatchGeminiGeneration(String prompt, List<StagedFile> attachments, ChatMessage assistantMsg) {
        JSONObject payload = new JSONObject();
        try {
            JSONArray contents = new JSONArray();
            JSONObject userPart = new JSONObject();
            userPart.put("role", "user");
            JSONArray parts = new JSONArray();
            JSONObject textPart = new JSONObject();
            textPart.put("text", prompt);
            parts.put(textPart);

            // Multimodal image attachments
            if (attachments != null) {
                for (StagedFile file : attachments) {
                    if (file.getBase64DataUri() != null && file.getMimeType() != null && file.getMimeType().startsWith("image/")) {
                        String cleanB64 = file.getBase64DataUri().replaceFirst("^data:[^;]+;base64,", "");
                        JSONObject inlineData = new JSONObject();
                        inlineData.put("mimeType", file.getMimeType());
                        inlineData.put("data", cleanB64);
                        JSONObject partObj = new JSONObject();
                        partObj.put("inlineData", inlineData);
                        parts.put(partObj);
                    }
                }
            }

            userPart.put("parts", parts);
            contents.put(userPart);
            payload.put("contents", contents);

            // System instructions
            String sysInst = currentSession.getConfig().getSystemInstructions();
            if (sysInst != null && !sysInst.trim().isEmpty()) {
                JSONObject sysObj = new JSONObject();
                JSONArray sysParts = new JSONArray();
                JSONObject sysTextPart = new JSONObject();
                sysTextPart.put("text", sysInst);
                sysParts.put(sysTextPart);
                sysObj.put("parts", sysParts);
                payload.put("systemInstruction", sysObj);
            }

            if (currentSession.getConfig().isWebGroundingEnabled()) {
                JSONObject tools = new JSONObject();
                JSONObject googleSearch = new JSONObject();
                tools.put("googleSearch", googleSearch);
                JSONArray toolsArray = new JSONArray();
                toolsArray.put(tools);
                payload.put("tools", toolsArray);
            }
        } catch (Exception ignored) {}

        geminiService.executeCall(activeModelId, payload.toString(), true, new GeminiService.GeminiStreamListener() {
            @Override
            public void onChunk(String chunkText) {
                runOnUiThread(() -> {
                    assistantMsg.setText(assistantMsg.getText() + chunkText);
                    chatAdapter.notifyItemChanged(chatAdapter.getItemCount() - 1);
                    rvChatTimeline.smoothScrollToPosition(chatAdapter.getItemCount() - 1);
                });
            }

            @Override
            public void onComplete(String fullResponse) {
                runOnUiThread(() -> {
                    assistantMsg.setText(fullResponse);
                    assistantMsg.setStreaming(false);
                    chatAdapter.notifyItemChanged(chatAdapter.getItemCount() - 1);
                    finishGeneration();
                });
            }

            @Override
            public void onCitationsReceived(List<GroundingSource> citations) {
                runOnUiThread(() -> {
                    assistantMsg.setGroundingSources(citations);
                    chatAdapter.notifyItemChanged(chatAdapter.getItemCount() - 1);
                });
            }

            @Override
            public void onError(String errorMessage) {
                runOnUiThread(() -> {
                    assistantMsg.setText("Gemini Error: " + errorMessage);
                    assistantMsg.setStreaming(false);
                    chatAdapter.notifyItemChanged(chatAdapter.getItemCount() - 1);
                    finishGeneration();
                });
            }
        });
    }

    private void stopCurrentGeneration() {
        puterChatManager.stopCurrentGeneration();
        geminiService.cancelCurrentCall();
        finishGeneration();
    }

    private void finishGeneration() {
        isGenerating = false;
        if (ecgLoaderContainer != null) ecgLoaderContainer.setVisibility(View.GONE);
        if (ivEcgIcon != null) ivEcgIcon.clearAnimation();
        updateSendButtonState();
        if (currentSession != null) sessionManager.saveSession(currentSession);
        updateUsageStats();
    }

    private void regenerateTurn(ChatMessage botMessage) {
        int idx = chatAdapter.getMessages().indexOf(botMessage);
        if (idx > 0) {
            ChatMessage prevUserMsg = chatAdapter.getMessages().get(idx - 1);
            if (prevUserMsg.getRole().equalsIgnoreCase(ChatMessage.ROLE_USER)) {
                chatAdapter.removeMessage(botMessage);
                currentSession.getMessages().remove(botMessage);

                ChatMessage newBotMsg = new ChatMessage(
                        "msg_bot_" + System.currentTimeMillis(),
                        ChatMessage.ROLE_BOT,
                        "",
                        System.currentTimeMillis()
                );
                newBotMsg.setModelUsed(activeModelId);
                newBotMsg.setStreaming(true);
                chatAdapter.addMessage(newBotMsg);
                currentSession.addMessage(newBotMsg);

                isGenerating = true;
                updateSendButtonState();
                ecgLoaderContainer.setVisibility(View.VISIBLE);

                if ("Gemini".equalsIgnoreCase(activeProvider) || activeModelId.toLowerCase(Locale.US).contains("gemini")) {
                    dispatchGeminiGeneration(prevUserMsg.getText(), prevUserMsg.getAttachments(), newBotMsg);
                } else {
                    dispatchPuterGeneration(prevUserMsg.getText(), prevUserMsg.getAttachments(), newBotMsg);
                }
            }
        }
    }

    private void quoteMessageIntoInput(ChatMessage message) {
        String quoted = "> " + message.getText().replace("\n", "\n> ") + "\n\n";
        etMessageInput.setText(quoted);
        etMessageInput.setSelection(etMessageInput.getText().length());
    }

    private void loadInitialSession() {
        String currentId = sessionManager.getCurrentSessionId();
        if (currentId != null) {
            currentSession = sessionManager.loadSession(currentId);
        }
        if (currentSession == null) {
            currentSession = sessionManager.createNewSession(activeModelId);
        }
        activeModelId = currentSession.getSelectedModelId();
        selectedModelText.setText(activeModelId);
        chatAdapter.setMessages(currentSession.getMessages());
        updateAspectRatioBarVisibility();
    }

    private void createNewSession() {
        currentSession = sessionManager.createNewSession(activeModelId);
        activeModelId = currentSession.getSelectedModelId();
        selectedModelText.setText(activeModelId);
        chatAdapter.setMessages(currentSession.getMessages());
        updateAspectRatioBarVisibility();
        Toast.makeText(this, "New Session Started", Toast.LENGTH_SHORT).show();
    }

    private void loadSessionIntoUI(ChatSession session) {
        sessionManager.setCurrentSessionId(session.getId());
        currentSession = session;
        activeModelId = session.getSelectedModelId();
        selectedModelText.setText(activeModelId);
        chatAdapter.setMessages(session.getMessages());
        updateAspectRatioBarVisibility();
        rvChatTimeline.scrollToPosition(Math.max(0, chatAdapter.getItemCount() - 1));
    }

    // --- MODEL ACCORDION & TAXONOMY ---

    private void loadModels() {
        allModelsList.clear();
        modelsByProvider.clear();

        isLiveMode = prefs.getBoolean(AppConstants.KEY_USE_LIVE_MODELS, false);
        activeProvider = prefs.getString(AppConstants.KEY_ACTIVE_PROVIDER, "Puter");
        if (menuModelSourceToggle != null) {
            menuModelSourceToggle.setText(isLiveMode ? "Use Local Models" : "Enable Live Models");
        }

        if ("Gemini".equalsIgnoreCase(activeProvider)) {
            if (isLiveMode) {
                String cached = prefs.getString(AppConstants.KEY_GEMINI_CACHED_LIVE_MODELS, null);
                if (cached != null && !cached.trim().isEmpty() && !cached.equals("{}") && !cached.equals("[]")) {
                    parseGeminiModelsJson(cached);
                    setupAccordionAdapter();
                } else {
                    selectedModelText.setText("No Live Gemini Models Cached (Fetch in Settings)");
                    Toast.makeText(this, "No live models cached. Tap Fetch in Gemini Settings.", Toast.LENGTH_SHORT).show();
                    loadLocalGeminiModels();
                    setupAccordionAdapter();
                }
            } else {
                loadLocalGeminiModels();
                setupAccordionAdapter();
            }
        } else {
            // Puter Provider
            if (isLiveMode) {
                String cached = prefs.getString(AppConstants.KEY_PUTER_CACHED_LIVE_MODELS, null);
                if (cached != null && !cached.trim().isEmpty() && !cached.equals("{}") && !cached.equals("[]")) {
                    parsePuterModelsJson(cached);
                    setupAccordionAdapter();
                }

                if (AuthManager.getInstance(this).isLoggedIn()) {
                    selectedModelText.setText("Syncing live models...");
                    String token = AuthManager.getInstance(this).getAuthToken();
                    puterChatManager.fetchLiveModels(token, new PuterChatManager.PuterLiveModelsListener() {
                        @Override
                        public void onModelsFetched(String rawJson) {
                            runOnUiThread(() -> {
                                prefs.edit().putString(AppConstants.KEY_PUTER_CACHED_LIVE_MODELS, rawJson).apply();
                                parsePuterModelsJson(rawJson);
                                setupAccordionAdapter();
                                Toast.makeText(MainActivity.this, "Live Puter models updated!", Toast.LENGTH_SHORT).show();
                            });
                        }

                        @Override
                        public void onError(String errorMessage) {
                            runOnUiThread(() -> {
                                if (allModelsList.isEmpty()) {
                                    loadLocalPuterModels();
                                    setupAccordionAdapter();
                                }
                                Log.w(TAG, "Puter live models fetch error: " + errorMessage);
                            });
                        }
                    });
                } else {
                    if (allModelsList.isEmpty()) {
                        loadLocalPuterModels();
                        setupAccordionAdapter();
                    }
                    selectedModelText.setText("Sign in for Live Models");
                }
            } else {
                loadLocalPuterModels();
                setupAccordionAdapter();
            }
        }

        // Validate activeModelId
        boolean found = false;
        for (AIModelDefinition m : allModelsList) {
            if (m.getId().equalsIgnoreCase(activeModelId)) {
                found = true;
                selectedModelText.setText(m.getDisplayName());
                break;
            }
        }
        if (!found && !allModelsList.isEmpty()) {
            activeModelId = allModelsList.get(0).getId();
            selectedModelText.setText(allModelsList.get(0).getDisplayName());
            if (currentSession != null) {
                currentSession.setSelectedModelId(activeModelId);
                sessionManager.saveSession(currentSession);
            }
        }
        updateAspectRatioBarVisibility();
    }

    private void loadLocalGeminiModels() {
        try {
            InputStream is = getAssets().open(AppConstants.GEMINI_MODELS_JSON_FILE);
            int size = is.available();
            byte[] buffer = new byte[size];
            is.read(buffer);
            is.close();
            String jsonStr = new String(buffer, StandardCharsets.UTF_8);
            parseGeminiModelsJson(jsonStr);
        } catch (Exception e) {
            Log.e(TAG, "Failed to load gemini_models.json: " + e.getMessage());
        }
    }

    private void loadLocalPuterModels() {
        try {
            InputStream is = getAssets().open(AppConstants.MODELS_JSON_FILE);
            int size = is.available();
            byte[] buffer = new byte[size];
            is.read(buffer);
            is.close();
            String jsonStr = new String(buffer, StandardCharsets.UTF_8);
            parsePuterModelsJson(jsonStr);
        } catch (Exception e) {
            Log.e(TAG, "Failed to load models.json: " + e.getMessage());
        }
    }

    private void parseGeminiModelsJson(String jsonStr) {
        try {
            allModelsList.clear();
            modelsByProvider.clear();
            modelsByProvider.put("Google Gemini", new ArrayList<>());

            JSONArray arr = null;
            if (jsonStr.trim().startsWith("[")) {
                arr = new JSONArray(jsonStr);
            } else {
                JSONObject root = new JSONObject(jsonStr);
                if (root.has("models")) {
                    arr = root.getJSONArray("models");
                } else if (root.has("Google Gemini")) {
                    arr = root.getJSONArray("Google Gemini");
                } else {
                    Iterator<String> keys = root.keys();
                    while (keys.hasNext()) {
                        JSONArray a = root.optJSONArray(keys.next());
                        if (a != null) {
                            arr = a;
                            break;
                        }
                    }
                }
            }

            if (arr != null) {
                for (int i = 0; i < arr.length(); i++) {
                    JSONObject obj = arr.getJSONObject(i);
                    String rawId = obj.optString("id", obj.optString("name", "")).replace("models/", "");
                    if (rawId.isEmpty()) continue;
                    String name = obj.optString("displayName", obj.optString("name", rawId));
                    String desc = obj.optString("description", "").toLowerCase(Locale.US);
                    String idLower = rawId.toLowerCase(Locale.US);

                    List<String> cats = new ArrayList<>();
                    boolean isLiveAudio = idLower.contains("live-preview") || idLower.contains("native-audio") || (idLower.contains("exp") && idLower.contains("live"));
                    if (isLiveAudio) {
                        cats.add("live");
                        cats.add("audio");
                    } else if (idLower.contains("audio") || idLower.contains("tts") || idLower.contains("speech")) {
                        cats.add("audio");
                    }
                    if (idLower.contains("image") || idLower.contains("imagen")) {
                        cats.add("image_generation");
                    }
                    if (idLower.contains("flash") || idLower.contains("pro") || desc.contains("multimodal") || desc.contains("vision")) {
                        cats.add("vision");
                        cats.add("multimodal");
                    }
                    if (idLower.contains("thinking") || idLower.contains("reasoning") || desc.contains("thinking")) {
                        cats.add("reasoning");
                    }
                    if (obj.optJSONArray("supportedGenerationMethods") != null) {
                        JSONArray meth = obj.optJSONArray("supportedGenerationMethods");
                        for (int k = 0; k < meth.length(); k++) {
                            if ("generateContent".equals(meth.optString(k)) && (idLower.contains("flash") || idLower.contains("pro"))) {
                                cats.add("tool_calling");
                                break;
                            }
                        }
                    }
                    if (cats.isEmpty() || !cats.contains("image_generation")) {
                        cats.add("text_chat");
                    }

                    boolean isImg = cats.contains("image_generation");
                    boolean isVis = cats.contains("vision");
                    boolean isLiv = cats.contains("live");

                    AIModelDefinition model = new AIModelDefinition(rawId, name, "Google Gemini", "1000k", isVis, isImg, isLiv);
                    model.setCategories(cats);
                    allModelsList.add(model);
                    modelsByProvider.get("Google Gemini").add(model);
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error parsing Gemini models: " + e.getMessage());
        }
    }

    private void parsePuterModelsJson(String jsonStr) {
        try {
            allModelsList.clear();
            modelsByProvider.clear();

            if (jsonStr.trim().startsWith("[")) {
                JSONArray arr = new JSONArray(jsonStr);
                for (int i = 0; i < arr.length(); i++) {
                    addPuterModel(arr.getJSONObject(i), "Other");
                }
                return;
            }

            JSONObject root = new JSONObject(jsonStr);
            if (root.has("result") && root.optJSONArray("result") != null) {
                JSONArray arr = root.getJSONArray("result");
                for (int i = 0; i < arr.length(); i++) {
                    addPuterModel(arr.getJSONObject(i), "Other");
                }
                return;
            }

            if (root.has("models") && root.optJSONArray("models") != null) {
                JSONArray arr = root.getJSONArray("models");
                for (int i = 0; i < arr.length(); i++) {
                    addPuterModel(arr.getJSONObject(i), "Other");
                }
                return;
            }

            Iterator<String> keys = root.keys();
            while (keys.hasNext()) {
                String providerKey = keys.next();
                JSONArray arr = root.optJSONArray(providerKey);
                if (arr != null) {
                    String providerDisplay = capitalize(providerKey);
                    if (!modelsByProvider.containsKey(providerDisplay)) {
                        modelsByProvider.put(providerDisplay, new ArrayList<>());
                    }
                    for (int i = 0; i < arr.length(); i++) {
                        addPuterModel(arr.getJSONObject(i), providerDisplay);
                    }
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error parsing Puter models: " + e.getMessage());
        }
    }

    private void addPuterModel(JSONObject obj, String defaultProvider) {
        String id = obj.optString("id", obj.optString("name", ""));
        if (id.isEmpty()) return;
        String name = obj.optString("name", id);
        String provider = capitalize(obj.optString("provider", obj.optString("vendor", defaultProvider)));
        if (!modelsByProvider.containsKey(provider)) {
            modelsByProvider.put(provider, new ArrayList<>());
        }
        int ctx = obj.optInt("context", 128000);

        List<String> cats = inferPuterCategories(id, name, obj.optString("description", ""));
        boolean imgGen = cats.contains("image_generation");
        boolean vision = cats.contains("vision");

        AIModelDefinition m = new AIModelDefinition(id, name, provider, (ctx / 1000) + "k", vision, imgGen, false);
        m.setCategories(cats);
        allModelsList.add(m);
        modelsByProvider.get(provider).add(m);
    }

    private List<String> inferPuterCategories(String id, String name, String desc) {
        String combined = (id + " " + name + " " + desc).toLowerCase(Locale.US);
        List<String> cats = new ArrayList<>();
        if (combined.contains("image") || combined.contains("dall-e") || combined.contains("flux") ||
                combined.contains("stable-diffusion") || combined.contains("imagen") || combined.contains("midjourney") ||
                combined.contains("txt2img")) {
            cats.add("image_generation");
        }
        if (combined.contains("vision") || combined.contains("vl") || combined.contains("4o") ||
                combined.contains("gemini") || combined.contains("multimodal") || combined.contains("omni")) {
            cats.add("vision");
            cats.add("multimodal");
        }
        if (combined.contains("coder") || combined.contains("code") || combined.contains("deepseek") ||
                combined.contains("starcoder") || combined.contains("qwen") || combined.contains("wizardcoder")) {
            cats.add("code");
        }
        if (combined.contains("reasoning") || combined.contains("thinking") || combined.contains("r1") ||
                combined.contains("o1") || combined.contains("o3")) {
            cats.add("reasoning");
        }
        if (combined.contains("live") || combined.contains("audio") || combined.contains("voice") || combined.contains("tts")) {
            cats.add("audio");
            cats.add("live");
        }
        if (cats.isEmpty() || !cats.contains("image_generation")) {
            cats.add("text_chat");
        }
        return cats;
    }

    private void setupAccordionAdapter() {
        modelAccordionAdapter = new ModelAccordionAdapter(model -> {
            activeModelId = model.getId();
            selectedModelText.setText(model.getDisplayName());
            if (accordionMenuCard != null) accordionMenuCard.setVisibility(View.GONE);
            if (ivTriggerChevron != null) ivTriggerChevron.setRotation(0);
            updateAspectRatioBarVisibility();
            if (currentSession != null) {
                currentSession.setSelectedModelId(activeModelId);
                sessionManager.saveSession(currentSession);
            }
            Toast.makeText(MainActivity.this, "Selected: " + model.getDisplayName(), Toast.LENGTH_SHORT).show();
        });

        List<ModelAccordionAdapter.AccordionItem> items = new ArrayList<>();

        // Section 1: All Models (sorted alphabetically)
        List<AIModelDefinition> sortedAll = new ArrayList<>(allModelsList);
        Collections.sort(sortedAll, (a, b) -> a.getDisplayName().compareToIgnoreCase(b.getDisplayName()));
        items.add(ModelAccordionAdapter.AccordionItem.createHeader("★ All Models", sortedAll.size()));
        for (AIModelDefinition m : sortedAll) {
            items.add(ModelAccordionAdapter.AccordionItem.createChild(m, "★ All Models"));
        }

        // Section 2: By Parent / Provider
        for (Map.Entry<String, List<AIModelDefinition>> entry : modelsByProvider.entrySet()) {
            String pName = "Parent: " + entry.getKey();
            items.add(ModelAccordionAdapter.AccordionItem.createHeader(pName, entry.getValue().size()));
            for (AIModelDefinition m : entry.getValue()) {
                items.add(ModelAccordionAdapter.AccordionItem.createChild(m, pName));
            }
        }

        // Section 3: By Category
        Map<String, List<AIModelDefinition>> byCategory = new HashMap<>();
        for (AIModelDefinition m : allModelsList) {
            List<String> cats = m.getCategories();
            if (cats == null || cats.isEmpty()) cats = m.getCapabilityTags();
            for (String cat : cats) {
                String formatted = "Category: " + formatCategoryName(cat);
                if (!byCategory.containsKey(formatted)) {
                    byCategory.put(formatted, new ArrayList<>());
                }
                byCategory.get(formatted).add(m);
            }
        }
        for (Map.Entry<String, List<AIModelDefinition>> entry : byCategory.entrySet()) {
            items.add(ModelAccordionAdapter.AccordionItem.createHeader(entry.getKey(), entry.getValue().size()));
            for (AIModelDefinition m : entry.getValue()) {
                items.add(ModelAccordionAdapter.AccordionItem.createChild(m, entry.getKey()));
            }
        }

        modelAccordionAdapter.setData(items, activeModelId);
        rvAccordionModels.setLayoutManager(new LinearLayoutManager(this));
        rvAccordionModels.setAdapter(modelAccordionAdapter);
    }

    private String formatCategoryName(String cat) {
        if (cat == null) return "General";
        return cat.replace("_", " ").toUpperCase(Locale.US);
    }

    private String capitalize(String str) {
        if (str == null || str.isEmpty()) return "";
        return str.substring(0, 1).toUpperCase(Locale.US) + str.substring(1);
    }

    private void updateAspectRatioBarVisibility() {
        if (aspectRatioBar == null) return;
        boolean isImage = PuterChatManager.isImageModel(activeModelId);
        aspectRatioBar.setVisibility(isImage ? View.VISIBLE : View.GONE);
    }

    // --- LIVE VOICE AGENT ---

    private void launchLiveVoiceAgent() {
        toggleStarAnimation(true);
        if (headlessAudioBridge != null) {
            headlessAudioBridge.startCommentarySession("auto", activeModelId);
        }
    }

    private void closeLiveVoiceAgent() {
        toggleStarAnimation(false);
        if (headlessAudioBridge != null) {
            headlessAudioBridge.stopCommentarySession();
        }
    }

    private void toggleStarAnimation(boolean start) {
        if (start) {
            orbitStar1.setVisibility(View.VISIBLE);
            orbitStar2.setVisibility(View.VISIBLE);
            orbitStar3.setVisibility(View.VISIBLE);
            
            Animation rotate = AnimationUtils.loadAnimation(this, R.anim.rotate_stars);
            orbitStar1.startAnimation(rotate);
            orbitStar2.startAnimation(rotate);
            orbitStar3.startAnimation(rotate);
        } else {
            orbitStar1.setVisibility(View.INVISIBLE);
            orbitStar2.setVisibility(View.INVISIBLE);
            orbitStar3.setVisibility(View.INVISIBLE);
            orbitStar1.clearAnimation();
            orbitStar2.clearAnimation();
            orbitStar3.clearAnimation();
        }
    }

    private void toggleChatStarAnimation(boolean start, TextView s1, TextView s2, TextView s3) {
        if (start) {
            s1.setVisibility(View.VISIBLE);
            s2.setVisibility(View.VISIBLE);
            s3.setVisibility(View.VISIBLE);
            
            Animation rotate = AnimationUtils.loadAnimation(this, R.anim.rotate_stars);
            s1.startAnimation(rotate);
            s2.startAnimation(rotate);
            s3.startAnimation(rotate);
        } else {
            s1.setVisibility(View.INVISIBLE);
            s2.setVisibility(View.INVISIBLE);
            s3.setVisibility(View.INVISIBLE);
            s1.clearAnimation();
            s2.clearAnimation();
            s3.clearAnimation();
        }
    }

    // --- DIALOGS & OVERFLOWS ---

    private void showHistoryDialog() {
        BottomSheetDialog dialog = new BottomSheetDialog(this, R.style.BottomSheetDialogTheme);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(32, 32, 32, 32);

        TextView title = new TextView(this);
        title.setText("Chat History");
        title.setTextSize(18);
        title.setTextColor(Color.parseColor("#333333"));
        title.setTypeface(null, android.graphics.Typeface.BOLD);
        root.addView(title);

        RecyclerView rv = new RecyclerView(this);
        rv.setLayoutManager(new LinearLayoutManager(this));
        List<ChatSession> sessions = sessionManager.getAllSessions();

        SessionHistoryAdapter adapter = new SessionHistoryAdapter(sessions, session -> {
            loadSessionIntoUI(session);
            dialog.dismiss();
        }, session -> {
            sessionManager.deleteSession(session.getId());
            if (currentSession != null && currentSession.getId().equals(session.getId())) {
                loadInitialSession();
            }
            dialog.dismiss();
        });
        rv.setAdapter(adapter);
        root.addView(rv);

        dialog.setContentView(root);
        dialog.show();
    }

    private void showSettingsHubDialog() {
        BottomSheetDialog dialog = new BottomSheetDialog(this, R.style.BottomSheetDialogTheme);
        View sheet = LayoutInflater.from(this).inflate(R.layout.dialog_settings_hub, null);

        sheet.findViewById(R.id.btnHubCustomInstruction).setOnClickListener(v -> {
            dialog.dismiss();
            showCustomInstructionDialog();
        });

        sheet.findViewById(R.id.btnHubGeminiSettings).setOnClickListener(v -> {
            dialog.dismiss();
            showGeminiSettingsDialog();
        });

        sheet.findViewById(R.id.btnHubNostrIdentity).setOnClickListener(v -> {
            dialog.dismiss();
            showNostrIdentityDialog();
        });

        sheet.findViewById(R.id.btnHubClose).setOnClickListener(v -> dialog.dismiss());

        dialog.setContentView(sheet);
        dialog.show();
    }

    private void showCustomInstructionDialog() {
        BottomSheetDialog dialog = new BottomSheetDialog(this, R.style.BottomSheetDialogTheme);
        View sheet = LayoutInflater.from(this).inflate(R.layout.dialog_custom_instruction, null);
        EditText et = sheet.findViewById(R.id.etCustomInstructionInput);
        et.setText(prefs.getString(AppConstants.KEY_CUSTOM_INSTRUCTION, ""));

        sheet.findViewById(R.id.btnSaveCustom).setOnClickListener(v -> {
            String val = et.getText().toString().trim();
            prefs.edit().putString(AppConstants.KEY_CUSTOM_INSTRUCTION, val).apply();
            if (currentSession != null) {
                currentSession.getConfig().setSystemInstructions(val);
                sessionManager.saveSession(currentSession);
            }
            Toast.makeText(this, "Custom Instruction saved", Toast.LENGTH_SHORT).show();
            dialog.dismiss();
        });

        sheet.findViewById(R.id.btnCancelCustom).setOnClickListener(v -> dialog.dismiss());
        dialog.setContentView(sheet);
        dialog.show();
    }

    private void showGeminiSettingsDialog() {
        BottomSheetDialog dialog = new BottomSheetDialog(this, R.style.BottomSheetDialogTheme);
        View sheet = LayoutInflater.from(this).inflate(R.layout.dialog_gemini_settings, null);
        EditText etKey = sheet.findViewById(R.id.etGeminiApiKey);
        TextView tvStatus = sheet.findViewById(R.id.tvGeminiFetchStatus);
        etKey.setText(prefs.getString(AppConstants.KEY_GEMINI_API_KEY, ""));

        sheet.findViewById(R.id.btnFetchGeminiModels).setOnClickListener(v -> {
            String key = etKey.getText().toString().trim();
            if (key.isEmpty()) {
                Toast.makeText(this, "Enter API Key first", Toast.LENGTH_SHORT).show();
                return;
            }
            prefs.edit().putString(AppConstants.KEY_GEMINI_API_KEY, key).apply();
            tvStatus.setText("Fetching models from Google...");
            geminiService.fetchAvailableModels(key, new GeminiService.GeminiModelsListener() {
                @Override
                public void onModelsFetched(String json) {
                    runOnUiThread(() -> {
                        prefs.edit().putString(AppConstants.KEY_GEMINI_CACHED_LIVE_MODELS, json).apply();
                        loadModels();
                        tvStatus.setText("Models fetched successfully!");
                        Toast.makeText(MainActivity.this, "Live Gemini models updated!", Toast.LENGTH_SHORT).show();
                    });
                }

                @Override
                public void onError(String errorMessage) {
                    runOnUiThread(() -> {
                        tvStatus.setText("Fetch error: " + errorMessage);
                        Toast.makeText(MainActivity.this, "Fetch error: " + errorMessage, Toast.LENGTH_SHORT).show();
                    });
                }
            });
        });

        sheet.findViewById(R.id.btnGeminiSettingsDone).setOnClickListener(v -> {
            prefs.edit().putString(AppConstants.KEY_GEMINI_API_KEY, etKey.getText().toString().trim()).apply();
            Toast.makeText(this, "Gemini settings saved", Toast.LENGTH_SHORT).show();
            dialog.dismiss();
        });

        sheet.findViewById(R.id.btnGeminiSettingsBack).setOnClickListener(v -> dialog.dismiss());
        dialog.setContentView(sheet);
        dialog.show();
    }

    private void showNostrIdentityDialog() {
        BottomSheetDialog dialog = new BottomSheetDialog(this, R.style.BottomSheetDialogTheme);
        View sheet = LayoutInflater.from(this).inflate(R.layout.dialog_nostr_identity, null);

        TextView tvNpub = sheet.findViewById(R.id.tvNostrNpub);
        TextView tvNsec = sheet.findViewById(R.id.tvNostrNsec);

        String npub = prefs.getString(AppConstants.KEY_NOSTR_PUBLIC_KEY, null);
        String nsec = prefs.getString(AppConstants.KEY_NOSTR_PRIVATE_KEY, null);

        if (npub == null || nsec == null) {
            String[] pair = NostrKeyManager.generateKeyPair();
            nsec = pair[0];
            npub = pair[1];
            prefs.edit().putString(AppConstants.KEY_NOSTR_PRIVATE_KEY, nsec)
                    .putString(AppConstants.KEY_NOSTR_PUBLIC_KEY, npub).apply();
        }

        final String finalNpub = npub;
        final String finalNsec = nsec;
        tvNpub.setText(finalNpub);
        tvNsec.setText(finalNsec);

        sheet.findViewById(R.id.btnCopyNpub).setOnClickListener(v -> copyText(finalNpub));
        sheet.findViewById(R.id.btnCopyNsec).setOnClickListener(v -> copyText(finalNsec));

        sheet.findViewById(R.id.btnNostrDone).setOnClickListener(v -> dialog.dismiss());
        sheet.findViewById(R.id.btnNostrBack).setOnClickListener(v -> dialog.dismiss());

        dialog.setContentView(sheet);
        dialog.show();
    }

    private void showBrowserDialog() {
        BottomSheetDialog dialog = new BottomSheetDialog(this, R.style.BottomSheetDialogTheme);
        View sheet = LayoutInflater.from(this).inflate(R.layout.dialog_code_preview_fullscreen, null);
        WebView wv = sheet.findViewById(R.id.wvCodePreview);
        wv.getSettings().setJavaScriptEnabled(true);
        wv.loadUrl(AppConstants.LOCAL_BROWSER_URL);
        sheet.findViewById(R.id.btnCloseCodePreview).setOnClickListener(v -> dialog.dismiss());
        sheet.findViewById(R.id.btnReloadCodePreview).setOnClickListener(v -> wv.reload());
        dialog.setContentView(sheet);
        dialog.show();
    }

    private void showCryptoBotDialog() {
        BottomSheetDialog dialog = new BottomSheetDialog(this, R.style.BottomSheetDialogTheme);
        View sheet = LayoutInflater.from(this).inflate(R.layout.dialog_code_preview_fullscreen, null);
        WebView wv = sheet.findViewById(R.id.wvCodePreview);
        wv.getSettings().setJavaScriptEnabled(true);
        wv.loadUrl(AppConstants.LOCAL_BOT_URL);
        sheet.findViewById(R.id.btnCloseCodePreview).setOnClickListener(v -> dialog.dismiss());
        sheet.findViewById(R.id.btnReloadCodePreview).setOnClickListener(v -> wv.reload());
        dialog.setContentView(sheet);
        dialog.show();
    }

    private void showHelpDialog() {
        new AlertDialog.Builder(this)
                .setTitle("Puter Unofficial Help")
                .setMessage("Pure Native Android client for Puter AI, Gemini Live Audio commentary, and multi-model completions.\n\nDeveloped by Josyvine.")
                .setPositiveButton("OK", null)
                .show();
    }

    private void showCodePreviewFullscreen(String code) {
        BottomSheetDialog dialog = new BottomSheetDialog(this, R.style.BottomSheetDialogTheme);
        View sheet = LayoutInflater.from(this).inflate(R.layout.dialog_code_preview_fullscreen, null);
        WebView wv = sheet.findViewById(R.id.wvCodePreview);
        wv.getSettings().setJavaScriptEnabled(true);
        wv.loadDataWithBaseURL(null, code, "text/html", "UTF-8", null);

        sheet.findViewById(R.id.btnCloseCodePreview).setOnClickListener(v -> dialog.dismiss());
        sheet.findViewById(R.id.btnReloadCodePreview).setOnClickListener(v -> wv.loadDataWithBaseURL(null, code, "text/html", "UTF-8", null));
        dialog.setContentView(sheet);
        dialog.show();
    }

    private void showImagePreviewViewer(String base64OrUrl) {
        BottomSheetDialog dialog = new BottomSheetDialog(this, R.style.BottomSheetDialogTheme);
        View sheet = LayoutInflater.from(this).inflate(R.layout.dialog_image_preview_viewer, null);
        ImageView iv = sheet.findViewById(R.id.ivFullPreviewImage);

        if (base64OrUrl.startsWith("data:image")) {
            String clean = base64OrUrl.replaceFirst("^data:image/[a-zA-Z]+;base64,", "");
            byte[] decoded = Base64.decode(clean, Base64.DEFAULT);
            Bitmap bmp = android.graphics.BitmapFactory.decodeByteArray(decoded, 0, decoded.length);
            iv.setImageBitmap(bmp);
        }

        sheet.findViewById(R.id.btnCloseImagePreview).setOnClickListener(v -> dialog.dismiss());
        sheet.findViewById(R.id.btnSaveImageToGallery).setOnClickListener(v -> {
            if (webAppInterface != null) webAppInterface.saveImageToGallery(base64OrUrl, "Puter_" + System.currentTimeMillis() + ".jpg");
        });
        sheet.findViewById(R.id.btnShareImage).setOnClickListener(v -> Toast.makeText(this, "Sharing image...", Toast.LENGTH_SHORT).show());

        dialog.setContentView(sheet);
        dialog.show();
    }

    private void showAttachmentPickerDialog() {
        BottomSheetDialog dialog = new BottomSheetDialog(this, R.style.BottomSheetDialogTheme);
        View sheet = LayoutInflater.from(this).inflate(R.layout.dialog_attachment_picker, null);

        sheet.findViewById(R.id.btnOptionGallery).setOnClickListener(v -> {
            dialog.dismiss();
            galleryLauncher.launch("image/*");
        });

        sheet.findViewById(R.id.btnOptionDocument).setOnClickListener(v -> {
            dialog.dismiss();
            documentLauncher.launch("*/*");
        });

        sheet.findViewById(R.id.btnOptionCamera).setOnClickListener(v -> {
            dialog.dismiss();
            galleryLauncher.launch("image/*");
        });

        dialog.setContentView(sheet);
        dialog.show();
    }

    private void showUniversalContextMenu(ChatMessage message, View view) {
        contextTargetMessage = message;
        if (customContextMenuCard != null) {
            // Fix positioning
            int[] location = new int[2];
            view.getLocationOnScreen(location);
            customContextMenuCard.setX(location[0]);
            customContextMenuCard.setY(location[1] + view.getHeight());

            customContextMenuCard.setVisibility(View.VISIBLE);
            if (ctxCancelGen != null) ctxCancelGen.setVisibility(isGenerating ? View.VISIBLE : View.GONE);
            if (ctxToggleProvider != null) {
                if ("Gemini".equalsIgnoreCase(activeProvider)) {
                    ctxToggleProvider.setText("☁ Puter Mode");
                } else {
                    ctxToggleProvider.setText("🤖 Gemini Mode");
                }
            }
            if (ctxToggleMode != null) {
                ctxToggleMode.setText(isAutoMode ? "⚡ Auto Mode" : "✋ Normal Mode");
            }
        }
    }

    // --- UTILITIES ---

    private void toggleAuthMirror() {
        boolean loggedIn = AuthManager.getInstance(this).isLoggedIn();
        if (loggedIn) {
            AuthManager.getInstance(this).logout();
            Toast.makeText(this, "Signed Out", Toast.LENGTH_SHORT).show();
        } else {
            showAuthPopupDialog();
        }
    }

    private void showAuthPopupDialog() {
        Dialog dialog = new Dialog(this, android.R.style.Theme_Black_NoTitleBar_Fullscreen);
        dialog.setContentView(R.layout.dialog_auth_popup);
        WebView wv = dialog.findViewById(R.id.wvAuthPopup);
        WebSettings ws = wv.getSettings();
        ws.setJavaScriptEnabled(true);
        ws.setDomStorageEnabled(true);
        CookieManager.getInstance().setAcceptCookie(true);

        wv.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                if (AuthManager.getInstance(MainActivity.this).isAuthCallback(url)) {
                    AuthManager.getInstance(MainActivity.this).setLoggedIn(true);
                    Toast.makeText(MainActivity.this, "Puter Sign-In Successful!", Toast.LENGTH_SHORT).show();
                    dialog.dismiss();
                    return true;
                }
                // Only allow navigation within puter.com/login flow
                return !url.startsWith("https://puter.com/");
            }
        });

        dialog.findViewById(R.id.btnAuthCloseBar).setOnClickListener(v -> {
            AuthManager.getInstance(MainActivity.this).setLoggedIn(true);
            dialog.dismiss();
            Toast.makeText(this, "Signed In", Toast.LENGTH_SHORT).show();
        });

        wv.loadUrl(AppConstants.PUTER_LOGIN_URL);
        dialog.show();
    }

    private void toggleModelSource() {
        boolean curr = prefs.getBoolean(AppConstants.KEY_USE_LIVE_MODELS, false);
        boolean next = !curr;
        prefs.edit().putBoolean(AppConstants.KEY_USE_LIVE_MODELS, next).apply();
        loadModels();
        Toast.makeText(this, next ? "Live Models Enabled" : "Using Local Models", Toast.LENGTH_SHORT).show();
    }

    private void copyText(String text) {
        ClipboardManager cm = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
        if (cm != null) {
            cm.setPrimaryClip(ClipData.newPlainText("Puter Chat", text));
            Toast.makeText(this, "Copied to clipboard", Toast.LENGTH_SHORT).show();
        }
    }

    private void speakText(String text) {
        if (textToSpeech != null && text != null) {
            textToSpeech.speak(text, TextToSpeech.QUEUE_FLUSH, null, AppConstants.TTS_UTTERANCE_ID);
        }
    }

    private void startSpeechToText() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.RECORD_AUDIO}, 202);
            return;
        }

        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault());
        intent.putExtra(RecognizerIntent.EXTRA_PROMPT, "Speak to Puter...");

        if (speechRecognizer == null) {
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this);
        }

        speechRecognizer.setRecognitionListener(new RecognitionListener() {
            @Override public void onReadyForSpeech(Bundle params) { Toast.makeText(MainActivity.this, "Listening...", Toast.LENGTH_SHORT).show(); }
            @Override public void onBeginningOfSpeech() {}
            @Override public void onRmsChanged(float rmsdB) {}
            @Override public void onBufferReceived(byte[] buffer) {}
            @Override public void onEndOfSpeech() {}
            @Override public void onError(int error) {}
            @Override
            public void onResults(Bundle results) {
                ArrayList<String> matches = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                if (matches != null && !matches.isEmpty()) {
                    String spoke = matches.get(0);
                    etMessageInput.setText(spoke);
                    etMessageInput.setSelection(spoke.length());
                }
            }
            @Override public void onPartialResults(Bundle partialResults) {}
            @Override public void onEvent(int eventType, Bundle params) {}
        });

        speechRecognizer.startListening(intent);
    }

    private void updateUsageStats() {
        long currentTotal = prefs.getLong(AppConstants.KEY_USAGE_TOTAL_TOKENS, 0);
        prefs.edit().putLong(AppConstants.KEY_USAGE_TOTAL_TOKENS, currentTotal + 150).apply();
    }

    private void checkAndRequestPermissions() {
        List<String> needed = new ArrayList<>();
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            needed.add(Manifest.permission.RECORD_AUDIO);
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            needed.add(Manifest.permission.CAMERA);
        }
        if (!needed.isEmpty()) {
            ActivityCompat.requestPermissions(this, needed.toArray(new String[0]), AppConstants.REQUEST_CODE_PERMISSIONS);
        }
    }

    public void handleUrlChange(String url) {
        Log.d(TAG, "handleUrlChange: " + url);
    }

    public void reloadWebView() {
        if (headlessAudioWebView != null) {
            headlessAudioWebView.reload();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (textToSpeech != null) {
            textToSpeech.stop();
            textToSpeech.shutdown();
        }
        if (speechRecognizer != null) {
            speechRecognizer.destroy();
        }
        if (geminiService != null) {
            geminiService.shutdown();
        }
        if (headlessAudioBridge != null) {
            headlessAudioBridge.stopCommentarySession();
        }
    }

    // Helper adapter for the session history list
    private static class SessionHistoryAdapter extends RecyclerView.Adapter<SessionHistoryAdapter.ViewHolder> {
        private final List<ChatSession> sessions;
        private final OnSessionClickListener clickListener;
        private final OnSessionDeleteListener deleteListener;

        interface OnSessionClickListener { void onClick(ChatSession session); }
        interface OnSessionDeleteListener { void onDelete(ChatSession session); }

        SessionHistoryAdapter(List<ChatSession> sessions, OnSessionClickListener c, OnSessionDeleteListener d) {
            this.sessions = sessions;
            this.clickListener = c;
            this.deleteListener = d;
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull android.view.ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_session_drawer_item, parent, false);
            return new ViewHolder(v);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            ChatSession s = sessions.get(position);
            holder.tvTitle.setText(s.getTitle());
            holder.itemView.setOnClickListener(v -> clickListener.onClick(s));
            holder.btnDelete.setOnClickListener(v -> deleteListener.onDelete(s));
        }

        @Override
        public int getItemCount() {
            return sessions != null ? sessions.size() : 0;
        }

        static class ViewHolder extends RecyclerView.ViewHolder {
            TextView tvTitle;
            ImageView btnDelete;
            ViewHolder(View v) {
                super(v);
                tvTitle = v.findViewById(R.id.tvSessionTitle);
                btnDelete = v.findViewById(R.id.btnDeleteSession);
            }
        }
    }
}