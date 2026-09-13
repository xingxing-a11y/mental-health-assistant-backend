package com.mindharbor.modules.user.vo;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class PageResult<T> {

    private List<T> items;

    private int page;

    private int size;

    private long total;

    private int totalPages;
}
