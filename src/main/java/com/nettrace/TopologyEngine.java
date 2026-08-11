package com.nettrace;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class TopologyEngine {
    private static final String[] PROTOCOLS = {"TCP", "UDP", "ICMP"};
    private static final int[] SUSPICIOUS_PORTS = {4444, 31337, 6667, 1337};

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

            // The "Core AI Firewall" hop is where AiThreatClassifier actually
            // runs: build a representative synthetic packet (same feature
            // distribution as SyntheticPacketStream, biased by attack mode)
            // and hand it to the real trained logistic regression, instead of
            // just echoing the attackMode flag straight through.
            boolean threatAtHop = node.id.equals("r3") && classifyHopPacket(simulateThreat);

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

    /**
     * Generates one representative packet's features (same distributions
     * NetTraceServer.SyntheticPacketStream uses for the packet log) and runs
     * it through AiThreatClassifier -- the same trained model, the same
     * scoring code, just invoked once here to decide whether the live
     * topology view should show a threat lighting up at the firewall hop.
     */
    private boolean classifyHopPacket(boolean attackMode) {
        double synProbability = attackMode ? 0.6 : 0.2;
        double suspiciousPortProbability = attackMode ? 0.45 : 0.0;
        double highSrcPortProbability = attackMode ? 0.55 : 0.25;
        int payloadFloor = attackMode ? 500 : 64;
        int payloadRange = attackMode ? 964 : 736;

        String protocol = PROTOCOLS[random.nextInt(PROTOCOLS.length)];
        String flags = protocol.equals("TCP") ? (random.nextDouble() < synProbability ? "SYN" : "ACK") : "N/A";
        int payloadSize = payloadFloor + random.nextInt(payloadRange);
        int srcPort = (random.nextDouble() < highSrcPortProbability)
                ? 60001 + random.nextInt(4535)
                : 1024 + random.nextInt(58976);
        int dstPort = (random.nextDouble() < suspiciousPortProbability)
                ? SUSPICIOUS_PORTS[random.nextInt(SUSPICIOUS_PORTS.length)]
                : (random.nextBoolean() ? 80 : 443);

        return AiThreatClassifier.isThreat(protocol, flags, payloadSize, srcPort, dstPort);
    }
}
