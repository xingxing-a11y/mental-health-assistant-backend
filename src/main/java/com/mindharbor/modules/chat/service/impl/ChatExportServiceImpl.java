package com.mindharbor.modules.chat.service.impl;

import com.lowagie.text.Chunk;
import com.lowagie.text.Document;
import com.lowagie.text.DocumentException;
import com.lowagie.text.Font;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.pdf.BaseFont;
import com.lowagie.text.pdf.PdfWriter;
import com.mindharbor.common.ResourceNotFoundException;
import com.mindharbor.common.TooManyRequestsException;
import com.mindharbor.modules.chat.entity.ChatMessage;
import com.mindharbor.modules.chat.entity.ChatSession;
import com.mindharbor.modules.chat.mapper.ChatMessageMapper;
import com.mindharbor.modules.chat.mapper.ChatSessionMapper;
import com.mindharbor.modules.chat.service.ChatExportService;
import com.mindharbor.modules.chat.vo.ChatExportFileVO;
import com.mindharbor.security.CurrentUserProvider;
import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

/**
 * 会话导出实现。
 * PDF 使用 OpenPDF（LGPL/MPL），字体只缓存不可变字节，BaseFont 每次导出新建，避免跨线程共享。
 */
@Service
public class ChatExportServiceImpl implements ChatExportService {

    private static final Logger log = LoggerFactory.getLogger(ChatExportServiceImpl.class);

    private static final String FORMAT_TXT = "txt";

    private static final String FORMAT_PDF = "pdf";

    private static final String TXT_CONTENT_TYPE = "text/plain;charset=UTF-8";

    private static final String PDF_CONTENT_TYPE = "application/pdf";

    private static final String EXPORT_BUSY_MESSAGE = "导出任务繁忙，请稍后重试";

    private static final String FONT_MISSING_MESSAGE = "未配置中文字体，无法导出 PDF";

    private static final byte[] UTF8_BOM = {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF};

    private static final DateTimeFormatter TIME_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private static final int MAX_FILENAME_LENGTH = 40;

    private static final float PDF_FONT_SIZE = 12F;

    private static final float PDF_TITLE_FONT_SIZE = 16F;

    private static final float PDF_META_FONT_SIZE = 10F;

    /** jar 内字体只能按字节流读取，无法按文件路径打开。 */
    private static final List<String> CLASSPATH_FONT_CANDIDATES = List.of(
            "fonts/simhei.ttf",
            "fonts/NotoSansSC-Regular.ttf",
            "fonts/SourceHanSansSC-Regular.ttf");

    /** 系统字体兜底，第二列为 BaseFont 使用的字体名，ttc 必须带集合内序号。 */
    private static final List<String[]> SYSTEM_FONT_CANDIDATES = List.of(
            new String[] {"C:/Windows/Fonts/simhei.ttf", "simhei.ttf"},
            new String[] {"C:/Windows/Fonts/simsun.ttc", "simsun.ttc,0"},
            new String[] {"C:/Windows/Fonts/msyh.ttc", "msyh.ttc,0"},
            new String[] {
                    "/usr/share/fonts/opentype/noto/NotoSansCJK-Regular.ttc",
                    "NotoSansCJK-Regular.ttc,0"},
            new String[] {
                    "/usr/share/fonts/truetype/arphic/uming.ttc",
                    "uming.ttc,0"});

    private final ChatSessionMapper chatSessionMapper;

    private final ChatMessageMapper chatMessageMapper;

    private final CurrentUserProvider currentUserProvider;

    private final Semaphore exportSemaphore;

    private final String configuredFontPath;

    private final long acquireTimeoutSeconds;

    /** 只缓存字体字节，volatile 保证多线程安全发布。 */
    private volatile FontResource fontResource;

    public ChatExportServiceImpl(
            ChatSessionMapper chatSessionMapper,
            ChatMessageMapper chatMessageMapper,
            CurrentUserProvider currentUserProvider,
            @Value("${export.pdf.font-path:}") String configuredFontPath,
            @Value("${export.pdf.max-concurrent:2}") int maxConcurrent,
            @Value("${export.pdf.acquire-timeout-seconds:2}") long acquireTimeoutSeconds) {
        this.chatSessionMapper = chatSessionMapper;
        this.chatMessageMapper = chatMessageMapper;
        this.currentUserProvider = currentUserProvider;
        this.configuredFontPath = configuredFontPath;
        this.exportSemaphore = new Semaphore(Math.max(maxConcurrent, 1));
        this.acquireTimeoutSeconds = acquireTimeoutSeconds;
    }

