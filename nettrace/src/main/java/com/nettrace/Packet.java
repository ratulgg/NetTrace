package com.nettrace;

import java.util.ArrayList;
import java.util.List;

public class Packet {
    public String packetId;
    public String sourceIp;
    public String destIp;
    public int payloadBytes;
    public List<HopRecord> hops = new ArrayList<>();
    public boolean isThreat;

    public static class HopRecord {
        public String nodeName;
        public double latencyMs;
        public boolean threatDetected;

        public HopRecord(String nodeName, double latencyMs, boolean threatDetected) {
            this.nodeName = nodeName;
            this.latencyMs = latencyMs;
            this.threatDetected = threatDetected;
        }
    }
}