package com.mindharbor.modules.chat.controller;

import com.mindharbor.common.ApiResponse;
import com.mindharbor.modules.chat.dto.SensitiveWordRequest;
import com.mindharbor.modules.chat.service.SensitiveWordAdminService;
import com.mindharbor.modules.chat.vo.SensitiveWordVO;
import jakarta.validation.Valid;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 管理员敏感词词库接口。 */
@RestController
@RequestMapping("/admin/sensitive-words")
public class AdminSensitiveWordController {

    private static final Logger log = LoggerFactory.getLogger(AdminSensitiveWordController.class);

    private final SensitiveWordAdminService service;

    public AdminSensitiveWordController(SensitiveWordAdminService service) {
        this.service = service;
    }

    @GetMapping
    public ApiResponse<List<SensitiveWordVO>> list() {
        log.info("List sensitive words");
        return ApiResponse.ok(service.list());
    }

    @PostMapping
    public ApiResponse<SensitiveWordVO> create(@Valid @RequestBody SensitiveWordRequest request) {
        log.info("Create sensitive word, level={}", request.getLevel());
        return ApiResponse.ok(service.create(request));
    }

    @PutMapping("/{id}")
    public ApiResponse<SensitiveWordVO> update(
            @PathVariable long id,
            @Valid @RequestBody SensitiveWordRequest request) {
        log.info("Update sensitive word, wordId={}", id);
        return ApiResponse.ok(service.update(id, request));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable long id) {
        log.info("Delete sensitive word, wordId={}", id);
        service.delete(id);
        return ApiResponse.ok(null);
    }
}
