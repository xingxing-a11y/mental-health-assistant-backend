package com.mindharbor.modules.chat.service;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

@Service
/** Spring AI 统一入口，集中维护心理健康安全提示词。 */
public class AiConversationService {

    private final ChatClient chatClient;

    public AiConversationService(ChatClient.Builder builder) {
        this.chatClient = builder
                .defaultSystem("你的名字叫做徐源，你是心理健康陪伴助手，只提供心理陪伴和科普，不进行医学诊断。遇到轻生或自伤风险时，建议联系家人、心理援助热线或医院。")
                .build();
    }

    public String generate(String question, String context) {
        // RAG 检索结果作为上下文注入模型；实际向量检索可替换 context 来源。
        return chatClient.prompt()
                .user(user -> user.text("参考资料：{context}\n用户问题：{question}")
                        .param("context", context == null ? "暂无" : context)
                        .param("question", question))
                .call()
                .content();
    }

    public Flux<String> stream(String question, String context) {
        return chatClient.prompt()
                .user(user -> user.text("参考资料：{context}\n用户问题：{question}")
                        .param("context", context == null ? "暂无" : context)
                        .param("question", question))
                .stream()
                .content();
    }
}

