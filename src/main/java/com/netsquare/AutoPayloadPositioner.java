package com.netsquare;


import burp.api.montoya.BurpExtension;
import burp.api.montoya.MontoyaApi;

public class AutoPayloadPositioner implements BurpExtension {
    private MontoyaApi api;

    @Override
    public void initialize(MontoyaApi api) {
        this.api = api;
        // Initialize Extension Properties like name
        api.extension().setName("Auto Payload Positioner");
        api.logging().logToOutput("Simple Payload Positioner loaded Successfully!");
    }
}