package com.findguni.model;

public enum PuzzleType {
    STORY("스토리"), NUMBER_LOCK("숫자 휠"), KEYPAD("숫자 키패드"),
    ALPHABET_LOCK("알파벳 휠"), DIRECTION_LOCK("방향 자물쇠"),
    COLOR_LOCK("색상 자물쇠"), MULTIPLE_CHOICE("객관식"), TEXT_ANSWER("단답형");

    private final String displayName;
    PuzzleType(String displayName) { this.displayName = displayName; }
    public String getDisplayName() { return displayName; }
    public boolean requiresAnswer() { return this != STORY; }
}
