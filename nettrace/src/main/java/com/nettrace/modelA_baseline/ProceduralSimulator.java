package com.nettrace.modelA_baseline;

import java.util.Random;

/**
 * Model A: Procedural Baseline Simulation Engine
 * Represents a monolithic, tightly-coupled implementation.
 */
public class ProceduralSimulator {

    private static final int PACKET_COUNT = 10_000;
    private static final Random random = new Random(42); // Fixed seed for reproducible benchmarks

    public static void main(String[] args) {
        System.out.println("==================================================");
        System.out.println("  STARTING MODEL A: PROCEDURAL BASELINE SIMULATION ");
        System.out.println("==================================================");

        long startTime = System.nanoTime();

        int processedTcp = 0;
        int processedUdp = 0;
        int processedIcmp = 0;
        int droppedPackets = 0;

        // Monolithic execution loop
        for (int i = 1; i <= PACKET_COUNT; i++) {
            String protocol = getRandomProtocol();
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
                // Hardcoded TCP logic
                processedTcp++;
                logTraffic("TCP", i, sourceIp, destIp, payloadSize, "ACK_RECEIVED");
            } else if ("UDP".equals(protocol)) {
                // Hardcoded UDP logic
                processedUdp++;
                logTraffic("UDP", i, sourceIp, destIp, payloadSize, "NO_HANDSHAKE");
            } else if ("ICMP".equals(protocol)) {
                // Hardcoded ICMP logic
                processedIcmp++;
                logTraffic("ICMP", i, sourceIp, destIp, payloadSize, "ECHO_REQUEST");
            }
        }

        long endTime = System.nanoTime();
        double durationMs = (endTime - startTime) / 1_000_000.0;

        System.out.println("\n--------------------------------------------------");
        System.out.println("  BASE RESULTS (MODEL A)");
        System.out.println("--------------------------------------------------");
        System.out.println("Total Packets Sent : " + PACKET_COUNT);
        System.out.println("TCP Processed      : " + processedTcp);
        System.out.println("UDP Processed      : " + processedUdp);
        System.out.println("ICMP Processed     : " + processedIcmp);
        System.out.println("Packets Dropped    : " + droppedPackets);
        System.out.println("Execution Time     : " + String.format("%.3f", durationMs) + " ms");
        System.out.println("==================================================");
    }

    private static String getRandomProtocol() {
        int choice = random.nextInt(3);
        return switch (choice) {
            case 0 -> "TCP";
            case 1 -> "UDP";
            default -> "ICMP";
        };
    }

    private static void logTraffic(String proto, int id, String src, String dst, int size, String flag) {
        // Simulates logging overhead inside the procedural loop
        if (id % 2500 == 0) { // Sample output to keep terminal clean
            System.out.printf("[LOG #%d] Protocol: %-4s | %s -> %s | Size: %d bytes | Flag: %s%n",
                    id, proto, src, dst, size, flag);
        }
    }
}