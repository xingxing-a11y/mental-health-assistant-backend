package com.mindharbor.modules.assessment.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mindharbor.modules.assessment.dto.AssessmentSubmitRequest;
import com.mindharbor.modules.assessment.entity.AssessmentOption;
import com.mindharbor.modules.assessment.entity.AssessmentQuestion;
import com.mindharbor.modules.assessment.entity.AssessmentResult;
import com.mindharbor.modules.assessment.entity.AssessmentScale;
import com.mindharbor.modules.assessment.mapper.AssessmentOptionMapper;
import com.mindharbor.modules.assessment.mapper.AssessmentQuestionMapper;
import com.mindharbor.modules.assessment.mapper.AssessmentResultMapper;
import com.mindharbor.modules.assessment.mapper.AssessmentScaleMapper;
import com.mindharbor.modules.assessment.vo.AssessmentReportVO;
import com.mindharbor.modules.user.entity.User;
import com.mindharbor.modules.user.mapper.UserMapper;
import com.mindharbor.security.CurrentUserProvider;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 测评提交的「选项从哪来」与题型分叉。
 *
 * <p>{@code assessment_options} 与题目分属两张表，{@code AssessmentQuestion.options} 是
 * {@code @TableField(exist = false)}——它只是内存字段，查题目时不会带出任何选项，必须查完
 * 选项手工塞进去才有值。
 *
 * <p>因此提交循环里一旦按题目 id 重新查一次库，拿到的是<b>另一个</b> options 恒为空的题目对象，
 * 于是单选题一律报「选项不存在」、矩阵题一律报「矩阵题选项无效」——只有整卷全是 TEXT 的卷子
 * 能提交成功。这类故障不报错也不写日志，只会在用户点「提交」时冒出一句无厘头的提示。
 *
 * <p>这里逐题型固定住「选项来自加载题目那一次填充」，并用「不得再按 id 重查」把这次修复钉死：
 * 谁把实现改回按 id 单查，本类用例立即变红。
 */
@DisplayName("测评提交")
class AssessmentServiceImplSubmitTest {

    private static final long SCALE_ID = 1L;

    private static final long USER_ID = 7L;

    @Test
    @DisplayName("单选题：从已填充的题目对象取选项，按选项分值计分")
    void singleChoiceScoresSelectedOption() {
        Fixture fixture = new Fixture()
                .question(question(11L, "SINGLE"))
                .options(11L, option(101L, 11L, "偶尔", 1), option(102L, 11L, "经常", 3));

        AssessmentReportVO report = fixture.submit(answerWithOption(11L, 102L));

        assertEquals(3, report.getTotalScore());
        assertEquals(100D, report.getStandardScore());
        assertTrue(fixture.questionMapper.reloads.isEmpty(),
                "选项已在加载题目时填充，再按 id 重查只会拿到空选项，必致「选项不存在」");
    }

    @Test
    @DisplayName("标准分保留小数：分子分母不整除时不能被整数除法截断")
    void standardScoreKeepsFraction() {
        Fixture fixture = new Fixture()
                .question(question(11L, "SINGLE"))
                .options(11L, option(101L, 11L, "偶尔", 1), option(102L, 11L, "经常", 3));

        AssessmentReportVO report = fixture.submit(answerWithOption(11L, 101L));

        assertEquals(1, report.getTotalScore());
        assertEquals(100D / 3D, report.getStandardScore().doubleValue(), 1e-9,
                "score * 100 / maxScore 一旦漏掉那个 D，1/3 会被整数除法截断成 33.0");
    }

    @Test
    @DisplayName("多选题：分母是正分之和而非最大分值，全选正好满分")
    void multipleChoiceSumsSelectedOptions() {
        Fixture fixture = new Fixture()
                .question(question(11L, "MULTIPLE"))
                .options(11L, option(101L, 11L, "从不", 1), option(102L, 11L, "总是", 3));

        AssessmentSubmitRequest.Answer answer = new AssessmentSubmitRequest.Answer();
        answer.setQuestionId(11L);
        answer.setOptionIds(List.of(101L, 102L));

        AssessmentReportVO report = fixture.submit(answer);

        assertEquals(4, report.getTotalScore());
        assertEquals(100D, report.getStandardScore(),
                "多选的分母是所有正分之和，若误用最大分值会算出 133.33");
    }

    @Test
    @DisplayName("判断题：两个选项取到分值，不做成空选项")
    void judgeQuestionScores() {
        Fixture fixture = new Fixture()
                .question(question(11L, "JUDGE"))
                .options(11L, option(101L, 11L, "是", 0), option(102L, 11L, "否", 5));

        AssessmentReportVO report = fixture.submit(answerWithOption(11L, 102L));

        assertEquals(5, report.getTotalScore());
        assertEquals(100D, report.getStandardScore());
    }

