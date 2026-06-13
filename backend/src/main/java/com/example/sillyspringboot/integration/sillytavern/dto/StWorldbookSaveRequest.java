package com.example.sillyspringboot.integration.sillytavern.dto;

import java.util.Map;

public record StWorldbookSaveRequest(String name, Map<String, Object> data) {
}
