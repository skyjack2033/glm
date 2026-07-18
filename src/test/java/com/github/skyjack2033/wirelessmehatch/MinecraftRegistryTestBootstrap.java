package com.github.skyjack2033.wirelessmehatch;

import java.lang.reflect.Method;

import net.minecraft.item.Item;

public final class MinecraftRegistryTestBootstrap {

    private static boolean initialized;

    private MinecraftRegistryTestBootstrap() {}

    public static synchronized void initializeVanillaItems() throws ReflectiveOperationException {
        if (initialized) return;
        registerItemIfAbsent(288, "minecraft:feather");
        if (net.minecraft.init.Items.feather == null) {
            throw new IllegalStateException("Minecraft Items initialized before the test registry bootstrap");
        }
        initialized = true;
    }

    public static synchronized Item registerItemIfAbsent(int id, String name) throws ReflectiveOperationException {
        Item registered = (Item) Item.itemRegistry.getObject(name);
        if (registered != null) return registered;
        if (Item.itemRegistry.getObjectById(id) != null) {
            throw new IllegalStateException("Test item ID " + id + " is already occupied");
        }

        Method addObjectRaw = Item.itemRegistry.getClass()
            .getDeclaredMethod("addObjectRaw", int.class, String.class, Object.class);
        addObjectRaw.setAccessible(true);
        Item item = new Item();
        addObjectRaw.invoke(Item.itemRegistry, id, name, item);
        return item;
    }
}
