package com.mindharbor.modules.chat.service.impl;

import com.mindharbor.modules.chat.dto.ChatMessageRequest;
import com.mindharbor.modules.chat.dto.CreateSessionRequest;
import com.mindharbor.modules.chat.entity.ChatMessage;
import com.mindharbor.modules.chat.entity.ChatSession;
import com.mindharbor.modules.chat.mapper.ChatMessageMapper;
import com.mindharbor.modules.chat.mapper.ChatSessionMapper;
import com.mindharbor.modules.chat.service.AiConversationService;
import com.mindharbor.modules.chat.service.ChatService;
import com.mindharbor.modules.chat.dto.ChatSettingsRequest;
import com.mindharbor.modules.chat.vo.ChatSessionVO;
import com.mindharbor.modules.user.entity.User;
import com.mindharbor.modules.user.mapper.UserMapper;
import java.time.Instant;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import com.mindharbor.security.CurrentUserProvider;
import com.fasterxml.jackson.databind.ObjectMapper;
import reactor.core.Disposable;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;

@Service
public class ChatServiceImpl implements ChatService {

    private final ChatSessionMapper sessions;
    private final ChatMessageMapper messages;
    private final UserMapper users;
    private final AiConversationService aiService;
    private final CurrentUserProvider currentUserProvider;
    private final ObjectMapper objectMapper;
    private final VectorStore vectorStore;

    public ChatServiceImpl(
            ChatSessionMapper sessions,
            ChatMessageMapper messages,
            UserMapper users,
            AiConversationService aiService,
            CurrentUserProvider currentUserProvider,
            ObjectMapper objectMapper,
            VectorStore vectorStore) {
        this.sessions = sessions;
        this.messages = messages;
        this.users = users;
        this.aiService = aiService;
        this.currentUserProvider = currentUserProvider;
        this.objectMapper = objectMapper;
        this.vectorStore = vectorStore;
    }

    private User currentUser() {
        return users.findById(currentUserProvider.getUserId())
                .orElseThrow(() -> new IllegalStateException("当前没有可用用户"));
    }

    @Override
    public ChatSessionVO create(CreateSessionRequest request) {
        ChatSession session = new ChatSession();
        session.setUser(currentUser());
        session.setName(request == null || request.getName() == null || request.getName().isBlank()
                ? "新的对话"
                : request.getName());
        session.setPreview("还没有消息");
        return toView(sessions.save(session));
    }

    @Override
    public List<ChatSessionVO> list() {
        return sessions.findByUserIdOrderByLatestMessageAtDesc(currentUser().getId())
                .stream()
                .map(this::toView)
                .toList();
    }

    private ChatSessionVO toView(ChatSession session) {
        ChatSessionVO view = new ChatSessionVO();
        view.setId(session.getId());
        view.setName(session.getName());
        view.setPreview(session.getPreview());
        view.setLatestMessageAt(session.getLatestMessageAt());
        view.setMode(session.getMode());
        return view;
    }

    @Override
    public List<?> history(long sessionId) {
        getOwnedSession(sessionId);
        return messages.findBySessionIdOrderByCreatedAtAsc(sessionId);
    }

    @Override
    public void delete(long sessionId) {
        getOwnedSession(sessionId);
        messages.deleteBySessionId(sessionId);
        sessions.deleteById(sessionId);
    }

    @Override
    public void clearContext(long id) {
        ChatSession session = getOwnedSession(id);
        session.setContextCleared(true);
        sessions.save(session);
    }

    @Override
    public void updateSettings(long sessionId, ChatSettingsRequest request) {
        ChatSession session = getOwnedSession(sessionId);
        session.setMode(request.getMode());
        sessions.save(session);
    }

    @Override
    public void favoriteMessage(long messageId, boolean favorite) {
        ChatMessage message = messages.findById(messageId)
                .orElseThrow(() -> new IllegalArgumentException("消息不存在"));
        getOwnedSession(message.getSession().getId());
        if (message.getRole() != ChatMessage.Role.ASSISTANT) {
            throw new IllegalArgumentException("只能收藏 AI 消息");
        }
        message.setFavorite(favorite);
        messages.save(message);
    }

    @Override
    public List<?> favoriteMessages() {
        return messages.findBySessionUserIdAndFavoriteTrueOrderByCreatedAtDesc(
                currentUserProvider.getUserId());
    }

