package com.mindharbor.modules.relax.vo;

import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class RelaxRecordVO {

    private Long id;

    private String type;

    private int durationSeconds;

    private Integer rounds;

    private Instant createdAt;
}
