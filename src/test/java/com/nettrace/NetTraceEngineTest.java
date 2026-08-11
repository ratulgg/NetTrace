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