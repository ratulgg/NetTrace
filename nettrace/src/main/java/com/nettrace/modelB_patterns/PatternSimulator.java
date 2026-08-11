package com.nettrace.modelB_patterns;

import com.nettrace.modelB_patterns.channel.NetworkChannel;
import com.nettrace.modelB_patterns.core.Packet;
import com.nettrace.modelB_patterns.core.PacketFactory;
import com.nettrace.modelB_patterns.factory.IcmpPacketFactory;
import com.nettrace.modelB_patterns.factory.TcpPacketFactory;
import com.nettrace.modelB_patterns.factory.UdpPacketFactory;
import com.nettrace.modelB_patterns.observer.MetricsCollector;
import com.nettrace.modelB_patterns.observer.NoOpObserver;

import java.util.Random;

/**
 * Model B: Pattern-Driven Architecture Simulation Engine
 * Demonstrates decoupled execution using Factory and Observer patterns.
 */
public class PatternSimulator {

    private static final int PACKET_COUNT = 10_000;
    private static final long DEFAULT_SEED = 42; // Identical seed to Model A

    /**
     * Outcome of a single simulation pass. Deliberately carries only counts and
     * timing -- no console output happens while a pass is being timed.
     */
    public static class Result {
        public final int processedTcp;
        public final int processedUdp;
        public final int processedIcmp;
        public final int droppedPackets;
        public final double durationMs;

        public Result(int processedTcp, int processedUdp, int processedIcmp, int droppedPackets, double durationMs) {
            this.processedTcp = processedTcp;
            this.processedUdp = processedUdp;
            this.processedIcmp = processedIcmp;
            this.droppedPackets = droppedPackets;
            this.durationMs = durationMs;
        }

        public int getTotalProcessed() {
            return processedTcp + processedUdp + processedIcmp;
        }
    }

    /**
     * Runs the Factory + Observer pattern pipeline with NO I/O inside the timed
     * region. Two observers are still registered (matching the real dispatch
     * fan-out) but both are silent, so the measured duration reflects Factory
     * creation + Observer dispatch cost only -- not println/console overhead.
     * A fresh Random is seeded on every call, so the workload is reproducible
     * on every single invocation, not just the first one in a process.
     */
    public static Result runQuiet(long seed) {
        Random random = new Random(seed);

        NetworkChannel channel = new NetworkChannel();
        MetricsCollector metrics = new MetricsCollector();
        NoOpObserver silentObserver = new NoOpObserver();

        channel.registerObserver(metrics);
        channel.registerObserver(silentObserver);

        PacketFactory tcpFactory = new TcpPacketFactory();
        PacketFactory udpFactory = new UdpPacketFactory();
        PacketFactory icmpFactory = new IcmpPacketFactory();

        long startTime = System.nanoTime();

        for (int i = 1; i <= PACKET_COUNT; i++) {
            String protocol = getRandomProtocol(random);
            int payloadSize = random.nextInt(1400) + 64;
            String sourceIp = "192.168.1." + random.nextInt(255);
            String destIp = "10.0.0." + random.nextInt(255);

            // Encapsulated Packet Creation via Factory
            Packet packet = switch (protocol) {
                case "TCP" -> tcpFactory.createPacket(sourceIp, destIp, payloadSize);
                case "UDP" -> udpFactory.createPacket(sourceIp, destIp, payloadSize);
                default -> icmpFactory.createPacket(sourceIp, destIp, payloadSize);
            };

            // Event Dispatch through Network Channel
            channel.dispatchPacket(packet);
        }

        long endTime = System.nanoTime();
        double durationMs = (endTime - startTime) / 1_000_000.0;

        return new Result(metrics.getTcpCount(), metrics.getUdpCount(), metrics.getIcmpCount(),
                metrics.getDroppedCount(), durationMs);
    }

    /**
     * Standalone CLI demo entry point. Uses the same quiet, I/O-free core loop
     * as the benchmark harness, then prints a summary once timing is complete.
     */
    public static void main(String[] args) {
        System.out.println("==================================================");
        System.out.println("  STARTING MODEL B: PATTERN-DRIVEN SIMULATION     ");
        System.out.println("==================================================");

        Result result = runQuiet(DEFAULT_SEED);

        System.out.println("\n--------------------------------------------------");
        System.out.println("  PATTERN RESULTS (MODEL B)");
        System.out.println("--------------------------------------------------");
        System.out.println("Total Packets Sent : " + PACKET_COUNT);
        System.out.println("TCP Processed      : " + result.processedTcp);
        System.out.println("UDP Processed      : " + result.processedUdp);
        System.out.println("ICMP Processed     : " + result.processedIcmp);
        System.out.println("Packets Dropped    : " + result.droppedPackets);
        System.out.println("Execution Time     : " + String.format("%.3f", result.durationMs) + " ms");
        System.out.println("==================================================");
    }

    private static String getRandomProtocol(Random random) {
        int choice = random.nextInt(3);
        return switch (choice) {
            case 0 -> "TCP";
            case 1 -> "UDP";
            default -> "ICMP";
        };
    }
}
