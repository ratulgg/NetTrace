package com.nettrace.modelB_patterns;

import com.nettrace.benchmark.BenchmarkRunner;
import com.nettrace.modelA_baseline.ProceduralSimulator;
import com.nettrace.modelB_patterns.channel.NetworkChannel;
import com.nettrace.modelB_patterns.core.Packet;
import com.nettrace.modelB_patterns.core.PacketFactory;
import com.nettrace.modelB_patterns.factory.IcmpPacketFactory;
import com.nettrace.modelB_patterns.factory.TcpPacketFactory;
import com.nettrace.modelB_patterns.factory.UdpPacketFactory;
import com.nettrace.modelB_patterns.observer.MetricsCollector;
import com.nettrace.modelB_patterns.observer.NoOpObserver;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Coverage for the actual subject of the project: the Factory and Observer
 * patterns in Model B (PatternSimulator), the Model A procedural baseline
 * they're benchmarked against, and the benchmark harness that measures the
 * difference between them. Previously only the dashboard's synthetic data
 * generators (PacketQueue/TopologyEngine/SyntheticPacketStream) had tests.
 */
@DisplayName("Pattern-Driven Engine (Model B) & Benchmark Test Suite")
class PatternEngineTest {

    // =========================================================
    // 1. PACKET FACTORY TESTS
    // =========================================================
    @Nested
    @DisplayName("Packet Factory Tests")
    class PacketFactoryTests {

        @Test
        @DisplayName("TcpPacketFactory should produce a correctly-tagged TCP packet")
        void testTcpFactory() {
            PacketFactory factory = new TcpPacketFactory();
            Packet packet = factory.createPacket("192.168.1.1", "10.0.0.1", 512);

            assertEquals("TCP", packet.getProtocol());
            assertEquals("ACK_RECEIVED", packet.getFlag());
            assertEquals("192.168.1.1", packet.getSourceIp());
            assertEquals("10.0.0.1", packet.getDestIp());
            assertEquals(512, packet.getPayloadSize());
        }

        @Test
        @DisplayName("UdpPacketFactory should produce a correctly-tagged UDP packet")
        void testUdpFactory() {
            PacketFactory factory = new UdpPacketFactory();
            Packet packet = factory.createPacket("192.168.1.2", "10.0.0.2", 256);

            assertEquals("UDP", packet.getProtocol());
            assertEquals("NO_HANDSHAKE", packet.getFlag());
            assertEquals("192.168.1.2", packet.getSourceIp());
            assertEquals("10.0.0.2", packet.getDestIp());
            assertEquals(256, packet.getPayloadSize());
        }

        @Test
        @DisplayName("IcmpPacketFactory should produce a correctly-tagged ICMP packet")
        void testIcmpFactory() {
            PacketFactory factory = new IcmpPacketFactory();
            Packet packet = factory.createPacket("192.168.1.3", "10.0.0.3", 64);

            assertEquals("ICMP", packet.getProtocol());
            assertEquals("ECHO_REQUEST", packet.getFlag());
            assertEquals("192.168.1.3", packet.getSourceIp());
            assertEquals("10.0.0.3", packet.getDestIp());
            assertEquals(64, packet.getPayloadSize());
        }
    }

    // =========================================================
    // 2. NETWORK CHANNEL / OBSERVER DISPATCH TESTS
    // =========================================================
    @Nested
    @DisplayName("NetworkChannel Observer Dispatch Tests")
    class NetworkChannelTests {

        private NetworkChannel channel;
        private MetricsCollector metrics;

        @BeforeEach
        void setUp() {
            channel = new NetworkChannel();
            metrics = new MetricsCollector();
            channel.registerObserver(metrics);
        }

        @Test
        @DisplayName("Should notify observers of a transmitted packet and count it by protocol")
        void testDispatchTransmitsToObservers() {
            Packet packet = new TcpPacketFactory().createPacket("192.168.1.10", "10.0.0.10", 128);

            channel.dispatchPacket(packet);

            assertEquals(1, metrics.getTcpCount(), "TCP packet should be counted as transmitted");
            assertEquals(0, metrics.getDroppedCount(), "Non-firewalled packet should not be dropped");
        }

        @Test
        @DisplayName("Should route packets to the firewalled destination as dropped, not transmitted")
        void testDispatchAppliesFirewallRule() {
            Packet packet = new UdpPacketFactory().createPacket("192.168.1.11", "10.0.0.100", 128);

            channel.dispatchPacket(packet);

            assertEquals(0, metrics.getUdpCount(), "Firewalled packet must not be counted as processed");
            assertEquals(1, metrics.getDroppedCount(), "Firewalled packet must be counted as dropped");
        }

        @Test
        @DisplayName("Should fan out a single dispatch to every registered observer")
        void testDispatchFansOutToMultipleObservers() {
            NoOpObserver second = new NoOpObserver();
            channel.registerObserver(second);

            Packet packet = new IcmpPacketFactory().createPacket("192.168.1.12", "10.0.0.12", 64);
            channel.dispatchPacket(packet);

            assertEquals(1, metrics.getIcmpCount());
            assertEquals(1, second.getTransmittedCount(), "Second observer must also receive the dispatch");
        }

        @Test
        @DisplayName("Should stop notifying an observer once it is unregistered")
        void testUnregisterObserverStopsNotifications() {
            channel.unregisterObserver(metrics);

            Packet packet = new TcpPacketFactory().createPacket("192.168.1.13", "10.0.0.13", 128);
            channel.dispatchPacket(packet);

            assertEquals(0, metrics.getTcpCount(), "Unregistered observer must not receive further dispatches");
        }
    }

