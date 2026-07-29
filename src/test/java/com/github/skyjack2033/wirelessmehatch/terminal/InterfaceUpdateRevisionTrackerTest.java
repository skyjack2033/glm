package com.github.skyjack2033.wirelessmehatch.terminal;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class InterfaceUpdateRevisionTrackerTest {

    @Test
    public void eachContainerConsumesTheSameRevisionIndependently() {
        InterfaceUpdateRevisionTracker first = new InterfaceUpdateRevisionTracker(4L);
        InterfaceUpdateRevisionTracker second = new InterfaceUpdateRevisionTracker(4L);

        assertFalse(first.consume(4L));
        assertTrue(first.consume(5L));
        assertFalse(first.consume(5L));
        assertTrue(second.consume(5L));
        assertFalse(second.consume(5L));
    }

    @Test
    public void treatsLongWraparoundAsARevisionChange() {
        InterfaceUpdateRevisionTracker tracker = new InterfaceUpdateRevisionTracker(Long.MAX_VALUE);

        assertTrue(tracker.consume(Long.MIN_VALUE));
        assertFalse(tracker.consume(Long.MIN_VALUE));
    }
}
