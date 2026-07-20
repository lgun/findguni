package com.findguni.model;

public enum GameStatus {
    DRAFT("초안"), PUBLISHED("게시됨"), HIDDEN("숨김");

    private final String displayName;
    GameStatus(String displayName) { this.displayName = displayName; }
    public String getDisplayName() { return displayName; }
}
