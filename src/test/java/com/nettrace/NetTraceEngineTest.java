package com.nettrace;

import com.nettrace.NetTraceServer.SyntheticPacketStream;
import com.nettrace.NetTraceServer.SyntheticPacketStream.SyntheticPacket;

// Packet, PacketQueue, and TopologyEngine are top-level classes in this
// same package (com.nettrace) now, so they need no import.

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("NetTrace Core Engine & Telemetry Test Suite")
class NetTraceEngineTest {

    // =========================================================
    // 1. PACKET QUEUE UNIT TESTS
    // =========================================================
    @Nested
    @DisplayName("PacketQueue Buffer & Backpressure Tests")
    class PacketQueueTests {

        private PacketQueue queue;

        @BeforeEach
        void setUp() {
            queue = new PacketQueue(16);
        }

        @Test
        @DisplayName("Should process normal traffic within capacity bounds")
        void testNormalTrafficQueue() {
            PacketQueue.QueueResult result = queue.processPacket(false);

            assertTrue(result.depth >= 0 && result.depth <= 16, "Queue depth should be bounded by capacity");
            assertEquals(16, result.capacity, "Queue capacity should remain fixed");
            assertTrue(result.queueDelayMs >= 0.0, "Queuing delay should be non-negative");
            assertFalse(result.overflowDrop, "Normal traffic should not trigger queue overflow drop");
        }

        @Test
        @DisplayName("Should simulate high backpressure under attack traffic")
        void testAttackTrafficQueue() {
            PacketQueue.QueueResult result = queue.processPacket(true);

            assertTrue(result.depth > 0, "Attack traffic should elevate queue depth");
            assertTrue(result.queueDelayMs > 0.0, "Queuing delay should reflect buffer backpressure");
        }

        @Test
        @DisplayName("Batch overflow-drop sampling should stay near 0 in normal mode and rise substantially under attack")
        void testCountOverflowDropsRespondsToAttackMode() {
            // capacity=16 queue: normal-mode fillRatio tops out at 0.40, so
            // depth can never reach capacity -- drops should be exactly 0.
            // Attack-mode fillRatio ranges up to 1.00, so a meaningful
            // fraction of a large batch should overflow.
            int batchSize = 500;
            int normalDrops = queue.countOverflowDrops(batchSize, false);
            int attackDrops = queue.countOverflowDrops(batchSize, true);

            assertEquals(0, normalDrops, "Normal-mode fillRatio never reaches capacity, so no drops should occur");
            assertTrue(attackDrops > 0, "Attack-mode fillRatio should produce some overflow drops across a large batch");
            assertTrue(attackDrops > normalDrops,
                "Attack mode should produce meaningfully more overflow drops than normal mode");
        }
    }

    // =========================================================
    // 2. MULTI-HOP TOPOLOGY ROUTING TESTS
    // =========================================================
    @Nested
    @DisplayName("TopologyEngine Routing & Firewall Tests")
    class TopologyEngineTests {

        private TopologyEngine topology;

        @BeforeEach
        void setUp() {
            topology = new TopologyEngine();
        }

        @Test
        @DisplayName("Should traverse all 5 router nodes in exact sequential path")
        void testPacketHopTraversal() {
            Packet packet = topology.tracePacketPath("PKT-1001", "192.168.1.100", "10.0.4.50", false);

            assertNotNull(packet, "Traced packet should not be null");
            assertEquals("PKT-1001", packet.packetId);
            assertEquals("192.168.1.100", packet.sourceIp);
            assertEquals("10.0.4.50", packet.destIp);
            assertEquals(5, packet.hops.size(), "Packet route must contain exactly 5 hop nodes");

            assertEquals("Client Gateway", packet.hops.get(0).nodeName);
            assertEquals("Ingress Router", packet.hops.get(1).nodeName);
            assertEquals("Core AI Firewall", packet.hops.get(2).nodeName);
            assertEquals("Egress Switch", packet.hops.get(3).nodeName);
            assertEquals("Target Server", packet.hops.get(4).nodeName);
        }

