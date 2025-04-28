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
import com.fasterxml.jackson.databind.JsonNode;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class AutoPayloadPositioner implements BurpExtension {
    private MontoyaApi api;

    @Override
    public void initialize(MontoyaApi api) {
        this.api = api;
        // Initialize Extension name
        api.extension().setName("Auto Payload Positioner by Net-Square");
        // Register Context Menu Items Provider to user interface
        api.userInterface().registerContextMenuItemsProvider(new PayloadPositionMenuProvider());
        api.logging().logToOutput("Simple Payload Positioner loaded Successfully!");
    }

    private class PayloadPositionMenuProvider implements ContextMenuItemsProvider {
        @Override
        public List<Component> provideMenuItems(ContextMenuEvent contextMenuEvent) {
            List<Component> menuItems = new ArrayList<>();

            JMenuItem menuItem1 = new JMenuItem("Body Only");

            menuItem1.addActionListener(e -> processRequest(contextMenuEvent));

            menuItems.add(menuItem1);

            return menuItems;
        }
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

        if (contextMenuEvent.isFromTool(ToolType.REPEATER)) { // handle the repeater request normally
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
        for (ParsedHttpParameter param: request.parameters()) {  // Using burp's in-built parameter parser to get the parameters
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
                            if(pairStart >= 0) {
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

    }

    // -- Method to recursively process JSON nodes -- //
    private void processJsonNode(JsonNode node, String originalJson, int baseOffset, List<Range> positions) {

    }


    // -- helper method to find array element positions in the original JSON string -- //
    private int findArrayElementPosition(String json, String value, int index, boolean isString) {
        return 0;
    }

    // -- backup method, in case jackson library fails to parse json for any reason, use regex for json parsing -- //
    private void fallbackJsonProcessing(String requestBody, int bodyStart, List<Range> positions) {}

    // -- backup method to process json recursively -- //
    private void fallbackProcessJsonRecursively(String json, int startInJson, int baseOffset, List<Range> positions, int depth) {}

    // -- process json array -- //
    private void processJsonArray(String json, int startPos, int endPos, int baseOffset, List<Range> positions, int depth) {}

    // -- helper method to find matching close brace  -- //
    private int findMatchingCloseBrace(String json, int openPos) {
        return 0;
    }

    // -- helper method to find matching close bracket -- //
    private int findMachingCloseBracket(String json, int openPos) {
        return 0;
    }

    // -- Method to process XML -- //
    private void processXmlBody(String requestBody, int requestBodyStart, List<Range> positions) {

    }

    // -- method to process xml attributes -- //
    private void processXmlAttributes(String attributes, int baseOffset, List<Range> positions) {

    }

    // -- process embedded data -- //
    private void processEmbeddedFormats(String requestBody, int requestBodyStart, List<Range> positions) {

    }

    // -- validate the positions -- //
    private List<Range> validatePositions(List<Range> ranegs, int maxLength) {}

}