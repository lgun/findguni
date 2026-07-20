package com.findguni.model;

public enum AccountStatus {
    ACTIVE("활성"), SUSPENDED("정지");

    private final String displayName;
    AccountStatus(String displayName) { this.displayName = displayName; }
    public String getDisplayName() { return displayName; }
}
