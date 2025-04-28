package com.netsquare;


import burp.api.montoya.BurpExtension;
import burp.api.montoya.MontoyaApi;
import burp.api.montoya.core.Range;
import burp.api.montoya.core.ToolType;
import burp.api.montoya.http.message.requests.HttpRequest;
import burp.api.montoya.http.message.HttpRequestResponse;
import burp.api.montoya.intruder.HttpRequestTemplate;
import burp.api.montoya.ui.contextmenu.ContextMenuEvent;
import burp.api.montoya.ui.contextmenu.ContextMenuItemsProvider;
import burp.api.montoya.ui.contextmenu.MessageEditorHttpRequestResponse;

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
        api.extension().setName("Auto Payload Positioner");
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

        // find parameter positions

        // fins json parameters

        // add header positions

        return positions;
    }



}