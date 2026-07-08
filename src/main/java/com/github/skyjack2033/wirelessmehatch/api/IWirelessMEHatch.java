package com.github.skyjack2033.wirelessmehatch.api;

/**
 * Marks a hatch that connects to an ME network wirelessly via a bound AE2 Wireless Access Point serial. The Memory
 * Card handler uses these methods to bind/unbind a WAP serial.
 */
public interface IWirelessMEHatch {

    /** @return the bound WAP locatable serial, or 0 if unbound. */
    long getBoundWapSerial();

    /**
     * Bind this hatch to a WAP serial. Pass 0 to unbind. Implementations must persist the serial to NBT and
     * (re)establish the grid connection.
     */
    void setBoundWapSerial(long serial);

    /** @return true if the grid connection to the bound network is currently active. */
    boolean isWirelessConnected();
}
