package com.findguni.model;

public enum Role {
    MAKER("메이커"), ADMIN("통합 관리자");

    private final String displayName;
    Role(String displayName) { this.displayName = displayName; }
    public String getDisplayName() { return displayName; }
}
