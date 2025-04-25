package com.netsquare;

import burp.api.montoya.ui.contextmenu.ContextMenuEvent;
import burp.api.montoya.ui.contextmenu.ContextMenuItemsProvider;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

public class PayloadPositionMenuProvider implements ContextMenuItemsProvider {
    @Override
    public List<Component> provideMenuItems(ContextMenuEvent contextMenuEvent) {
        List<Component> menuItems = new ArrayList<>();

        JMenuItem menuItem1 = new JMenuItem("Body Only");

        menuItem1.addActionListener(e -> processRequest(contextMenuEvent, false));

        menuItems.add(menuItem1);

        return menuItems;
    }
}
