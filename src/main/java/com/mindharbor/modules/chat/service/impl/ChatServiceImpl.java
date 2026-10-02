package com.mindharbor.modules.chat.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mindharbor.common.ResourceNotFoundException;
import com.mindharbor.modules.chat.dto.ChatMessageRequest;
import com.mindharbor.modules.chat.dto.ChatSettingsRequest;
import com.mindharbor.modules.chat.dto.CreateSessionRequest;
import com.mindharbor.modules.chat.entity.ChatMessage;
import com.mindharbor.modules.chat.entity.ChatSession;
import com.mindharbor.modules.chat.mapper.ChatMessageMapper;
import com.mindharbor.modules.chat.mapper.ChatSessionMapper;
import com.mindharbor.modules.chat.service.AiConversationService;
import com.mindharbor.modules.chat.service.ChatService;
import com.mindharbor.modules.chat.service.SensitiveWordService;
import com.mindharbor.modules.chat.vo.ChatSessionVO;
import com.mindharbor.modules.chat.vo.FavoriteMessageVO;
import com.mindharbor.modules.chat.vo.SensitiveCheckVO;
import com.mindharbor.modules.user.entity.User;
import com.mindharbor.modules.user.mapper.UserMapper;
import com.mindharbor.modules.user.vo.PageResult;
import com.mindharbor.security.CurrentUserProvider;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import reactor.core.Disposable;

/** 会话与消息业务实现，包含归属校验、SSE 流式回复和高危内容拦截。 */
@Service
public class ChatServiceImpl implements ChatService {

    private static final Logger log = LoggerFactory.getLogger(ChatServiceImpl.class);

    private static final String DEFAULT_SESSION_NAME = "新的对话";

    private static final String EMPTY_SESSION_PREVIEW = "还没有消息";

    private static final int HISTORY_CONTEXT_LIMIT = 6;

    private static final int KNOWLEDGE_CONTEXT_LIMIT = 3;

    private static final int PREVIEW_MAX_LENGTH = 255;

    private static final int DEFAULT_PAGE_SIZE = 20;

    private static final int MAX_PAGE_SIZE = 50;

    private static final int MAX_KEYWORD_LENGTH = 50;

    private final ChatSessionMapper chatSessionMapper;
    private final ChatMessageMapper chatMessageMapper;
    private final UserMapper userMapper;
    private final AiConversationService aiConversationService;
    private final SensitiveWordService sensitiveWordService;
    private final CurrentUserProvider currentUserProvider;
    private final ObjectMapper objectMapper;
    private final VectorStore vectorStore;

    public ChatServiceImpl(
            ChatSessionMapper chatSessionMapper,
            ChatMessageMapper chatMessageMapper,
            UserMapper userMapper,
            AiConversationService aiConversationService,
            SensitiveWordService sensitiveWordService,
            CurrentUserProvider currentUserProvider,
            ObjectMapper objectMapper,
            @Lazy VectorStore vectorStore) {
        this.chatSessionMapper = chatSessionMapper;
        this.chatMessageMapper = chatMessageMapper;
        this.userMapper = userMapper;
        this.aiConversationService = aiConversationService;
        this.sensitiveWordService = sensitiveWordService;
        this.currentUserProvider = currentUserProvider;
        this.objectMapper = objectMapper;
        this.vectorStore = vectorStore;
    }

    private User currentUser() {
        return userMapper.findById(currentUserProvider.getUserId())
                .orElseThrow(() -> new IllegalStateException("当前没有可用用户"));
    }

    @Override
    public ChatSessionVO create(CreateSessionRequest request) {
        User user = currentUser();
        ChatSession session = new ChatSession();
        session.setUserId(user.getId());
        session.setUser(user);
        session.setName(request == null || request.getName() == null || request.getName().isBlank()
                ? DEFAULT_SESSION_NAME
                : request.getName());
        session.setPreview(EMPTY_SESSION_PREVIEW);
        chatSessionMapper.insert(session);
        return toView(session);
    }

    @Override
    public List<ChatSessionVO> list() {
        return chatSessionMapper.findByUserIdOrderByLatestMessageAtDesc(currentUser().getId())
                .stream()
                .filter(session -> session.getPreview() != null
                        && !EMPTY_SESSION_PREVIEW.equals(session.getPreview()))
                .map(this::toView)
                .toList();
    }

