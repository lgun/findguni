package com.findguni.model;

public enum ItemType {
    KEY("열쇠"), DOCUMENT("문서"), PHOTO("사진"), MAP("지도"), TOOL("도구"),
    SYMBOL("상징"), EVIDENCE("증거"), DEVICE("장치"), FOOD("음식"), CUSTOM("기타");

    private final String displayName;
    ItemType(String displayName) { this.displayName = displayName; }
    public String getDisplayName() { return displayName; }
}
