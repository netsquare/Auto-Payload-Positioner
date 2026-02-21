package com.netsquare.autopayloadpositioner.body.xml;

import burp.api.montoya.core.Range;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class XmlBodyProcessor {
    public void process(String requestBody, int requestBodyStart, List<Range> positions) {
        try {
            Pattern tagPattern = Pattern.compile("<([^\\s/>]+)[^>]*>(.*?)</\\1>|<([^\\s/>]+)\\s+([^>]*)/>");
            Matcher matcher = tagPattern.matcher(requestBody);
            int matchCount = 0;

            while (matcher.find() && matchCount < 50) {
                // Self-closing tags with attributes
                if (matcher.group(3) != null && matcher.group(4) != null) {
                    String attributes = matcher.group(4);
                    processXmlAttributes(attributes, matcher.start(4) + requestBodyStart, positions);
                }
                // Start-end tags with attributes and content
                else if (matcher.group(1) != null) {
                    String content = matcher.group(2);
                    if (!content.trim().isEmpty() && !content.contains("<")) {
                        positions.add(Range.range(
                                requestBodyStart + matcher.start(2),
                                requestBodyStart + matcher.end(2)));
                    }

                    // Process attributes in opening tag
                    int tagEnd = requestBody.indexOf('>', matcher.start());
                    if (tagEnd > matcher.start()) {
                        String openingTag = requestBody.substring(matcher.start(), tagEnd);
                        int attrStart = openingTag.indexOf(' ');
                        if (attrStart > 0) {
                            String attributes = openingTag.substring(attrStart + 1);
                            processXmlAttributes(
                                    attributes,
                                    matcher.start() + attrStart + 1 + requestBodyStart,
                                    positions
                            );
                        }
                    }

                    // Recursively process nested tags
                    if (content.contains("<")) {
                        process(content, requestBodyStart + matcher.start(2), positions);
                    }
                }

                matchCount++;
            }
        } catch (Exception e) {
            // Ignore invalid XML fragments.
        }
    }

    private void processXmlAttributes(String attributes, int baseOffset, List<Range> positions) {
        Pattern attrPattern = Pattern.compile("(\\w+)\\s*=\\s*([\"'])(.*?)\\2");
        Matcher matcher = attrPattern.matcher(attributes);

        int matchCount = 0;
        while (matcher.find() && matchCount < 20) {
            positions.add(Range.range(
                    baseOffset + matcher.start(3),
                    baseOffset + matcher.end(3)));
            matchCount++;
        }
    }
}

