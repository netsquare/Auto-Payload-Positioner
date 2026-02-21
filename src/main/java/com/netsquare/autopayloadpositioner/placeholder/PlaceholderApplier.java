package com.netsquare.autopayloadpositioner.placeholder;

import burp.api.montoya.core.Range;
import burp.api.montoya.http.message.requests.HttpRequest;
import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonToken;
import com.netsquare.autopayloadpositioner.body.json.JsonTokenUtils;
import com.netsquare.autopayloadpositioner.core.InsertionOp;
import com.netsquare.autopayloadpositioner.core.PlaceholderResult;
import com.netsquare.autopayloadpositioner.util.ContentTypeUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

public final class PlaceholderApplier {
    public static final String EMPTY_VALUE_PLACEHOLDER = "__DELETE_ME__";

    public PlaceholderResult apply(HttpRequest request, String requestString) {
        TreeMap<Integer, String> plannedInsertions = new TreeMap<>();
        String contentType = ContentTypeUtils.getContentType(request);

        int bodyStart = requestString.indexOf("\r\n\r\n");
        bodyStart = bodyStart >= 0 ? bodyStart + 4 : -1;
        String body = bodyStart >= 0 && bodyStart <= requestString.length()
                ? requestString.substring(bodyStart)
                : "";

        // 1) Query string empty pairs: ?a=&b=
        collectEmptyQueryInsertions(requestString, plannedInsertions);

        // 2) Body key=value empties for form/plain style content
        boolean looksJson = contentType.contains("json") || body.trim().startsWith("{") || body.trim().startsWith("[");
        boolean looksXml = contentType.contains("xml") || body.trim().startsWith("<");
        boolean canScanKeyValueBody = !looksJson && !looksXml && bodyStart >= 0;
        if (canScanKeyValueBody) {
            collectEmptyKeyValueInsertions(body, bodyStart, plannedInsertions);
        }

        // 3) JSON empty strings: {"x":""}
        if (looksJson && bodyStart >= 0 && !body.isEmpty()) {
            collectEmptyJsonStringValueInsertions(body, bodyStart, plannedInsertions);
        }

        if (plannedInsertions.isEmpty()) {
            return new PlaceholderResult(requestString, new ArrayList<>(), new ArrayList<>(), false);
        }

        List<InsertionOp> ops = new ArrayList<>();
        List<Range> placeholderRanges = new ArrayList<>();
        StringBuilder modified = new StringBuilder(
                requestString.length() + plannedInsertions.size() * EMPTY_VALUE_PLACEHOLDER.length()
        );
        int cursor = 0;

        for (Map.Entry<Integer, String> entry : plannedInsertions.entrySet()) {
            int insertAt = entry.getKey();
            String text = entry.getValue();
            if (insertAt < 0 || insertAt > requestString.length()) continue;
            if (insertAt < cursor) continue;

            modified.append(requestString, cursor, insertAt);
            int newInsertStart = modified.length();
            modified.append(text);
            int newInsertEnd = modified.length();

            ops.add(new InsertionOp(insertAt, text));
            placeholderRanges.add(Range.range(newInsertStart, newInsertEnd));

            cursor = insertAt;
        }
        modified.append(requestString.substring(cursor));

        return new PlaceholderResult(modified.toString(), placeholderRanges, ops, true);
    }

    private void collectEmptyQueryInsertions(String requestString, TreeMap<Integer, String> insertions) {
        int firstLineEnd = requestString.indexOf("\r\n");
        if (firstLineEnd <= 0) return;
        String requestLine = requestString.substring(0, firstLineEnd);

        int firstSpace = requestLine.indexOf(' ');
        int secondSpace = requestLine.lastIndexOf(' ');
        if (firstSpace < 0 || secondSpace <= firstSpace) return;

        String target = requestLine.substring(firstSpace + 1, secondSpace);
        int qIdx = target.indexOf('?');
        if (qIdx < 0 || qIdx + 1 >= target.length()) return;

        int absoluteTargetStart = firstSpace + 1;
        String query = target.substring(qIdx + 1);
        collectEmptyKeyValueInsertions(query, absoluteTargetStart + qIdx + 1, insertions);
    }

    private void collectEmptyKeyValueInsertions(String text, int baseOffset, TreeMap<Integer, String> insertions) {
        int i = 0;
        while (i < text.length()) {
            int eq = text.indexOf('=', i);
            if (eq < 0) break;

            int afterEq = eq + 1;
            if (afterEq >= text.length()) {
                insertions.putIfAbsent(baseOffset + afterEq, EMPTY_VALUE_PLACEHOLDER);
                break;
            }

            char next = text.charAt(afterEq);
            if (next == '&' || next == ';' || next == '\n' || next == '\r' || next == ' ') {
                insertions.putIfAbsent(baseOffset + afterEq, EMPTY_VALUE_PLACEHOLDER);
                i = afterEq + 1;
                continue;
            }

            // Skip this value until next separator
            int sep = afterEq;
            while (sep < text.length()) {
                char c = text.charAt(sep);
                if (c == '&' || c == ';' || c == '\n' || c == '\r') break;
                sep++;
            }
            i = sep + 1;
        }
    }

    private void collectEmptyJsonStringValueInsertions(String jsonBody, int baseOffset, TreeMap<Integer, String> insertions) {
        try {
            JsonFactory factory = JsonFactory.builder().build();
            try (com.fasterxml.jackson.core.JsonParser parser = factory.createParser(jsonBody)) {
                while (!parser.isClosed()) {
                    JsonToken token = parser.nextToken();
                    if (token == null) break;
                    if (token != JsonToken.VALUE_STRING) continue;

                    String value = parser.getText();
                    if (value != null && value.isEmpty()) {
                        int[] bounds = JsonTokenUtils.locateStringTokenContentBounds(jsonBody, parser);
                        if (bounds != null) {
                            // Empty string means contentStart == contentEnd. Insert placeholder there.
                            insertions.putIfAbsent(baseOffset + bounds[0], EMPTY_VALUE_PLACEHOLDER);
                        }
                    }
                }
            }
        } catch (Exception ignored) {
           
        }
    }
}

