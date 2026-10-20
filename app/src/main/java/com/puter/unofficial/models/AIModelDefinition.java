package com.puter.unofficial.models;

import java.util.ArrayList;
import java.util.List;

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
        this.contextTokenLimit = contextTokenLimit;
        this.supportsVision = supportsVision;
        this.supportsImageGen = supportsImageGen;
        this.isGeminiLive = isGeminiLive;

        if (supportsVision) capabilityTags.add("vision");
        if (supportsImageGen) capabilityTags.add("image_gen");
        if (isGeminiLive) capabilityTags.add("live");
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public String getProvider() {
        return provider;
    }

    public void setProvider(String provider) {
        this.provider = provider;
    }

    public String getContextTokenLimit() {
        return contextTokenLimit;
    }

    public void setContextTokenLimit(String contextTokenLimit) {
        this.contextTokenLimit = contextTokenLimit;
    }

    public boolean isSupportsVision() {
        return supportsVision;
    }

    public void setSupportsVision(boolean supportsVision) {
        this.supportsVision = supportsVision;
    }

    public boolean isSupportsImageGen() {
        return supportsImageGen;
    }

    public void setSupportsImageGen(boolean supportsImageGen) {
        this.supportsImageGen = supportsImageGen;
    }

    public boolean isGeminiLive() {
        return isGeminiLive;
    }

    public void setGeminiLive(boolean geminiLive) {
        isGeminiLive = geminiLive;
    }

    public List<String> getCapabilityTags() {
        return capabilityTags;
    }

    public void setCapabilityTags(List<String> capabilityTags) {
        this.capabilityTags = capabilityTags;
    }

    public List<String> getCategories() {
        return categories;
    }

    public void setCategories(List<String> categories) {
        this.categories = categories;
    }
}
