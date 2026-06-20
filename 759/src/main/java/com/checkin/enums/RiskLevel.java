package com.checkin.enums;

/**
 * 风险等级枚举
 */
public enum RiskLevel {
    LOW("低风险", 0, 39),
    MEDIUM("中风险", 40, 69),
    HIGH("高风险", 70, 100);

    private final String description;
    private final int minScore;
    private final int maxScore;

    RiskLevel(String description, int minScore, int maxScore) {
        this.description = description;
        this.minScore = minScore;
        this.maxScore = maxScore;
    }

    public String getDescription() {
        return description;
    }

    public int getMinScore() {
        return minScore;
    }

    public int getMaxScore() {
        return maxScore;
    }

    public static RiskLevel fromScore(int score) {
        if (score >= 70) return HIGH;
        if (score >= 40) return MEDIUM;
        return LOW;
    }
}
