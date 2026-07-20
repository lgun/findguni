package com.findguni.model;

public enum StageEntryMode {
    START("시작부터 공개", "게임을 시작하면 문제 목록에 바로 표시됩니다."),
    QR("QR로 발견", "현장 QR을 스캔해야 문제가 열립니다."),
    LINKED("이전 문제에서 연결", "다른 문제를 해결했을 때만 자동으로 열립니다.");

    private final String displayName;
    private final String description;

    StageEntryMode(String displayName, String description) {
        this.displayName = displayName;
        this.description = description;
    }

    public String getDisplayName() { return displayName; }
    public String getDescription() { return description; }
}
