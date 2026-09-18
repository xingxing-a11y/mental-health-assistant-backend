package com.mindharbor.modules.assessment.service;

import com.mindharbor.modules.assessment.dto.AssessmentSubmitRequest;
import com.mindharbor.modules.assessment.dto.ScaleRequest;
import com.mindharbor.modules.assessment.dto.QuestionRequest;
import com.mindharbor.modules.assessment.entity.AssessmentScale;
import com.mindharbor.modules.assessment.entity.AssessmentQuestion;
import com.mindharbor.modules.assessment.vo.AssessmentAnalysisVO;
import com.mindharbor.modules.assessment.vo.AssessmentComparisonVO;
import com.mindharbor.modules.assessment.vo.AssessmentFavoriteVO;
import com.mindharbor.modules.assessment.vo.AssessmentHistoryVO;
import com.mindharbor.modules.assessment.vo.AssessmentRecommendationVO;
import com.mindharbor.modules.assessment.vo.AssessmentReportVO;
import com.mindharbor.modules.assessment.vo.AssessmentScaleVO;
import com.mindharbor.modules.assessment.vo.AssessmentScaleDetailVO;
import java.util.List;

public interface AssessmentService {

    List<AssessmentScale> scales();

    List<AssessmentScaleVO> publishedScales();

    AssessmentScale publish(Long scaleId);

    AssessmentScaleDetailVO detail(Long scaleId);

    AssessmentScale create(ScaleRequest request);

    AssessmentScale update(Long scaleId, ScaleRequest request);

    void delete(Long scaleId);

    List<AssessmentQuestion> replaceQuestions(Long scaleId, List<QuestionRequest> requests);

    AssessmentReportVO submit(AssessmentSubmitRequest request);

    AssessmentAnalysisVO analyze(Long resultId);

    List<AssessmentHistoryVO> history();

    void updateFavorite(Long scaleId, boolean favorite);

    List<AssessmentFavoriteVO> favorites();

    List<AssessmentComparisonVO> scaleHistory(Long scaleId);

    List<AssessmentRecommendationVO> recommendations();

}
