package com.findguni.model;

public enum GameVisibility {
    LINK_ONLY("링크 공개"), PUBLIC("전체 공개");

    private final String displayName;
    GameVisibility(String displayName) { this.displayName = displayName; }
    public String getDisplayName() { return displayName; }
}
