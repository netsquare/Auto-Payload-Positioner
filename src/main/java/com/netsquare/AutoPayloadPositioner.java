package com.netsquare;


import burp.api.montoya.BurpExtension;
import burp.api.montoya.MontoyaApi;
import burp.api.montoya.ui.contextmenu.ContextMenuEvent;
import burp.api.montoya.ui.contextmenu.ContextMenuItemsProvider;

import java.awt.*;
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

}