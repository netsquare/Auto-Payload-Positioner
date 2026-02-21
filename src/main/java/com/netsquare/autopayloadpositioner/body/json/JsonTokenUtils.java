package com.netsquare.autopayloadpositioner.body.json;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

public final class JsonTokenUtils {
    private JsonTokenUtils() {
    }

    /**
     * Resolve string token content bounds [start, endExclusive) excluding surrounding quotes.
     * Works for both FIELD_NAME and VALUE_STRING even when token location points to either
     * opening quote or first character inside quotes.
     */
    public static int[] locateStringTokenContentBounds(String json, com.fasterxml.jackson.core.JsonParser parser) {
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

        // Primary strategy: walk backward from token end hint to find closing/opening quotes
        // and validate that unescaped content equals parser.getText().
        int closeSearchFrom = Math.min(jsonLen - 1, Math.max(0, endHint));
        int closeSearchTo = Math.max(0, startHint - 4096);
        for (int close = closeSearchFrom; close >= closeSearchTo; close--) {
            if (json.charAt(close) != '"' || isEscapedQuote(json, close)) continue;
            for (int open = close - 1; open >= Math.max(0, close - 8192); open--) {
                if (json.charAt(open) != '"' || isEscapedQuote(json, open)) continue;
                String rawContent = json.substring(open + 1, close);
                if (tokenText == null || tokenText.equals(unescapeJsonStringContent(rawContent))) {
                    return new int[]{open + 1, close};
                }
            }
        }

        // Fallback: use token text to locate bounds.
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

    private static String unescapeJsonStringContent(String rawContent) {
        if (rawContent.indexOf('\\') < 0) return rawContent;

        StringBuilder out = new StringBuilder(rawContent.length());
        for (int i = 0; i < rawContent.length(); i++) {
            char c = rawContent.charAt(i);
            if (c != '\\' || i + 1 >= rawContent.length()) {
                out.append(c);
                continue;
            }

            char esc = rawContent.charAt(i + 1);
            if (esc == 'u' && i + 5 < rawContent.length()) {
                String hex = rawContent.substring(i + 2, i + 6);
                try {
                    out.append((char) Integer.parseInt(hex, 16));
                } catch (NumberFormatException e) {
                    out.append('u');
                }
                i += 5;
                continue;
            }

            switch (esc) {
                case '"':
                case '\\':
                case '/':
                    out.append(esc);
                    break;
                case 'b':
                    out.append('\b');
                    break;
                case 'f':
                    out.append('\f');
                    break;
                case 'n':
                    out.append('\n');
                    break;
                case 'r':
                    out.append('\r');
                    break;
                case 't':
                    out.append('\t');
                    break;
                default:
                    out.append(esc);
                    break;
            }
            i++;
        }
        return out.toString();
    }
}

