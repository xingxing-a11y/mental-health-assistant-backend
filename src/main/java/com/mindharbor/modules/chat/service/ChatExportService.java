package com.mindharbor.modules.chat.service;

import com.mindharbor.modules.chat.vo.ChatExportFileVO;

/** 会话导出业务接口，只导出当前用户自己的会话。 */
public interface ChatExportService {

    /** 按 format 导出会话，支持 txt 与 pdf。 */
    ChatExportFileVO export(long sessionId, String format);
}