    @Override
    public SseEmitter stream(long id, ChatMessageRequest request) {
        SseEmitter emitter = new SseEmitter(120000L);
        String input = request == null ? "" : request.getContent();
        ChatSession ownedSession = getOwnedSession(id);

        ChatSession session = ownedSession;
        ChatMessage userMessage = new ChatMessage();
        userMessage.setSession(session);
        userMessage.setRole(ChatMessage.Role.USER);
        userMessage.setContent(input);
        messages.save(userMessage);

        if (isHighRisk(input)) {
            sendRiskAndComplete(emitter, session, userMessage);
            return emitter;
        }

        try {
            send(emitter, "ping", "heartbeat");
        } catch (Exception exception) {
            emitter.completeWithError(exception);
            return emitter;
        }

        StringBuilder reply = new StringBuilder();
        String context = buildKnowledgeContext(input, session);
        session.setContextCleared(false);
        sessions.save(session);

        Disposable subscription = aiService.stream(input, context)
                .subscribe(
                        chunk -> {
                            reply.append(chunk);
                            try {
                                send(emitter, "message", java.util.Map.of("content", chunk));
                            } catch (Exception exception) {
                                emitter.completeWithError(exception);
                            }
                        },
                        exception -> sendErrorAndComplete(emitter, exception),
                        () -> completeAssistantReply(emitter, session, reply.toString()));
        emitter.onCompletion(subscription::dispose);

        return emitter;
    }

    private void sendRiskAndComplete(
            SseEmitter emitter,
            ChatSession session,
            ChatMessage userMessage) {
        try {
            session.setHighRisk(true);
            session.setPreview("已触发危机干预提示");
            session.setLatestMessageAt(Instant.now());
            sessions.save(session);
            send(emitter, "risk", java.util.Map.of(
                    "level", "HIGH",
                    "message", "如果你正处于立即危险中，请马上联系家人、拨打当地心理援助热线或前往医院急诊。"));
            send(emitter, "done", java.util.Map.of("messageId", userMessage.getId()));
            emitter.complete();
        } catch (Exception exception) {
            emitter.completeWithError(exception);
        }
    }

    private void completeAssistantReply(
            SseEmitter emitter,
            ChatSession session,
            String reply) {
        try {
            ChatMessage assistantMessage = new ChatMessage();
            assistantMessage.setSession(session);
            assistantMessage.setRole(ChatMessage.Role.ASSISTANT);
            assistantMessage.setContent(reply);
            messages.save(assistantMessage);
            session.setPreview(toPreview(reply));
            session.setLatestMessageAt(Instant.now());
            sessions.save(session);
            send(emitter, "done", java.util.Map.of("messageId", assistantMessage.getId()));
            emitter.complete();
        } catch (Exception exception) {
            sendErrorAndComplete(emitter, exception);
        }
    }

    private void sendErrorAndComplete(SseEmitter emitter, Throwable exception) {
        try {
            send(emitter, "error", java.util.Map.of(
                    "code", "AI_SERVICE_ERROR",
                    "message", "暂时无法生成回复"));
        } catch (Exception ignored) {
            // The connection may already be closed by the client.
        } finally {
            emitter.completeWithError(exception);
        }
    }

    private ChatSession getOwnedSession(long sessionId) {
        ChatSession session = sessions.findById(sessionId)
                .orElseThrow(() -> new IllegalArgumentException("会话不存在"));
        if (!session.getUser().getId().equals(currentUser().getId())) {
            throw new SecurityException("无权访问该会话");
        }
        return session;
    }

    private boolean isHighRisk(String content) {
        String normalized = content == null ? "" : content.toLowerCase();
        return normalized.contains("自杀")
                || normalized.contains("轻生")
                || normalized.contains("自残")
                || normalized.contains("不想活");
    }

    private String buildKnowledgeContext(String query, ChatSession session) {
        List<Document> documents = vectorStore.similaritySearch(query);
        String knowledge = documents == null
                ? ""
                : documents.stream()
                .map(Document::getText)
                .filter(java.util.Objects::nonNull)
                .limit(3)
                .collect(java.util.stream.Collectors.joining("\n\n"));

        List<ChatMessage> historyMessages = session.isContextCleared()
                ? List.of()
                : messages.findBySessionIdOrderByCreatedAtAsc(session.getId());
        String history = historyMessages
                        .stream()
                        .skip(Math.max(0, historyMessages.size() - 6))
                        .map(message -> message.getRole() + ": " + message.getContent())
                        .collect(java.util.stream.Collectors.joining("\n"));

        return "知识库参考：\n"
                + (knowledge.isBlank() ? "暂无可用知识库内容" : knowledge)
                + "\n\n对话模式：\n"
                + (session.getMode() == ChatSession.Mode.ANALYTICAL
                ? "理性分析：结构化梳理问题，给出清晰可执行的建议"
                : "温柔陪伴：使用温和、共情的表达，优先倾听和支持")
                + "\n\n历史对话：\n"
                + (history.isBlank() ? "无" : history);
    }

    private void send(SseEmitter emitter, String event, Object data) throws Exception {
        emitter.send(SseEmitter.event()
                .name(event)
                .data(objectMapper.writeValueAsString(data)));
    }

    private String toPreview(String content) {
        if (content == null || content.isBlank()) {
            return "暂无消息";
        }
        return content.length() <= 255
                ? content
                : content.substring(0, 255);
    }
}
