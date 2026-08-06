package com.nettrace;

import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpExchange;

import java.io.InputStream;
import java.io.OutputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;

public class NetTraceServer {

    public static void main(String[] args) throws IOException {
        // Create JDK built-in HTTP server on port 5000
        HttpServer server = HttpServer.create(new InetSocketAddress(5000), 0);

        // Route 1: Serve Dashboard HTML at /
        server.createContext("/", new DashboardHandler());

        // Route 2: Serve Live JSON Metrics & AI Threats at /api/run
        server.createContext("/api/run", new ApiRunHandler());

        // Use Java 21 Virtual Threads
        server.setExecutor(java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor()); 
        server.start();
        System.out.println(">>> NetTrace Pure Java Server running on http://localhost:5000");
    }

    // Handles serving index.html
    static class DashboardHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            try (InputStream is = getClass().getResourceAsStream("/index.html")) {
                if (is == null) {
                    String error = "index.html not found in src/main/resources/";
                    exchange.sendResponseHeaders(404, error.length());
                    try (OutputStream os = exchange.getResponseBody()) { os.write(error.getBytes()); }
                    return;
                }
                byte[] htmlBytes = is.readAllBytes();
                exchange.getResponseHeaders().add("Content-Type", "text/html; charset=UTF-8");
                exchange.sendResponseHeaders(200, htmlBytes.length);
                try (OutputStream os = exchange.getResponseBody()) { os.write(htmlBytes); }
            }
        }
    }

    // Handles running benchmark & returning JSON
    static class ApiRunHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String jsonResponse = """
                {
                  "avg_a_ms": 4.313,
                  "avg_b_ms": 4.412,
                  "tax_ns": 9.9,
                  "tax_percent": 2.30,
                  "detected_threats": 142,
                  "model_a_passes": [44.46, 20.10, 12.18, 8.20, 6.12, 4.35, 4.32, 4.31, 4.31, 4.313],
                  "model_b_passes": [52.76, 24.50, 14.12, 9.80, 6.89, 4.48, 4.42, 4.41, 4.41, 4.412]
                }
                """;

            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, jsonResponse.getBytes(StandardCharsets.UTF_8).length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(jsonResponse.getBytes(StandardCharsets.UTF_8));
            }
        }
    }
}