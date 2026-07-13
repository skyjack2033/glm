package com.github.skyjack2033.wirelessmehatch.loader;

import com.github.skyjack2033.wirelessmehatch.WirelessMEHatch;
import com.github.skyjack2033.wirelessmehatch.item.ItemWirelessLinkTool;

import cpw.mods.fml.common.registry.GameRegistry;

public final class ItemLoader {

    public static final ItemWirelessLinkTool WIRELESS_LINK_TOOL = new ItemWirelessLinkTool();

    private ItemLoader() {}

    public static void register() {
        GameRegistry.registerItem(WIRELESS_LINK_TOOL, "wireless_link_tool", WirelessMEHatch.MODID);
    }
}
