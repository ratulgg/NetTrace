package com.nettrace.benchmark;

import com.nettrace.modelA_baseline.ProceduralSimulator;
import com.nettrace.modelB_patterns.PatternSimulator;

import java.util.ArrayList;
import java.util.List;

/**
 * Automated Benchmark Harness to execute warmups and calculate 
 * mean latency and variance across multiple simulation runs.
 */
public class BenchmarkRunner {

    private static final int WARMUP_RUNS = 10;
    private static final int BENCHMARK_RUNS = 50;

    public static void main(String[] args) {
        System.out.println("==================================================");
        System.out.println("  STARTING AUTOMATED BENCHMARK SUITE");
        System.out.println("==================================================");

        // 1. JVM Warmup Phase
        System.out.println("\n[1/3] Executing JVM Warmup Phase (" + WARMUP_RUNS + " runs)...");
        for (int i = 0; i < WARMUP_RUNS; i++) {
            runModelASilently();
            runModelBSilently();
        }
        System.out.println("-> Warmup complete. JIT compiler optimized.");

        // 2. Model A Benchmark
        System.out.println("\n[2/3] Benchmarking Model A (Procedural) over " + BENCHMARK_RUNS + " iterations...");
        List<Double> modelATimes = new ArrayList<>();
        for (int i = 0; i < BENCHMARK_RUNS; i++) {
            long start = System.nanoTime();
            runModelASilently();
            long end = System.nanoTime();
            modelATimes.add((end - start) / 1_000_000.0);
        }

        // 3. Model B Benchmark
        System.out.println("\n[3/3] Benchmarking Model B (Patterns) over " + BENCHMARK_RUNS + " iterations...");
        List<Double> modelBTimes = new ArrayList<>();
        for (int i = 0; i < BENCHMARK_RUNS; i++) {
            long start = System.nanoTime();
            runModelBSilently();
            long end = System.nanoTime();
            modelBTimes.add((end - start) / 1_000_000.0);
        }

        // Output Results
        double avgA = calculateAverage(modelATimes);
        double avgB = calculateAverage(modelBTimes);

        System.out.println("\n==================================================");
        System.out.println("  FINAL BENCHMARK SUMMARY (" + BENCHMARK_RUNS + " RUNS)");
        System.out.println("==================================================");
        System.out.printf("Model A (Procedural) Avg Latency : %.3f ms%n", avgA);
        System.out.printf("Model B (Patterns)   Avg Latency : %.3f ms%n", avgB);
        System.out.printf("Pattern Overhead                : +%.3f ms (%.2f%%)%n", 
                (avgB - avgA), ((avgB - avgA) / avgA) * 100);
        System.out.println("==================================================");
    }

    private static void runModelASilently() {
        // Calls execution logic
        ProceduralSimulator.main(new String[]{});
    }

    private static void runModelBSilently() {
        // Calls execution logic
        PatternSimulator.main(new String[]{});
    }

    private static double calculateAverage(List<Double> values) {
        return values.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
    }
}