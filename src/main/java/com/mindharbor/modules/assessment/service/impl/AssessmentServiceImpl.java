package com.mindharbor.modules.assessment.service.impl;

import com.mindharbor.common.ResourceNotFoundException;
import com.mindharbor.modules.admin.entity.AdminOperationLog;
import com.mindharbor.modules.admin.service.AdminOperationLogService;
import com.mindharbor.modules.assessment.dto.AssessmentSubmitRequest;
import com.mindharbor.modules.assessment.dto.QuestionRequest;
import com.mindharbor.modules.assessment.dto.ScaleRequest;
import com.mindharbor.modules.assessment.entity.AssessmentScale;
import com.mindharbor.modules.assessment.entity.AssessmentQuestion;
import com.mindharbor.modules.assessment.entity.AssessmentOption;
import com.mindharbor.modules.assessment.dto.QuestionRequest.OptionRequest;
import java.util.ArrayList;
import java.util.Arrays;
import com.mindharbor.modules.assessment.mapper.AssessmentScaleMapper;
import com.mindharbor.modules.assessment.mapper.AssessmentQuestionMapper;
import com.mindharbor.modules.assessment.mapper.AssessmentResultMapper;
import com.mindharbor.modules.assessment.mapper.AssessmentOptionMapper;
import com.mindharbor.modules.assessment.mapper.AssessmentFavoriteMapper;
import com.mindharbor.modules.assessment.entity.AssessmentFavorite;
import com.mindharbor.modules.assessment.vo.AssessmentComparisonVO;
import com.mindharbor.modules.assessment.vo.AssessmentFavoriteVO;
import com.mindharbor.modules.assessment.vo.AssessmentRecommendationVO;
import com.mindharbor.modules.user.mapper.UserMapper;
import com.mindharbor.modules.assessment.entity.AssessmentResult;
import com.mindharbor.modules.assessment.service.AssessmentService;
import com.mindharbor.modules.assessment.vo.AssessmentAnalysisVO;
import com.mindharbor.modules.assessment.vo.AssessmentHistoryVO;
import com.mindharbor.modules.assessment.vo.AssessmentReportVO;
import com.mindharbor.modules.assessment.vo.AssessmentScaleVO;
import com.mindharbor.modules.assessment.vo.AssessmentScaleDetailVO;
import java.util.List;
import java.util.Map;
import java.util.HashSet;
import java.util.HashMap;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.context.annotation.Lazy;
import org.springframework.transaction.annotation.Transactional;
import com.mindharbor.security.CurrentUserProvider;
import com.mindharbor.modules.chat.service.AiConversationService;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;

@Service
public class AssessmentServiceImpl implements AssessmentService {

    /** 建议在数据库中按行保存，读取时还原成列表，保证落库内容与接口响应一致。 */
    private static final String SUGGESTION_SEPARATOR = "\n";

    /** 推荐列表最多返回 5 条，标准分达到阈值时优先建议复测。 */
    private static final int MAX_RECOMMENDATIONS = 5;

    private static final double RECHECK_SCORE_THRESHOLD = 60D;

    private final AssessmentScaleMapper mapper;
    private final AssessmentQuestionMapper questionMapper;
    private final AssessmentResultMapper resultMapper;
    private final AssessmentOptionMapper optionMapper;
    private final AssessmentFavoriteMapper favoriteMapper;
    private final UserMapper userMapper;
    private final AdminOperationLogService operationLogService;
    private final CurrentUserProvider currentUserProvider;
    private final AiConversationService aiService;
    private final VectorStore vectorStore;

    public AssessmentServiceImpl(
            AssessmentScaleMapper mapper,
            AssessmentQuestionMapper questionMapper,
            AssessmentResultMapper resultMapper,
            AssessmentOptionMapper optionMapper,
            AssessmentFavoriteMapper favoriteMapper,
            UserMapper userMapper,
            AdminOperationLogService operationLogService,
            CurrentUserProvider currentUserProvider,
            AiConversationService aiService,
            @Lazy VectorStore vectorStore) {
        this.mapper = mapper;
        this.questionMapper = questionMapper;
        this.resultMapper = resultMapper;
        this.optionMapper = optionMapper;
        this.favoriteMapper = favoriteMapper;
        this.userMapper = userMapper;
        this.operationLogService = operationLogService;
        this.currentUserProvider = currentUserProvider;
        this.aiService = aiService;
        this.vectorStore = vectorStore;
    }

