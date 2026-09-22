package com.mindharbor.modules.chat.service;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

/**
 * Spring AI 统一入口，集中维护心理健康安全提示词。
 *
 * <p>每次请求都会把服务端当前时间写进用户消息 —— 模型自身没有时钟，不注入就无法回答日期类
 * 问题，也换算不了「最近两周」这类相对时间。
 */
@Service
public class AiConversationService {

    private static final String SYSTEM_PROMPT =
            "你的名字叫做小暖，你是心理健康陪伴助手，只提供心理陪伴和科普，不进行医学诊断。"
                    + "遇到轻生或自伤风险时，建议联系家人、心理援助热线或医院。"
                    + "涉及日期或时间的问题，以对话中给出的「当前时间」为准，不要凭记忆推测。";

    /** 统一按东八区呈现时间，与 DiaryServiceImpl 的 APP_ZONE 保持一致。 */
    private static final ZoneId APP_ZONE = ZoneId.of("Asia/Shanghai");

    private static final DateTimeFormatter TIME_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm EEEE", Locale.CHINA);

    /** 包级可见：测试要断言三个占位符齐全，不对外暴露。 */
    static final String USER_TEMPLATE =
            "当前时间：{time}\n参考资料：{context}\n用户问题：{question}";

    private final ChatClient chatClient;

    public AiConversationService(ChatClient.Builder builder) {
        this.chatClient = builder.defaultSystem(SYSTEM_PROMPT).build();
    }

    /** 单轮生成，参考资料由业务层检索后注入。 */
    public String generate(String question, String context) {
        return chatClient.prompt()
                .user(user -> user.text(USER_TEMPLATE)
                        .param("time", currentAppTime())
                        .param("context", context == null ? "暂无" : context)
                        .param("question", question))
                .call()
                .content();
    }

    /** 流式生成，供 SSE 接口逐段返回。 */
    public Flux<String> stream(String question, String context) {
        return chatClient.prompt()
                .user(user -> user.text(USER_TEMPLATE)
                        .param("time", currentAppTime())
                        .param("context", context == null ? "暂无" : context)
                        .param("question", question))
                .stream()
                .content();
    }

    /** 包级可见：纯函数，单测据此断言格式，不对外暴露。 */
    static String formatAppTime(LocalDateTime time) {
        return time.format(TIME_FORMATTER);
    }

    /** 每次请求现取，绝不能提成静态常量 —— 否则服务跑过午夜就停在昨天。 */
    private static String currentAppTime() {
        return formatAppTime(LocalDateTime.now(APP_ZONE));
    }
}
