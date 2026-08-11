package com.nettrace.modelA_baseline;

import java.util.Random;

/**
 * Model A: Procedural Baseline Simulation Engine
 * Represents a monolithic, tightly-coupled implementation.
 */
public class ProceduralSimulator {

    private static final int PACKET_COUNT = 10_000;
    private static final long DEFAULT_SEED = 42; // Fixed seed for reproducible benchmarks

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
     * Runs the monolithic simulation loop with NO I/O inside the timed region,
     * so the measured duration reflects procedural processing cost only -- not
     * System.out overhead. A fresh Random is seeded on every call (rather than
     * reused from a shared static field), so the workload is reproducible on
     * every single invocation, not just the first one in a process.
     */
    public static Result runQuiet(long seed) {
        Random random = new Random(seed);

        int processedTcp = 0;
        int processedUdp = 0;
        int processedIcmp = 0;
        int droppedPackets = 0;

        long startTime = System.nanoTime();

        // Monolithic execution loop
        for (int i = 1; i <= PACKET_COUNT; i++) {
            String protocol = getRandomProtocol(random);
            int payloadSize = random.nextInt(1400) + 64; // 64 to 1464 bytes
            String sourceIp = "192.168.1." + random.nextInt(255);
            String destIp = "10.0.0." + random.nextInt(255);

            // Monolithic Firewall / Filtering Logic (Tightly coupled)
            if (destIp.endsWith(".100")) {
                droppedPackets++;
                continue; // Drop packet
            }

            // Monolithic Protocol Processing (Switch/If-Else branching)
            if ("TCP".equals(protocol)) {
                processedTcp++;
            } else if ("UDP".equals(protocol)) {
                processedUdp++;
            } else if ("ICMP".equals(protocol)) {
                processedIcmp++;
            }
        }

        long endTime = System.nanoTime();
        double durationMs = (endTime - startTime) / 1_000_000.0;

        return new Result(processedTcp, processedUdp, processedIcmp, droppedPackets, durationMs);
    }

    /**
     * Standalone CLI demo entry point. Uses the same quiet, I/O-free core loop
     * as the benchmark harness, then prints a summary once timing is complete.
     */
    public static void main(String[] args) {
        System.out.println("==================================================");
        System.out.println("  STARTING MODEL A: PROCEDURAL BASELINE SIMULATION ");
        System.out.println("==================================================");

        Result result = runQuiet(DEFAULT_SEED);

        System.out.println("\n--------------------------------------------------");
        System.out.println("  BASE RESULTS (MODEL A)");
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
