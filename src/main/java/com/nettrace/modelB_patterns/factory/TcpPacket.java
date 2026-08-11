package com.nettrace.modelB_patterns.factory;

import com.nettrace.modelB_patterns.core.Packet;

public record TcpPacket(String sourceIp, String destIp, int payloadSize) implements Packet {
    @Override public String getProtocol() { return "TCP"; }
    @Override public String getFlag() { return "ACK_RECEIVED"; }
    @Override public String getSourceIp() { return sourceIp; }
    @Override public String getDestIp() { return destIp; }
    @Override public int getPayloadSize() { return payloadSize; }
}