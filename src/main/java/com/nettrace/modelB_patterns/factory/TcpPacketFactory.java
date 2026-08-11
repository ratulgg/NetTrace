package com.nettrace.modelB_patterns.factory;

import com.nettrace.modelB_patterns.core.Packet;
import com.nettrace.modelB_patterns.core.PacketFactory;

public class TcpPacketFactory implements PacketFactory {
    @Override
    public Packet createPacket(String sourceIp, String destIp, int payloadSize) {
        return new TcpPacket(sourceIp, destIp, payloadSize);
    }
}