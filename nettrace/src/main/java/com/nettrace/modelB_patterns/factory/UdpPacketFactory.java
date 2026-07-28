package com.nettrace.modelB_patterns.factory;

import com.nettrace.modelB_patterns.core.Packet;
import com.nettrace.modelB_patterns.core.PacketFactory;

public class UdpPacketFactory implements PacketFactory {
    @Override
    public Packet createPacket(String sourceIp, String destIp, int payloadSize) {
        return new UdpPacket(sourceIp, destIp, payloadSize);
    }
}