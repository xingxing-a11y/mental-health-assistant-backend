package com.mindharbor.modules.risk.service;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 测评高危判定：核心是「用标准分而非原始分」，以及满分为 0 的纯文本量表。 */
class AssessmentRiskEvaluatorTest {

    private static final double THRESHOLD = 70D;

    @Test
    @DisplayName("19/27 ≈ 70.37% → 高危")
    void aboveThresholdByRatio() {
        assertTrue(AssessmentRiskEvaluator.isHighRisk(19, 27, THRESHOLD));
    }

    @Test
    @DisplayName("18/27 ≈ 66.67% → 不算高危")
    void belowThresholdByRatio() {
        assertFalse(AssessmentRiskEvaluator.isHighRisk(18, 27, THRESHOLD));
    }

    @Test
    @DisplayName("恰好等于阈值（7/10 = 70.0）→ 算高危，边界取等号")
    void exactlyAtThreshold() {
        assertTrue(AssessmentRiskEvaluator.isHighRisk(7, 10, THRESHOLD));
    }

    @Test
    @DisplayName("同样 30 分：满分 27 判高危，满分 80 不判 —— 这正是不能用原始分的原因")
    void sameRawScoreDifferentScales() {
        assertTrue(AssessmentRiskEvaluator.isHighRisk(30, 27, THRESHOLD));
        assertFalse(AssessmentRiskEvaluator.isHighRisk(30, 80, THRESHOLD));
    }

    @Test
    @DisplayName("满分 0（纯 TEXT/MATRIX 量表）→ 一律不判高危")
    void zeroMaxScoreNeverHighRisk() {
        assertFalse(AssessmentRiskEvaluator.isHighRisk(0, 0, THRESHOLD));
        assertFalse(AssessmentRiskEvaluator.isHighRisk(99, 0, THRESHOLD));
    }

    @Test
    @DisplayName("满分为负的脏数据 → 不判高危，也不能抛异常")
    void negativeMaxScoreIsSafe() {
        assertFalse(AssessmentRiskEvaluator.isHighRisk(10, -5, THRESHOLD));
    }

    @Test
    @DisplayName("零分不判高危，即使阈值被配成 0")
    void zeroScoreIsNeverHighRisk() {
        assertFalse(AssessmentRiskEvaluator.isHighRisk(0, 10, 0D));
    }

    @Test
    @DisplayName("阈值为 0 且有得分 → 判高危（阈值语义保持单调）")
    void zeroThresholdMatchesAnyPositiveScore() {
        assertTrue(AssessmentRiskEvaluator.isHighRisk(1, 10, 0D));
    }

    @Test
    @DisplayName("阈值超过 100 → 正常分数不可能命中")
    void thresholdAboveHundred() {
        assertFalse(AssessmentRiskEvaluator.isHighRisk(27, 27, 101D));
    }
}
