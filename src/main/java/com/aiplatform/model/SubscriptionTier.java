package com.aiplatform.model;

public enum SubscriptionTier {
    FREE(15.0, 3.0, 30, 0, "gemini-2.5-flash-lite", "gemini-3.1-flash-image", 15, 0, 0.0),
    STANDARD(150.0, 25.0, 0, 1, "gemini-2.5-flash-lite", "imagen-4-standard", 100, 5, 499.0),
    PRO(1500.0, -1.0, 0, 2, "gemini-1.5-pro", "imagen-4-ultra", 500, 25, 1499.0),
    SUPER_PRO(50000.0, -1.0, 0, 3, "gemini-1.5-pro", "gemini-3-pro-image", 2000, 100, 2999.0);

    private final Double monthlyLimit;
    private final Double dailyLimit;
    private final int cooldownMinutes;
    private final int level;
    private final String defaultChatModel;
    private final String defaultImageModel;
    private final int maxStoredImages;
    private final int maxStoredVideos;
    private final Double priceInInr;

    SubscriptionTier(Double monthlyLimit, Double dailyLimit, int cooldownMinutes, int level, String defaultChatModel, String defaultImageModel, int maxStoredImages, int maxStoredVideos, Double priceInInr) {
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

    public Double getMonthlyLimit() { return monthlyLimit; }
    public Double getDailyLimit() { return dailyLimit; }
    public int getCooldownMinutes() { return cooldownMinutes; }
    public int getLevel() { return level; }
    public String getDefaultChatModel() { return defaultChatModel; }
    public String getDefaultImageModel() { return defaultImageModel; }
    public int getMaxStoredImages() { return maxStoredImages; }
    public int getMaxStoredVideos() { return maxStoredVideos; }
    public Double getPriceInInr() { return priceInInr; }
}
