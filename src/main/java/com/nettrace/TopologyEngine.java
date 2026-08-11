package com.nettrace;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class TopologyEngine {
    public static class RouterNode {
        public String id;
        public String name;
        public double baseLatencyMs;
        public PacketQueue queue;

        public RouterNode(String id, String name, double baseLatencyMs, int queueCapacity) {
            this.id = id;
            this.name = name;
            this.baseLatencyMs = baseLatencyMs;
            this.queue = new PacketQueue(queueCapacity);
        }
    }

    private final List<RouterNode> path = new ArrayList<>();
    private final Random random = new Random();

    public TopologyEngine() {
        path.add(new RouterNode("r1", "Client Gateway", 1.2, 16));
        path.add(new RouterNode("r2", "Ingress Router", 3.4, 32));
        path.add(new RouterNode("r3", "Core AI Firewall", 7.8, 16));
        path.add(new RouterNode("r4", "Egress Switch", 2.1, 32));
        path.add(new RouterNode("r5", "Target Server", 0.9, 16));
    }

    public Packet tracePacketPath(String packetId, String srcIp, String destIp, boolean simulateThreat) {
        Packet packet = new Packet();
        packet.packetId = packetId;
        packet.sourceIp = srcIp;
        packet.destIp = destIp;

        for (RouterNode node : path) {
            // Process through node's PacketQueue
            PacketQueue.QueueResult qRes = node.queue.processPacket(simulateThreat);

            double totalHopDelay = node.baseLatencyMs + qRes.queueDelayMs + (random.nextDouble() * 0.4 - 0.2);
            totalHopDelay = Math.max(0.1, Math.round(totalHopDelay * 100.0) / 100.0);

            boolean threatAtHop = simulateThreat && node.id.equals("r3");

            packet.hops.add(new Packet.HopRecord(
                node.name,
                totalHopDelay,
                threatAtHop,
                qRes.depth,
                qRes.capacity,
                qRes.overflowDrop
            ));
        }
        return packet;
    }
}
