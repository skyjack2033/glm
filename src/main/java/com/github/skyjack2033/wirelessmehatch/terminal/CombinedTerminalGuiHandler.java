package com.github.skyjack2033.wirelessmehatch.terminal;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;
import net.minecraftforge.common.util.ForgeDirection;

import com.github.skyjack2033.wirelessmehatch.WirelessMEHatch;
import com.github.skyjack2033.wirelessmehatch.terminal.client.GuiCombinedTerminal;

import appeng.api.parts.IPart;
import appeng.api.parts.IPartHost;
import appeng.core.sync.GuiBridge;
import cpw.mods.fml.common.network.IGuiHandler;
import cpw.mods.fml.common.network.NetworkRegistry;

public final class CombinedTerminalGuiHandler implements IGuiHandler {

    public static final int GUI_ID_BASE = 0x5A0;

    public static void register() {
        NetworkRegistry.INSTANCE.registerGuiHandler(WirelessMEHatch.INSTANCE, new CombinedTerminalGuiHandler());
    }

    public static void open(EntityPlayer player, PartCombinedTerminal terminal) {
        if (player == null || terminal == null) return;
        open(player, terminal.getTile(), terminal.getSide());
    }

    static void open(EntityPlayer player, TileEntity tile, ForgeDirection side) {
        if (player == null || tile == null
            || tile.getWorldObj() == null
            || side == null
            || side.ordinal() < 0
            || side.ordinal() > 5) return;

        if (!(tile instanceof IPartHost) || ((IPartHost) tile).getPart(side) == null
            || !(((IPartHost) tile).getPart(side) instanceof PartCombinedTerminal)) return;

        World world = tile.getWorldObj();
        player.openGui(
            WirelessMEHatch.INSTANCE,
            GUI_ID_BASE + side.ordinal(),
            world,
            tile.xCoord,
            tile.yCoord,
            tile.zCoord);
    }

    @Override
    public Object getServerGuiElement(int id, EntityPlayer player, World world, int x, int y, int z) {
        PartCombinedTerminal terminal = findTerminal(id, world, x, y, z);
        return terminal == null || !hasPermission(player, terminal) ? null
            : new CombinedTerminalContainer(player.inventory, terminal);
    }

    @Override
    public Object getClientGuiElement(int id, EntityPlayer player, World world, int x, int y, int z) {
        PartCombinedTerminal terminal = findTerminal(id, world, x, y, z);
        return terminal == null ? null : new GuiCombinedTerminal(player.inventory, terminal);
    }

    private static PartCombinedTerminal findTerminal(int id, World world, int x, int y, int z) {
        int sideIndex = id - GUI_ID_BASE;
        if (world == null || sideIndex < 0 || sideIndex > 5) return null;

        TileEntity tile = world.getTileEntity(x, y, z);
        if (!(tile instanceof IPartHost)) return null;
        IPartHost host = (IPartHost) tile;
        IPart part = host.getPart(ForgeDirection.getOrientation(sideIndex));
        return part instanceof PartCombinedTerminal ? (PartCombinedTerminal) part : null;
    }

    private static boolean hasPermission(EntityPlayer player, PartCombinedTerminal terminal) {
        TileEntity tile = terminal.getTile();
        return tile != null && GuiBridge.GUI_PATTERN_TERMINAL
            .hasPermissions(tile, tile.xCoord, tile.yCoord, tile.zCoord, terminal.getSide(), player);
    }
}
