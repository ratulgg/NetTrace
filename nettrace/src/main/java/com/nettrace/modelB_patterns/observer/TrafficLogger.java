package com.nettrace.modelB_patterns.observer;

import com.nettrace.modelB_patterns.core.NetworkObserver;
import com.nettrace.modelB_patterns.core.Packet;

public class TrafficLogger implements NetworkObserver {
    private int packetCounter = 0;

    @Override
    public void onPacketTransmitted(Packet packet) {
        packetCounter++;
        if (packetCounter % 2500 == 0) { // Keep terminal output clean
            System.out.printf("[LOG #%d] Protocol: %-4s | %s -> %s | Size: %d bytes | Flag: %s%n",
                    packetCounter, packet.getProtocol(), packet.getSourceIp(), 
                    packet.getDestIp(), packet.getPayloadSize(), packet.getFlag());
        }
    }

    @Override
    public void onPacketDropped(String sourceIp, String destIp, String reason) {
        packetCounter++;
        // Optional drop logging
    }
}