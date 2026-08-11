package com.nettrace;

import java.util.ArrayList;
import java.util.List;

public class Packet {
    public String packetId;
    public String sourceIp;
    public String destIp;
    public List<HopRecord> hops = new ArrayList<>();

    public static class HopRecord {
        public String nodeName;
        public double latencyMs;
        public boolean threatDetected;
        public int queueDepth;
        public int queueCapacity;
        public boolean wasDropped;

        public HopRecord(String nodeName, double latencyMs, boolean threatDetected, int queueDepth, int queueCapacity, boolean wasDropped) {
            this.nodeName = nodeName;
            this.latencyMs = latencyMs;
            this.threatDetected = threatDetected;
            this.queueDepth = queueDepth;
            this.queueCapacity = queueCapacity;
            this.wasDropped = wasDropped;
        }
    }
}
