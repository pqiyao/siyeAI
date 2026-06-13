package com.example.sillyspringboot.admin.service;

import java.util.List;
import java.util.Map;

public record AdminCharacterPageResult(long total, List<Map<String, Object>> rows) {}
