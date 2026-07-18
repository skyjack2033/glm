package com.github.skyjack2033.wirelessmehatch.me;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.Collections;

import org.junit.BeforeClass;
import org.junit.Test;
import org.spongepowered.asm.lib.ClassReader;
import org.spongepowered.asm.lib.tree.AbstractInsnNode;
import org.spongepowered.asm.lib.tree.ClassNode;
import org.spongepowered.asm.lib.tree.MethodInsnNode;
import org.spongepowered.asm.lib.tree.MethodNode;

import com.github.skyjack2033.wirelessmehatch.MinecraftRegistryTestBootstrap;
import com.github.skyjack2033.wirelessmehatch.metatileentity.MTEWirelessUnifiedOutputAssemblyME;

import appeng.tile.networking.TileWirelessConnector;
import gregtech.api.interfaces.metatileentity.IMetaTileEntity;
import gregtech.api.metatileentity.BaseMetaTileEntity;

public class WirelessKitInteractionHandlerTest {

    @BeforeClass
    public static void bootstrapMinecraftRegistries() throws ReflectiveOperationException {
        MinecraftRegistryTestBootstrap.initializeVanillaItems();
    }

    @Test
    public void lineInterceptionFindsAnAssemblyBetweenNativeEndpoints() throws Exception {
        WirelessKitEndpoint connector = WirelessKitEndpoint.fromTile(new TileWirelessConnector());
        WirelessKitEndpoint assembly = WirelessKitEndpoint.fromTile(assemblyBase());

        assertTrue(WirelessKitInteractionHandler.containsAssembly(Arrays.asList(connector, assembly, connector)));
        assertFalse(WirelessKitInteractionHandler.containsAssembly(Arrays.asList(connector, connector)));
        assertFalse(WirelessKitInteractionHandler.containsAssembly(Collections.emptyList()));
    }

    @Test
    public void superModeUsesNativeBindSuperWithTheLiveTileEntity() throws Exception {
        ClassNode handler = new ClassNode();
        new ClassReader(WirelessKitInteractionHandler.class.getName()).accept(handler, 0);
        for (MethodNode method : handler.methods) {
            if (!"handleServerUse".equals(method.name)) continue;
            for (AbstractInsnNode instruction : method.instructions.toArray()) {
                if (instruction instanceof MethodInsnNode call && "appeng/helpers/WireLessToolHelper".equals(call.owner)
                    && "bindSuper".equals(call.name)
                    && "(Lnet/minecraft/tileentity/TileEntity;Lnet/minecraft/item/ItemStack;"
                        .concat("Lnet/minecraft/world/World;Lnet/minecraft/entity/player/EntityPlayer;)Z")
                        .equals(call.desc)) {
                    return;
                }
            }
        }
        throw new AssertionError("Super mode does not call the AE2 bindSuper endpoint");
    }

    private static BaseMetaTileEntity assemblyBase() throws Exception {
        BaseMetaTileEntity base = new BaseMetaTileEntity();
        MTEWirelessUnifiedOutputAssemblyME assembly = new MTEWirelessUnifiedOutputAssemblyME(
            "wireless_kit_line_test",
            4,
            new String[0],
            null);
        BaseMetaTileEntity.class.getMethod("setMetaTileEntity", IMetaTileEntity.class)
            .invoke(base, assembly);
        ((Object) assembly).getClass()
            .getMethod("setBaseMetaTileEntity", Class.forName("gregtech.api.interfaces.tileentity.IGregTechTileEntity"))
            .invoke(assembly, base);
        return base;
    }
}
