package com.netsquare.autopayloadpositioner.body.mixed;

import burp.api.montoya.core.Range;
import com.netsquare.autopayloadpositioner.body.json.JsonBodyProcessor;
import com.netsquare.autopayloadpositioner.body.xml.XmlBodyProcessor;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class MixedBodyProcessor {
    private final JsonBodyProcessor jsonBodyProcessor;
    private final XmlBodyProcessor xmlBodyProcessor;

    public MixedBodyProcessor(JsonBodyProcessor jsonBodyProcessor, XmlBodyProcessor xmlBodyProcessor) {
        this.jsonBodyProcessor = jsonBodyProcessor;
        this.xmlBodyProcessor = xmlBodyProcessor;
    }

    public void process(String requestBody, int requestBodyStart, List<Range> positions) {
        // Find JSON object/array snippets embedded in other formats
        Pattern jsonPattern = Pattern.compile("\\{[^\\{\\}]*\\}|\\[[^\\[\\]]*\\]");
        Matcher jsonMatcher = jsonPattern.matcher(requestBody);

        int matchCount = 0;
        while (jsonMatcher.find() && matchCount < 20) {
            String jsonCandidate = jsonMatcher.group();
            if ((jsonCandidate.startsWith("{") && jsonCandidate.endsWith("}")) ||
                    (jsonCandidate.startsWith("[") && jsonCandidate.endsWith("]"))) {
                jsonBodyProcessor.process(jsonCandidate, requestBodyStart + jsonMatcher.start(), positions);
            }
            matchCount++;
        }

        // XML-like snippets
        if (requestBody.contains("<") && requestBody.contains(">")) {
            Pattern xmlPattern = Pattern.compile("<[^>]+>[^<]*</[^>]+>|<[^>]+/>");
            Matcher xmlMatcher = xmlPattern.matcher(requestBody);

            matchCount = 0;
            while (xmlMatcher.find() && matchCount < 20) {
                String xmlCandidate = xmlMatcher.group();
                xmlBodyProcessor.process(xmlCandidate, requestBodyStart + xmlMatcher.start(), positions);
                matchCount++;
            }
        }

        // key=value pairs in plain-ish bodies
        Pattern keyValuePattern = Pattern.compile("([\\w\\-]+)=([^&\\s]+)");
        Matcher keyValueMatcher = keyValuePattern.matcher(requestBody);

        matchCount = 0;
        while (keyValueMatcher.find() && matchCount < 30) {
            positions.add(Range.range(
                    requestBodyStart + keyValueMatcher.start(2),
                    requestBodyStart + keyValueMatcher.end(2)
            ));
            matchCount++;
        }
    }
}