    @Test
    @DisplayName("矩阵题：选项集合非空，合法的行列填写被接受")
    void matrixQuestionAcceptsValidOptionIds() {
        AssessmentQuestion matrix = question(11L, "MATRIX");
        matrix.setMatrixRows("睡眠\n食欲");
        matrix.setMatrixColumns("从不\n总是");
        Fixture fixture = new Fixture()
                .question(matrix)
                .options(11L, option(101L, 11L, "从不", 0), option(102L, 11L, "总是", 0));

        AssessmentSubmitRequest.Answer answer = new AssessmentSubmitRequest.Answer();
        answer.setQuestionId(11L);
        answer.setMatrixAnswers(Map.of("睡眠", 101L, "食欲", 102L));

        AssessmentReportVO report = fixture.submit(answer);

        assertEquals(0, report.getTotalScore(), "矩阵题不计分");
        assertEquals(0D, report.getStandardScore(), "分母为 0 时标准分取 0，不能除零");
        assertTrue(fixture.questionMapper.reloads.isEmpty(),
                "矩阵校验同样读 question.getOptions()，重查会让合法选项被判无效");
    }

    @Test
    @DisplayName("文本题：不读选项，提交原文即可通过")
    void textQuestionAcceptsFreeText() {
        Fixture fixture = new Fixture().question(question(11L, "TEXT"));

        AssessmentSubmitRequest.Answer answer = new AssessmentSubmitRequest.Answer();
        answer.setQuestionId(11L);
        answer.setTextAnswer("最近两周入睡困难，白天容易疲惫。");

        AssessmentReportVO report = fixture.submit(answer);

        assertEquals(0, report.getTotalScore());
        assertEquals(0D, report.getStandardScore());
        assertEquals(1, fixture.resultMapper.inserts, "提交成功必须落一条测评结果");
        assertEquals(
                String.join("\n", report.getSuggestions()),
                fixture.resultMapper.inserted.getSuggestions(),
                "落库的建议文本与响应里的列表必须同源，历史接口才拆得回来");
    }

    @Test
    @DisplayName("选中「其他」选项却未填说明：拒绝提交")
    void otherOptionRequiresText() {
        Fixture fixture = new Fixture()
                .question(question(11L, "SINGLE"))
                .options(11L, option(101L, 11L, "其他", 0), option(102L, 11L, "从不", 3));

        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> fixture.submit(answerWithOption(11L, 101L)));

