package com.nettrace;

import com.nettrace.benchmark.BenchmarkRunner;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.Executors;

public class NetTraceServer {

    private static final int PORT = 5000;

    public static void main(String[] args) throws IOException {
        String envPort = System.getenv("PORT");
        int port = (envPort != null) ? Integer.parseInt(envPort) : 5000;

        HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);
        server.setExecutor(Executors.newVirtualThreadPerTaskExecutor());

        server.createContext("/", new StaticFileHandler());
        server.createContext("/api/run", new ApiRunHandler());
        server.createContext("/api/tests", new ApiTestsHandler());

        server.start();
        System.out.println(">>> NetTrace Engine running live on port " + port);
    }

    // Live Unit Test Runner Endpoint
    static class ApiTestsHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
            exchange.getResponseHeaders().set("Content-Type", "application/json");

            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(204, -1);
                return;
            }

            TopologyEngine topology = new TopologyEngine();
            SyntheticPacketStream stream = new SyntheticPacketStream();
            PacketQueue queue = new PacketQueue(16);

            boolean t1 = queue.processPacket(false).capacity == 16;
            boolean t2 = queue.processPacket(false).queueDelayMs >= 0.0;
            
            Packet p = topology.tracePacketPath("PKT-TEST", "192.168.1.1", "10.0.0.1", false);
            boolean t3 = p.hops.size() == 5;
            boolean t4 = p.hops.get(0).nodeName.equals("Client Gateway");
            
            Packet threatPkt = topology.tracePacketPath("PKT-ATTACK", "192.168.1.1", "10.0.0.1", true);
            boolean t5 = threatPkt.hops.get(2).threatDetected;
            
            List<SyntheticPacketStream.SyntheticPacket> batch = stream.generateStreamBatch(25, false, "10.0.0.1", "10.0.0.2");
            boolean t6 = batch.size() == 25;
            boolean t7 = batch.get(0).srcIp.equals("10.0.0.1");
            
            List<SyntheticPacketStream.SyntheticPacket> attackBatch = stream.generateStreamBatch(50, true, "10.0.0.1", "10.0.0.2");
            boolean t8 = attackBatch.stream().filter(pkt -> pkt.isThreat).count() > 0;

            boolean t9 = AiThreatClassifier.scorePacket("TCP", "ACK", 64, 8080, 443) < AiThreatClassifier.THREAT_THRESHOLD
                    && AiThreatClassifier.scorePacket("ICMP", "SYN", 1400, 62000, 31337) > AiThreatClassifier.THREAT_THRESHOLD;

            boolean[] results = {t1, t2, t3, t4, t5, t6, t7, t8, t9};
            String[] names = {
                "PacketQueue capacity boundary test",
                "Queue delay non-negativity check",
                "5-Hop sequential topology traversal",
                "Client Gateway ingress node check",
                "Core AI Firewall threat anomaly detection",
                "Batch stream generation size matching",
                "Packet header IP/Port integrity verification",
                "Attack mode elevated threat ratio test",
                "AiThreatClassifier benign/attack score separation"
            };

            int passedCount = 0;
            for (boolean r : results) if (r) passedCount++;
            boolean allPassed = passedCount == results.length;

            StringBuilder json = new StringBuilder();
            json.append("{");
            json.append(String.format("\"status\": \"%s\",", allPassed ? "PASSED" : "FAILED"));
            json.append(String.format("\"total_tests\": %d,", results.length));
            json.append(String.format("\"passed_tests\": %d,", passedCount));
            json.append("\"results\": [");
            for (int i = 0; i < results.length; i++) {
                json.append(String.format("{\"name\":\"%s\",\"passed\":%b}", names[i], results[i]));
                if (i < results.length - 1) json.append(",");
            }
            json.append("]}");

            byte[] resp = json.toString().getBytes("UTF-8");
            exchange.sendResponseHeaders(200, resp.length);
            try (OutputStream os = exchange.getResponseBody()) { os.write(resp); }
        }
    }

    // Static Resource Handler
    static class StaticFileHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(405, -1);
                return;
            }

            String path = exchange.getRequestURI().getPath();
            if (path.equals("/")) path = "/index.html";

            byte[] content = null;
            try (InputStream is = NetTraceServer.class.getResourceAsStream(path)) {
                if (is != null) content = is.readAllBytes();
            }
            if (content == null) {
                File file = new File("src/main/resources" + path);
                if (file.exists()) content = Files.readAllBytes(file.toPath());
            }
            if (content == null) {
                File file = new File("." + path);
                if (file.exists()) content = Files.readAllBytes(file.toPath());
            }

            if (content == null) {
                String notFound = "404 Not Found - Could not locate " + path;
                exchange.sendResponseHeaders(404, notFound.length());
                try (OutputStream os = exchange.getResponseBody()) { os.write(notFound.getBytes()); }
                return;
            }

            exchange.getResponseHeaders().set("Content-Type", getContentType(path));
            exchange.sendResponseHeaders(200, content.length);
            try (OutputStream os = exchange.getResponseBody()) { os.write(content); }
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

    // API Handler with Packet Queues & Telemetry
    static class ApiRunHandler implements HttpHandler {
        private final TopologyEngine topology = new TopologyEngine();
        private final SyntheticPacketStream streamGen = new SyntheticPacketStream();

        private static final int LIVE_WARMUP_RUNS = 200;
        private static final int LIVE_BENCHMARK_RUNS = 15;
        private static final long LIVE_BENCHMARK_SEED = 42;

        @Override
        public void handle(HttpExchange exchange) throws IOException {
            exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
            exchange.getResponseHeaders().set("Access-Control-Allow-Methods", "GET, OPTIONS");

            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(204, -1);
                return;
            }

            Map<String, String> query = parseQuery(exchange.getRequestURI().getQuery());
            String srcIp = query.getOrDefault("srcIp", "192.168.1.105");
            String dstIp = query.getOrDefault("dstIp", "10.0.4.22");
            boolean attackMode = "attack".equalsIgnoreCase(query.getOrDefault("mode", "normal"));
            int batchSize = Integer.parseInt(query.getOrDefault("batchSize", "10"));

            Packet samplePacket = topology.tracePacketPath("PKT-9902", srcIp, dstIp, attackMode);
            List<SyntheticPacketStream.SyntheticPacket> streamBatch = streamGen.generateStreamBatch(batchSize, attackMode, srcIp, dstIp);

            long threatsCount = streamBatch.stream().filter(p -> p.isThreat).count();

            BenchmarkRunner.BenchmarkResult benchmark =
                    BenchmarkRunner.run(LIVE_WARMUP_RUNS, LIVE_BENCHMARK_RUNS, LIVE_BENCHMARK_SEED);

            double taxNs = benchmark.taxMs * 1_000_000.0;
            double taxPercent = benchmark.taxPercent;

            // Real, live-measured ratio of Model A's average duration to Model B's,
            // from the SAME BenchmarkRunner pass as the tax figure above. The
            // topology view uses this to scale Model A's displayed hop delays --
            // replacing what used to be a hardcoded *0.92 guess on the client --
            // so "how much faster Model A looks" is driven by an actual
            // measurement of this request, not a fixed constant.
            double modelARatio = (benchmark.avgModelBMs > 0)
                    ? benchmark.avgModelAMs / benchmark.avgModelBMs
                    : 1.0;

            // Real, per-request throughput: sum this trace's 5 real hop
            // latencies (base latency + PacketQueue's queueDelayMs + jitter,
            // all already computed in tracePacketPath()) to get one packet's
            // actual simulated end-to-end transit time. Treat that transit
            // time as the time to clear one batch of batchSize packets
            // pipelined back-to-back through the path -- i.e. pps =
            // batchSize packets / (transitMs / 1000) seconds. This makes
            // throughput respond to batchSize directly, and to attackMode
            // indirectly through queueDelayMs (attack mode raises
            // PacketQueue's fillRatio, which raises queueDelayMs, which
            // raises transitMs, which lowers pps) -- the same direction a
            // real congested link would move in.
            double totalTransitMs = 0.0;
            for (Packet.HopRecord hop : samplePacket.hops) {
                totalTransitMs += hop.latencyMs;
            }
            double throughputPps = (batchSize * 1000.0) / totalTransitMs;

            // Real packet loss: run batchSize independent synthetic packets
            // through each of the 5 hops' actual PacketQueue fillRatio math
            // (PacketQueue.countOverflowDrops(), same distribution
            // processPacket() uses) and count how many hit an overflowed
            // queue somewhere on their path. Reported as
            // (total drops across all hops) / (batchSize * 5 hop-traversals)
            // * 100, since each of the batchSize packets crosses all 5
            // hops and can drop at any of them.
            int totalDrops = 0;
            for (TopologyEngine.RouterNode node : topology.getPath()) {
                totalDrops += node.queue.countOverflowDrops(batchSize, attackMode);
            }
            double packetLossPct = (totalDrops / (double) (batchSize * 5)) * 100.0;

            StringBuilder json = new StringBuilder();
            json.append("{");
            json.append(String.format("\"tax_ns\": %.1f,", taxNs));
            json.append(String.format("\"tax_percent\": %.2f,", taxPercent));
            json.append(String.format("\"model_a_ratio\": %.4f,", modelARatio));
            json.append(String.format("\"detected_threats\": %d,", threatsCount));
            json.append(String.format("\"throughput_pps\": %d,", Math.round(throughputPps)));
            json.append(String.format("\"packet_loss_pct\": %.2f,", packetLossPct));
            json.append(String.format("\"model_a_passes\": %s,", toJsonArray(lastN(benchmark.modelATimesMs, 10))));
            json.append(String.format("\"model_b_passes\": %s,", toJsonArray(lastN(benchmark.modelBTimesMs, 10))));

            json.append("\"hops\": [");
            for (int i = 0; i < samplePacket.hops.size(); i++) {
                Packet.HopRecord hop = samplePacket.hops.get(i);
                json.append(String.format("{\"node\":\"%s\",\"delay_ms\":%.2f,\"threat\":%b,\"queue_depth\":%d,\"queue_capacity\":%d,\"dropped\":%b}", 
                    hop.nodeName, hop.latencyMs, hop.threatDetected, hop.queueDepth, hop.queueCapacity, hop.wasDropped));
                if (i < samplePacket.hops.size() - 1) json.append(",");
            }
            json.append("],");

            json.append("\"packet_stream\": [");
            for (int i = 0; i < streamBatch.size(); i++) {
                SyntheticPacketStream.SyntheticPacket p = streamBatch.get(i);
                json.append(String.format(
                    "{\"id\":\"%s\",\"srcIp\":\"%s\",\"dstIp\":\"%s\",\"srcPort\":%d,\"dstPort\":%d,\"protocol\":\"%s\",\"flags\":\"%s\",\"size\":%d,\"threat\":%b,\"type\":\"%s\"}",
                    p.id, p.srcIp, p.dstIp, p.srcPort, p.dstPort, p.protocol, p.flags, p.sizeBytes, p.isThreat, p.attackType
                ));
                if (i < streamBatch.size() - 1) json.append(",");
            }
            json.append("]}");

            byte[] responseBytes = json.toString().getBytes("UTF-8");
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, responseBytes.length);

            try (OutputStream os = exchange.getResponseBody()) { os.write(responseBytes); }
        }

        private Map<String, String> parseQuery(String queryStr) {
            Map<String, String> map = new HashMap<>();
            if (queryStr == null || queryStr.isEmpty()) return map;
            for (String param : queryStr.split("&")) {
                String[] pair = param.split("=");
                if (pair.length > 1) map.put(pair[0], pair[1]);
            }
            return map;
        }

        private List<Double> lastN(List<Double> values, int n) {
            if (values.size() <= n) return values;
            return values.subList(values.size() - n, values.size());
        }

        private String toJsonArray(List<Double> values) {
            StringBuilder sb = new StringBuilder("[");
            for (int i = 0; i < values.size(); i++) {
                sb.append(String.format("%.3f", values.get(i)));
                if (i < values.size() - 1) sb.append(",");
            }
            sb.append("]");
            return sb.toString();
        }
    }

    public static class SyntheticPacketStream {
        public static class SyntheticPacket {
            public String id;
            public String srcIp;
            public String dstIp;
            public int srcPort;
            public int dstPort;
            public String protocol;
            public String flags;
            public int sizeBytes;
            public boolean isThreat;
            public String attackType;
        }

        private final Random rand = new Random();
        private static final int[] SUSPICIOUS_PORTS = {4444, 31337, 6667, 1337};

        public List<SyntheticPacket> generateStreamBatch(int count, boolean attackMode, String srcIp, String dstIp) {
            List<SyntheticPacket> batch = new ArrayList<>();
            String[] protocols = {"TCP", "UDP", "ICMP"};

            int payloadFloor = attackMode ? 500 : 64;
            int payloadRange = attackMode ? 964 : 736;
            double synProbability = attackMode ? 0.6 : 0.2;
            double suspiciousPortProbability = attackMode ? 0.45 : 0.0;
            double highSrcPortProbability = attackMode ? 0.55 : 0.25;

            for (int i = 1; i <= count; i++) {
                SyntheticPacket p = new SyntheticPacket();
                p.id = "PKT-" + (1000 + rand.nextInt(9000));
                p.srcIp = srcIp;
                p.dstIp = dstIp;

                p.srcPort = (rand.nextDouble() < highSrcPortProbability)
                        ? 60001 + rand.nextInt(4535)
                        : 1024 + rand.nextInt(58976);

                p.dstPort = (rand.nextDouble() < suspiciousPortProbability)
                        ? SUSPICIOUS_PORTS[rand.nextInt(SUSPICIOUS_PORTS.length)]
                        : (rand.nextBoolean() ? 80 : 443);

                p.protocol = protocols[rand.nextInt(protocols.length)];
                p.flags = p.protocol.equals("TCP") ? (rand.nextDouble() < synProbability ? "SYN" : "ACK") : "N/A";
                p.sizeBytes = payloadFloor + rand.nextInt(payloadRange);

                p.isThreat = AiThreatClassifier.isThreat(p.protocol, p.flags, p.sizeBytes, p.srcPort, p.dstPort);
                p.attackType = p.isThreat ? classifyAttackType(p) : "BENIGN";

                batch.add(p);
            }
            return batch;
        }

        private String classifyAttackType(SyntheticPacket p) {
            if ("SYN".equals(p.flags)) return "SYN Flood";
            if (p.dstPort == 4444 || p.dstPort == 31337 || p.dstPort == 6667 || p.dstPort == 1337) return "C2 Beacon";
            if (p.sizeBytes > 1100) return "DDoS Payload";
            if ("TCP".equals(p.protocol)) return "SQLi Injection";
            return "Port Scan";
        }
    }
}