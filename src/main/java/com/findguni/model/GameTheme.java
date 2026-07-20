package com.findguni.model;

public enum GameTheme {
    MIDNIGHT("미드나잇"), MANSION("고딕 저택"), LAB("비밀 연구소"),
    FOREST("신비한 숲"), RETRO("레트로 아케이드");

    private final String displayName;
    GameTheme(String displayName) { this.displayName = displayName; }
    public String getDisplayName() { return displayName; }
}
