package com.findguni.model;

public enum StoryEffect {
    NONE("효과 없음"),
    FADE("페이드"),
    TYPEWRITER("타이프라이터"),
    GLITCH("글리치"),
    SHAKE("흔들림"),
    FLICKER("깜빡임"),
    SPOTLIGHT("스포트라이트");

    private final String displayName;

    StoryEffect(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}