    @Override
    public ChatExportFileVO export(long sessionId, String format) {
        ChatSession session = getOwnedSession(sessionId);
        List<ChatMessage> history = chatMessageMapper.findBySessionIdOrderByCreatedAtAsc(sessionId);
        String baseName = buildBaseName(session);
        if (FORMAT_PDF.equals(normalizeFormat(format))) {
            return new ChatExportFileVO(
                    baseName + ".pdf",
                    PDF_CONTENT_TYPE,
                    renderPdf(session, history));
        }
        return new ChatExportFileVO(
                baseName + ".txt",
                TXT_CONTENT_TYPE,
                renderTxt(session, history));
    }

    private ChatSession getOwnedSession(long sessionId) {
        ChatSession session = chatSessionMapper.findById(sessionId)
                .orElseThrow(() -> new ResourceNotFoundException("会话不存在"));
        if (!currentUserProvider.getUserId().equals(session.getUserId())) {
            throw new SecurityException("无权访问该会话");
        }
        return session;
    }

    private String normalizeFormat(String format) {
        String normalized = format == null ? "" : format.trim().toLowerCase();
        if (FORMAT_PDF.equals(normalized)) {
            return FORMAT_PDF;
        }
        if (normalized.isEmpty() || FORMAT_TXT.equals(normalized)) {
            return FORMAT_TXT;
        }
        throw new IllegalArgumentException("导出格式仅支持 txt 或 pdf");
    }

    /** 会话名由用户输入，写入响应头前先剔除路径与换行等不安全字符。 */
    private String buildBaseName(ChatSession session) {
        String name = session.getName();
        String sanitized = name == null
                ? ""
                : name.replaceAll("[\\\\/:*?\"<>|\\r\\n\\t]", "_").trim();
        if (sanitized.isEmpty()) {
            sanitized = "chat-" + session.getId();
        }
        return sanitized.length() <= MAX_FILENAME_LENGTH
                ? sanitized
                : sanitized.substring(0, MAX_FILENAME_LENGTH);
    }

    /** TXT 带 UTF-8 BOM，避免 Windows 记事本按本地编码打开导致中文乱码。 */
    private byte[] renderTxt(ChatSession session, List<ChatMessage> history) {
        StringBuilder builder = new StringBuilder();
        builder.append("会话：").append(displayName(session)).append('\n');
        builder.append("导出时间：").append(LocalDateTime.now().format(TIME_FORMAT)).append("\n\n");
        for (ChatMessage message : history) {
            builder.append(speaker(message)).append('：');
            builder.append(message.getContent() == null ? "" : message.getContent());
            builder.append("\n\n");
        }

        byte[] body = builder.toString().getBytes(StandardCharsets.UTF_8);
        byte[] content = new byte[UTF8_BOM.length + body.length];
        System.arraycopy(UTF8_BOM, 0, content, 0, UTF8_BOM.length);
        System.arraycopy(body, 0, content, UTF8_BOM.length, body.length);
        return content;
    }

    private byte[] renderPdf(ChatSession session, List<ChatMessage> history) {
        if (!acquirePermit()) {
            throw new TooManyRequestsException(EXPORT_BUSY_MESSAGE);
        }
        try {
            return buildPdf(session, history);
        } finally {
            exportSemaphore.release();
        }
    }

    private byte[] buildPdf(ChatSession session, List<ChatMessage> history) {
        BaseFont baseFont = createBaseFont();
        Font titleFont = new Font(baseFont, PDF_TITLE_FONT_SIZE, Font.BOLD);
        Font metaFont = new Font(baseFont, PDF_META_FONT_SIZE, Font.NORMAL, Color.GRAY);
        Font speakerFont = new Font(baseFont, PDF_FONT_SIZE, Font.BOLD);
        Font bodyFont = new Font(baseFont, PDF_FONT_SIZE, Font.NORMAL);

        ByteArrayOutputStream output = new ByteArrayOutputStream();
        Document document = new Document(PageSize.A4, 56F, 56F, 56F, 56F);
        try {
            PdfWriter.getInstance(document, output);
            document.open();
            document.add(new Paragraph("MindHarbor 对话记录", titleFont));
            document.add(new Paragraph("会话：" + displayName(session), metaFont));
            document.add(new Paragraph(
                    "导出时间：" + LocalDateTime.now().format(TIME_FORMAT),
                    metaFont));
            document.add(Chunk.NEWLINE);
            for (ChatMessage message : history) {
                document.add(new Paragraph(speaker(message), speakerFont));
                document.add(new Paragraph(
                        message.getContent() == null ? "" : message.getContent(),
                        bodyFont));
                document.add(Chunk.NEWLINE);
            }
            document.close();
        } catch (DocumentException exception) {
            log.error("Failed to render chat export pdf", exception);
            throw new IllegalStateException("导出 PDF 失败");
        }
        return output.toByteArray();
    }

