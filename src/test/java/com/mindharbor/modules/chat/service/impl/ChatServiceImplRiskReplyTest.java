package com.mindharbor.modules.chat.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mindharbor.modules.chat.dto.ChatMessageRequest;
import com.mindharbor.modules.chat.entity.ChatMessage;
import com.mindharbor.modules.chat.entity.ChatSession;
import com.mindharbor.modules.chat.mapper.ChatMessageMapper;
import com.mindharbor.modules.chat.mapper.ChatSessionMapper;
import com.mindharbor.modules.chat.service.SensitiveWordService;
import com.mindharbor.modules.chat.vo.SensitiveCheckVO;
import com.mindharbor.modules.risk.service.RiskAlertService;
import com.mindharbor.modules.user.entity.User;
import com.mindharbor.modules.user.mapper.UserMapper;
import com.mindharbor.security.CurrentUserProvider;
import java.io.IOException;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import reactor.core.publisher.Flux;

/**
 * 高危对话的回复必须落库。
 *
 * <p>背景：原实现只把危机提示通过 SSE 推给当前页面，不写 chat_messages。
 * 用户刷新后只剩自己那句话，看起来像没人回应；管理员事后也查不到当时给过什么帮助。
 */
class ChatServiceImplRiskReplyTest {

    private static final long USER_ID = 7L;
    private static final long SESSION_ID = 21L;

    private final List<ChatMessage> inserted = new ArrayList<>();

    private ChatSession session;

    @Test
    @DisplayName("高危对话：危机提示写进 chat_messages，角色为 ASSISTANT")
    void highRiskReplyIsPersisted() {
        ChatServiceImpl service = serviceWith(new SensitiveCheckVO(
                true, SensitiveCheckVO.Level.HIGH,
                "你提到的内容让我们很担心你。", "全国心理援助热线 12356"));

        ChatMessageRequest request = new ChatMessageRequest();
        request.setContent("我想轻生");

        service.stream(SESSION_ID, request);

        assertEquals(2, inserted.size(), "应写入用户消息与危机回复两条");
        assertEquals(ChatMessage.Role.USER, inserted.get(0).getRole());
        assertEquals("我想轻生", inserted.get(0).getContent());

        ChatMessage reply = inserted.get(1);
        assertEquals(ChatMessage.Role.ASSISTANT, reply.getRole());
        assertEquals("你提到的内容让我们很担心你。\n心理援助热线：全国心理援助热线 12356",
                reply.getContent(),
                "正文与热线的拼法要和前端本地渲染一致，否则刷新前后看到的是两段不同的话");
        assertNotNull(reply.getId(), "落库后要有主键，否则 done 事件拿不到消息号");

        // 会话预览保持中性，不把危机话术写进列表
        assertEquals("已触发危机干预提示", session.getPreview());
        assertTrue(session.isHighRisk());
        // 标题脱敏：列表里不能再出现用户原话
        assertEquals(ChatServiceImpl.riskSessionName(), session.getName());
        assertTrue(!session.getName().contains("轻生"), "会话标题不能带用户原话");
    }

    @Test
    @DisplayName("浏览器断开：生成继续跑完，回复照样落库")
    void replySurvivesClientDisconnect() throws Exception {
        ChatServiceImpl service = serviceWith(new SensitiveCheckVO(
                true, SensitiveCheckVO.Level.NONE, null, null));
        session.setId(SESSION_ID);

        // 模拟已经断开的连接：任何 send 都抛 IOException
        SseEmitter brokenPipe = new SseEmitter() {
            @Override
            public void send(SseEventBuilder builder) throws IOException {
                throw new IOException("Broken pipe");
            }
        };

        service.streamReply(brokenPipe, session, Flux.just("你", "好"));

        assertEquals(1, inserted.size(), "连接断了也必须把完整回复写进库");
        assertEquals("你好", inserted.get(0).getContent());
        assertEquals(ChatMessage.Role.ASSISTANT, inserted.get(0).getRole());
    }

    @Test
    @DisplayName("热线为空：不留一个空的「心理援助热线：」尾巴")
    void blankHotlineOmitsSuffix() {
        String reply = ChatServiceImpl.crisisReply(new SensitiveCheckVO(
                true, SensitiveCheckVO.Level.HIGH, "先照顾好自己。", "  "));

        assertEquals("先照顾好自己。", reply);
    }

    private ChatServiceImpl serviceWith(SensitiveCheckVO check) {
        session = new ChatSession();
        session.setId(SESSION_ID);
        session.setUserId(USER_ID);
        session.setName("新会话");

        ChatSessionMapper sessionMapper = (ChatSessionMapper) Proxy.newProxyInstance(
                ChatSessionMapper.class.getClassLoader(),
                new Class<?>[] {ChatSessionMapper.class},
                (proxy, method, args) -> {
                    if (method.getDeclaringClass() == Object.class) {
                        return null;
                    }
                    return switch (method.getName()) {
                        case "findById" -> Optional.of(session);
                        case "updateById" -> 1;
                        default -> null;
                    };
                });

        ChatMessageMapper messageMapper = (ChatMessageMapper) Proxy.newProxyInstance(
                ChatMessageMapper.class.getClassLoader(),
                new Class<?>[] {ChatMessageMapper.class},
                (proxy, method, args) -> {
                    if (method.getDeclaringClass() == Object.class) {
                        return null;
                    }
                    if ("insert".equals(method.getName())) {
                        ChatMessage message = (ChatMessage) args[0];
                        message.setId((long) inserted.size() + 1);
                        inserted.add(message);
                        return 1;
                    }
                    return null;
                });

        User user = new User();
        user.setId(USER_ID);
        UserMapper userMapper = (UserMapper) Proxy.newProxyInstance(
                UserMapper.class.getClassLoader(),
                new Class<?>[] {UserMapper.class},
                (proxy, method, args) -> {
                    if (method.getDeclaringClass() == Object.class) {
                        return null;
                    }
                    return "findById".equals(method.getName()) ? Optional.of(user) : null;
                });

        // 接口有两个方法，不能用 lambda
        SensitiveWordService sensitiveWordService = new SensitiveWordService() {
            @Override
            public SensitiveCheckVO inspect(String content) {
                return check;
            }

            @Override
            public SensitiveCheckVO inspectAndAudit(String content, Long sessionId) {
                return check;
            }
        };

        RiskAlertService riskAlertService = (RiskAlertService) Proxy.newProxyInstance(
                RiskAlertService.class.getClassLoader(),
                new Class<?>[] {RiskAlertService.class},
                (proxy, method, args) -> {
                    if (method.getDeclaringClass() == Object.class) {
                        return null;
                    }
                    // 投递失败也不能影响危机提示，这里直接返回成功
                    return method.getReturnType() == boolean.class ? false : null;
                });

        return new ChatServiceImpl(
                sessionMapper,
                messageMapper,
                userMapper,
                null,                 // AiConversationService：高危分支不调用模型
                sensitiveWordService,
                currentUser(USER_ID),
                new ObjectMapper(),
                null,                 // VectorStore：高危分支不检索知识库
                riskAlertService);
    }

    private static CurrentUserProvider currentUser(long userId) {
        return new CurrentUserProvider() {
            @Override
            public Optional<Long> findUserId() {
                return Optional.of(userId);
            }

            @Override
            public Long getUserId() {
                return userId;
            }
        };
    }
}