        @Test
        @DisplayName("Core AI Firewall should flag threats far more often in attack mode, since the hop now runs the real trained classifier instead of a hardcoded flag")
        void testFirewallThreatFlagging() {
            // The firewall hop now calls the same AiThreatClassifier used by the
            // packet log, on randomized-but-attack-mode-biased features -- so a
            // single trace is no longer guaranteed to flag a threat the way a
            // hardcoded `simulateThreat && node == firewall` boolean was. Instead,
            // verify the real classifier fires far more often under attack mode
            // across repeated traces, the same way testAttackModeThreatRatio
            // already validates SyntheticPacketStream below.
            int trials = 200;
            int attackFlags = 0;
            int normalFlags = 0;

            for (int i = 0; i < trials; i++) {
                Packet attackPacket = topology.tracePacketPath("PKT-9999", "192.168.1.105", "10.0.4.22", true);
                Packet normalPacket = topology.tracePacketPath("PKT-0000", "192.168.1.105", "10.0.4.22", false);

                assertFalse(attackPacket.hops.get(0).threatDetected, "Client Gateway should never flag a threat");
                assertFalse(attackPacket.hops.get(1).threatDetected, "Ingress Router should never flag a threat");
                assertFalse(attackPacket.hops.get(3).threatDetected, "Egress Switch should never flag a threat");
                assertFalse(attackPacket.hops.get(4).threatDetected, "Target Server should never flag a threat");

                if (attackPacket.hops.get(2).threatDetected) attackFlags++;
                if (normalPacket.hops.get(2).threatDetected) normalFlags++;
            }

            assertTrue(attackFlags > normalFlags,
                "Attack-mode traces should trip the real classifier at the firewall hop more often than normal-mode traces");
            assertTrue(attackFlags > trials / 4,
                "Attack mode should flag a meaningful fraction of traces given the classifier's feature distribution");
        }

        @Test
        @DisplayName("All hop latencies must be strictly positive non-zero values")
        void testPositiveLatencies() {
            Packet packet = topology.tracePacketPath("PKT-1002", "10.0.0.1", "10.0.0.2", false);

            for (Packet.HopRecord hop : packet.hops) {
                assertTrue(hop.latencyMs > 0.0, "Hop latency must be greater than 0 ms");
            }
        }
    }

    // =========================================================
    // 2b. LIVE THROUGHPUT / PACKET LOSS DERIVATION TESTS
    //
    // ApiRunHandler.handle() is a package-private HTTP handler (not a
    // standalone unit under test), so these tests replicate its
    // throughput_pps / packet_loss_pct formulas directly against
    // TopologyEngine and PacketQueue -- the same real per-hop latency and
    // queue data the handler reads -- to verify the formulas themselves
    // behave correctly, independent of the HTTP plumbing.
    // =========================================================
    @Nested
    @DisplayName("Live Throughput & Packet Loss Derivation Tests")
    class LiveMetricsDerivationTests {

        private TopologyEngine topology;

        @BeforeEach
        void setUp() {
            topology = new TopologyEngine();
        }

        private double throughputFor(int batchSize, boolean attackMode) {
            Packet packet = topology.tracePacketPath("PKT-TEST", "10.0.0.1", "10.0.0.2", attackMode);
            double totalTransitMs = 0.0;
            for (Packet.HopRecord hop : packet.hops) totalTransitMs += hop.latencyMs;
            return (batchSize * 1000.0) / totalTransitMs;
        }

        private double packetLossPctFor(int batchSize, boolean attackMode) {
            int totalDrops = 0;
            for (TopologyEngine.RouterNode node : topology.getPath()) {
                totalDrops += node.queue.countOverflowDrops(batchSize, attackMode);
            }
            return (totalDrops / (double) (batchSize * 5)) * 100.0;
        }

        @Test
        @DisplayName("Throughput should scale up with batchSize, holding attackMode fixed")
        void testThroughputScalesWithBatchSize() {
            double smallBatchPps = throughputFor(10, false);
            double largeBatchPps = throughputFor(1000, false);

            assertTrue(largeBatchPps > smallBatchPps * 10,
                "A 100x larger batch should yield roughly proportionally higher throughput, not a fixed constant");
        }

