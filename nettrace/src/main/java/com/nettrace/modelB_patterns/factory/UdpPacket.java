package com.nettrace.modelB_patterns.factory;

import com.nettrace.modelB_patterns.core.Packet;

public record UdpPacket(String sourceIp, String destIp, int payloadSize) implements Packet {
    @Override public String getProtocol() { return "UDP"; }
    @Override public String getFlag() { return "NO_HANDSHAKE"; }
    @Override public String getSourceIp() { return sourceIp; }
    @Override public String getDestIp() { return destIp; }
    @Override public int getPayloadSize() { return payloadSize; }
}