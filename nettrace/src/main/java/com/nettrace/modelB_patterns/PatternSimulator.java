package com.nettrace.modelB_patterns;

import com.nettrace.modelB_patterns.channel.NetworkChannel;
import com.nettrace.modelB_patterns.core.Packet;
import com.nettrace.modelB_patterns.core.PacketFactory;
import com.nettrace.modelB_patterns.factory.IcmpPacketFactory;
import com.nettrace.modelB_patterns.factory.TcpPacketFactory;
import com.nettrace.modelB_patterns.factory.UdpPacketFactory;
import com.nettrace.modelB_patterns.observer.MetricsCollector;
import com.nettrace.modelB_patterns.observer.TrafficLogger;

import java.util.Random;

/**
 * Model B: Pattern-Driven Architecture Simulation Engine
 * Demonstrates decoupled execution using Factory and Observer patterns.
 */
public class PatternSimulator {

    private static final int PACKET_COUNT = 10_000;
    private static final Random random = new Random(42); // Identical seed to Model A

    public static void main(String[] args) {
        System.out.println("==================================================");
        System.out.println("  STARTING MODEL B: PATTERN-DRIVEN SIMULATION     ");
        System.out.println("==================================================");

        // 1. Initialize Core Components & Observers
        NetworkChannel channel = new NetworkChannel();
        MetricsCollector metrics = new MetricsCollector();
        TrafficLogger logger = new TrafficLogger();

        channel.registerObserver(metrics);
        channel.registerObserver(logger);

        // 2. Initialize Factories
        PacketFactory tcpFactory = new TcpPacketFactory();
        PacketFactory udpFactory = new UdpPacketFactory();
        PacketFactory icmpFactory = new IcmpPacketFactory();

        long startTime = System.nanoTime();

        // 3. Execution Loop
        for (int i = 1; i <= PACKET_COUNT; i++) {
            String protocol = getRandomProtocol();
            int payloadSize = random.nextInt(1400) + 64;
            String sourceIp = "192.168.1." + random.nextInt(255);
            String destIp = "10.0.0." + random.nextInt(255);

            // Encapsulated Packet Creation via Factory
            Packet packet = switch (protocol) {
                case "TCP"  -> tcpFactory.createPacket(sourceIp, destIp, payloadSize);
                case "UDP"  -> udpFactory.createPacket(sourceIp, destIp, payloadSize);
                default     -> icmpFactory.createPacket(sourceIp, destIp, payloadSize);
            };

            // Event Dispatch through Network Channel
            channel.dispatchPacket(packet);
        }

        long endTime = System.nanoTime();
        double durationMs = (endTime - startTime) / 1_000_000.0;

        System.out.println("\n--------------------------------------------------");
        System.out.println("  PATTERN RESULTS (MODEL B)");
        System.out.println("--------------------------------------------------");
        System.out.println("Total Packets Sent : " + PACKET_COUNT);
        System.out.println("TCP Processed      : " + metrics.getTcpCount());
        System.out.println("UDP Processed      : " + metrics.getUdpCount());
        System.out.println("ICMP Processed     : " + metrics.getIcmpCount());
        System.out.println("Packets Dropped    : " + metrics.getDroppedCount());
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
}