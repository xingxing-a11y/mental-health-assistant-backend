package com.mindharbor.modules.risk.entity;

/** 管理员对一条高危预警的处理结论。 */
public enum HandleResult {

    /** 已电话联系本人。 */
    CONTACTED,

    /** 已通知家属或辅导员。 */
    FAMILY_NOTIFIED,

    /** 已转介专业机构（校心理咨询中心、医院等）。 */
    REFERRED,

    /** 暂时观察，尚未介入。 */
    OBSERVING,

    /** 判定为误报（如引用他人言论、歌词等）。 */
    FALSE_ALARM
}
