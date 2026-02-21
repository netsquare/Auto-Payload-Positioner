package com.netsquare.autopayloadpositioner.core;

public enum PayloadPositionMode {
    DEFAULT("Default (No HTTP Method)"),
    DEFAULT_FULL_PATH("Default + Full URL Path"),
    EVERYTHING("Everything (Default + HTTP Method)"),
    EVERYTHING_FULL_PATH("Everything + Full URL Path"),
    HEADERS_ONLY("Headers Only"),
    HEADERS_METHOD("Headers + Method"),
    HEADERS_URL_LAST("Headers + URL Path Last Part"),
    HEADERS_URL_LAST_METHOD("Headers + URL Path Last Part + Method"),
    HEADERS_FULL_PATH("Headers + Full URL Path"),
    HEADERS_FULL_PATH_METHOD("Headers + Full URL Path + Method");

    private final String displayName;

    PayloadPositionMode(String displayName) {
        this.displayName = displayName;
    }

    @Override
    public String toString() {
        return displayName;
    }
}

