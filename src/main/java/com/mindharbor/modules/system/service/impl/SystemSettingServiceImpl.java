package com.mindharbor.modules.system.service.impl;

import com.mindharbor.modules.system.dto.KnowledgeSettingsRequest;
import com.mindharbor.modules.system.entity.SystemSetting;
import com.mindharbor.modules.system.mapper.SystemSettingMapper;
import com.mindharbor.modules.system.service.SystemSettingService;
import com.mindharbor.modules.system.vo.KnowledgeSettingsVO;
import com.mindharbor.modules.system.vo.SystemPolicyVO;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/** 全局系统配置实现，配置缺失时回退到 application.yml 的默认值。 */
@Service
public class SystemSettingServiceImpl implements SystemSettingService {

    private static final Logger log = LoggerFactory.getLogger(SystemSettingServiceImpl.class);

    private static final String KEY_CHUNK_SIZE = "knowledge.chunk-size";

    private static final String KEY_CHUNK_OVERLAP = "knowledge.chunk-overlap";

    private static final String PRIVACY_POLICY =
            "MindHarbor 只在提供心理健康陪伴服务所需的范围内收集信息，"
                    + "不会将你的对话、日记与测评内容用于服务之外的用途。";

    private static final String DATA_USAGE =
            "对话与日记内容用于生成回复和生成你的个人记录；"
                    + "匿名使用数据授权默认关闭，开启后仅用于知识库内容优化，可随时关闭。";

    private static final String DISCLAIMER =
            "本服务提供情绪陪伴与自助参考，不构成医学诊断或治疗建议，"
                    + "不能替代精神科医生或心理治疗师的专业服务。";

    private static final List<String> HOTLINES = List.of(
            "全国心理援助热线 12356",
            "北京心理危机研究与干预中心 010-82951332",
            "紧急医疗救助 120");

    private final SystemSettingMapper mapper;

    private final int defaultChunkSize;

    private final int defaultChunkOverlap;

    private final String version;

    public SystemSettingServiceImpl(
            SystemSettingMapper mapper,
            @Value("${rag.chunk-size:1000}") int defaultChunkSize,
            @Value("${rag.chunk-overlap:100}") int defaultChunkOverlap,
            @Value("${app.version:1.0.0}") String version) {
        this.mapper = mapper;
        this.defaultChunkSize = defaultChunkSize;
        this.defaultChunkOverlap = defaultChunkOverlap;
        this.version = version;
    }

    @Override
    public KnowledgeSettingsVO knowledgeSettings() {
        Map<String, String> settings = loadSettings();
        return new KnowledgeSettingsVO(
                readInt(settings, KEY_CHUNK_SIZE, defaultChunkSize),
                readInt(settings, KEY_CHUNK_OVERLAP, defaultChunkOverlap));
    }

    @Override
    public KnowledgeSettingsVO updateKnowledgeSettings(KnowledgeSettingsRequest request) {
        int chunkSize = request.getChunkSize();
        int chunkOverlap = request.getChunkOverlap();
        if (chunkOverlap >= chunkSize) {
            throw new IllegalArgumentException("重叠长度必须小于分片大小");
        }
        save(KEY_CHUNK_SIZE, String.valueOf(chunkSize));
        save(KEY_CHUNK_OVERLAP, String.valueOf(chunkOverlap));
        return new KnowledgeSettingsVO(chunkSize, chunkOverlap);
    }

    @Override
    public SystemPolicyVO policy() {
        return new SystemPolicyVO(version, PRIVACY_POLICY, DATA_USAGE, DISCLAIMER, HOTLINES);
    }

    /** 配置表读取失败时回退默认值，避免影响依赖配置的业务流程。 */
    private Map<String, String> loadSettings() {
        try {
            return mapper.findAll()
                    .stream()
                    .filter(setting -> setting.getSettingKey() != null)
                    .collect(Collectors.toMap(
                            SystemSetting::getSettingKey,
                            setting -> setting.getSettingValue() == null
                                    ? ""
                                    : setting.getSettingValue(),
                            (existing, replacement) -> replacement));
        } catch (RuntimeException exception) {
            log.error("System settings unavailable, fallback to defaults", exception);
            return Map.of();
        }
    }

    private int readInt(Map<String, String> settings, String key, int fallback) {
        String value = settings.get(key);
        if (value == null || value.isBlank()) {
            return fallback;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException exception) {
            log.warn("Invalid system setting value, fallback to default, key={}", key);
            return fallback;
        }
    }

    private void save(String key, String value) {
        SystemSetting setting = new SystemSetting();
        setting.setSettingKey(key);
        setting.setSettingValue(value);
        setting.setUpdatedAt(Instant.now());
        if (mapper.selectById(key) == null) {
            mapper.insert(setting);
            return;
        }
        mapper.updateById(setting);
    }
}
