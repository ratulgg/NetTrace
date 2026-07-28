package com.nettrace.modelB_patterns.core;

/**
 * Factory Interface to encapsulate packet creation mechanics.
 */
public interface PacketFactory {
    Packet createPacket(String sourceIp, String destIp, int payloadSize);
}