package com.netsquare;


import burp.api.montoya.BurpExtension;
import burp.api.montoya.MontoyaApi;
import burp.api.montoya.core.Range;
import burp.api.montoya.core.ToolType;
import burp.api.montoya.http.message.HttpHeader;
import burp.api.montoya.http.message.params.ParsedHttpParameter;
import burp.api.montoya.http.message.requests.HttpRequest;
import burp.api.montoya.http.message.HttpRequestResponse;
import burp.api.montoya.intruder.HttpRequestTemplate;
import burp.api.montoya.ui.contextmenu.ContextMenuEvent;
import burp.api.montoya.ui.contextmenu.ContextMenuItemsProvider;
import burp.api.montoya.ui.contextmenu.MessageEditorHttpRequestResponse;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import javax.swing.*;
import java.awt.*;
import java.util.*;
import java.util.List;
import java.util.Map.Entry;
import java.util.concurrent.Callable;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


public class AutoPayloadPositioner implements BurpExtension {
    private MontoyaApi api;
    private final ExecutorService executorService = Executors.newCachedThreadPool();
    private final AtomicBoolean isProcessing = new AtomicBoolean(false);


    @Override
    public void initialize(MontoyaApi api) {
        this.api = api;
        // Initialize Extension name
        api.extension().setName("Auto Payload Positioner by Net-Square");
        // Register Context Menu Items Provider to user interface
        api.userInterface().registerContextMenuItemsProvider(new PayloadPositionMenuProvider());
        api.logging().logToOutput("Auto Payload Positioner loaded Successfully! \n by https://net-square.com/");

        // Register unloading handler to clean up threads
        api.extension().registerUnloadingHandler(() -> {
            executorService.shutdown();
            try {
                if (!executorService.awaitTermination(2, TimeUnit.SECONDS)) {
                    executorService.shutdownNow();
                }
            } catch (InterruptedException e) {
                executorService.shutdownNow();
            }
        });
    }

    private class PayloadPositionMenuProvider implements ContextMenuItemsProvider {
        @Override
        public List<Component> provideMenuItems(ContextMenuEvent contextMenuEvent) {
            List<Component> menuItems = new ArrayList<>();

            JMenuItem menuItem1 = new JMenuItem("Set Auto Positions");

            // Update menu item based on processing state
            menuItem1.setEnabled(!isProcessing.get());

            menuItem1.addActionListener(e -> {
                if (isProcessing.compareAndSet(false, true)) {
                    // Visual feedback
                    menuItem1.setEnabled(false);

                    api.logging().logToOutput("Auto Payload Positioner: Processing request...");

                    submitProcessingTask(contextMenuEvent, menuItem1);
                }
            });

            menuItems.add(menuItem1);
            return menuItems;
        }
    }

    private void submitProcessingTask(ContextMenuEvent contextMenuEvent, JMenuItem menuItem) {
        // Create a callable task that will process the request
        Callable<Void> processingTask = () -> {
            try {
                processRequest(contextMenuEvent);
            } catch (Exception e) {
                SwingUtilities.invokeLater(() -> {
                    api.logging().logToError("Error processing request: " + e.getMessage());
                    api.logging().logToOutput("Auto Payload Positioner: Error occurred during processing");
                });
            }
            return null;
        };

        // Submit the task to the executor with timeout
        Future<Void> future = executorService.submit(processingTask);

        // Start another thread to monitor the future with timeout
        executorService.submit(() -> {
            try {
                // Wait for the processing to complete with timeout
                future.get(15, TimeUnit.SECONDS);
                api.logging().logToOutput("Auto Payload Positioner: Processing completed successfully");

            } catch (TimeoutException e) {
                // Cancel the processing task if it times out
                future.cancel(true);
                api.logging().logToOutput("Auto Payload Positioner: Processing timed out - request might be too complex");

                api.logging().logToError("Processing timed out");

            } catch (Exception e) {
                api.logging().logToError("Error waiting for processing task: " + e.getMessage());
            } finally {
                // Always reset processing state and re-enable menu item
                SwingUtilities.invokeLater(() -> {
                    isProcessing.set(false);
                    menuItem.setEnabled(true);
                });
            }
        });
    }


