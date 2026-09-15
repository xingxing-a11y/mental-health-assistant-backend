package com.mindharbor;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.mybatis.spring.annotation.MapperScan;

@SpringBootApplication
@MapperScan(
        basePackages = {
                "com.mindharbor.modules.admin.mapper",
                "com.mindharbor.modules.article.mapper",
                "com.mindharbor.modules.assessment.mapper",
                "com.mindharbor.modules.chat.mapper",
                "com.mindharbor.modules.diary.mapper",
                "com.mindharbor.modules.knowledge.mapper",
                "com.mindharbor.modules.user.mapper"
        })
public class AssistantApplication {

    public static void main(String[] args) {
        SpringApplication.run(AssistantApplication.class, args);
    }
}




