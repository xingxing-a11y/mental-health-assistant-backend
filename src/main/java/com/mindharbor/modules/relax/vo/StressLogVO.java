package com.mindharbor.modules.relax.vo;

import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class StressLogVO {

    private Long id;

    private int level;

    private String note;

    private Instant createdAt;
}
