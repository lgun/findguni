package com.findguni.model;

public enum Difficulty {
    EASY("쉬움"), NORMAL("보통"), HARD("어려움"), EXPERT("전문가");

    private final String displayName;
    Difficulty(String displayName) { this.displayName = displayName; }
    public String getDisplayName() { return displayName; }
}
