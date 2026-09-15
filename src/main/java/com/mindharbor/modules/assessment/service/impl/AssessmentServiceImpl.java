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

        for (QuestionRequest request : requests) {
            AssessmentQuestion question = new AssessmentQuestion();
            question.setScaleId(scale.getId());
            question.setScale(scale);
            question.setQuestionOrder(request.getOrder());
            question.setContent(request.getContent());

            for (OptionRequest optionRequest : request.getOptions()) {
                AssessmentOption option = new AssessmentOption();
                option.setLabel(optionRequest.getLabel());
                option.setScore(optionRequest.getScore());
                question.getOptions().add(option);
            }
            result.add(question);
        }

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
        scaleQuestions.forEach(question -> question.setOptions(optionMapper.findByQuestionId(question.getId())));
        if (scaleQuestions.isEmpty()) {
            throw new IllegalArgumentException("当前量表暂无题目");
        }
        if (request.getAnswers().size() != scaleQuestions.size()) {
            throw new IllegalArgumentException("请完成全部题目后再提交");
        }

        Set<Long> answeredQuestionIds = new HashSet<>();
        for (AssessmentQuestion question : scaleQuestions) {
            maxScore += question.getOptions().stream()
                    .mapToInt(option -> option.getScore() == null ? 0 : option.getScore())
                    .max()
                    .orElse(0);
        }
        for (AssessmentSubmitRequest.Answer answer : request.getAnswers()) {
            if (answer == null
                    || answer.getQuestionId() == null
                    || answer.getOptionId() == null
                    || !answeredQuestionIds.add(answer.getQuestionId())) {
                throw new IllegalArgumentException("题目答案存在重复或缺失");
            }
            AssessmentQuestion question = questionMapper
                    .findByIdAndScaleId(answer.getQuestionId(), request.getScaleId())
                    .orElseThrow(() -> new IllegalArgumentException("题目不属于当前量表"));
            AssessmentOption option = question.getOptions()
                    .stream()
                    .filter(item -> item.getId().equals(answer.getOptionId()))
                    .findFirst()
                    .orElseThrow(() -> new IllegalArgumentException("选项不存在"));
            score += option.getScore();
        }
        if (answeredQuestionIds.size() != scaleQuestions.size()) {
            throw new IllegalArgumentException("请完成全部题目后再提交");
        }

        String range = score < 30 ? "情绪状态总体平稳" : "情绪可能存在一定波动";
        String suggestions = "保持规律作息；必要时咨询专业机构";
        AssessmentScale scale = mapper.findById(request.getScaleId()).orElseThrow();
        String interpretation = aiService.generate(
                "请根据量表“" + scale.getName() + "”的总分 " + score
                        + "，给出仅描述情绪倾向的简短解读和自我调节建议。禁止医学诊断。",
                buildScaleContext(scale.getName()));
        AssessmentResult result = new AssessmentResult();
        result.setUser(userMapper.findById(currentUserProvider.getUserId()).orElseThrow());
        result.setScale(scale);
        result.setTotalScore(score);
        result.setStandardScore(maxScore == 0 ? 0D : score * 100D / maxScore);
        result.setRangeDescription(range + "，结果仅供参考，不构成诊断");
        result.setSuggestions(interpretation == null || interpretation.isBlank()
                ? suggestions
                : interpretation);
        resultMapper.insert(result);

        return Map.of(
                "totalScore", score,
                "standardScore", result.getStandardScore(),
                "rangeDescription", range + "，结果仅供参考，不构成诊断",
                "suggestions", List.of(
                        interpretation == null || interpretation.isBlank()
                                ? suggestions
                                : interpretation,
                        "如情绪困扰持续或加重，建议咨询专业机构"));
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
}
