package com.github.skyjack2033.wirelessmehatch.terminal;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.Vec3;

public final class CombinedTerminalActivationHandler {

    private CombinedTerminalActivationHandler() {}

    public static boolean tryOpen(Object part, EntityPlayer player, Vec3 hitVec) {
        if (!(part instanceof PartCombinedTerminal)) return false;
        return ((PartCombinedTerminal) part).openCombinedTerminal(player);
    }
}
