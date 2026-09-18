package com.mindharbor.modules.system.vo;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 隐私协议、数据用途说明、免责声明与心理援助热线汇总。 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class SystemPolicyVO {

    private String version;

    private String privacyPolicy;

    private String dataUsage;

    private String disclaimer;

    private List<String> hotlines;
}
