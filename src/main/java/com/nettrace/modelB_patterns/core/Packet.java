package com.nettrace.modelB_patterns.core;

/**
 * Abstraction for all packet types in Model B.
 * Eliminates protocol-specific branching in execution logic.
 */
public interface Packet {
    String getProtocol();
    String getSourceIp();
    String getDestIp();
    int getPayloadSize();
    String getFlag();
}