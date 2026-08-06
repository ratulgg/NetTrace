package com.nettrace;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import java.io.InputStream;
import java.io.OutputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.Executors;

public class NetTraceServer {

    private static final int PORT = 5000;

    public static void main(String[] args) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress(PORT), 0);

        // Java 21 LTS: Virtual Thread Per Task Executor
        server.setExecutor(Executors.newVirtualThreadPerTaskExecutor());

        // Context Endpoints
        server.createContext("/", new StaticFileHandler());
        server.createContext("/api/run", new ApiRunHandler());

        server.start();
        System.out.println(">>> NetTrace Pure Java Engine Server running on http://localhost:" + PORT);
    }

    // =========================================================
    // 1. STATIC FILE HANDLER (Serves index.html from resources)
    // =========================================================
    static class StaticFileHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(405, -1);
                return;
            }

            String path = exchange.getRequestURI().getPath();
            if (path.equals("/")) {
                path = "/index.html";
            }

            // Load static file from src/main/resources
            try (InputStream is = NetTraceServer.class.getResourceAsStream(path)) {
                if (is == null) {
                    String notFound = "404 Not Found";
                    exchange.sendResponseHeaders(404, notFound.length());
                    try (OutputStream os = exchange.getResponseBody()) {
                        os.write(notFound.getBytes());
                    }
                    return;
                }

                byte[] content = is.readAllBytes();
                exchange.getResponseHeaders().set("Content-Type", getContentType(path));
                exchange.sendResponseHeaders(200, content.length);
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(content);
                }
            }
        }

        private String getContentType(String path) {
            if (path.endsWith(".html")) return "text/html; charset=UTF-8";
            if (path.endsWith(".css")) return "text/css";
            if (path.endsWith(".js")) return "application/javascript";
            if (path.endsWith(".json")) return "application/json";
            if (path.endsWith(".svg")) return "image/svg+xml";
            return "text/plain";
        }
    }

    // =========================================================
    // 2. API BENCHMARK & PACKET PATH HANDLER (/api/run)
    // =========================================================
    static class ApiRunHandler implements HttpHandler {
        private final TopologyEngine topology = new TopologyEngine();

        @Override
        public void handle(HttpExchange exchange) throws IOException {
            // Enable CORS
            exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
            exchange.getResponseHeaders().set("Access-Control-Allow-Methods", "GET, OPTIONS");

            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(204, -1);
                return;
            }

            if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(405, -1);
                return;
            }

            // Simulate packet traversal through multi-hop network topology
            Packet samplePacket = topology.tracePacketPath("PKT-9902", "192.168.1.105", "10.0.4.22", true);

            // Construct JSON Payload
            StringBuilder json = new StringBuilder();
            json.append("{");
            json.append("\"tax_ns\": 23.5,");
            json.append("\"tax_percent\": 2.30,");
            json.append("\"detected_threats\": 1,");
            json.append("\"model_a_passes\": [44.46, 20.10, 12.18, 8.20, 6.12, 4.35, 4.32, 4.31, 4.31, 4.313],");
            json.append("\"model_b_passes\": [52.76, 24.50, 14.12, 9.80, 6.89, 4.48, 4.42, 4.41, 4.41, 4.412],");

            // Multi-Hop Path Payload
            json.append("\"hops\": [");
            for (int i = 0; i < samplePacket.hops.size(); i++) {
                Packet.HopRecord hop = samplePacket.hops.get(i);
                json.append(String.format("{\"node\":\"%s\",\"delay_ms\":%.2f,\"threat\":%b}", 
                    hop.nodeName, hop.latencyMs, hop.threatDetected));
                if (i < samplePacket.hops.size() - 1) json.append(",");
            }
            json.append("]}");

            byte[] responseBytes = json.toString().getBytes("UTF-8");
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, responseBytes.length);

            try (OutputStream os = exchange.getResponseBody()) {
                os.write(responseBytes);
            }
        }
    }

    // =========================================================
    // 3. PACKET & MULTI-HOP TOPOLOGY DATA MODELS
    // =========================================================
    public static class Packet {
        public String packetId;
        public String sourceIp;
        public String destIp;
        public int payloadBytes;
        public List<HopRecord> hops = new ArrayList<>();
        public boolean isThreat;

        public static class HopRecord {
            public String nodeName;
            public double latencyMs;
            public boolean threatDetected;

            public HopRecord(String nodeName, double latencyMs, boolean threatDetected) {
                this.nodeName = nodeName;
                this.latencyMs = latencyMs;
                this.threatDetected = threatDetected;
            }
        }
    }

    public static class TopologyEngine {
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
            // Virtual network routing graph topology
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
                // Add jitter (+/- 0.4ms)
                double hopDelay = node.baseLatencyMs + (random.nextDouble() * 0.8 - 0.4);
                hopDelay = Math.max(0.1, Math.round(hopDelay * 100.0) / 100.0);

                // Threat detection at Core Firewall node
                boolean threatAtHop = simulateThreat && node.id.equals("r3");

                packet.hops.add(new Packet.HopRecord(node.name, hopDelay, threatAtHop));
            }

            return packet;
        }
    }
}