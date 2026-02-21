package com.netsquare.autopayloadpositioner.core.positioners;

import burp.api.montoya.core.Range;
import burp.api.montoya.http.message.requests.HttpRequest;

import java.util.List;

public final class MethodPositioner {
    public void add(HttpRequest request, String requestString, List<Range> positions) {
        String method = request.method();
        int methodStart = requestString.indexOf(method);
        if (methodStart >= 0) {
            positions.add(Range.range(methodStart, methodStart + method.length()));
        }
    }
}

