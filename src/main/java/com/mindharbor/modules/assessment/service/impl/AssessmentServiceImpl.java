package com.mindharbor.modules.assessment.service.impl;

import com.mindharbor.modules.assessment.dto.AssessmentSubmitRequest;
import com.mindharbor.modules.assessment.dto.QuestionRequest;
import com.mindharbor.modules.assessment.dto.ScaleRequest;
import com.mindharbor.modules.assessment.entity.AssessmentScale;
import com.mindharbor.modules.assessment.entity.AssessmentQuestion;
import com.mindharbor.modules.assessment.entity.AssessmentOption;
import com.mindharbor.modules.assessment.dto.QuestionRequest.OptionRequest;
import java.util.ArrayList;
import com.mindharbor.modules.assessment.mapper.AssessmentScaleMapper;
import com.mindharbor.modules.assessment.mapper.AssessmentQuestionMapper;
import com.mindharbor.modules.assessment.mapper.AssessmentResultMapper;
import com.mindharbor.modules.assessment.mapper.AssessmentOptionMapper;
import com.mindharbor.modules.user.mapper.UserMapper;
import com.mindharbor.modules.assessment.entity.AssessmentResult;
import com.mindharbor.modules.assessment.service.AssessmentService;
import java.util.List;
import java.util.Map;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.context.annotation.Lazy;
import com.mindharbor.security.CurrentUserProvider;
import com.mindharbor.modules.chat.service.AiConversationService;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;

@Service
public class AssessmentServiceImpl implements AssessmentService {

    private final AssessmentScaleMapper mapper;
    private final AssessmentQuestionMapper questionMapper;
    private final AssessmentResultMapper resultMapper;
    private final AssessmentOptionMapper optionMapper;
    private final UserMapper userMapper;
    private final CurrentUserProvider currentUserProvider;
    private final AiConversationService aiService;
    private final VectorStore vectorStore;

    public AssessmentServiceImpl(
            AssessmentScaleMapper mapper,
            AssessmentQuestionMapper questionMapper,
            AssessmentResultMapper resultMapper,
            AssessmentOptionMapper optionMapper,
            UserMapper userMapper,
            CurrentUserProvider currentUserProvider,
            AiConversationService aiService,
            @Lazy VectorStore vectorStore) {
        this.mapper = mapper;
        this.questionMapper = questionMapper;
        this.resultMapper = resultMapper;
        this.optionMapper = optionMapper;
        this.userMapper = userMapper;
        this.currentUserProvider = currentUserProvider;
        this.aiService = aiService;
        this.vectorStore = vectorStore;
    }

    @Override
    public List<?> scales() {
        return mapper.findAll();
    }

    @Override
    public Object detail(Long scaleId) {
        AssessmentScale scale = mapper.findById(scaleId).orElseThrow();
        List<AssessmentQuestion> questions = questionMapper.findByScaleIdOrderByQuestionOrderAsc(scaleId);
        questions.forEach(question -> question.setOptions(optionMapper.findByQuestionId(question.getId())));
        return Map.of(
                "id", scale.getId(),
                "name", scale.getName(),
                "description", scale.getDescription(),
                "applicableScene", scale.getApplicableScene(),
                "questions", questions);
    }

    @Override
    public AssessmentScale create(ScaleRequest request) {
        AssessmentScale scale = new AssessmentScale();
        scale.setName(request.getName());
        scale.setDescription(request.getDescription());
        scale.setApplicableScene(request.getApplicableScene());
        mapper.insert(scale);
        return scale;
    }

    @Override
    public AssessmentScale update(Long scaleId, ScaleRequest request) {
        AssessmentScale scale = mapper.findById(scaleId).orElseThrow();
        scale.setName(request.getName());
        scale.setDescription(request.getDescription());
        scale.setApplicableScene(request.getApplicableScene());
        mapper.updateById(scale);
        return scale;
    }

    @Override
    public void delete(Long scaleId) {
        mapper.deleteById(scaleId);
    }

