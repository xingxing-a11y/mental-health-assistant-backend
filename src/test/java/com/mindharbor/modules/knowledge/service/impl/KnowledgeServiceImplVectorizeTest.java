package com.mindharbor.modules.knowledge.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mindharbor.modules.admin.service.AdminOperationLogService;
import com.mindharbor.modules.knowledge.entity.KnowledgeDocument;
import com.mindharbor.modules.knowledge.mapper.KnowledgeDocumentMapper;
import com.mindharbor.modules.system.service.SystemSettingService;
import com.mindharbor.modules.system.vo.KnowledgeSettingsVO;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.vectorstore.VectorStore;

/**
 * 向量化入口的分叉：{@code /vectorize} 与 {@code /re-vectorize} 必须真的不同。
 *
 * <p>两个接口都转发到 {@code vectorize(id, rebuild)}。一旦 {@code rebuild} 不参与判断，
 * 它们就退化成同一个接口——管理端在「已向量化」的文档上误点一次，代价是重新付一遍
 * Embedding 的调用费用与耗时，而界面上完全看不出来。因此这里固定住分叉：只有
 * {@code rebuild=true} 才允许动已向量化的文档。
 */
@DisplayName("知识库向量化入口")
class KnowledgeServiceImplVectorizeTest {

    @Test
    @DisplayName("已向量化的文档走幂等入口：不删旧向量、不调向量模型、不写库")
    void vectorizeSkipsAlreadyVectorizedDocument() {
        FakeKnowledgeDocumentMapper documents = new FakeKnowledgeDocumentMapper();
        documents.document = document(KnowledgeDocument.VectorStatus.VECTORIZED);
        FakeVectorStore vectorStore = new FakeVectorStore();
        FakeAdminOperationLogService operationLogs = new FakeAdminOperationLogService();

        KnowledgeDocument result =
                serviceWith(documents, vectorStore, operationLogs).vectorize(1L, false);

        assertEquals(KnowledgeDocument.VectorStatus.VECTORIZED, result.getVectorStatus());
        assertTrue(vectorStore.calls.isEmpty(),
                "幂等入口必须零外部调用，否则误点一次就重付一遍 Embedding");
        assertTrue(documents.writtenStatuses.isEmpty(),
                "状态本来就是 VECTORIZED，无需回写 PROCESSING 再写回 VECTORIZED");
    }

    @Test
    @DisplayName("re-vectorize 无视现状强制重建：删旧向量、重新切片并写入")
    void rebuildAlwaysRepeatsWork() {
        FakeKnowledgeDocumentMapper documents = new FakeKnowledgeDocumentMapper();
        documents.document = document(KnowledgeDocument.VectorStatus.VECTORIZED);
        FakeVectorStore vectorStore = new FakeVectorStore();
        FakeAdminOperationLogService operationLogs = new FakeAdminOperationLogService();

        KnowledgeDocument result =
                serviceWith(documents, vectorStore, operationLogs).vectorize(1L, true);

        assertEquals(KnowledgeDocument.VectorStatus.VECTORIZED, result.getVectorStatus());
        assertEquals(List.of("delete", "add"), vectorStore.calls);
        assertEquals(
                List.of(
                        KnowledgeDocument.VectorStatus.PROCESSING,
                        KnowledgeDocument.VectorStatus.VECTORIZED),
                documents.writtenStatuses,
                "强制重建要走完整的 PROCESSING → VECTORIZED 状态流转");
        assertEquals(
                List.of("向量化知识库文档"),
                operationLogs.summaries,
                "真正花钱的向量化必须留一条审计，否则事后无法追溯");
    }

