package com.netsquare;


import burp.api.montoya.BurpExtension;
import burp.api.montoya.MontoyaApi;
import burp.api.montoya.core.Range;
import burp.api.montoya.http.message.requests.HttpRequest;
import burp.api.montoya.http.message.HttpRequestResponse;
import burp.api.montoya.ui.contextmenu.ContextMenuEvent;
import burp.api.montoya.ui.contextmenu.ContextMenuItemsProvider;
import burp.api.montoya.ui.contextmenu.MessageEditorHttpRequestResponse;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
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
        } catch (Exception e) {
            api.logging().logToError(e.getStackTrace().toString());
        }
    }

    private HttpRequestResponse getRequestResponse(ContextMenuEvent contextMenuEvent) {
        MessageEditorHttpRequestResponse repeaterRequestResponse = contextMenuEvent.messageEditorRequestResponse().orElse(null);
        if (repeaterRequestResponse != null && repeaterRequestResponse.requestResponse() != null) {
            return repeaterRequestResponse.requestResponse();
        }

        return null;
    }

}