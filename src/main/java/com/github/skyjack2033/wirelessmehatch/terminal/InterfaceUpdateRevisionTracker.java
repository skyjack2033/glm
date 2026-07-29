package com.github.skyjack2033.wirelessmehatch.terminal;

final class InterfaceUpdateRevisionTracker {

    private long lastSeen;

    InterfaceUpdateRevisionTracker(long initialRevision) {
        lastSeen = initialRevision;
    }

    boolean consume(long revision) {
        if (revision == lastSeen) return false;
        lastSeen = revision;
        return true;
    }
}