    // =========================================================
    // 3. NO-OP OBSERVER TESTS (used inside timed benchmark runs)
    // =========================================================
    @Nested
    @DisplayName("NoOpObserver Counting Tests")
    class NoOpObserverTests {

        @Test
        @DisplayName("Should count transmitted and dropped events without performing any I/O")
        void testCountsTransmittedAndDropped() {
            NoOpObserver observer = new NoOpObserver();

            observer.onPacketTransmitted(new TcpPacketFactory().createPacket("a", "b", 64));
            observer.onPacketTransmitted(new TcpPacketFactory().createPacket("a", "b", 64));
            observer.onPacketDropped("a", "b", "FIREWALL_BLOCKED");

            assertEquals(2, observer.getTransmittedCount());
            assertEquals(1, observer.getDroppedCount());
        }
    }

    // =========================================================
    // 4. QUIET SIMULATION DETERMINISM TESTS
    // =========================================================
    @Nested
    @DisplayName("Quiet Simulation Determinism Tests")
    class QuietSimulationTests {

        @Test
        @DisplayName("PatternSimulator.runQuiet should be deterministic for a fixed seed")
        void testPatternSimulatorDeterministic() {
            PatternSimulator.Result first = PatternSimulator.runQuiet(42L);
            PatternSimulator.Result second = PatternSimulator.runQuiet(42L);

            assertEquals(first.processedTcp, second.processedTcp);
            assertEquals(first.processedUdp, second.processedUdp);
            assertEquals(first.processedIcmp, second.processedIcmp);
            assertEquals(first.droppedPackets, second.droppedPackets);
        }

        @Test
        @DisplayName("ProceduralSimulator.runQuiet should be deterministic for a fixed seed")
        void testProceduralSimulatorDeterministic() {
            ProceduralSimulator.Result first = ProceduralSimulator.runQuiet(42L);
            ProceduralSimulator.Result second = ProceduralSimulator.runQuiet(42L);

            assertEquals(first.processedTcp, second.processedTcp);
            assertEquals(first.processedUdp, second.processedUdp);
            assertEquals(first.processedIcmp, second.processedIcmp);
            assertEquals(first.droppedPackets, second.droppedPackets);
        }

        @Test
        @DisplayName("Every packet should be either processed or dropped, with none lost or double-counted")
        void testPacketAccounting() {
            PatternSimulator.Result result = PatternSimulator.runQuiet(7L);
            assertEquals(10_000, result.getTotalProcessed() + result.droppedPackets);
        }

        @Test
        @DisplayName("Model A and Model B should reach identical business outcomes for the same seed")
        void testModelsAreBehaviorallyEquivalent() {
            // Same seed drives the exact same sequence of random draws through
            // the exact same protocol/drop branching logic in both models, so
            // only the architecture should differ -- not the simulated traffic
            // itself. This is what makes the measured "abstraction tax"
            // attributable to Factory/Observer overhead rather than the two
            // models simply doing different amounts of work.
            ProceduralSimulator.Result modelA = ProceduralSimulator.runQuiet(42L);
            PatternSimulator.Result modelB = PatternSimulator.runQuiet(42L);

            assertEquals(modelA.processedTcp, modelB.processedTcp, "TCP counts must match between models");
            assertEquals(modelA.processedUdp, modelB.processedUdp, "UDP counts must match between models");
            assertEquals(modelA.processedIcmp, modelB.processedIcmp, "ICMP counts must match between models");
            assertEquals(modelA.droppedPackets, modelB.droppedPackets, "Dropped counts must match between models");
        }
    }

    // =========================================================
    // 5. BENCHMARK RUNNER TESTS
    // =========================================================
    @Nested
    @DisplayName("BenchmarkRunner Tests")
    class BenchmarkRunnerTests {

        @Test
        @DisplayName("run() should return exactly benchmarkRuns timings per model")
        void testRunReturnsExpectedSampleCounts() {
            BenchmarkRunner.BenchmarkResult result = BenchmarkRunner.run(1, 3, 42L);

            assertEquals(3, result.modelATimesMs.size());
            assertEquals(3, result.modelBTimesMs.size());
        }

        @Test
        @DisplayName("All recorded pass durations should be non-negative")
        void testTimingsAreNonNegative() {
            BenchmarkRunner.BenchmarkResult result = BenchmarkRunner.run(1, 3, 42L);

            for (double ms : result.modelATimesMs) assertTrue(ms >= 0.0);
            for (double ms : result.modelBTimesMs) assertTrue(ms >= 0.0);
        }

        @Test
        @DisplayName("taxMs() and taxPercent() should be derived correctly from known averages")
        void testTaxCalculation() {
            List<Double> modelA = new ArrayList<>(List.of(4.0, 4.0));
            List<Double> modelB = new ArrayList<>(List.of(5.0, 5.0));
            BenchmarkRunner.BenchmarkResult result = new BenchmarkRunner.BenchmarkResult(modelA, modelB);

            assertEquals(4.0, result.avgModelAMs(), 1e-9);
            assertEquals(5.0, result.avgModelBMs(), 1e-9);
            assertEquals(1.0, result.taxMs(), 1e-9);
            assertEquals(25.0, result.taxPercent(), 1e-9, "Tax percent should be (1.0 / 4.0) * 100");
        }
    }
}
