package com.findguni.model;

public enum GameFlowMode {
    QR_EXPLORATION("QR 탐색형", "현장의 QR을 자유롭게 찾아 문제와 단서를 발견합니다."),
    LINEAR("선형 진행형", "정해진 순서대로 문제와 이야기가 이어집니다.");

    private final String displayName;
    private final String description;

    GameFlowMode(String displayName, String description) {
        this.displayName = displayName;
        this.description = description;
    }

    public String getDisplayName() { return displayName; }
    public String getDescription() { return description; }
}