    @Override
    public List<AssessmentScale> scales() {
        return mapper.findAll();
    }

    @Override
    public List<AssessmentScaleVO> publishedScales() {
        Set<Long> favoritedScaleIds = findFavoritedScaleIds();
        return mapper.findAllPublished().stream()
                .map(scale -> toScaleView(scale, favoritedScaleIds))
                .toList();
    }

    @Override
    public AssessmentScale publish(Long scaleId) {
        AssessmentScale scale = mapper.findById(scaleId)
                .orElseThrow(() -> new ResourceNotFoundException("量表不存在"));
        List<AssessmentQuestion> questions = questionMapper.findByScaleIdOrderByQuestionOrderAsc(scaleId);
        if (questions.isEmpty()) {
            throw new IllegalArgumentException("请先添加题目后再发布");
        }
        scale.setPublished(true);
        mapper.updateById(scale);
        operationLogService.record(
                AdminOperationLog.Type.SCALE,
                "assessment_scale",
                scale.getId(),
                "发布量表");
        return scale;
    }

    @Override
    public AssessmentScaleDetailVO detail(Long scaleId) {
        AssessmentScale scale = mapper.findById(scaleId)
                .orElseThrow(() -> new ResourceNotFoundException("量表不存在"));
        List<AssessmentQuestion> questions = questionMapper.findByScaleIdOrderByQuestionOrderAsc(scaleId);
        questions.forEach(question -> question.setOptions(optionMapper.findByQuestionId(question.getId())));
        return new AssessmentScaleDetailVO(
                scale.getId(),
                scale.getName(),
                scale.getDescription(),
                scale.getApplicableScene(),
                questions);
    }

    @Override
    public AssessmentScale create(ScaleRequest request) {
        AssessmentScale scale = new AssessmentScale();
        scale.setName(request.getName());
        scale.setDescription(request.getDescription());
        scale.setApplicableScene(request.getApplicableScene());
        scale.setPublished(false);
        mapper.insert(scale);
        return scale;
    }

    @Override
    public AssessmentScale update(Long scaleId, ScaleRequest request) {
        AssessmentScale scale = mapper.findById(scaleId)
                .orElseThrow(() -> new ResourceNotFoundException("量表不存在"));
        scale.setName(request.getName());
        scale.setDescription(request.getDescription());
        scale.setApplicableScene(request.getApplicableScene());
        mapper.updateById(scale);
        return scale;
    }

    /** 删除量表前先确认没有测评记录，再按选项、题目、量表的顺序清理，不依赖数据库级联。 */
    @Override
    @Transactional
    public void delete(Long scaleId) {
        AssessmentScale scale = mapper.findById(scaleId)
                .orElseThrow(() -> new ResourceNotFoundException("量表不存在"));
        if (resultMapper.countByScaleId(scale.getId()) > 0) {
            throw new IllegalArgumentException("该量表已有测评记录，无法删除");
        }

        List<AssessmentQuestion> questions = questionMapper
                .findByScaleIdOrderByQuestionOrderAsc(scaleId);
        questions.forEach(question -> optionMapper.deleteByQuestionId(question.getId()));
        questionMapper.deleteByScaleId(scaleId);
        mapper.deleteById(scaleId);
        operationLogService.record(
                AdminOperationLog.Type.SCALE,
                "assessment_scale",
                scale.getId(),
                "删除量表");
    }

