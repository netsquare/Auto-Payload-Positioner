package com.netsquare.autopayloadpositioner.core.positioners;

import burp.api.montoya.core.Range;
import burp.api.montoya.http.message.requests.HttpRequest;

import java.util.List;

public final class UrlPositioner {
    public void add(HttpRequest request, String requestString, boolean useFullUrlPath, List<Range> positions) {
        String urlPath = request.pathWithoutQuery();
        int urlPathStart = requestString.indexOf(urlPath);
        if (urlPathStart < 0) return;

        if (useFullUrlPath) {
            positions.add(Range.range(urlPathStart, urlPathStart + urlPath.length()));
            return;
        }

        // Montoya does not allow 0-byte insertion points; use last segment.
        if (urlPath.endsWith("/")) {
            String pathWithoutTrailingSlash = urlPath.substring(0, urlPath.length() - 1);
            int secondLastSlashIdx = pathWithoutTrailingSlash.lastIndexOf('/') + 1;
            int segStart = urlPathStart + secondLastSlashIdx;
            int segEnd = urlPathStart + urlPath.length() - 1; // exclude trailing slash
            if (segStart < segEnd) {
                positions.add(Range.range(segStart, segEnd));
            }
        } else {
            int lastSlashIdx = urlPath.lastIndexOf('/') + 1;
            int segStart = urlPathStart + lastSlashIdx;
            int segEnd = urlPathStart + urlPath.length();
            if (segStart < segEnd) {
                positions.add(Range.range(segStart, segEnd));
            }
        }
    }
}

