package com.archive;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 投委会档案管理系统 - 后端启动类.
 *
 * @author Mavis
 */
@SpringBootApplication
@EnableScheduling
@EnableJpaAuditing
public class ArchiveApplication {

    public static void main(String[] args) {
        SpringApplication.run(ArchiveApplication.class, args);
    }
}