    public void processRequest(ContextMenuEvent contextMenuEvent) {
        try {
            // Getting the request response object using getRequestResponse() method
            HttpRequestResponse requestResponse = getRequestResponse(contextMenuEvent);
            // Fetching request from request response object
            HttpRequest httpRequest = requestResponse.request();
            // Getting position for payloads in http request using findPositions method
            List<Range> positions = findPositions(httpRequest);

            if (positions.isEmpty()) {
                return; // if positions are 0 which is very unlikely to happen but somehow if they are 0 then return, this may occur is all payload positions are 0 byte which are not allowed by montoya api
            }

            // constructing a request template to send it to intruder tab, httpRequest is the request to sent along with payload positions locations
            HttpRequestTemplate httpRequestTemplate = new HttpRequestTemplate() {
                @Override
                public burp.api.montoya.core.ByteArray content() {
                    return httpRequest.toByteArray();
                }

                @Override
                public List<Range> insertionPointOffsets() {
                    return positions;
                }
            };

            // After constructing the http request template, send it to intruder tab
            api.intruder().sendToIntruder(requestResponse.httpService(), httpRequestTemplate);

        } catch (Exception e) {
            api.logging().logToError("Error occured in processRequest() : \n" + Arrays.toString(e.getStackTrace()));
        }
    }

    private HttpRequestResponse getRequestResponse(ContextMenuEvent contextMenuEvent) {
        List<HttpRequestResponse> selectedItems = contextMenuEvent.selectedRequestResponses(); // apart from repeater this will be used to get request from other tabs likes proxy history tabs

        if (!selectedItems.isEmpty()) { // if user has selected multiple requests from logs, select only first one
            return selectedItems.get(0);
        }

        if (contextMenuEvent.isFromTool(ToolType.REPEATER) || contextMenuEvent.isFromTool(ToolType.LOGGER) || contextMenuEvent.isFromTool(ToolType.PROXY) || contextMenuEvent.isFromTool(ToolType.TARGET) || contextMenuEvent.isFromTool(ToolType.SCANNER)) { // handle the repeater request normally
            MessageEditorHttpRequestResponse repeaterRequestResponse = contextMenuEvent.messageEditorRequestResponse().orElse(null);
            if (repeaterRequestResponse != null && repeaterRequestResponse.requestResponse() != null) {
                return repeaterRequestResponse.requestResponse();
            }
        }
        return null;
    }

