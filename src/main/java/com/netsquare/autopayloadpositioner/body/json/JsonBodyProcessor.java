package com.netsquare.autopayloadpositioner.body.json;

import burp.api.montoya.core.Range;
import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class JsonBodyProcessor {
    private static final int MAX_JSON_BODY_CHARS = 500_000;
    private static final int MAX_JSON_INSERTION_POINTS = 5_000;
    private static final int MAX_EMBEDDED_JSON_CHARS = 150_000;

    public void process(String requestBody, int requestBodyStart, List<Range> positions) {
        if (requestBody == null) return;
        if (requestBody.length() > MAX_JSON_BODY_CHARS) {
            return;
        }

        try {
            processJsonWithStreamingOffsets(requestBody, requestBodyStart, positions);
        } catch (Exception e) {
            processJsonDirectRegex(requestBody, requestBodyStart, positions);
        }
    }

    private void processJsonWithStreamingOffsets(String json, int baseOffset, List<Range> positions) throws Exception {
        JsonFactory factory = JsonFactory.builder().build();

        try (com.fasterxml.jackson.core.JsonParser parser = factory.createParser(json)) {
            while (!parser.isClosed()) {
                JsonToken token = parser.nextToken();
                if (token == null) break;
                if (positions.size() >= MAX_JSON_INSERTION_POINTS) break;

                if (token == JsonToken.FIELD_NAME) {
                    // Example: JSON key contains embedded JSON
                    processPossiblyEmbeddedJsonStringToken(json, baseOffset, positions, parser, true);
                } else if (token == JsonToken.VALUE_STRING) {
                    processPossiblyEmbeddedJsonStringToken(json, baseOffset, positions, parser, false);
                } else if (token == JsonToken.VALUE_NUMBER_INT ||
                        token == JsonToken.VALUE_NUMBER_FLOAT ||
                        token == JsonToken.VALUE_TRUE ||
                        token == JsonToken.VALUE_FALSE) {
                    addNonStringScalarPosition(json, baseOffset, positions, parser);
                }
            }
        }
    }

    private void addNonStringScalarPosition(String json, int baseOffset, List<Range> positions,
                                            com.fasterxml.jackson.core.JsonParser parser) {
        long start = parser.currentTokenLocation().getCharOffset();
        if (start < 0 || start >= json.length()) return;

        int startIdx = adjustScalarTokenStart(json, (int) start);
        int endIdx = findNonStringTokenEnd(json, startIdx);
        if (endIdx <= startIdx) return;

        positions.add(Range.range(baseOffset + startIdx, baseOffset + endIdx));
    }

    private void processPossiblyEmbeddedJsonStringToken(
            String json,
            int baseOffset,
            List<Range> positions,
            com.fasterxml.jackson.core.JsonParser parser,
            boolean isFieldName
    ) {
        int[] contentBounds = locateStringTokenContentBounds(json, parser);
        if (contentBounds == null) return;
        int contentStart = contentBounds[0];
        int contentEnd = contentBounds[1];
        if (contentEnd <= contentStart) return;

        String rawContent = json.substring(contentStart, contentEnd);

        if (rawContent.length() <= MAX_EMBEDDED_JSON_CHARS) {
            UnescapedStringMapping mapping = unescapeJsonStringWithMapping(rawContent);
            String unescaped = mapping.unescaped;
            if (looksLikeJsonText(unescaped)) {
                boolean extractedAny = extractEmbeddedJsonInsertionPoints(
                        unescaped,
                        mapping,
                        baseOffset + contentStart,
                        positions
                );
                if (extractedAny) {
                    return;
                }
            }
        }

        // Non-embedded: add whole string value content; keys are ignored by default.
        if (!isFieldName) {
            positions.add(Range.range(baseOffset + contentStart, baseOffset + contentEnd));
        }
    }

    private boolean extractEmbeddedJsonInsertionPoints(
            String unescapedJson,
            UnescapedStringMapping mapping,
            int rawContentBaseOffset,
            List<Range> positions
    ) {
        int addedBefore = positions.size();

        try {
            JsonFactory factory = JsonFactory.builder().build();
            try (com.fasterxml.jackson.core.JsonParser parser = factory.createParser(unescapedJson)) {
                while (!parser.isClosed()) {
                    JsonToken token = parser.nextToken();
                    if (token == null) break;
                    if (positions.size() >= MAX_JSON_INSERTION_POINTS) break;

                    if (token == JsonToken.VALUE_STRING) {
                        addEmbeddedStringValue(unescapedJson, mapping, rawContentBaseOffset, positions, parser);
                    } else if (token == JsonToken.VALUE_NUMBER_INT ||
                            token == JsonToken.VALUE_NUMBER_FLOAT ||
                            token == JsonToken.VALUE_TRUE ||
                            token == JsonToken.VALUE_FALSE) {
                        addEmbeddedNonStringScalar(unescapedJson, mapping, rawContentBaseOffset, positions, parser);
                    }
                }
            }
        } catch (Exception ignored) {
            
        }

        return positions.size() > addedBefore;
    }

    private void addEmbeddedStringValue(
            String unescapedJson,
            UnescapedStringMapping mapping,
            int rawContentBaseOffset,
            List<Range> positions,
            com.fasterxml.jackson.core.JsonParser parser
    ) {
        int[] contentBounds = locateStringTokenContentBounds(unescapedJson, parser);
        if (contentBounds == null) return;
        int contentStart = contentBounds[0];
        int contentEnd = contentBounds[1];
        if (contentEnd <= contentStart) return;

        int[] rawSpan = mapUnescapedSpanToRaw(mapping, contentStart, contentEnd);
        if (rawSpan == null) return;

        positions.add(Range.range(rawContentBaseOffset + rawSpan[0], rawContentBaseOffset + rawSpan[1]));
    }

    private void addEmbeddedNonStringScalar(
            String unescapedJson,
            UnescapedStringMapping mapping,
            int rawContentBaseOffset,
            List<Range> positions,
            com.fasterxml.jackson.core.JsonParser parser
    ) {
        long start = parser.currentTokenLocation().getCharOffset();
        if (start < 0 || start >= unescapedJson.length()) return;
        int startIdx = adjustScalarTokenStart(unescapedJson, (int) start);

        int endIdx = findNonStringTokenEnd(unescapedJson, startIdx);
        if (endIdx <= startIdx) return;

        int[] rawSpan = mapUnescapedSpanToRaw(mapping, startIdx, endIdx);
        if (rawSpan == null) return;

        positions.add(Range.range(rawContentBaseOffset + rawSpan[0], rawContentBaseOffset + rawSpan[1]));
    }

    private static boolean looksLikeJsonText(String s) {
        if (s == null) return false;
        String t = s.trim();
        if (t.length() < 2) return false;
        return (t.startsWith("{") && t.endsWith("}")) || (t.startsWith("[") && t.endsWith("]"));
    }

    private static int findNonStringTokenEnd(String json, int startIdx) {
        int i = startIdx;
        while (i < json.length()) {
            char c = json.charAt(i);
            if (c == ',' || c == '}' || c == ']' ||
                    c == ' ' || c == '\n' || c == '\r' || c == '\t') {
                break;
            }
            i++;
        }
        return i;
    }

    private static int adjustScalarTokenStart(String json, int startIdx) {
        int i = Math.max(0, Math.min(startIdx, json.length() - 1));
        while (i < json.length()) {
            char c = json.charAt(i);
            if (c == ':' || c == ' ' || c == '\n' || c == '\r' || c == '\t') {
                i++;
                continue;
            }
            break;
        }
        return i;
    }

    private static final class UnescapedStringMapping {
        final String unescaped;
        final int[] rawStartByUnescapedIndex;
        final int[] rawEndExclusiveByUnescapedIndex;

        UnescapedStringMapping(String unescaped, int[] rawStartByUnescapedIndex, int[] rawEndExclusiveByUnescapedIndex) {
            this.unescaped = unescaped;
            this.rawStartByUnescapedIndex = rawStartByUnescapedIndex;
            this.rawEndExclusiveByUnescapedIndex = rawEndExclusiveByUnescapedIndex;
        }
    }

    private static UnescapedStringMapping unescapeJsonStringWithMapping(String rawContent) {
        StringBuilder out = new StringBuilder(rawContent.length());
        int[] rawStart = new int[rawContent.length()];
        int[] rawEnd = new int[rawContent.length()];
        int outLen = 0;

        for (int i = 0; i < rawContent.length(); i++) {
            char c = rawContent.charAt(i);
            if (c != '\\') {
                out.append(c);
                rawStart[outLen] = i;
                rawEnd[outLen] = i + 1;
                outLen++;
                continue;
            }

            if (i + 1 >= rawContent.length()) {
                out.append('\\');
                rawStart[outLen] = i;
                rawEnd[outLen] = i + 1;
                outLen++;
                continue;
            }

            char esc = rawContent.charAt(i + 1);
            if (esc == 'u' && i + 5 < rawContent.length()) {
                String hex = rawContent.substring(i + 2, i + 6);
                char decoded;
                try {
                    decoded = (char) Integer.parseInt(hex, 16);
                } catch (NumberFormatException e) {
                    decoded = 'u';
                }
                out.append(decoded);
                rawStart[outLen] = i;
                rawEnd[outLen] = Math.min(rawContent.length(), i + 6);
                outLen++;
                i += 5;
                continue;
            }

            char decoded;
            switch (esc) {
                case '"':
                case '\\':
                case '/':
                    decoded = esc;
                    break;
                case 'b':
                    decoded = '\b';
                    break;
                case 'f':
                    decoded = '\f';
                    break;
                case 'n':
                    decoded = '\n';
                    break;
                case 'r':
                    decoded = '\r';
                    break;
                case 't':
                    decoded = '\t';
                    break;
                default:
                    decoded = esc;
                    break;
            }
            out.append(decoded);
            rawStart[outLen] = i;
            rawEnd[outLen] = i + 2;
            outLen++;
            i++;
        }

        return new UnescapedStringMapping(
                out.toString(),
                Arrays.copyOf(rawStart, outLen),
                Arrays.copyOf(rawEnd, outLen)
        );
    }

    private static int[] mapUnescapedSpanToRaw(UnescapedStringMapping mapping, int unescapedStart, int unescapedEndExclusive) {
        if (mapping == null) return null;
        if (unescapedStart < 0 || unescapedEndExclusive > mapping.unescaped.length()) return null;
        if (unescapedEndExclusive <= unescapedStart) return null;

        int rawStart = mapping.rawStartByUnescapedIndex[unescapedStart];
        int rawEnd = mapping.rawEndExclusiveByUnescapedIndex[unescapedEndExclusive - 1];
        if (rawEnd <= rawStart) return null;
        return new int[]{rawStart, rawEnd};
    }

    private static int[] locateStringTokenContentBounds(String json, com.fasterxml.jackson.core.JsonParser parser) {
        if (json == null || json.isEmpty()) return null;

        long startHintLong = parser.currentTokenLocation().getCharOffset();
        long endHintLong = parser.currentLocation().getCharOffset();
        if (startHintLong < 0) return null;

        int jsonLen = json.length();
        int startHint = (int) Math.min(Math.max(0, startHintLong), jsonLen - 1);
        int endHint = endHintLong < 0 ? jsonLen : (int) Math.min(Math.max(0, endHintLong), jsonLen);

        String tokenText = null;
        try {
            tokenText = parser.getText();
        } catch (Exception ignored) {
            // Ignore token text failures.
        }

        int closeSearchFrom = Math.min(jsonLen - 1, Math.max(0, endHint));
        int closeSearchTo = Math.max(0, startHint - 4096);
        for (int close = closeSearchFrom; close >= closeSearchTo; close--) {
            if (json.charAt(close) != '"' || isEscapedQuote(json, close)) continue;
            for (int open = close - 1; open >= Math.max(0, close - 8192); open--) {
                if (json.charAt(open) != '"' || isEscapedQuote(json, open)) continue;
                String rawContent = json.substring(open + 1, close);
                if (tokenText == null || tokenText.equals(unescapeJsonStringWithMapping(rawContent).unescaped)) {
                    return new int[]{open + 1, close};
                }
            }
        }

        try {
            if (tokenText != null && !tokenText.isEmpty()) {
                String escapedContent = escapeAsJsonStringContent(tokenText);
                int fallbackStart = Math.max(0, startHint - 256);
                int fallbackEnd = Math.min(jsonLen, Math.max(endHint + 2048, startHint + 2048));
                int idx = json.indexOf(escapedContent, fallbackStart);
                while (idx >= 0 && idx < fallbackEnd) {
                    int leftQuote = idx - 1;
                    int rightQuote = idx + escapedContent.length();
                    if (leftQuote >= 0 && rightQuote < jsonLen &&
                            json.charAt(leftQuote) == '"' && json.charAt(rightQuote) == '"') {
                        return new int[]{idx, idx + escapedContent.length()};
                    }
                    idx = json.indexOf(escapedContent, idx + 1);
                }
            }
        } catch (Exception ignored) {
            // Ignore fallback failures.
        }

        return null;
    }

    private static boolean isEscapedQuote(String s, int quoteIdx) {
        int backslashes = 0;
        for (int i = quoteIdx - 1; i >= 0 && s.charAt(i) == '\\'; i--) {
            backslashes++;
        }
        return (backslashes & 1) == 1;
    }

    private static String escapeAsJsonStringContent(String value) throws JsonProcessingException {
        String quoted = new ObjectMapper().writeValueAsString(value);
        if (quoted.length() >= 2 && quoted.charAt(0) == '"' && quoted.charAt(quoted.length() - 1) == '"') {
            return quoted.substring(1, quoted.length() - 1);
        }
        return quoted;
    }

    // Regex fallback for invalid JSON bodies.
    private void processJsonDirectRegex(String requestBody, int requestBodyStart, List<Range> positions) {
        try {
            Pattern[] simplePatterns = {
                    Pattern.compile(":\\s*\"([^\"]{1,500})\""),
                    Pattern.compile(":\\s*(-?\\d{1,18}(?:\\.\\d{1,18})?)"),
                    Pattern.compile(":\\s*(true|false)")
            };

            for (Pattern pattern : simplePatterns) {
                Matcher matcher = pattern.matcher(requestBody);
                int count = 0;

                while (matcher.find() && count < 500 && positions.size() < MAX_JSON_INSERTION_POINTS) {
                    String value = matcher.group(1);
                    if (value != null && !value.isEmpty()) {
                        int valueStart = requestBodyStart + matcher.start(1);
                        int valueEnd = requestBodyStart + matcher.end(1);
                        if (valueEnd > valueStart) {
                            positions.add(Range.range(valueStart, valueEnd));
                        }
                    }
                    count++;
                }
            }
        } catch (Exception ignored) {
            // Ignore regex failures.
        }
    }
}

