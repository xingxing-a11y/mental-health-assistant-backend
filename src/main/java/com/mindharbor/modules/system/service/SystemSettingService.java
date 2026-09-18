package com.mindharbor.modules.system.service;

import com.mindharbor.modules.system.dto.KnowledgeSettingsRequest;
import com.mindharbor.modules.system.vo.KnowledgeSettingsVO;
import com.mindharbor.modules.system.vo.SystemPolicyVO;

/** 全局系统配置业务接口：知识库分片参数与政策说明。 */
public interface SystemSettingService {

    /** 读取知识库分片参数，未配置时返回配置文件中的默认值。 */
    KnowledgeSettingsVO knowledgeSettings();

    KnowledgeSettingsVO updateKnowledgeSettings(KnowledgeSettingsRequest request);

    SystemPolicyVO policy();
}
