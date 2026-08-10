package com.example.sillyspringboot.ops.service;

import java.util.Map;

public interface ImageGenerationEngine {

    String engineName();

    Map<String, Object> generate(String clientUid, Map<String, Object> payload);
}