    /** 先删除旧题目再写入新题目，必须放在同一事务中，避免中途失败导致题目丢失。 */
    @Override
    @Transactional
    public List<AssessmentQuestion> replaceQuestions(
            Long scaleId,
            List<QuestionRequest> requests) {
        AssessmentScale scale = mapper.findById(scaleId)
                .orElseThrow(() -> new ResourceNotFoundException("量表不存在"));
        List<AssessmentQuestion> questions = questionMapper
                .findByScaleIdOrderByQuestionOrderAsc(scaleId);
        questions.forEach(question -> {
            optionMapper.deleteByQuestionId(question.getId());
            questionMapper.deleteById(question.getId());
        });
        List<AssessmentQuestion> result = new ArrayList<>();

        // 添加题目
        for (QuestionRequest request : requests) {
            AssessmentQuestion question = new AssessmentQuestion();
            question.setScaleId(scale.getId());
            question.setScale(scale);
            question.setQuestionOrder(request.getOrder());
            question.setContent(request.getContent());
            // 获取题目类型
            String type = normalizeQuestionType(request.getType());
            question.setType(type);
            question.setMatrixRows(request.getMatrixRows());
            question.setMatrixColumns(request.getMatrixColumns());
            validateQuestionOptions(type, request.getOptions());

            // 设置题目标签和分值
            for (OptionRequest optionRequest : request.getOptions() == null
                    ? List.<OptionRequest>of()
                    : request.getOptions()) {
                AssessmentOption option = new AssessmentOption();
                option.setLabel(optionRequest.getLabel());
                option.setScore(optionRequest.getScore());
                question.getOptions().add(option);
            }
            // 添加题目到问卷列表
            result.add(question);
        }

        // 遍历列表将题目插入到问卷中
        result.forEach(question -> {
            questionMapper.insert(question);
            question.getOptions().forEach(option -> {
                option.setQuestionId(question.getId());
                optionMapper.insert(option);
            });
        });
        return result;
    }

    @Override
    public AssessmentReportVO submit(AssessmentSubmitRequest request) {
        if (request == null || request.getScaleId() == null || request.getAnswers() == null) {
            throw new IllegalArgumentException("测评参数不能为空");
        }

        AssessmentScale submitScale = mapper.findById(request.getScaleId())
                .orElseThrow(() -> new ResourceNotFoundException("量表不存在"));
        if (!Boolean.TRUE.equals(submitScale.getPublished())) {
            throw new IllegalArgumentException("该量表尚未发布");
        }

        int score = 0;
        int maxScore = 0;
        List<AssessmentQuestion> scaleQuestions = questionMapper
                .findByScaleIdOrderByQuestionOrderAsc(request.getScaleId());
        scaleQuestions.forEach(question -> {
            question.setOptions(optionMapper.findByQuestionId(question.getId()));
            question.setType(normalizeQuestionType(question.getType()));
        });
        if (scaleQuestions.isEmpty()) {
            throw new IllegalArgumentException("当前量表暂无题目");
        }
        if (request.getAnswers().size() != scaleQuestions.size()) {
            throw new IllegalArgumentException("请完成全部题目后再提交");
        }

        Set<Long> answeredQuestionIds = new HashSet<>();
        for (AssessmentQuestion question : scaleQuestions) {
            maxScore += maxQuestionScore(question);
        }
        for (AssessmentSubmitRequest.Answer answer : request.getAnswers()) {
            if (answer == null || answer.getQuestionId() == null
                    || !answeredQuestionIds.add(answer.getQuestionId())) {
                throw new IllegalArgumentException("题目答案存在重复或缺失");
            }
            AssessmentQuestion question = questionMapper
                    .findByIdAndScaleId(answer.getQuestionId(), request.getScaleId())
                    .orElseThrow(() -> new IllegalArgumentException("题目不属于当前量表"));
            String type = normalizeQuestionType(question.getType());
            if ("TEXT".equals(type)) {
                if (answer.getTextAnswer() == null || answer.getTextAnswer().isBlank()) {
                    throw new IllegalArgumentException("请完成全部题目后再提交");
                }
                continue;
            }
            if ("MATRIX".equals(type)) {
                validateMatrixAnswer(answer, question);
                continue;
            }
            List<Long> optionIds = answerOptionIds(answer, type);
            if (optionIds.isEmpty() || optionIds.size() != new HashSet<>(optionIds).size()) {
                throw new IllegalArgumentException("请完成全部题目后再提交");
            }
            for (Long optionId : optionIds) {
                AssessmentOption option = question.getOptions().stream()
                        .filter(item -> item.getId().equals(optionId))
                        .findFirst()
                        .orElseThrow(() -> new IllegalArgumentException("选项不存在"));
                score += option.getScore() == null ? 0 : option.getScore();
            }
            if (optionIds.stream().anyMatch(optionId -> question.getOptions().stream()
                    .anyMatch(option -> option.getId().equals(optionId)
                            && "其他".equals(option.getLabel())))
                    && (answer.getOtherAnswer() == null || answer.getOtherAnswer().isBlank())) {
                throw new IllegalArgumentException("请填写其他选项内容");
            }
        }
        if (answeredQuestionIds.size() != scaleQuestions.size()) {
            throw new IllegalArgumentException("请完成全部题目后再提交");
        }

        String range = score < 30 ? "情绪状态总体平稳" : "情绪可能存在一定波动";
        List<String> suggestions = buildSuggestions();
        AssessmentScale scale = mapper.findById(request.getScaleId())
                .orElseThrow(() -> new ResourceNotFoundException("量表不存在"));
        AssessmentResult result = new AssessmentResult();
        result.setUserId(currentUserProvider.getUserId());
        result.setUser(userMapper.findById(currentUserProvider.getUserId())
                .orElseThrow(() -> new IllegalStateException("当前没有可用用户")));
        result.setScale(scale);
        result.setTotalScore(score);
        result.setStandardScore(maxScore == 0 ? 0D : score * 100D / maxScore);
        result.setRangeDescription(range + "，结果仅供参考，不构成诊断");
        result.setSuggestions(String.join(SUGGESTION_SEPARATOR, suggestions));
        resultMapper.insert(result);

        return new AssessmentReportVO(
                result.getId(),
                score,
                result.getStandardScore(),
                result.getRangeDescription(),
                suggestions);
    }

