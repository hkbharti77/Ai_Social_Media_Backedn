package com.aiplatform.model;

public enum SubscriptionTier {
    FREE(10.0, 2.0, 60, 0, "gemini-2.1-flash-lite", "gemini-2.5-flash-image", 10, 0),
    STANDARD(100.0, 20.0, 0, 1, "gemini-2.5-flash-lite", "imagen-4-standard", 50, 0),
    PRO(1000.0, -1.0, 0, 2, "gemini-1.5-pro", "imagen-4-ultra", 200, 10),
    SUPER_PRO(20000.0, -1.0, 0, 3, "gemini-1.5-pro", "gemini-3-pro-image", -1, 50);

    private final Double monthlyLimit;
    private final Double dailyLimit;
    private final int cooldownMinutes;
    private final int level;
    private final String defaultChatModel;
    private final String defaultImageModel;
    private final int maxStoredImages;
    private final int maxStoredVideos;

    SubscriptionTier(Double monthlyLimit, Double dailyLimit, int cooldownMinutes, int level, String defaultChatModel, String defaultImageModel, int maxStoredImages, int maxStoredVideos) {
        this.monthlyLimit = monthlyLimit;
        this.dailyLimit = dailyLimit;
        this.cooldownMinutes = cooldownMinutes;
        this.level = level;
        this.defaultChatModel = defaultChatModel;
        this.defaultImageModel = defaultImageModel;
        this.maxStoredImages = maxStoredImages;
        this.maxStoredVideos = maxStoredVideos;
    }

    public Double getMonthlyLimit() { return monthlyLimit; }
    public Double getDailyLimit() { return dailyLimit; }
    public int getCooldownMinutes() { return cooldownMinutes; }
    public int getLevel() { return level; }
    public String getDefaultChatModel() { return defaultChatModel; }
    public String getDefaultImageModel() { return defaultImageModel; }
    public int getMaxStoredImages() { return maxStoredImages; }
    public int getMaxStoredVideos() { return maxStoredVideos; }
}
