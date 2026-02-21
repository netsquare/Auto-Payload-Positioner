package com.netsquare.autopayloadpositioner.util;

import burp.api.montoya.http.message.HttpHeader;
import burp.api.montoya.http.message.requests.HttpRequest;

public final class ContentTypeUtils {
    private ContentTypeUtils() {
    }

    public static String getContentType(HttpRequest httpRequest) {
        for (HttpHeader header : httpRequest.headers()) {
            if (header.name().equalsIgnoreCase("Content-Type")) {
                return header.value().toLowerCase();
            }
        }
        return "";
    }
}