    @Override
    public AssessmentAnalysisVO analyze(Long resultId) {
        AssessmentResult result = resultMapper.selectById(resultId);
        if (result == null || !currentUserProvider.getUserId().equals(result.getUserId())) {
            throw new ResourceNotFoundException("测评结果不存在");
        }

        AssessmentScale scale = mapper.findById(result.getScaleId())
                .orElseThrow(() -> new ResourceNotFoundException("量表不存在"));
        String analysis = aiService.generate(
                "请根据量表“" + scale.getName() + "”的总分 " + result.getTotalScore()
                        + "，给出温和、简短的情绪倾向解读和适当的安慰建议。禁止医学诊断，"
                        + "不要夸大风险，不要编造用户经历。",
                buildScaleContext(scale.getName()));
        String safeAnalysis = analysis == null || analysis.isBlank()
                ? "暂时无法生成 AI 分析，请稍后再试。"
                : analysis;
        // AI 解读按需生成，不写回 assessment_results.suggestions，避免覆盖提交时保存的报告建议
        return new AssessmentAnalysisVO(safeAnalysis);
    }

    @Override
    public List<AssessmentHistoryVO> history() {
        List<AssessmentResult> results =
                resultMapper.findByUserIdOrderByCreatedAtDesc(currentUserProvider.getUserId());
        Map<Long, String> scaleNames = findScaleNames(results);

        return results.stream()
                .map(result -> new AssessmentHistoryVO(
                        result.getId(),
                        scaleNames.get(result.getScaleId()),
                        result.getTotalScore(),
                        result.getStandardScore(),
                        result.getRangeDescription(),
                        splitSuggestions(result.getSuggestions()),
                        result.getCreatedAt()))
                .collect(Collectors.toList());
    }
    @Override
    public List<AssessmentComparisonVO> scaleHistory(Long scaleId) {
        AssessmentScale scale = mapper.findById(scaleId)
                .orElseThrow(() -> new ResourceNotFoundException("量表不存在"));
        return resultMapper.findByUserIdAndScaleIdOrderByCreatedAtAsc(
                        currentUserProvider.getUserId(),
                        scale.getId())
                .stream()
                .map(result -> new AssessmentComparisonVO(
                        result.getId(),
                        result.getTotalScore(),
                        result.getStandardScore(),
                        result.getRangeDescription(),
                        result.getCreatedAt()))
                .collect(Collectors.toList());
    }

