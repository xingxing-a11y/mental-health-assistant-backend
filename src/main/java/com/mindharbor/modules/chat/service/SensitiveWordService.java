package com.mindharbor.modules.chat.service;

import com.mindharbor.modules.chat.vo.SensitiveCheckVO;

/**
 * 敏感内容判定入口。
 * 发送前预校验与对话主接口共用同一套服务端规则，前端校验只作为提示。
 */
public interface SensitiveWordService {

    /** 只判定级别并给出提示文案，不写审计，用于发送前预校验。 */
    SensitiveCheckVO inspect(String content);

    /** 对话主接口的强制校验，命中 CARE 级时额外记录审计。 */
    SensitiveCheckVO inspectAndAudit(String content, Long sessionId);
}