    @Override
    public List<ChatSessionVO> archived() {
        return chatSessionMapper.findArchivedByUserIdOrderByLatestMessageAtDesc(currentUser().getId())
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

    /** 查询会话历史消息，先校验会话归属再读取消息。 */
    @Override
    public List<ChatMessage> history(long sessionId) {
        getOwnedSession(sessionId);
        return chatMessageMapper.findBySessionIdOrderByCreatedAtAsc(sessionId);
    }

    /** 删除已归档会话及其全部消息。 */
    @Override
    public void delete(long sessionId) {
        ChatSession session = getOwnedSession(sessionId);
        if (!session.isArchived()) {
            throw new IllegalArgumentException("请先归档会话后再删除");
        }
        chatMessageMapper.deleteBySessionId(sessionId);
        chatSessionMapper.deleteById(sessionId);
    }

    @Override
    public void clearContext(long id) {
        ChatSession session = getOwnedSession(id);
        session.setContextCleared(true);
        chatSessionMapper.updateById(session);
    }

    @Override
    public void updateSettings(long sessionId, ChatSettingsRequest request) {
        ChatSession session = getOwnedSession(sessionId);
        session.setMode(request.getMode());
        chatSessionMapper.updateById(session);
    }

    @Override
    public ChatSessionVO rename(long sessionId, CreateSessionRequest request) {
        ChatSession session = getOwnedSession(sessionId);
        String name = request == null ? "" : request.getName();
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("会话名称不能为空");
        }
        session.setName(name.trim());
        chatSessionMapper.updateById(session);
        return toView(session);
    }

    @Override
    public ChatSessionVO togglePin(long sessionId) {
        ChatSession session = getOwnedSession(sessionId);
        session.setPinned(!session.isPinned());
        chatSessionMapper.updateById(session);
        return toView(session);
    }

    @Override
    public ChatSessionVO archive(long sessionId) {
        ChatSession session = getOwnedSession(sessionId);
        session.setArchived(true);
        session.setPinned(false);
        chatSessionMapper.updateById(session);
        return toView(session);
    }

    @Override
    public ChatSessionVO unarchive(long sessionId) {
        ChatSession session = getOwnedSession(sessionId);
        session.setArchived(false);
        chatSessionMapper.updateById(session);
        return toView(session);
    }

    @Override
    public void favoriteMessage(long messageId, boolean favorite) {
        ChatMessage message = getOwnedAssistantMessage(messageId);
        message.setFavorite(favorite);
        chatMessageMapper.updateById(message);
    }

    @Override
    public void removeFavorite(long messageId) {
        ChatMessage message = getOwnedAssistantMessage(messageId);
        message.setFavorite(false);
        chatMessageMapper.updateById(message);
    }

    @Override
    public PageResult<FavoriteMessageVO> favoriteMessages(
            int page,
            int size,
            String keyword,
            Long sessionId) {
        Long userId = currentUserProvider.getUserId();
        int safePage = Math.max(page, 0);
        int safeSize = normalizePageSize(size);
        String normalizedKeyword = normalizeKeyword(keyword);
        long total = chatMessageMapper.countFavorites(userId, sessionId, normalizedKeyword);
        List<FavoriteMessageVO> items = chatMessageMapper
                .findFavorites(userId, sessionId, normalizedKeyword, safeSize, safePage * safeSize)
                .stream()
                .map(message -> new FavoriteMessageVO(
                        message.getId(),
                        message.getContent(),
                        message.getSessionId(),
                        message.getCreatedAt()))
                .toList();
        return new PageResult<>(items, safePage, safeSize, total, totalPages(total, safeSize));
    }

    private ChatMessage getOwnedAssistantMessage(long messageId) {
        ChatMessage message = chatMessageMapper.findById(messageId)
                .orElseThrow(() -> new ResourceNotFoundException("消息不存在"));
        getOwnedSession(message.getSessionId());
        if (message.getRole() != ChatMessage.Role.ASSISTANT) {
            throw new IllegalArgumentException("只能收藏 AI 消息");
        }
        return message;
    }

    private int normalizePageSize(int size) {
        if (size <= 0) {
            return DEFAULT_PAGE_SIZE;
        }
        return Math.min(size, MAX_PAGE_SIZE);
    }

