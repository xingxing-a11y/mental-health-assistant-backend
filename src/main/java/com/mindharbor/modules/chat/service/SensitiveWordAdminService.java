package com.mindharbor.modules.chat.service;

import com.mindharbor.modules.chat.dto.SensitiveWordRequest;
import com.mindharbor.modules.chat.vo.SensitiveWordVO;
import java.util.List;

/** 敏感词词库维护接口，仅管理员可用。 */
public interface SensitiveWordAdminService {

    List<SensitiveWordVO> list();

    SensitiveWordVO create(SensitiveWordRequest request);

    SensitiveWordVO update(long wordId, SensitiveWordRequest request);

    void delete(long wordId);
}
