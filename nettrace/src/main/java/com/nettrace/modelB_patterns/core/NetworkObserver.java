package com.nettrace.modelB_patterns.core;

/**
 * Observer Interface to decouple logging, firewalling, and 
 * UI updates from packet processing.
 */
public interface NetworkObserver {
    void onPacketTransmitted(Packet packet);
    void onPacketDropped(String sourceIp, String destIp, String reason);
}