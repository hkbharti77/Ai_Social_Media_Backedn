package com.aiplatform.model;

public enum SubscriptionTier {
    //                    imgCr  dayLim  cool  lvl  chatModel              imgModel          maxImg  maxVid  price
    FREE    (10.0,    3.0,  30, 0, "gemini-2.5-flash-lite", "gemini-3.1-flash-image", 15,   0,   0.0),
    CREATOR (400.0,  -1.0,   0, 1, "gemini-2.5-flash-lite", "imagen-4-standard",      200,  0,   799.0),
    STANDARD(200.0,  25.0,   0, 1, "gemini-2.5-flash-lite", "imagen-4-standard",      100,  5,   499.0),
    PRO     (1000.0, -1.0,   0, 2, "gemini-1.5-pro",        "imagen-4-ultra",          500, 25,  1499.0),
    SUPER_PRO(2000.0,-1.0,   0, 3, "gemini-1.5-pro",        "gemini-3-pro-image",     2000, 100, 5999.0);

    // ── Included video credits given to wallet on plan purchase ──────────────
    // Standard:  5 Lite
    // Pro:       8 Lite + 2 Fast
    // Super Pro: 10 Lite + 5 Fast + 2 Standard
    // Free/Creator: 0

    private final Double monthlyLimit;   // Image credits per month
    private final Double dailyLimit;
    private final int cooldownMinutes;
    private final int level;
    private final String defaultChatModel;
    private final String defaultImageModel;
    private final int maxStoredImages;
    private final int maxStoredVideos;
    private final Double priceInInr;

    SubscriptionTier(Double monthlyLimit, Double dailyLimit, int cooldownMinutes, int level,
                     String defaultChatModel, String defaultImageModel,
                     int maxStoredImages, int maxStoredVideos, Double priceInInr) {
        this.monthlyLimit = monthlyLimit;
        this.dailyLimit = dailyLimit;
        this.cooldownMinutes = cooldownMinutes;
        this.level = level;
        this.defaultChatModel = defaultChatModel;
        this.defaultImageModel = defaultImageModel;
        this.maxStoredImages = maxStoredImages;
        this.maxStoredVideos = maxStoredVideos;
        this.priceInInr = priceInInr;
    }

    // ── Included video credits per model ─────────────────────────────────────
    public int getIncludedLiteCredits() {
        return switch (this) {
            case STANDARD  -> 5;
            case PRO       -> 8;
            case SUPER_PRO -> 10;
            default        -> 0;
        };
    }

    public int getIncludedFastCredits() {
        return switch (this) {
            case PRO       -> 2;
            case SUPER_PRO -> 5;
            default        -> 0;
        };
    }

    public int getIncludedStandardCredits() {
        return switch (this) {
            case SUPER_PRO -> 2;
            default        -> 0;
        };
    }

    public Double getMonthlyLimit()      { return monthlyLimit; }
    public Double getDailyLimit()        { return dailyLimit; }
    public int getCooldownMinutes()      { return cooldownMinutes; }
    public int getLevel()                { return level; }
    public String getDefaultChatModel() { return defaultChatModel; }
    public String getDefaultImageModel(){ return defaultImageModel; }
    public int getMaxStoredImages()      { return maxStoredImages; }
    public int getMaxStoredVideos()      { return maxStoredVideos; }
    public Double getPriceInInr()        { return priceInInr; }
}
