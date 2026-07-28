package com.nettrace.modelB_patterns.observer;

import com.nettrace.modelB_patterns.core.NetworkObserver;
import com.nettrace.modelB_patterns.core.Packet;

public class MetricsCollector implements NetworkObserver {
    private int tcpCount = 0;
    private int udpCount = 0;
    private int icmpCount = 0;
    private int droppedCount = 0;

    @Override
    public void onPacketTransmitted(Packet packet) {
        switch (packet.getProtocol()) {
            case "TCP"  -> tcpCount++;
            case "UDP"  -> udpCount++;
            case "ICMP" -> icmpCount++;
        }
    }

    @Override
    public void onPacketDropped(String sourceIp, String destIp, String reason) {
        droppedCount++;
    }

    // Metric Getters for Benchmarking
    public int getTcpCount() { return tcpCount; }
    public int getUdpCount() { return udpCount; }
    public int getIcmpCount() { return icmpCount; }
    public int getDroppedCount() { return droppedCount; }
    public int getTotalProcessed() { return tcpCount + udpCount + icmpCount; }
}