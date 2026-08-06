package com.nettrace;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class TopologyEngine {

    public static class RouterNode {
        public String id;
        public String name;
        public double baseLatencyMs;

        public RouterNode(String id, String name, double baseLatencyMs) {
            this.id = id;
            this.name = name;
            this.baseLatencyMs = baseLatencyMs;
        }
    }

    private final List<RouterNode> path = new ArrayList<>();
    private final Random random = new Random();

    public TopologyEngine() {
        // Define multi-hop virtual routing topology
        path.add(new RouterNode("r1", "Client Gateway", 1.2));
        path.add(new RouterNode("r2", "Ingress Router", 3.4));
        path.add(new RouterNode("r3", "Core AI Firewall", 7.8));
        path.add(new RouterNode("r4", "Egress Switch", 2.1));
        path.add(new RouterNode("r5", "Target Server", 0.9));
    }

    public Packet tracePacketPath(String packetId, String srcIp, String destIp, boolean simulateThreat) {
        Packet packet = new Packet();
        packet.packetId = packetId;
        packet.sourceIp = srcIp;
        packet.destIp = destIp;
        packet.payloadBytes = 64 + random.nextInt(1400);
        packet.isThreat = simulateThreat;

        for (RouterNode node : path) {
            // Add slight network jitter (+/- 0.4ms)
            double hopDelay = node.baseLatencyMs + (random.nextDouble() * 0.8 - 0.4);
            hopDelay = Math.max(0.1, Math.round(hopDelay * 100.0) / 100.0);

            // In-loop AI threat classification at the Core Firewall hop
            boolean threatAtHop = simulateThreat && node.id.equals("r3");

            packet.hops.add(new Packet.HopRecord(node.name, hopDelay, threatAtHop));
        }

        return packet;
    }
}