    @Test
    @DisplayName("未完成的文档不会被幂等入口拦下：NOT_VECTORIZED 与 FAILED 都真正执行")
    void unfinishedDocumentsAreProcessed() {
        for (KnowledgeDocument.VectorStatus status : List.of(
                KnowledgeDocument.VectorStatus.NOT_VECTORIZED,
                KnowledgeDocument.VectorStatus.FAILED)) {
            FakeKnowledgeDocumentMapper documents = new FakeKnowledgeDocumentMapper();
            documents.document = document(status);
            FakeVectorStore vectorStore = new FakeVectorStore();
            FakeAdminOperationLogService operationLogs = new FakeAdminOperationLogService();

            serviceWith(documents, vectorStore, operationLogs).vectorize(1L, false);

            assertEquals(List.of("delete", "add"), vectorStore.calls,
                    "状态 " + status + " 应真正执行向量化，否则失败文档永远无法重试");
        }
    }

    private static KnowledgeDocument document(KnowledgeDocument.VectorStatus status) {
        KnowledgeDocument document = new KnowledgeDocument();
        document.setId(1L);
        document.setTitle("情绪管理");
        document.setContent("识别情绪、接纳情绪、表达情绪。");
        document.setVectorStatus(status);
        return document;
    }

    /** vectorize 依赖文档 mapper、向量库、分片参数与审计日志四者，缺一不可。 */
    private static KnowledgeServiceImpl serviceWith(
            FakeKnowledgeDocumentMapper documents,
            FakeVectorStore vectorStore,
            FakeAdminOperationLogService operationLogs) {
        return new KnowledgeServiceImpl(
                documents.asMapper(),
                vectorStore.asVectorStore(),
                settingsService(),
                operationLogs.asService());
    }

    private static SystemSettingService settingsService() {
        return (SystemSettingService) Proxy.newProxyInstance(
                KnowledgeServiceImplVectorizeTest.class.getClassLoader(),
                new Class<?>[] {SystemSettingService.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "knowledgeSettings" -> new KnowledgeSettingsVO(1000, 100);
                    default -> throw new UnsupportedOperationException(method.getName());
                });
    }

    private static final class FakeKnowledgeDocumentMapper {

        private KnowledgeDocument document;

        private final List<KnowledgeDocument.VectorStatus> writtenStatuses = new ArrayList<>();

        private KnowledgeDocumentMapper asMapper() {
            return (KnowledgeDocumentMapper) Proxy.newProxyInstance(
                    KnowledgeServiceImplVectorizeTest.class.getClassLoader(),
                    new Class<?>[] {KnowledgeDocumentMapper.class},
                    (proxy, method, args) -> switch (method.getName()) {
                        case "findById" -> Optional.ofNullable(document);
                        case "updateById" -> {
                            writtenStatuses.add(document.getVectorStatus());
                            yield 1;
                        }
                        default -> throw new UnsupportedOperationException(method.getName());
                    });
        }
    }

    /** delete 与 add 都是 void，只记录「被调用过」这件事。 */
    private static final class FakeVectorStore {

        private final List<String> calls = new ArrayList<>();

        private VectorStore asVectorStore() {
            return (VectorStore) Proxy.newProxyInstance(
                    KnowledgeServiceImplVectorizeTest.class.getClassLoader(),
                    new Class<?>[] {VectorStore.class},
                    (proxy, method, args) -> {
                        switch (method.getName()) {
                            case "add" -> calls.add("add");
                            case "delete" -> calls.add("delete");
                            default -> throw new UnsupportedOperationException(method.getName());
                        }
                        return null;
                    });
        }
    }

    /** record 是 void，只留一份摘要流水，用于断言「哪条出口写了什么」。 */
    private static final class FakeAdminOperationLogService {

        private final List<String> summaries = new ArrayList<>();

        private AdminOperationLogService asService() {
            return (AdminOperationLogService) Proxy.newProxyInstance(
                    KnowledgeServiceImplVectorizeTest.class.getClassLoader(),
                    new Class<?>[] {AdminOperationLogService.class},
                    (proxy, method, args) -> switch (method.getName()) {
                        case "record" -> {
                            summaries.add((String) args[3]);
                            yield null;
                        }
                        default -> throw new UnsupportedOperationException(method.getName());
                    });
        }
    }
}
