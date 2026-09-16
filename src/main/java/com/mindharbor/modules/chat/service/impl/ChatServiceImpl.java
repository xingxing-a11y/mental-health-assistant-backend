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
import org.springframework.context.annotation.Lazy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import com.mindharbor.security.CurrentUserProvider;
import com.fasterxml.jackson.databind.ObjectMapper;
import reactor.core.Disposable;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;

@Service
public class ChatServiceImpl implements ChatService {

    private static final Logger log = LoggerFactory.getLogger(ChatServiceImpl.class);

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
            @Lazy VectorStore vectorStore) {
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
        User user = currentUser();
        session.setUserId(user.getId());
        session.setUser(user);
        session.setName(request == null || request.getName() == null || request.getName().isBlank()
                ? "新的对话"
                : request.getName());
        session.setPreview("还没有消息");
        sessions.insert(session);
        return toView(session);
    }

    @Override
    public List<ChatSessionVO> list() {
        return sessions.findByUserIdOrderByLatestMessageAtDesc(currentUser().getId())
                .stream()
                .filter(session -> session.getPreview() != null
                        && !"还没有消息".equals(session.getPreview()))
                .map(this::toView)
                .toList();
    }

    @Override
    public List<ChatSessionVO> archived() {
        return sessions.findArchivedByUserIdOrderByLatestMessageAtDesc(currentUser().getId())
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
        view.setPinned(session.isPinned());
        view.setArchived(session.isArchived());
        return view;
    }

    /**
     * 查询会话历史
     * @param sessionId
     * @return
     */
    @Override
    public List<?> history(long sessionId) {

        //查询是否存在会话
        getOwnedSession(sessionId);
        //根据会话id查询会话
        return messages.findBySessionIdOrderByCreatedAtAsc(sessionId);
    }

    /**
     * 删除会话
     * @param sessionId
     */
    @Override
    public void delete(long sessionId) {
        ChatSession session = getOwnedSession(sessionId);
        if (!session.isArchived()) {
            throw new IllegalArgumentException("请先归档会话后再删除");
        }
        messages.deleteBySessionId(sessionId);
        sessions.deleteById(sessionId);
    }

    @Override
    public void clearContext(long id) {
        ChatSession session = getOwnedSession(id);
        session.setContextCleared(true);
        sessions.updateById(session);
    }

    @Override
    public void updateSettings(long sessionId, ChatSettingsRequest request) {
        ChatSession session = getOwnedSession(sessionId);
        session.setMode(request.getMode());
        sessions.updateById(session);
    }

    @Override
    public ChatSessionVO rename(long sessionId, CreateSessionRequest request) {
        ChatSession session = getOwnedSession(sessionId);
        String name = request == null ? "" : request.getName();
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("会话名称不能为空");
        }
        session.setName(name.trim());
        sessions.updateById(session);
        return toView(session);
    }

    @Override
    public ChatSessionVO togglePin(long sessionId) {
        ChatSession session = getOwnedSession(sessionId);
        session.setPinned(!session.isPinned());
        sessions.updateById(session);
        return toView(session);
    }

    @Override
    public ChatSessionVO archive(long sessionId) {
        ChatSession session = getOwnedSession(sessionId);
        session.setArchived(true);
        session.setPinned(false);
        sessions.updateById(session);
        return toView(session);
    }

    @Override
    public ChatSessionVO unarchive(long sessionId) {
        ChatSession session = getOwnedSession(sessionId);
        session.setArchived(false);
        sessions.updateById(session);
        return toView(session);
    }

    @Override
    public void favoriteMessage(long messageId, boolean favorite) {
        ChatMessage message = messages.findById(messageId)
                .orElseThrow(() -> new IllegalArgumentException("消息不存在"));
        getOwnedSession(message.getSessionId());
        if (message.getRole() != ChatMessage.Role.ASSISTANT) {
            throw new IllegalArgumentException("只能收藏 AI 消息");
        }
        message.setFavorite(favorite);
        messages.updateById(message);
    }

    @Override
    public List<?> favoriteMessages() {
        return messages.findBySessionUserIdAndFavoriteTrueOrderByCreatedAtDesc(
                currentUserProvider.getUserId()).stream()
                .map(message -> java.util.Map.of(
                        "id", message.getId(),
                        "content", message.getContent(),
                        "sessionId", message.getSessionId(),
                        "createdAt", message.getCreatedAt()))
                .toList();
    }

    /**
     * SSE 流式发送 AI 消息
     * @param id
     * @param request
     * @return
     */
    @Override
    public SseEmitter stream(long id, ChatMessageRequest request) {
        SseEmitter emitter = new SseEmitter(120000L);
        String input = request == null ? "" : request.getContent();
        ChatSession ownedSession = getOwnedSession(id);

        ChatSession session = ownedSession;
        ChatMessage userMessage = new ChatMessage();
        userMessage.setSessionId(session.getId());
        userMessage.setSession(session);
        userMessage.setRole(ChatMessage.Role.USER);
        userMessage.setContent(input);
        messages.insert(userMessage);
        if ("新的对话".equals(session.getName()) && !input.isBlank()) {
            session.setName(toPreview(input));
            session.setPreview(toPreview(input));
            sessions.updateById(session);
        }

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
        sessions.updateById(session);

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
            sessions.updateById(session);
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
            assistantMessage.setSessionId(session.getId());
            assistantMessage.setSession(session);
            assistantMessage.setRole(ChatMessage.Role.ASSISTANT);
            assistantMessage.setContent(reply);
            messages.insert(assistantMessage);
            session.setPreview(toPreview(reply));
            session.setLatestMessageAt(Instant.now());
            sessions.updateById(session);
            send(emitter, "done", java.util.Map.of("messageId", assistantMessage.getId()));
            emitter.complete();
        } catch (Exception exception) {
            sendErrorAndComplete(emitter, exception);
        }
    }

    private void sendErrorAndComplete(SseEmitter emitter, Throwable exception) {
        log.error("AI stream failed", exception);
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

    /**
     * 查询会话是否存在
     * @param sessionId
     * @return
     */
    private ChatSession getOwnedSession(long sessionId) {
        ChatSession session = sessions.findById(sessionId)
                .orElseThrow(() -> new IllegalArgumentException("会话不存在"));
        if (!currentUser().getId().equals(session.getUserId())) {
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

    /**
     * 构建知识上下文
     * @param query
     * @param session
     * @return
     */
    private String buildKnowledgeContext(String query, ChatSession session) {
        List<Document> documents;
        try {
            documents = vectorStore.similaritySearch(query);
        } catch (RuntimeException exception) {
            log.warn("Knowledge retrieval unavailable; continuing without RAG context", exception);
            documents = List.of();
        }
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