    private boolean acquirePermit() {
        try {
            return exportSemaphore.tryAcquire(acquireTimeoutSeconds, TimeUnit.SECONDS);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    /**
     * 每次导出都新建 BaseFont：OpenPDF 的 BaseFont 不是线程安全对象，
     * 因此只复用不可变的字体字节，并显式关闭静态缓存。
     */
    private BaseFont createBaseFont() {
        FontResource resource = resolveFontResource();
        try {
            return BaseFont.createFont(
                    resource.name,
                    BaseFont.IDENTITY_H,
                    BaseFont.EMBEDDED,
                    false,
                    resource.content,
                    null);
        } catch (DocumentException | IOException exception) {
            log.error("Failed to load pdf font", exception);
            throw new IllegalStateException("导出 PDF 失败");
        }
    }

    private FontResource resolveFontResource() {
        FontResource cached = fontResource;
        if (cached != null) {
            return cached;
        }
        synchronized (this) {
            if (fontResource == null) {
                fontResource = loadFontResource();
            }
            return fontResource;
        }
    }

    /** 字体来源三级兜底：外部绝对路径、jar 内字节流、系统字体。 */
    private FontResource loadFontResource() {
        FontResource configured = readConfiguredFont();
        if (configured != null) {
            return configured;
        }
        FontResource bundled = readClasspathFont();
        if (bundled != null) {
            return bundled;
        }
        FontResource system = readSystemFont();
        if (system != null) {
            return system;
        }
        throw new IllegalArgumentException(FONT_MISSING_MESSAGE);
    }

    /** 部署建议把中文字体放在服务器外部目录，用 export.pdf.font-path 指定绝对路径。 */
    private FontResource readConfiguredFont() {
        if (configuredFontPath == null || configuredFontPath.isBlank()) {
            return null;
        }

        Path path = Path.of(configuredFontPath.trim());
        if (!Files.isReadable(path)) {
            log.warn("Configured pdf font path is not readable, fallback to bundled font");
            return null;
        }
        try {
            return new FontResource(resolveFontName(path), Files.readAllBytes(path));
        } catch (IOException exception) {
            log.error("Failed to read configured pdf font", exception);
            return null;
        }
    }

    private FontResource readClasspathFont() {
        for (String location : CLASSPATH_FONT_CANDIDATES) {
            try (InputStream stream = new ClassPathResource(location).getInputStream()) {
                return new FontResource(resolveFontName(Path.of(location)), stream.readAllBytes());
            } catch (IOException exception) {
                // 该候选字体不存在时继续尝试下一个。
            }
        }
        return null;
    }

    private FontResource readSystemFont() {
        for (String[] candidate : SYSTEM_FONT_CANDIDATES) {
            Path path = Path.of(candidate[0]);
            if (!Files.isReadable(path)) {
                continue;
            }
            try {
                return new FontResource(candidate[1], Files.readAllBytes(path));
            } catch (IOException exception) {
                log.warn("Failed to read system pdf font, fallback to next candidate");
            }
        }
        return null;
    }

    /** ttc 字体集合必须带序号，否则取不到字形。 */
    private String resolveFontName(Path path) {
        String fileName = path.getFileName().toString();
        return fileName.toLowerCase().endsWith(".ttc") ? fileName + ",0" : fileName;
    }

    private String displayName(ChatSession session) {
        String name = session.getName();
        return name == null || name.isBlank() ? "未命名会话" : name;
    }

    private String speaker(ChatMessage message) {
        return message.getRole() == ChatMessage.Role.USER ? "我" : "MindHarbor";
    }

    /** 不可变字体资源，可安全地在多线程间共享。 */
    private static final class FontResource {

        private final String name;

        private final byte[] content;

        private FontResource(String name, byte[] content) {
            this.name = name;
            this.content = content;
        }
    }
}
