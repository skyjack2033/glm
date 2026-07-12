package com.github.skyjack2033.wirelessmehatch.api;

import com.github.skyjack2033.wirelessmehatch.me.WirelessLinkTarget;

public interface WirelessBindable {

    boolean bindWirelessTarget(WirelessLinkTarget target);

    void clearWirelessTarget();

    boolean hasWirelessTarget();

    boolean isWirelessConnected();
}
