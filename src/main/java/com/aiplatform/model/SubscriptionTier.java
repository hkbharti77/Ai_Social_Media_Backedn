package com.aiplatform.model;

public enum SubscriptionTier {
    FREE(10, 2, 60),          // 10/mo, 2/day, 60 min cooldown
    STANDARD(100, 20, 0),     // 100/mo, 20/day
    PRO(1000, -1, 0),         // 1000/mo, no daily max
    SUPER_PRO(20000, -1, 0);  // 20000/mo, no daily max

    private final int monthlyLimit;
    private final int dailyLimit;
    private final int cooldownMinutes;

    SubscriptionTier(int monthlyLimit, int dailyLimit, int cooldownMinutes) {
        this.monthlyLimit = monthlyLimit;
        this.dailyLimit = dailyLimit;
        this.cooldownMinutes = cooldownMinutes;
    }

    public int getMonthlyLimit() { return monthlyLimit; }
    public int getDailyLimit() { return dailyLimit; }
    public int getCooldownMinutes() { return cooldownMinutes; }
}
