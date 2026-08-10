package com.example.sillyspringboot.conversation.service;

import com.example.sillyspringboot.conversation.mapper.ConversationMemoryRefreshMetricMapper;
import com.example.sillyspringboot.conversation.model.ConversationMemoryRefreshMetric;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ConversationMemoryRefreshMetricsService {

    private final ConversationMemoryRefreshMetricMapper mapper;

    public ConversationMemoryRefreshMetricsService(ConversationMemoryRefreshMetricMapper mapper) {
        this.mapper = mapper;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(ConversationMemoryRefreshMetric metric) {
        mapper.insert(metric);
    }
}
