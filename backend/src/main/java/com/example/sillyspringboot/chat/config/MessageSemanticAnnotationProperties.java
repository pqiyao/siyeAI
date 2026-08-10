package com.example.sillyspringboot.chat.config;

import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "app.chat.semantic-annotation")
public class MessageSemanticAnnotationProperties {

    private boolean enabled = true;

    @Min(1)
    private int workerThreads = 2;

    @Min(1)
    private int queueCapacity = 64;

    @Min(3)
    private int timeoutSeconds = 30;

    @Min(100)
    private int maxContentChars = 12000;

    public boolean isEnabled() { return enabled; }

    public void setEnabled(boolean enabled) { this.enabled = enabled; }

    public int getWorkerThreads() { return workerThreads; }

    public void setWorkerThreads(int workerThreads) { this.workerThreads = workerThreads; }

    public int getQueueCapacity() { return queueCapacity; }

    public void setQueueCapacity(int queueCapacity) { this.queueCapacity = queueCapacity; }

    public int getTimeoutSeconds() { return timeoutSeconds; }

    public void setTimeoutSeconds(int timeoutSeconds) { this.timeoutSeconds = timeoutSeconds; }

    public int getMaxContentChars() { return maxContentChars; }

    public void setMaxContentChars(int maxContentChars) { this.maxContentChars = maxContentChars; }

}
