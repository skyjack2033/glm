package com.github.skyjack2033.wirelessmehatch.me;

import java.util.Objects;
import java.util.UUID;

import net.minecraft.entity.player.EntityPlayer;

import appeng.api.networking.security.PlayerSource;
import appeng.api.util.DimensionalCoord;
import appeng.core.localization.WirelessMessages;
import appeng.helpers.WireLessToolHelper;
import appeng.helpers.WireLessToolHelper.BindResult;

public final class WirelessKitBindingService {

    private WirelessKitBindingService() {}

    static boolean isAssemblyPair(WirelessKitEndpoint target, WirelessKitEndpoint source) {
        return target != null && source != null && target.isAssembly() != source.isAssembly();
    }

    public static BindResult bind(WirelessKitEndpoint target, WirelessKitEndpoint source, EntityPlayer player) {
        if (target == null) return BindResult.INVALID_TARGET;
        if (source == null) return BindResult.INVALID_SOURCE;

        DimensionalCoord targetLocation = target.getLocation();
        DimensionalCoord sourceLocation = source.getLocation();
        if (targetLocation == null || sourceLocation == null
            || targetLocation.getDimension() != sourceLocation.getDimension()) {
            player.addChatMessage(WirelessMessages.DimensionMismatch.toChat());
            return BindResult.FAILED;
        }

        PlayerSource actionSource = new PlayerSource(player, null);
        if (!isAssemblyPair(target, source)) {
            if (target.isWirelessBase() && source.isWirelessBase()) {
                return WireLessToolHelper
                    .performConnection(target.getWirelessBase(), source.getWirelessBase(), actionSource);
            }
            return BindResult.INVALID_SOURCE;
        }

        boolean sourceDenied = !source.canAccess(actionSource);
        boolean targetDenied = !target.canAccess(actionSource);
        if (sourceDenied || targetDenied) {
            player.addChatMessage(WirelessMessages.Security.toChat());
            if (sourceDenied && targetDenied) return BindResult.FAILED;
            return sourceDenied ? BindResult.INVALID_SOURCE : BindResult.INVALID_TARGET;
        }

        WirelessKitEndpoint assemblyEndpoint = target.isAssembly() ? target : source;
        WirelessKitEndpoint anchorEndpoint = target.isWirelessBase() ? target : source;
        UUID bindingOwner = player.getUniqueID();
        if (isSameBinding(
            assemblyEndpoint.getAssembly()
                .getWirelessTarget(),
            anchorEndpoint.getLocation(),
            bindingOwner)) {
            return BindResult.ALREADY_BIND;
        }

        WirelessLinkTarget wirelessTarget = WirelessLinkTarget
            .forWirelessConnector(anchorEndpoint.getWirelessBase(), bindingOwner, player.getCommandSenderName());
        if (wirelessTarget == null || !assemblyEndpoint.getAssembly()
            .bindWirelessTarget(wirelessTarget)) {
            player.addChatMessage(WirelessMessages.Failed.toChat());
            return BindResult.FAILED;
        }

        player.addChatMessage(
            WirelessMessages.Connected.toChat(
                assemblyEndpoint.getLocation()
                    .getGuiTextShortNoDim()));
        return BindResult.SUCCESS;
    }

    static boolean isSameBinding(WirelessLinkTarget current, DimensionalCoord anchor, UUID bindingOwner) {
        return current != null && current.getAnchorType() == WirelessLinkTarget.AnchorType.WIRELESS_CONNECTOR
            && anchor != null
            && current.getDimensionId() == anchor.getDimension()
            && current.getX() == anchor.x
            && current.getY() == anchor.y
            && current.getZ() == anchor.z
            && Objects.equals(current.getOwnerUuid(), bindingOwner);
    }
}
