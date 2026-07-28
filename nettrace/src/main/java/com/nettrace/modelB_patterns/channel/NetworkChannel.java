package com.nettrace.modelB_patterns.channel;

import com.nettrace.modelB_patterns.core.NetworkObserver;
import com.nettrace.modelB_patterns.core.Packet;

import java.util.ArrayList;
import java.util.List;

public class NetworkChannel {
    private final List<NetworkObserver> observers = new ArrayList<>();

    public void registerObserver(NetworkObserver observer) {
        observers.add(observer);
    }

    public void unregisterObserver(NetworkObserver observer) {
        observers.remove(observer);
    }

    public void dispatchPacket(Packet packet) {
        // Firewall Rule Check (Decoupled execution)
        if (packet.getDestIp().endsWith(".100")) {
            notifyPacketDropped(packet.getSourceIp(), packet.getDestIp(), "FIREWALL_BLOCKED");
            return;
        }

        notifyPacketTransmitted(packet);
    }

    private void notifyPacketTransmitted(Packet packet) {
        for (NetworkObserver observer : observers) {
            observer.onPacketTransmitted(packet);
        }
    }

    private void notifyPacketDropped(String sourceIp, String destIp, String reason) {
        for (NetworkObserver observer : observers) {
            observer.onPacketDropped(sourceIp, destIp, reason);
        }
    }
}