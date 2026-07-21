package com.example.sillyspringboot.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.NotBlank;

@Validated
@ConfigurationProperties(prefix = "app")
public class AppProperties {

    /**
     * 运行环境标签：dev / staging / prod 等，供日志与观测区分。
     */
    @NotBlank
    private String environment = "dev";

    public String getEnvironment() {
        return environment;
    }

    public void setEnvironment(String environment) {
        this.environment = environment;
    }
}