    @Override
    public List<AssessmentQuestion> replaceQuestions(
            Long scaleId,
            List<QuestionRequest> requests) {
        AssessmentScale scale = mapper.findById(scaleId).orElseThrow();
        List<AssessmentQuestion> questions = questionMapper.findByScaleIdOrderByQuestionOrderAsc(scaleId);
        questions.forEach(question -> {
            optionMapper.deleteByQuestionId(question.getId());
            questionMapper.deleteById(question.getId());
        });
        List<AssessmentQuestion> result = new ArrayList<>();

        //添加题目
        for (QuestionRequest request : requests) {
            AssessmentQuestion question = new AssessmentQuestion();
            question.setScaleId(scale.getId());
            question.setScale(scale);
            question.setQuestionOrder(request.getOrder());
            question.setContent(request.getContent());
            //获取题目类型
            String type = normalizeQuestionType(request.getType());
            question.setType(type);
            question.setMatrixRows(joinMatrixValues(request.getMatrixRows()));
            question.setMatrixColumns(joinMatrixValues(request.getMatrixColumns()));
            validateQuestionOptions(type, request.getOptions());

            //设置题目标签和分值
            for (OptionRequest optionRequest : request.getOptions() == null
                    ? List.<OptionRequest>of()
                    : request.getOptions()) {
                AssessmentOption option = new AssessmentOption();
                option.setLabel(optionRequest.getLabel());
                option.setScore(optionRequest.getScore());
                question.getOptions().add(option);
            }
            //添加题目到问卷列表
            result.add(question);
        }

        //遍历列表将题目插入到问卷中
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
    public Object submit(AssessmentSubmitRequest request) {
        if (request == null || request.getScaleId() == null || request.getAnswers() == null) {
            throw new IllegalArgumentException("测评参数不能为空");
        }

        mapper.findById(request.getScaleId())
                .orElseThrow(() -> new IllegalArgumentException("量表不存在"));

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
        String suggestions = "保持规律作息；必要时咨询专业机构";
        AssessmentScale scale = mapper.findById(request.getScaleId()).orElseThrow();
        AssessmentResult result = new AssessmentResult();
        result.setUserId(currentUserProvider.getUserId());
        result.setUser(userMapper.findById(currentUserProvider.getUserId()).orElseThrow());
        result.setScale(scale);
        result.setTotalScore(score);
        result.setStandardScore(maxScore == 0 ? 0D : score * 100D / maxScore);
        result.setRangeDescription(range + "，结果仅供参考，不构成诊断");
        result.setSuggestions(suggestions);
        resultMapper.insert(result);

        return Map.of(
                "resultId", result.getId(),
                "totalScore", score,
                "standardScore", result.getStandardScore(),
                "rangeDescription", range + "，结果仅供参考，不构成诊断",
                "suggestions", List.of(
                        suggestions,
                        "如情绪困扰持续或加重，建议咨询专业机构"));
    }

    @Override
    public Object analyze(Long resultId) {
        AssessmentResult result = resultMapper.selectById(resultId);
        if (result == null || !currentUserProvider.getUserId().equals(result.getUserId())) {
            throw new IllegalArgumentException("测评结果不存在");
        }

        AssessmentScale scale = mapper.findById(result.getScaleId()).orElseThrow();
        String analysis = aiService.generate(
                "请根据量表“" + scale.getName() + "”的总分 " + result.getTotalScore()
                        + "，给出温和、简短的情绪倾向解读和适当的安慰建议。禁止医学诊断，"
                        + "不要夸大风险，不要编造用户经历。",
                buildScaleContext(scale.getName()));
        String safeAnalysis = analysis == null || analysis.isBlank()
                ? "暂时无法生成 AI 分析，请稍后再试。"
                : analysis;
        result.setSuggestions(safeAnalysis);
        resultMapper.updateById(result);
        return Map.of("analysis", safeAnalysis);
    }

    @Override
    public List<?> history() {
        return resultMapper.findByUserIdOrderByCreatedAtDesc(currentUserProvider.getUserId());
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

    private String joinMatrixValues(List<String> values) {
        if (values == null) {
            return null;
        }
        return values.stream()
                .map(value -> value == null ? "" : value.trim())
                .filter(value -> !value.isEmpty())
                .limit(30)
                .collect(Collectors.joining("\n"));
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
