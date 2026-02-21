package com.netsquare.autopayloadpositioner.core.positioners;

import burp.api.montoya.core.Range;
import burp.api.montoya.http.message.HttpHeader;
import burp.api.montoya.http.message.requests.HttpRequest;

import java.util.List;

public final class HeaderPositioner {
    public void add(HttpRequest request, String requestString, List<Range> positions) {
        List<HttpHeader> headers = request.headers();
        for (HttpHeader header : headers) {
            String headerLine = header.name() + ": " + header.value();
            int headerStart = requestString.indexOf(headerLine);

            if (headerStart < 0) continue;

            String headerValue = header.value();
            int valueStart = headerStart + header.name().length() + 2; // +2 for ": "

            // Case 1: Cookies
            if (header.name().equalsIgnoreCase("Cookie") || header.name().contains("cookie")) {
                String[] pairs = headerValue.split("; ");
                for (String pair : pairs) {
                    int equalPos = pair.indexOf('=');
                    if (equalPos > 0 && equalPos < pair.length() - 1) {
                        String value = pair.substring(equalPos + 1).trim();
                        int pairStart = headerValue.indexOf(pair);
                        if (pairStart >= 0) {
                            int actualValueStart = valueStart + pairStart + equalPos + 1;
                            int actualValueEnd = actualValueStart + value.length();
                            if (actualValueStart >= valueStart &&
                                    actualValueEnd <= headerStart + headerLine.length()) {
                                positions.add(Range.range(actualValueStart, actualValueEnd));
                            }
                        }
                    }
                }
            }
            // Case 2: Authorization header
            else if (header.name().equalsIgnoreCase("Authorization")) {
                String[] parts = headerValue.split(" ", 2);
                if (parts.length == 2) {
                    int tokenStart = valueStart + parts[0].length() + 1;
                    int tokenEnd = headerStart + headerLine.length();
                    positions.add(Range.range(tokenStart, tokenEnd));
                } else {
                    positions.add(Range.range(valueStart, headerStart + headerLine.length()));
                }
            }
            // Case 3: normal header value
            else {
                positions.add(Range.range(valueStart, headerStart + headerLine.length()));
            }
        }
    }
}