    private List<Range> findPositions(HttpRequest request) {
        List<Range> positions = new ArrayList<>(); // this will store the positions
        String requestString = request.toString(); // converting http request into string for parsing

        // --- get positions in HTTP method --- //
        String method = request.method();
        int methodStart = requestString.indexOf(method);
        if (methodStart >= 0) {
            positions.add(Range.range(
                    methodStart,
                    methodStart + method.length()
            ));
        }

        // --- get payload positions for URL path -- //
        String urlPath = request.pathWithoutQuery();
        int urlPathStart = requestString.indexOf(urlPath);
        if (urlPathStart >= 0) {
            // as montoya api does not allow 0 bytes payload positions we have to set payload position to last directory in url path
            if (urlPath.endsWith("/")) { // if url path ends with '/', find the second-to-last slash
                String pathWithoutTrailingSlash = urlPath.substring(0, urlPath.length() - 1); // remove last trailing '/' from url path
                int secondLastSlashIdx = pathWithoutTrailingSlash.lastIndexOf('/') + 1;
                int segStart = urlPathStart + secondLastSlashIdx;
                int segEnd = urlPathStart + urlPath.length() - 1; // Exclude the trailing slash

                if (segStart < segEnd) { // making sure start index of last directory is less than it's ending
                    positions.add(Range.range(segStart, segEnd));
                }
            } else {
                // for paths not ending with '/'
                int lastSlashIdx = urlPath.lastIndexOf('/') + 1; // get the starting index of last directory/file in url path
                int segStart = urlPathStart + lastSlashIdx;
                int segEnd = urlPathStart + urlPath.length();

                if (segStart < segEnd) {
                    positions.add(Range.range(segStart, segEnd)); // add path payload position in list of payload position
                }
            }
        }

        // --- get payload positions for all parameters (URL parameters, POST parameters) -- //
        for (ParsedHttpParameter param : request.parameters()) {  // Using burp's in-built parameter parser to get the parameters
            positions.add(Range.range(
                    param.valueOffsets().startIndexInclusive(), // starting of parameter
                    param.valueOffsets().endIndexExclusive()     // ending of parameter
            ));
        }

        // --- Process request body to set payload positions in different formats like JSON and XML -- //
        int requestBodyStart = requestString.indexOf("\r\n\r\n") + 4; // get the starting of the request body
        if (requestBodyStart > 4 && requestBodyStart < requestString.length()) { // check if there content or not
            String requestBody = requestString.substring(requestBodyStart); // fetch the request body from whole request
            String contentType = getContentType(request);

            if (contentType.contains("json") || requestBody.trim().startsWith("{") || requestBody.trim().startsWith("[")) {
                // --- set payload position for json data --- //
                processJsonBody(requestBody, requestBodyStart, positions);
            } else if (contentType.contains("xml") || requestBody.trim().startsWith("<?xml") || requestBody.trim().startsWith("<")) {
                // --- set payload position for xml data --- //
                processXmlBody(requestBody, requestBodyStart, positions);
            } else {
                // --- try to detect embedded xml or json data in the request body --- //
                processEmbeddedFormats(requestBody, requestBodyStart, positions);
            }

        }

        // --- set payload positions for headers --- //
        List<HttpHeader> headers = request.headers();
        for (HttpHeader header : headers) {
            String headerLine = header.name() + ": " + header.value();
            int headerStart = requestString.indexOf(headerLine);

            if (headerStart >= 0) {
                // get the value from header
                String headerValue = header.value();
                int valueStart = headerStart + header.name().length() + 2; // +2 for ": "

                // Case 1: Cookies
                if (header.name().equalsIgnoreCase("Cookie") || header.name().contains("cookie")) {
                    // split the cookies' key:value pairs
                    String[] pairs = headerValue.split("; ");

                    for (String pair : pairs) {
                        // find the key=value split
                        int equalPos = pair.indexOf('=');
                        if (equalPos > 0 && equalPos < pair.length() - 1) {
                            String value = pair.substring(equalPos + 1).trim();

                            // calculate exact position in the request string
                            int pairStart = headerValue.indexOf(pair);
                            if (pairStart >= 0) {
                                int actualValueStart = valueStart + pairStart + equalPos + 1;
                                int actualValueEnd = actualValueStart + value.length();

                                // double-checking the position is withing bounds
                                if (actualValueStart >= valueStart && actualValueEnd <= headerStart + headerLine.length()) {
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
                        // add position for the auth token partonly
                        int tokenStart = valueStart + parts[0].length() + 1; // +1 for spac
                        int tokenEnd = headerStart + headerLine.length();
                        // --- add the auth header token to payload position --- //
                        positions.add(Range.range(tokenStart, tokenEnd));
                    } else { // if for any reason, canno't parse above, fallback to setting payload position to whole header
                        positions.add(Range.range(valueStart, headerStart + headerLine.length()));
                    }
                }

                // Case 3: normal add the entire header value as a position
                else {
                    positions.add(Range.range(valueStart, headerStart + headerLine.length()));
                }
            }
        }

        // --- validate positions to ensure no 0 bytes payloads position, no duplicate or overlaps and sort the positions
        return validatePositions(positions, requestString.length());
    }

    // -- helper method which will fetch and return content type from headers -- //
    private String getContentType(HttpRequest httpRequest) {
        for (HttpHeader header : httpRequest.headers()) {
            if (header.name().equalsIgnoreCase("Content-Type")) {
                return header.value().toLowerCase();
            }
        }
        // -- if content-type header is not present then return empty string -- //
        return "";
    }

    // -- Method to process JSON -- //
    private void processJsonBody(String requestBody, int requestBodyStart, List<Range> positions) {
        try {
            ObjectMapper mapper = new ObjectMapper();
            JsonNode rootNode = mapper.readTree(requestBody);

            // Process the json with jackson library
            processJsonNode(rootNode, requestBody, requestBodyStart, positions);
        } catch (JsonProcessingException e) {
            // for any reasone if there is any error use the backup method to parse the json
            fallbackJsonProcessing(requestBody, requestBodyStart, positions);
        }
    }

    // -- Method to recursively process JSON nodes -- //
    private void processJsonNode(JsonNode node, String originalJson, int baseOffset, List<Range> positions) {
        if (node.isObject()) {
            // process object fields
            Iterator<Entry<String, JsonNode>> fields = node.fields();
            while (fields.hasNext()) {
                Entry<String, JsonNode> entry = fields.next();
                String fieldName = entry.getKey();
                JsonNode valueNode = entry.getValue();

                // find the position of this field value in the original JSON
                String fieldPattern = "\"" + Pattern.quote(fieldName) + "\"\\s*:\\s*";

                if (valueNode.isValueNode()) {
                    // handle the primitive value - stirngs, numbers, booleans, null
                    String regex;
                    boolean isString = valueNode.isTextual();

                    if (isString) {
                        regex = fieldPattern + "\"([^\"\\\\]*(\\\\.[^\"\\\\]*)*)\"";
                    } else if (valueNode.isNumber()) {
                        regex = fieldPattern + "([-+]?[0-9]*\\.?[0-9]+([eE][-+]?[0-9]+)?)";
                    } else if (valueNode.isBoolean()) {
                        regex = fieldPattern + "(true|false)";
                    } else if (valueNode.isNull()) {
                        regex = fieldPattern + "(null)";
                    } else {
                        continue; // Skip if we don't know the type
                    }

                    Pattern pattern = Pattern.compile(regex);
                    Matcher matcher = pattern.matcher(originalJson);

                    if (matcher.find()) {
                        int valueStart = matcher.start(1);
                        int valueEnd = matcher.end(1);

                        // for strings, add payload positions between " " double quoates
                        // if (isString) {
                        //  positions.add(Range.range(baseOffset + valueStart, baseOffset + valueEnd));
                        //} else {
                        positions.add(Range.range(baseOffset + valueStart, baseOffset + valueEnd));
                        //}
                    }
                } else if (valueNode.isContainerNode()) {
                    // Recursively process nested objects and arrays
                    processJsonNode(valueNode, originalJson, baseOffset, positions);
                }
            }
        } else if (node.isArray()) {
            // process array elements
            for (int i = 0; i < node.size(); i++) {
                JsonNode element = node.get(i);

                if (element.isValueNode()) {
                    // fidn positions of this array element
                    String value = element.asText();
                    if (element.isTextual()) {
                        // try to find this string in the array context
                        int startIdx = findArrayElementPosition(originalJson, value, i, true);
                        if (startIdx >= 0) {
                            // -- add in positions -- //
                            positions.add(Range.range(baseOffset + startIdx, baseOffset + startIdx + value.length()));
                        }
                    } else {
                        // non string primitve values
                        int startIdx = findArrayElementPosition(originalJson, element.toString(), i, false);
                        if (startIdx >= 0) {
                            positions.add(Range.range(baseOffset + startIdx, baseOffset + startIdx + element.toString().length()));
                        }
                    }
                } else if (element.isContainerNode()) {
                    // -- recursively process nested objects and arrays -- //
                    processJsonNode(element, originalJson, baseOffset, positions);
                }
            }
        }
    }

    // -- helper method to find array element positions in the original JSON string -- //
    private int findArrayElementPosition(String json, String value, int index, boolean isString) {
        Pattern arrayElementPattern;
        if (isString) {
            arrayElementPattern = Pattern.compile("\\[\\s*(?:[^\\[\\]]*,\\s*){" + index + "}\"([^\"\\\\]*(\\\\.[^\"\\\\]*)*)\""
                    + (index < Integer.MAX_VALUE - 1 ? "(?:,|\\])" : ""));
        } else {
            arrayElementPattern = Pattern.compile("\\[\\s*(?:[^\\[\\]]*,\\s*){" + index + "}(" + Pattern.quote(value) + ")"
                    + (index < Integer.MAX_VALUE - 1 ? "(?:,|\\])" : ""));
        }

        Matcher matcher = arrayElementPattern.matcher(json);
        if (matcher.find()) {
            return matcher.start(1);
        }
        // -- if no found then return -1 to prevent addition in positions -- //
        return -1;
    }

    // -- backup method, in case jackson library fails to parse json for any reason, use regex for json parsing -- //
    private void fallbackJsonProcessing(String requestBody, int bodyStart, List<Range> positions) {
        try {
            fallbackProcessJsonRecursively(requestBody, 0, bodyStart, positions, 0);
        } catch (Exception e) {
            api.logging().logToError("Error fallback json processing: \n " + Arrays.toString(e.getStackTrace()));
        }
    }

    // -- backup method to process json recursively -- //
    private void fallbackProcessJsonRecursively(String json, int startInJson, int baseOffset, List<Range> positions, int depth) {
        if (depth > 1) return; // prevent too deep recursion to prevent crash

        // Pattern for basic JSON Key-value pairs
        Pattern keyPattern = Pattern.compile("\"([^\"]+)\"\\s*:");
        Matcher matcher = keyPattern.matcher(json);
        matcher.region(startInJson, json.length());

        while (matcher.find()) {
            String key = matcher.group(1);
            String valueStart = matcher.group(2);
            int valueStartPos = matcher.start(2);
            int valueEndPos;

            // handle different types of values
            if (valueStart.equals("{")) {
                // process the json object
                valueEndPos = findMatchingCloseBrace(json, valueStartPos);
                if (valueEndPos > valueStartPos) {
                    // process the nested object
                    fallbackProcessJsonRecursively(json, valueStartPos + 1, baseOffset, positions, depth + 1);
                }
            } else if (valueStart.equals("[")) {
                // process json array
                valueEndPos = findMatchingCloseBracket(json, valueStartPos);
                if (valueEndPos > valueStartPos) {
                    // process array contents
                    processJsonArray(json, valueStartPos + 1, valueEndPos, baseOffset, positions, depth + 1);
                }
            } else if (valueStart.startsWith("\"") && valueStart.endsWith("\"")) {
                // process string value
                valueEndPos = matcher.end(2);
                // add position for string content without quotes
                positions.add(Range.range(baseOffset + valueStartPos + 1, baseOffset + valueEndPos - 1));
            } else {
                // process number, boolean and null
                valueEndPos = matcher.end(2);
                positions.add(Range.range(baseOffset + valueStartPos, baseOffset + valueEndPos));
            }
        }
    }

    // -- process json array -- //
    private void processJsonArray(String json, int startPos, int endPos, int baseOffset, List<Range> positions, int depth) {
        if (depth > 1) return; // avoid deep recursion to prevent crash

        // array element detection logic
        int pos = startPos;
        while (pos < endPos) {
            // skip whitespace
            while (pos < endPos && Character.isWhitespace(json.charAt(pos))) pos++;
            if (pos >= endPos) break;

            char c = json.charAt(pos);
            switch (c) {
                case ',':
                    pos++;

                case '"':
                    // string value
                    int closeQuote = findClosingQuote(json, pos + 1);
                    if (closeQuote > pos) {
                        positions.add(Range.range(baseOffset + pos + 1, baseOffset + closeQuote));
                        pos = closeQuote + 1;
                    } else {
                        pos++;
                    }
                case '{':
                    // object in array
                    int closePos = findMatchingCloseBrace(json, pos);
                    if (closePos > pos) {
                        fallbackProcessJsonRecursively(json, pos + 1, baseOffset, positions, depth + 1);
                        pos = closePos + 1;
                    } else {
                        pos++;
                    }
                case '[':
                    // nested array
                    int closePosForBracket = findMatchingCloseBracket(json, pos);
                    if (closePosForBracket > pos) {
                        processJsonArray(json, pos + 1, closePosForBracket, baseOffset, positions, depth + 1);
                        pos = closePosForBracket + 1;
                    } else {
                        pos++;
                    }
                default:
                    // number boolean and null
                    int valueEnd = pos;
                    while (valueEnd < endPos && !",]}".contains(String.valueOf(json.charAt(valueEnd))) &&
                            !Character.isWhitespace(json.charAt(valueEnd))) {
                        valueEnd++;
                    }
                    if (valueEnd > pos) {
                        positions.add(Range.range(baseOffset + pos, baseOffset + valueEnd));
                        pos = valueEnd;
                    } else {
                        pos++;
                    }
            }
        }
    }

    // -- helper method to find matching close brace  -- //
    private int findMatchingCloseBrace(String json, int openPos) {
        int depth = 1;
        int pos = openPos + 1;
        while (pos < json.length() && depth > 0) {
            char c = json.charAt(pos);
            switch (c) {
                case '{':
                    depth++;
                case '}':
                    depth--;
                case '"':
                    // skip the string content
                    int closeQuote = findClosingQuote(json, pos + 1);
                    if (closeQuote > pos) {
                        pos = closeQuote;
                    }
            }
            pos++;
        }
        return depth == 0 ? pos - 1 : -1;
    }

    // -- helper method to find matching close bracket -- //
    private int findMatchingCloseBracket(String json, int openPos) {
        int depth = 1;
        int pos = openPos + 1;
        while (pos < json.length() && depth > 0) {
            char c = json.charAt(pos);
            switch (c) {
                case '[':
                    depth++;
                case ']':
                    depth--;
                case '"':
                    // skipt string
                    int closeQuote = findClosingQuote(json, pos + 1);
                    if (closeQuote > pos) {
                        pos = closeQuote;
                    }
            }
            pos++;
        }
        return depth == 0 ? pos - 1 : -1;
    }

    // -- helper method to find closing quoate '"' character -- //
    private int findClosingQuote(String json, int start) {
        int pos = start;
        while (pos < json.length()) {
            char c = json.charAt(pos);
            switch (c) {
                case '\\':
                    pos += 2; // skip escape character
                case '"':
                    return pos;
                default:
                    pos++;
            }
        }
        return -1;
    }

    // -- Method to process XML -- //
    private void processXmlBody(String requestBody, int requestBodyStart, List<Range> positions) {
        try {
            // xml parsing for tab based content
            Pattern tagPattern = Pattern.compile("<([^\\s/>]+)[^>]*>(.*?)</\\1>|<([^\\s/>]+)\\s+([^>]*)/>|<([^\\s/>]+)[^>]*>([^<]*)</\\5>");
            Matcher matcher = tagPattern.matcher(requestBody);

            while (matcher.find()) {
                // handle self-closing tags with attributes
                if (matcher.group(3) != null && matcher.group(4) != null) {
                    String attributes = matcher.group(4);
                    processXmlAttributes(attributes, matcher.start(4) + requestBodyStart, positions);
                }
                // handle start-end tags with attributes and content
                else if (matcher.group(1) != null) {
                    String content = matcher.group(2);

                    // Add position for tag content if it's not empty and doesn't contain nested tags
                    if (!content.trim().isEmpty() && !content.contains("<")) {
                        positions.add(Range.range(
                                requestBodyStart + matcher.start(2),
                                requestBodyStart + matcher.end(2)
                        ));
                    }

                    // process attributes in opening tag
                    int tagEnd = requestBody.indexOf('>', matcher.start());
                    if (tagEnd > matcher.start()) {
                        String openingTag = requestBody.substring(matcher.start(), tagEnd);
                        int attrStart = openingTag.indexOf(' ');
                        if (attrStart > 0) {
                            String attributes = openingTag.substring(attrStart + 1);
                            processXmlAttributes(attributes, matcher.start() + attrStart + 1 + requestBodyStart, positions);
                        }
                    }

                    // recursively process nested tags
                    if (content.contains("<")) {
                        processXmlBody(content, requestBodyStart + matcher.start(2), positions);
                    }
                }
                // handle simple tags with content
                else if (matcher.group(5) != null && matcher.group(6) != null) {
                    String content = matcher.group(6);
                    if (!content.trim().isEmpty()) {
                        positions.add(Range.range(
                                requestBodyStart + matcher.start(6),
                                requestBodyStart + matcher.end(6)
                        ));
                    }
                }
            }
        } catch (Exception e) {
            api.logging().logToError("Error processing XML body: \n" + Arrays.toString(e.getStackTrace()));
        }
    }

    // -- method to process xml attributes -- //
    private void processXmlAttributes(String attributes, int baseOffset, List<Range> positions) {
        // pattern for attributes like name="value" or name='value'
        Pattern attrPattern = Pattern.compile("(\\w+)\\s*=\\s*([\"'])(.*?)\\2");
        Matcher matcher = attrPattern.matcher(attributes);

        while (matcher.find()) {
            int valueStart = matcher.start(3);
            int valueEnd = matcher.end(3);
            positions.add(Range.range(
                    baseOffset + valueStart,
                    baseOffset + valueEnd
            ));
        }
    }

    // -- process embedded data -- //
    private void processEmbeddedFormats(String requestBody, int requestBodyStart, List<Range> positions) {
        // look for json object or arrays embedded in other content
        Pattern jsonPattern = Pattern.compile("\\{[^\\{\\}]*\\}|\\[[^\\[\\]]*\\]");
        Matcher jsonMatcher = jsonPattern.matcher(requestBody);

        while (jsonMatcher.find()) {
            String jsonCandidate = jsonMatcher.group();
            if ((jsonCandidate.startsWith("{") && jsonCandidate.endsWith("}")) ||
                    (jsonCandidate.startsWith("[") && jsonCandidate.endsWith("]"))) {
                // process this json
                processJsonBody(jsonCandidate, requestBodyStart + jsonMatcher.start(), positions);
            }
        }

        // xml liek structure
        if (requestBody.contains("<") && requestBody.contains(">")) {
            Pattern xmlPattern = Pattern.compile("<[^>]+>[^<]*</[^>]+>|<[^>]+/>");
            Matcher xmlMatcher = xmlPattern.matcher(requestBody);

            while (xmlMatcher.find()) {
                String xmlCandidate = xmlMatcher.group();
                // process this xml
                processXmlBody(xmlCandidate, requestBodyStart + xmlMatcher.start(), positions);
            }
        }

        // look for key=value pairs in non-json non-xml content
        Pattern keyValuePattern = Pattern.compile("([\\w\\-]+)=([^&\\s]+)");
        Matcher keyValueMatcher = keyValuePattern.matcher(requestBody);

        while (keyValueMatcher.find()) {
            positions.add(Range.range(
                    requestBodyStart + keyValueMatcher.start(2),
                    requestBodyStart + keyValueMatcher.end(2)
            ));
        }
    }

    // -- validate the positions -- //
    private List<Range> validatePositions(List<Range> ranges, int maxLength) {
        // Sort by start index
        ranges.sort(Comparator.comparingInt(Range::startIndexInclusive));

        List<Range> validRanges = new ArrayList<>();
        Range current = ranges.get(0);

        // Basic validation of the first range
        if (current.startIndexInclusive() >= 0 &&
                current.endIndexExclusive() <= maxLength &&
                current.startIndexInclusive() < current.endIndexExclusive()) {
            validRanges.add(current);
        }

        // Check remaining ranges with O(n) complexity instead of O(n*m)
        for (int i = 1; i < ranges.size(); i++) {
            Range next = ranges.get(i);

            // Skip invalid ranges
            if (next.startIndexInclusive() < 0 ||
                    next.endIndexExclusive() > maxLength ||
                    next.startIndexInclusive() >= next.endIndexExclusive()) {
                continue;
            }

            // Check for overlap with previous valid range
            if (next.startIndexInclusive() >= validRanges.get(validRanges.size() - 1).endIndexExclusive()) {
                validRanges.add(next);
            }
        }

        return validRanges;
    }
}