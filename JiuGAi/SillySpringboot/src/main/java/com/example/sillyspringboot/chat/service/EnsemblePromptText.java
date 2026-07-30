package com.example.sillyspringboot.chat.service;

final class EnsemblePromptText {
    private EnsemblePromptText() {
    }

    static String sanitize(String value) {
        if (value == null) {
            return "";
        }
        return value
                .replaceAll("[\\r\\n]+", " ")
                .replace("<|", "< ")
                .replace("|>", " >")
                .trim();
    }
}
