package com.puter.unofficial.models;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class AIModelDefinition {
    private String id;
    private String displayName;
    private String provider;
    private String contextTokenLimit;
    private boolean supportsVision;
    private boolean supportsImageGen;
    private boolean isGeminiLive;
    private List<String> capabilityTags = new ArrayList<>();
    private List<String> categories = new ArrayList<>();

    public AIModelDefinition(String id, String displayName, String provider, String contextTokenLimit,
                             boolean supportsVision, boolean supportsImageGen, boolean isGeminiLive) {
        this.id = id;
        this.displayName = displayName;
        this.provider = provider;
        this.contextTokenLimit = contextTokenLimit != null ? contextTokenLimit : "";
        this.supportsVision = supportsVision;
        this.supportsImageGen = supportsImageGen;
        this.isGeminiLive = isGeminiLive;

        if (supportsVision) addCapabilityTag("vision");
        if (supportsImageGen) addCapabilityTag("image_gen");
        if (isGeminiLive) addCapabilityTag("live");
    }

    public String getId() {
        return id != null ? id : "";
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getDisplayName() {
        if (displayName != null && !displayName.trim().isEmpty()) {
            return displayName;
        }
        return getId();
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public String getProvider() {
        return provider != null ? provider : "Other";
    }

    public void setProvider(String provider) {
        this.provider = provider;
    }

    public String getContextTokenLimit() {
        return contextTokenLimit != null ? contextTokenLimit : "";
    }

    public void setContextTokenLimit(String contextTokenLimit) {
        this.contextTokenLimit = contextTokenLimit != null ? contextTokenLimit : "";
    }

    public boolean isSupportsVision() {
        return supportsVision;
    }

    public void setSupportsVision(boolean supportsVision) {
        this.supportsVision = supportsVision;
        if (supportsVision) addCapabilityTag("vision");
    }

    public boolean isSupportsImageGen() {
        return supportsImageGen;
    }

    public void setSupportsImageGen(boolean supportsImageGen) {
        this.supportsImageGen = supportsImageGen;
        if (supportsImageGen) addCapabilityTag("image_gen");
    }

    public boolean isGeminiLive() {
        return isGeminiLive;
    }

    public void setGeminiLive(boolean geminiLive) {
        isGeminiLive = geminiLive;
        if (geminiLive) addCapabilityTag("live");
    }

    public List<String> getCapabilityTags() {
        return capabilityTags;
    }

    public void setCapabilityTags(List<String> capabilityTags) {
        this.capabilityTags = capabilityTags != null ? capabilityTags : new ArrayList<>();
    }

    public List<String> getCategories() {
        return categories;
    }

    /**
     * Ingests capability categories and synchronizes feature flags and tags.
     */
    public void setCategories(List<String> categories) {
        this.categories = categories != null ? categories : new ArrayList<>();
        for (String cat : this.categories) {
            String lower = cat.toLowerCase(Locale.US);
            if (lower.contains("image") || lower.contains("imagen") || lower.contains("flux") || lower.contains("txt2img")) {
                this.supportsImageGen = true;
                addCapabilityTag("image_gen");
            }
            if (lower.contains("vision") || lower.contains("multimodal") || lower.contains("vl")) {
                this.supportsVision = true;
                addCapabilityTag("vision");
            }
            if (lower.contains("live") || lower.contains("native-audio")) {
                this.isGeminiLive = true;
                addCapabilityTag("live");
            }
            if (lower.contains("code") || lower.contains("coder")) {
                addCapabilityTag("code");
            }
            if (lower.contains("reasoning") || lower.contains("thinking")) {
                addCapabilityTag("reasoning");
            }
        }
    }

    public boolean hasCategory(String category) {
        if (category == null || categories == null) return false;
        for (String c : categories) {
            if (c.equalsIgnoreCase(category)) return true;
        }
        return false;
    }

    public void addCategory(String category) {
        if (category == null || category.trim().isEmpty()) return;
        if (this.categories == null) this.categories = new ArrayList<>();
        if (!hasCategory(category)) {
            this.categories.add(category.trim().toLowerCase(Locale.US));
        }
    }

    public void addCapabilityTag(String tag) {
        if (tag == null || tag.trim().isEmpty()) return;
        if (this.capabilityTags == null) this.capabilityTags = new ArrayList<>();
        String clean = tag.trim().toLowerCase(Locale.US);
        if (!this.capabilityTags.contains(clean)) {
            this.capabilityTags.add(clean);
        }
    }
}