    @Override
    public List<AssessmentFavoriteVO> favorites() {
        Map<Long, AssessmentScale> scales = mapper.findAll()
                .stream()
                .collect(Collectors.toMap(
                        AssessmentScale::getId,
                        scale -> scale,
                        (first, second) -> first));
        return favoriteMapper.findByUserIdOrderByCreatedAtDesc(currentUserProvider.getUserId())
                .stream()
                .map(favorite -> toFavoriteView(scales.get(favorite.getScaleId()), favorite))
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    /** 收藏与取消收藏都按用户加量表维度处理，重复收藏不会产生第二条记录。 */
    @Override
    public void updateFavorite(Long scaleId, boolean favorite) {
        Long userId = currentUserProvider.getUserId();
        AssessmentScale scale = mapper.findById(scaleId)
                .orElseThrow(() -> new ResourceNotFoundException("量表不存在"));
        if (!favorite) {
            favoriteMapper.deleteByUserIdAndScaleId(userId, scale.getId());
            return;
        }
        if (favoriteMapper.findByUserIdAndScaleId(userId, scale.getId()).isEmpty()) {
            AssessmentFavorite entity = new AssessmentFavorite();
            entity.setUserId(userId);
            entity.setScaleId(scale.getId());
            favoriteMapper.insert(entity);
        }
    }

    /** 推荐规则：先建议近期得分偏高的量表复测，再补充尚未测评过的量表。 */
    @Override
    public List<AssessmentRecommendationVO> recommendations() {
        Long userId = currentUserProvider.getUserId();
        List<AssessmentScale> scales = mapper.findAllPublished();
        Map<Long, AssessmentResult> latestByScale = new HashMap<>();
        for (AssessmentResult result : resultMapper.findByUserIdOrderByCreatedAtDesc(userId)) {
            latestByScale.putIfAbsent(result.getScaleId(), result);
        }

        List<AssessmentRecommendationVO> items = new ArrayList<>();
        int priority = 1;
        for (AssessmentScale scale : scales) {
            if (items.size() >= MAX_RECOMMENDATIONS) {
                break;
            }
            AssessmentResult latest = latestByScale.get(scale.getId());
            if (latest == null
                    || latest.getStandardScore() == null
                    || latest.getStandardScore() < RECHECK_SCORE_THRESHOLD) {
                continue;
            }
            items.add(new AssessmentRecommendationVO(
                    scale.getId(),
                    scale.getName(),
                    scale.getDescription(),
                    "最近一次标准分偏高，建议复测观察情绪波动",
                    priority++));
        }
        for (AssessmentScale scale : scales) {
            if (items.size() >= MAX_RECOMMENDATIONS) {
                break;
            }
            if (latestByScale.containsKey(scale.getId())) {
                continue;
            }
            items.add(new AssessmentRecommendationVO(
                    scale.getId(),
                    scale.getName(),
                    scale.getDescription(),
                    "尚未测评过，可作为补充了解",
                    priority++));
        }
        return items;
    }

    private AssessmentFavoriteVO toFavoriteView(
            AssessmentScale scale,
            AssessmentFavorite favorite) {
        if (scale == null) {
            return null;
        }
        return new AssessmentFavoriteVO(
                scale.getId(),
                scale.getName(),
                scale.getDescription(),
                scale.getApplicableScene(),
                Boolean.TRUE.equals(scale.getPublished()),
                favorite.getCreatedAt());
    }

    /** 匿名访问量表列表时不带收藏信息，登录后按当前用户收藏标记。 */
    private Set<Long> findFavoritedScaleIds() {
        return currentUserProvider.findUserId()
                .map(favoriteMapper::findByUserIdOrderByCreatedAtDesc)
                .map(favorites -> favorites.stream()
                        .map(AssessmentFavorite::getScaleId)
                        .collect(Collectors.toSet()))
                .orElseGet(Set::of);
    }

    private AssessmentScaleVO toScaleView(AssessmentScale scale, Set<Long> favoritedScaleIds) {
        return new AssessmentScaleVO(
                scale.getId(),
                scale.getName(),
                scale.getDescription(),
                scale.getApplicableScene(),
                scale.getPublished(),
                favoritedScaleIds.contains(scale.getId()));
    }

    /** 一次批量查询量表名称，避免逐条查询历史记录对应的量表。 */
    private Map<Long, String> findScaleNames(List<AssessmentResult> results) {
        Set<Long> scaleIds = results.stream()
                .map(AssessmentResult::getScaleId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        if (scaleIds.isEmpty()) {
            return Map.of();
        }
        return mapper.selectBatchIds(scaleIds).stream()
                .filter(scale -> scale.getName() != null)
                .collect(Collectors.toMap(AssessmentScale::getId, AssessmentScale::getName));
    }

    /** 报告建议固定为两条，接口响应与落库内容使用同一份数据。 */
    private List<String> buildSuggestions() {
        return List.of(
                "保持规律作息；必要时咨询专业机构",
                "如情绪困扰持续或加重，建议咨询专业机构");
    }

    /** 历史记录按行还原建议列表，兼容早期只保存单条文本的数据。 */
    private List<String> splitSuggestions(String suggestions) {
        if (suggestions == null || suggestions.isBlank()) {
            return List.of();
        }
        return Arrays.stream(suggestions.split(SUGGESTION_SEPARATOR))
                .map(String::trim)
                .filter(item -> !item.isEmpty())
                .collect(Collectors.toList());
    }

    private String buildScaleContext(String scaleName) {
        List<Document> documents = vectorStore.similaritySearch(scaleName);
        if (documents == null || documents.isEmpty()) {
            return "暂无量表解读知识库内容";
        }
        return documents.stream()
                .map(Document::getText)
                .filter(java.util.Objects::nonNull)
                .limit(3)
                .collect(java.util.stream.Collectors.joining("\n\n"));
    }

    private String normalizeQuestionType(String type) {
        String normalized = type == null ? "SINGLE" : type.trim().toUpperCase();
        if (!List.of("SINGLE", "MULTIPLE", "TEXT", "JUDGE", "MATRIX").contains(normalized)) {
            throw new IllegalArgumentException("不支持的题目类型");
        }
        return normalized;
    }

    private void validateQuestionOptions(String type, List<OptionRequest> options) {
        if ("TEXT".equals(type) || "MATRIX".equals(type)) {
            return;
        }
        if (options == null || options.isEmpty()) {
            throw new IllegalArgumentException("选择题至少需要一个选项");
        }
        if ("JUDGE".equals(type) && options.size() != 2) {
            throw new IllegalArgumentException("判断题必须有两个选项");
        }
    }

    private int maxQuestionScore(AssessmentQuestion question) {
        String type = normalizeQuestionType(question.getType());
        if ("TEXT".equals(type) || "MATRIX".equals(type)) {
            return 0;
        }
        if ("MULTIPLE".equals(type)) {
            return question.getOptions().stream()
                    .mapToInt(option -> option.getScore() == null ? 0 : option.getScore())
                    .filter(scoreValue -> scoreValue > 0)
                    .sum();
        }
        return question.getOptions().stream()
                .mapToInt(option -> option.getScore() == null ? 0 : option.getScore())
                .max()
                .orElse(0);
    }

    private List<Long> answerOptionIds(
            AssessmentSubmitRequest.Answer answer,
            String type) {
        if ("MULTIPLE".equals(type)) {
            return answer.getOptionIds() == null ? List.of() : answer.getOptionIds();
        }
        return answer.getOptionId() == null ? List.of() : List.of(answer.getOptionId());
    }

    private void validateMatrixAnswer(
            AssessmentSubmitRequest.Answer answer,
            AssessmentQuestion question) {
        List<String> rows = splitMatrixValues(question.getMatrixRows());
        List<String> columns = splitMatrixValues(question.getMatrixColumns());
        if (rows.isEmpty() || columns.isEmpty()
                || answer.getMatrixAnswers() == null
                || answer.getMatrixAnswers().size() != rows.size()
                || answer.getMatrixAnswers().values().stream().anyMatch(value -> value == null)) {
            throw new IllegalArgumentException("请完成全部矩阵题目");
        }
        Set<Long> optionIds = new HashSet<>(answer.getMatrixAnswers().values());
        Set<Long> validOptionIds = question.getOptions().stream()
                .map(AssessmentOption::getId)
                .collect(Collectors.toSet());
        if (!validOptionIds.containsAll(optionIds)) {
            throw new IllegalArgumentException("矩阵题选项无效");
        }
    }

    private List<String> splitMatrixValues(String values) {
        if (values == null || values.isBlank()) {
            return List.of();
        }
        return List.of(values.split("\\n"));
    }
}
