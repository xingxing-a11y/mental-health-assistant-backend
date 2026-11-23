package com.mindharbor.modules.risk.service;

/**
 * 测评高危判定。
 *
 * <p>用标准分（0~100 的百分比）而不是原始分：不同量表满分不同（有的 27、有的 80），
 * 拿原始分跟固定阈值比没有意义。
 */
public final class AssessmentRiskEvaluator {

    private AssessmentRiskEvaluator() {
    }

    /**
     * @param totalScore 实际得分
     * @param maxScore   该量表满分；为 0 表示全是 TEXT/MATRIX 题，无法算比例
     * @param threshold  阈值（0~100），达到即判高危
     */
    public static boolean isHighRisk(int totalScore, int maxScore, double threshold) {
        // 满分为 0 说明量表只有 TEXT/MATRIX 题，算不出比例，一律不判高危；
        // 零分也不判，否则阈值被配成 0 时会把所有未作答的用户都标成高危。
        if (maxScore <= 0 || totalScore <= 0) {
            return false;
        }
        return totalScore * 100D / maxScore >= threshold;
    }
}
