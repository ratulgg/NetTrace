package com.nettrace.modelB_patterns.observer;

import com.nettrace.modelB_patterns.core.NetworkObserver;
import com.nettrace.modelB_patterns.core.Packet;

/**
 * A second observer used only during timed benchmark runs. It performs the
 * same virtual-dispatch + counter-increment work as a "real" observer, but
 * with zero I/O, so a two-observer NetworkChannel can be benchmarked without
 * println/console overhead skewing the measured Observer-pattern dispatch
 * cost. {@link TrafficLogger} remains available for interactive/CLI use
 * where console output is actually wanted.
 */
public class NoOpObserver implements NetworkObserver {
    private int transmittedCount = 0;
    private int droppedCount = 0;

    @Override
    public void onPacketTransmitted(Packet packet) {
        transmittedCount++;
    }

    @Override
    public void onPacketDropped(String sourceIp, String destIp, String reason) {
        droppedCount++;
    }

    public int getTransmittedCount() {
        return transmittedCount;
    }

    public int getDroppedCount() {
        return droppedCount;
    }
}
