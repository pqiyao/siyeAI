package com.example.sillyspringboot.conversation.mapper;

import com.example.sillyspringboot.conversation.model.ConversationMemoryRefreshMetric;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface ConversationMemoryRefreshMetricMapper {

    void insert(ConversationMemoryRefreshMetric metric);
}
