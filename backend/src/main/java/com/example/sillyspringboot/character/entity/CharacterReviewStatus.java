package com.example.sillyspringboot.character.entity;

public final class CharacterReviewStatus {

    public static final String PENDING = "PENDING";
    public static final String APPROVED = "APPROVED";
    public static final String REJECTED = "REJECTED";

    private CharacterReviewStatus() {}

    public static boolean isValid(String value) {
        return PENDING.equals(value) || APPROVED.equals(value) || REJECTED.equals(value);
    }

    public static String normalize(String value) {
        if (value == null) {
            return APPROVED;
        }
        String s = value.trim().toUpperCase();
        return isValid(s) ? s : APPROVED;
    }
}