        assertEquals("请填写其他选项内容", error.getMessage(),
                "若判成「选项不存在」，说明又去按 id 重查了题目，选项集合是空的");
    }

    @Test
    @DisplayName("答案里的题目不属于当前量表：提示题目不属于该量表")
    void answerFromAnotherScaleIsRejected() {
        Fixture fixture = new Fixture()
                .question(question(11L, "SINGLE"))
                .options(11L, option(101L, 11L, "偶尔", 1));

        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> fixture.submit(answerWithOption(99L, 101L)));

        assertEquals("题目不属于当前量表", error.getMessage());
    }

    private static AssessmentQuestion question(long id, String type) {
        AssessmentQuestion question = new AssessmentQuestion();
        question.setId(id);
        question.setScaleId(SCALE_ID);
        question.setQuestionOrder((int) id);
        question.setContent("题目 " + id);
        question.setType(type);
        return question;
    }

    private static AssessmentOption option(long id, long questionId, String label, Integer score) {
        AssessmentOption option = new AssessmentOption();
        option.setId(id);
        option.setQuestionId(questionId);
        option.setLabel(label);
        option.setScore(score);
        return option;
    }

    private static AssessmentSubmitRequest.Answer answerWithOption(long questionId, long optionId) {
        AssessmentSubmitRequest.Answer answer = new AssessmentSubmitRequest.Answer();
        answer.setQuestionId(questionId);
        answer.setOptionId(optionId);
        return answer;
    }

    /**
     * 还原一次真实查询的产物：字段照抄，但 options 永远是新的空列表。
     * 若让两次查询返回同一个对象，被测代码对它的填充会「顺带」修好第二次查询，反而掩盖缺陷。
     */
    private static AssessmentQuestion copyOf(AssessmentQuestion template) {
        AssessmentQuestion question = new AssessmentQuestion();
        question.setId(template.getId());
        question.setScaleId(template.getScaleId());
        question.setQuestionOrder(template.getQuestionOrder());
        question.setContent(template.getContent());
        question.setType(template.getType());
        question.setMatrixRows(template.getMatrixRows());
        question.setMatrixColumns(template.getMatrixColumns());
        return question;
    }

    private static CurrentUserProvider currentUser(long userId) {
        return new CurrentUserProvider() {
            @Override
            public Optional<Long> findUserId() {
                return Optional.of(userId);
            }
        };
    }

    private static User user() {
        User user = new User();
        user.setId(USER_ID);
        user.setNickname("测试用户");
        return user;
    }

    private static AssessmentScale scale() {
        AssessmentScale scale = new AssessmentScale();
        scale.setId(SCALE_ID);
        scale.setName("情绪自评量表");
        scale.setPublished(true);
        return scale;
    }

    /** submit 只用得到量表、题目、选项、结果、用户五类协作者，其余传 null：它们不在这条路径上。 */
    private static final class Fixture {

        private final FakeQuestionMapper questionMapper = new FakeQuestionMapper();

        private final FakeResultMapper resultMapper = new FakeResultMapper();

        private final FakeOptionMapper optionMapper = new FakeOptionMapper();

        private Fixture question(AssessmentQuestion question) {
            questionMapper.questions.add(question);
            return this;
        }

        private Fixture options(long questionId, AssessmentOption... options) {
            optionMapper.options.put(questionId, List.of(options));
            return this;
        }

        private AssessmentReportVO submit(AssessmentSubmitRequest.Answer... answers) {
            return service().submit(new AssessmentSubmitRequest(SCALE_ID, List.of(answers)));
        }

        private AssessmentServiceImpl service() {
            return new AssessmentServiceImpl(
                    new FakeScaleMapper().asMapper(),
                    questionMapper.asMapper(),
                    resultMapper.asMapper(),
                    optionMapper.asMapper(),
                    null,   // AssessmentFavoriteMapper：提交路径不读收藏
                    new FakeUserMapper().asMapper(),
                    null,   // AdminOperationLogService：提交路径不写审计
                    currentUser(USER_ID),
                    null,   // AiConversationService：提交路径不生成解读
                    null);  // VectorStore：提交路径不检索知识库
        }
    }

    private static final class FakeQuestionMapper {

        private final List<AssessmentQuestion> questions = new ArrayList<>();

        /** 按题目 id 重查的次数，用于断言「不再逐题回库」。 */
        private final List<Long> reloads = new ArrayList<>();

        private AssessmentQuestionMapper asMapper() {
            return (AssessmentQuestionMapper) Proxy.newProxyInstance(
                    AssessmentServiceImplSubmitTest.class.getClassLoader(),
                    new Class<?>[] {AssessmentQuestionMapper.class},
                    (proxy, method, args) -> switch (method.getName()) {
                        case "findByScaleIdOrderByQuestionOrderAsc" ->
                                questions.stream().map(AssessmentServiceImplSubmitTest::copyOf).toList();
                        case "findByIdAndScaleId" -> {
                            reloads.add((Long) args[0]);
                            yield questions.stream()
                                    .filter(question -> question.getId().equals(args[0]))
                                    .findFirst()
                                    .map(AssessmentServiceImplSubmitTest::copyOf);
                        }
                        default -> throw new UnsupportedOperationException(method.getName());
                    });
        }
    }

    private static final class FakeOptionMapper {

        private final Map<Long, List<AssessmentOption>> options = new HashMap<>();

        private AssessmentOptionMapper asMapper() {
            return (AssessmentOptionMapper) Proxy.newProxyInstance(
                    AssessmentServiceImplSubmitTest.class.getClassLoader(),
                    new Class<?>[] {AssessmentOptionMapper.class},
                    (proxy, method, args) -> switch (method.getName()) {
                        case "findByQuestionId" -> options.getOrDefault((Long) args[0], List.of());
                        default -> throw new UnsupportedOperationException(method.getName());
                    });
        }
    }

    private static final class FakeScaleMapper {

        private AssessmentScaleMapper asMapper() {
            return (AssessmentScaleMapper) Proxy.newProxyInstance(
                    AssessmentServiceImplSubmitTest.class.getClassLoader(),
                    new Class<?>[] {AssessmentScaleMapper.class},
                    (proxy, method, args) -> switch (method.getName()) {
                        case "findById" -> Optional.of(scale());
                        default -> throw new UnsupportedOperationException(method.getName());
                    });
        }
    }

    private static final class FakeResultMapper {

        private AssessmentResult inserted;

        private int inserts;

        private AssessmentResultMapper asMapper() {
            return (AssessmentResultMapper) Proxy.newProxyInstance(
                    AssessmentServiceImplSubmitTest.class.getClassLoader(),
                    new Class<?>[] {AssessmentResultMapper.class},
                    (proxy, method, args) -> switch (method.getName()) {
                        case "insert" -> {
                            inserted = (AssessmentResult) args[0];
                            inserted.setId(901L);
                            inserts++;
                            yield 1;
                        }
                        default -> throw new UnsupportedOperationException(method.getName());
                    });
        }
    }

    private static final class FakeUserMapper {

        private UserMapper asMapper() {
            return (UserMapper) Proxy.newProxyInstance(
                    AssessmentServiceImplSubmitTest.class.getClassLoader(),
                    new Class<?>[] {UserMapper.class},
                    (proxy, method, args) -> switch (method.getName()) {
                        case "findById" -> Optional.of(user());
                        default -> throw new UnsupportedOperationException(method.getName());
                    });
        }
    }
}
