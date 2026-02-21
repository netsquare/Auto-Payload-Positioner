package com.netsquare.autopayloadpositioner.core;

public final class ModeRules {
    private ModeRules() {
    }

    public static boolean shouldIncludeMethod(PayloadPositionMode mode) {
        return mode == PayloadPositionMode.EVERYTHING ||
                mode == PayloadPositionMode.EVERYTHING_FULL_PATH ||
                mode == PayloadPositionMode.HEADERS_METHOD ||
                mode == PayloadPositionMode.HEADERS_URL_LAST_METHOD ||
                mode == PayloadPositionMode.HEADERS_FULL_PATH_METHOD;
    }

    public static boolean shouldIncludeUrlPath(PayloadPositionMode mode) {
        return mode != PayloadPositionMode.HEADERS_ONLY &&
                mode != PayloadPositionMode.HEADERS_METHOD;
    }

    public static boolean shouldUseFullUrlPath(PayloadPositionMode mode) {
        return mode == PayloadPositionMode.DEFAULT_FULL_PATH ||
                mode == PayloadPositionMode.EVERYTHING_FULL_PATH ||
                mode == PayloadPositionMode.HEADERS_FULL_PATH ||
                mode == PayloadPositionMode.HEADERS_FULL_PATH_METHOD;
    }

    public static boolean shouldIncludeParameters(PayloadPositionMode mode) {
        return mode != PayloadPositionMode.HEADERS_ONLY &&
                mode != PayloadPositionMode.HEADERS_METHOD &&
                mode != PayloadPositionMode.HEADERS_URL_LAST &&
                mode != PayloadPositionMode.HEADERS_URL_LAST_METHOD &&
                mode != PayloadPositionMode.HEADERS_FULL_PATH &&
                mode != PayloadPositionMode.HEADERS_FULL_PATH_METHOD;
    }

    public static boolean shouldIncludeBody(PayloadPositionMode mode) {
        return mode != PayloadPositionMode.HEADERS_ONLY &&
                mode != PayloadPositionMode.HEADERS_METHOD &&
                mode != PayloadPositionMode.HEADERS_URL_LAST &&
                mode != PayloadPositionMode.HEADERS_URL_LAST_METHOD &&
                mode != PayloadPositionMode.HEADERS_FULL_PATH &&
                mode != PayloadPositionMode.HEADERS_FULL_PATH_METHOD;
    }

    public static boolean shouldIncludeHeaders(PayloadPositionMode mode) {
        return mode.name().startsWith("HEADERS") ||
                mode == PayloadPositionMode.DEFAULT ||
                mode == PayloadPositionMode.DEFAULT_FULL_PATH ||
                mode == PayloadPositionMode.EVERYTHING ||
                mode == PayloadPositionMode.EVERYTHING_FULL_PATH;
    }
}

