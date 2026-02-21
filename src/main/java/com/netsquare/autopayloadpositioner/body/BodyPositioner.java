package com.netsquare.autopayloadpositioner.body;

import burp.api.montoya.core.Range;
import burp.api.montoya.http.message.requests.HttpRequest;
import com.netsquare.autopayloadpositioner.body.json.JsonBodyProcessor;
import com.netsquare.autopayloadpositioner.body.mixed.MixedBodyProcessor;
import com.netsquare.autopayloadpositioner.body.xml.XmlBodyProcessor;
import com.netsquare.autopayloadpositioner.util.ContentTypeUtils;

import java.util.List;

public final class BodyPositioner {
    private final JsonBodyProcessor jsonBodyProcessor;
    private final XmlBodyProcessor xmlBodyProcessor;
    private final MixedBodyProcessor mixedBodyProcessor;

    public BodyPositioner() {
        this.jsonBodyProcessor = new JsonBodyProcessor();
        this.xmlBodyProcessor = new XmlBodyProcessor();
        this.mixedBodyProcessor = new MixedBodyProcessor(jsonBodyProcessor, xmlBodyProcessor);
    }

    public void addBodyPositions(HttpRequest request, String requestString, List<Range> positions) {
        int requestBodyStart = requestString.indexOf("\r\n\r\n") + 4;
        if (requestBodyStart <= 4 || requestBodyStart >= requestString.length()) return;

        String requestBody = requestString.substring(requestBodyStart);
        String contentType = ContentTypeUtils.getContentType(request);

        if (contentType.contains("json") || requestBody.trim().startsWith("{")
                || requestBody.trim().startsWith("[")) {
            jsonBodyProcessor.process(requestBody, requestBodyStart, positions);
        } else if (contentType.contains("xml") || requestBody.trim().startsWith("<")) {
            xmlBodyProcessor.process(requestBody, requestBodyStart, positions);
        } else {
            mixedBodyProcessor.process(requestBody, requestBodyStart, positions);
        }
    }
}

