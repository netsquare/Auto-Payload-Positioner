/*

"""

Name : AutoPayloadPositioner

Date : 04/29/2025

Author: Jafar Pathan

Copyright: Net-Square Solutions PVT LTD.

"""

*/

package com.netsquare;

import burp.api.montoya.BurpExtension;
import burp.api.montoya.MontoyaApi;
import burp.api.montoya.core.ToolType;
import burp.api.montoya.http.message.requests.HttpRequest;
import burp.api.montoya.http.message.HttpRequestResponse;
import burp.api.montoya.intruder.HttpRequestTemplate;
import burp.api.montoya.ui.contextmenu.ContextMenuEvent;
import burp.api.montoya.ui.contextmenu.ContextMenuItemsProvider;
import burp.api.montoya.ui.contextmenu.MessageEditorHttpRequestResponse;
import com.netsquare.autopayloadpositioner.burp.RequestProcessor;
import com.netsquare.autopayloadpositioner.core.PayloadPositionMode;

import javax.swing.*;
import java.awt.*;
import java.util.*;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

public class AutoPayloadPositioner implements BurpExtension {

    private MontoyaApi api;
    private final ExecutorService executorService = Executors.newCachedThreadPool();
    private final AtomicBoolean isProcessing = new AtomicBoolean(false);
    private final RequestProcessor requestProcessor = new RequestProcessor();

    private PayloadPositionMode selectedMode = PayloadPositionMode.DEFAULT;

    @Override
    public void initialize(MontoyaApi api) {
        this.api = api;
        // Initialize Extension name
        api.extension().setName("Auto Payload Positioner by Net-Square");
        // Register Context Menu Items Provider to user interface
        api.userInterface().registerContextMenuItemsProvider(new PayloadPositionMenuProvider());
        api.logging().logToOutput("Auto Payload Positioner loaded Successfully! \n by Net-Square Solutions PVT LTD. (https://net-square.com/)");
        
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
            for (PayloadPositionMode mode : PayloadPositionMode.values()) {
                JMenuItem modeItem = new JMenuItem(mode.toString());
                modeItem.setEnabled(!isProcessing.get());
                modeItem.addActionListener(e -> {
                    if (isProcessing.compareAndSet(false, true)) {
                        selectedMode = mode;
                        modeItem.setEnabled(false);
                        api.logging().logToOutput("Auto Payload Positioner: Processing request with mode: " + mode.toString());
                        submitProcessingTask(contextMenuEvent, modeItem);
                    }
                });
                menuItems.add(modeItem);
            }
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
            HttpRequestTemplate httpRequestTemplate = requestProcessor.buildIntruderTemplate(httpRequest, selectedMode);
            if (httpRequestTemplate.insertionPointOffsets().isEmpty()) {
                return;
            }

            // After constructing the http request template, send it to intruder tab
            api.intruder().sendToIntruder(requestResponse.httpService(), httpRequestTemplate);
        } catch (Exception e) {
            api.logging().logToError("Error occured in processRequest() : \n" + Arrays.toString(e.getStackTrace()));
        }
    }

    private HttpRequestResponse getRequestResponse(ContextMenuEvent contextMenuEvent) {
        List<HttpRequestResponse> selectedItems = contextMenuEvent.selectedRequestResponses(); // apart from repeater
                                                                                               // this will be used to get request from othetabs likes proxy history tabs

        if (!selectedItems.isEmpty()) { // if user has selected multiple requests from logs, select only first one
            return selectedItems.get(0);
        }

        if (contextMenuEvent.isFromTool(ToolType.REPEATER) || contextMenuEvent.isFromTool(ToolType.LOGGER)
                || contextMenuEvent.isFromTool(ToolType.PROXY) || contextMenuEvent.isFromTool(ToolType.TARGET)
                || contextMenuEvent.isFromTool(ToolType.SCANNER)) { // handle the repeater request normally
            MessageEditorHttpRequestResponse repeaterRequestResponse = contextMenuEvent.messageEditorRequestResponse()
                    .orElse(null);

            if (repeaterRequestResponse != null && repeaterRequestResponse.requestResponse() != null) {
                return repeaterRequestResponse.requestResponse();
            }
        }

        return null;
    }

}