        @Test
        @DisplayName("Attack mode should visibly change throughput vs. normal mode via queue-driven delay")
        void testThroughputRespondsToAttackMode() {
            // Average across several trials since per-hop jitter/fillRatio
            // are randomized -- a single trace could coincidentally land
            // close either way.
            int trials = 50;
            double normalTotal = 0.0;
            double attackTotal = 0.0;
            for (int i = 0; i < trials; i++) {
                normalTotal += throughputFor(50, false);
                attackTotal += throughputFor(50, true);
            }
            double normalAvg = normalTotal / trials;
            double attackAvg = attackTotal / trials;

            assertNotEquals(normalAvg, attackAvg, 0.01,
                "Attack mode's higher queue delay should produce a visibly different average throughput");
            assertTrue(attackAvg < normalAvg,
                "Attack mode's elevated queueDelayMs should increase transit time and therefore lower throughput");
        }

        @Test
        @DisplayName("Packet loss should be ~0% in normal mode and meaningfully higher in attack mode")
        void testPacketLossRespondsToAttackMode() {
            int batchSize = 500;
            double normalLossPct = packetLossPctFor(batchSize, false);
            double attackLossPct = packetLossPctFor(batchSize, true);

            assertEquals(0.0, normalLossPct, 0.01, "Normal-mode queue fill never reaches capacity, so loss should be ~0%");
            assertTrue(attackLossPct > 1.0, "Attack mode should produce a meaningfully non-zero loss percentage");
        }
    }

    // =========================================================
    // 3. SYNTHETIC PACKET STREAM GENERATOR TESTS
    // =========================================================
    @Nested
    @DisplayName("SyntheticPacketStream Generator Tests")
    class SyntheticPacketStreamTests {

        private SyntheticPacketStream streamGen;

        @BeforeEach
        void setUp() {
            streamGen = new SyntheticPacketStream();
        }

        @ParameterizedTest
        @ValueSource(ints = {5, 10, 25, 50})
        @DisplayName("Should generate exact requested batch size")
        void testBatchSizeGeneration(int batchSize) {
            List<SyntheticPacket> batch = streamGen.generateStreamBatch(batchSize, false, "192.168.1.100", "10.0.4.50");

            assertNotNull(batch);
            assertEquals(batchSize, batch.size(), "Generated batch size must match request");
        }

        @Test
        @DisplayName("Should populate valid IP headers, ports, protocols, and sizes")
        void testPacketHeaderIntegrity() {
            List<SyntheticPacket> batch = streamGen.generateStreamBatch(10, false, "192.168.1.105", "10.0.4.22");

            for (SyntheticPacket p : batch) {
                assertNotNull(p.id);
                assertTrue(p.id.startsWith("PKT-"));
                assertEquals("192.168.1.105", p.srcIp);
                assertEquals("10.0.4.22", p.dstIp);
                assertTrue(p.srcPort >= 1024 && p.srcPort <= 65535, "Source port must be in unprivileged range");
                assertTrue(p.dstPort == 80 || p.dstPort == 443 || p.dstPort == 8080, "Destination port should match standard HTTP/S ports");
                assertTrue(List.of("TCP", "UDP", "ICMP").contains(p.protocol), "Protocol must be TCP, UDP, or ICMP");
                assertTrue(p.sizeBytes >= 64 && p.sizeBytes <= 1500, "Payload size must be within standard MTU limits");
            }
        }

        @Test
        @DisplayName("Attack mode should yield significantly higher threat ratio than normal mode")
        void testAttackModeThreatRatio() {
            List<SyntheticPacket> normalBatch = streamGen.generateStreamBatch(100, false, "192.168.1.1", "10.0.0.1");
            List<SyntheticPacket> attackBatch = streamGen.generateStreamBatch(100, true, "192.168.1.1", "10.0.0.1");

            long normalThreats = normalBatch.stream().filter(p -> p.isThreat).count();
            long attackThreats = attackBatch.stream().filter(p -> p.isThreat).count();

            assertTrue(attackThreats > normalThreats, "Attack mode must generate more threat packets than normal mode");
        }
    }
}