    private String normalizeKeyword(String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return null;
        }
        String trimmed = keyword.trim();
        return trimmed.length() <= MAX_KEYWORD_LENGTH
                ? trimmed
                : trimmed.substring(0, MAX_KEYWORD_LENGTH);
    }

    private int totalPages(long total, int size) {
        return (int) ((total + size - 1) / size);
    }

    /** SSE 流式发送 AI 消息，高危内容直接返回危机提示而不调用模型。 */
    @Override
    public SseEmitter stream(long id, ChatMessageRequest request) {
        SseEmitter emitter = new SseEmitter(120000L);
        String input = request == null ? "" : request.getContent();
        ChatSession session = getOwnedSession(id);

        ChatMessage userMessage = new ChatMessage();
        userMessage.setSessionId(session.getId());
        userMessage.setSession(session);
        userMessage.setRole(ChatMessage.Role.USER);
        userMessage.setContent(input);
        chatMessageMapper.insert(userMessage);

        if (DEFAULT_SESSION_NAME.equals(session.getName()) && !input.isBlank()) {
            session.setName(toPreview(input));
            session.setPreview(toPreview(input));
            chatSessionMapper.updateById(session);
        }

        // 预校验接口只做提示，对话主接口必须复用同一套服务端判定
        SensitiveCheckVO sensitiveCheck = sensitiveWordService.inspectAndAudit(input, session.getId());
        if (sensitiveCheck.getLevel() == SensitiveCheckVO.Level.HIGH) {
            sendRiskAndComplete(emitter, session, userMessage, sensitiveCheck);
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
        chatSessionMapper.updateById(session);

        Disposable subscription = aiConversationService.stream(input, context)
                .subscribe(
                        chunk -> appendChunk(emitter, reply, chunk),
                        exception -> sendErrorAndComplete(emitter, exception),
                        () -> completeAssistantReply(emitter, session, reply.toString()));
        emitter.onCompletion(subscription::dispose);

        return emitter;
    }

    private void appendChunk(SseEmitter emitter, StringBuilder reply, String chunk) {
        reply.append(chunk);
        try {
            send(emitter, "message", Map.of("content", chunk));
        } catch (Exception exception) {
            emitter.completeWithError(exception);
        }
    }

    private void sendRiskAndComplete(
            SseEmitter emitter,
            ChatSession session,
            ChatMessage userMessage,
            SensitiveCheckVO sensitiveCheck) {
        try {
            session.setHighRisk(true);
            session.setPreview("已触发危机干预提示");
            session.setLatestMessageAt(Instant.now());
            chatSessionMapper.updateById(session);
            send(emitter, "risk", Map.of(
                    "level", sensitiveCheck.getLevel().name(),
                    "message", sensitiveCheck.getPrompt(),
                    "hotline", sensitiveCheck.getHotline()));
            send(emitter, "done", Map.of("messageId", userMessage.getId()));
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
            chatMessageMapper.insert(assistantMessage);
            session.setPreview(toPreview(reply));
            session.setLatestMessageAt(Instant.now());
            chatSessionMapper.updateById(session);
            send(emitter, "done", Map.of("messageId", assistantMessage.getId()));
            emitter.complete();
        } catch (Exception exception) {
            sendErrorAndComplete(emitter, exception);
        }
    }

    private void sendErrorAndComplete(SseEmitter emitter, Throwable exception) {
        log.error("AI stream failed", exception);
        try {
            send(emitter, "error", Map.of(
                    "code", "AI_SERVICE_ERROR",
                    "message", "暂时无法生成回复"));
        } catch (Exception ignored) {
            // 客户端可能已经断开连接，忽略二次发送失败。
        } finally {
            emitter.completeWithError(exception);
        }
    }

    private ChatSession getOwnedSession(long sessionId) {
        ChatSession session = chatSessionMapper.findById(sessionId)
                .orElseThrow(() -> new ResourceNotFoundException("会话不存在"));
        if (!currentUser().getId().equals(session.getUserId())) {
            throw new SecurityException("无权访问该会话");
        }
        return session;
    }

    /** 组装知识库、对话模式和历史对话上下文。 */
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
                        .filter(Objects::nonNull)
                        .limit(KNOWLEDGE_CONTEXT_LIMIT)
                        .collect(Collectors.joining("\n\n"));

        List<ChatMessage> historyMessages = session.isContextCleared()
                ? List.of()
                : chatMessageMapper.findBySessionIdOrderByCreatedAtAsc(session.getId());
        String history = historyMessages
                .stream()
                .skip(Math.max(0, historyMessages.size() - HISTORY_CONTEXT_LIMIT))
                .map(message -> message.getRole() + ": " + message.getContent())
                .collect(Collectors.joining("\n"));

        String modePrompt = session.getMode() == ChatSession.Mode.ANALYTICAL
                ? "理性分析：结构化梳理问题，给出清晰可执行的建议"
                : "温柔陪伴：使用温和、共情的表达，优先倾听和支持";

        return "知识库参考：\n"
                + (knowledge.isBlank() ? "暂无可用知识库内容" : knowledge)
                + "\n\n对话模式：\n"
                + modePrompt
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
        return content.length() <= PREVIEW_MAX_LENGTH
                ? content
                : content.substring(0, PREVIEW_MAX_LENGTH);
    }
}
