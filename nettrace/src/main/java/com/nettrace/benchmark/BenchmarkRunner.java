package com.nettrace.benchmark;

import com.nettrace.modelA_baseline.ProceduralSimulator;
import com.nettrace.modelB_patterns.PatternSimulator;

import java.util.ArrayList;
import java.util.List;

/**
 * Automated Benchmark Harness to execute warmups and calculate
 * mean latency and variance across multiple simulation runs.
 *
 * Both models are driven through their {@code runQuiet(seed)} entry points,
 * which perform zero console I/O inside the timed region and reseed
 * {@code Random} on every single call. That means the measured "abstraction
 * tax" reflects Factory/Observer dispatch cost, not print-stream overhead,
 * and every run -- not just the first -- is reproducible from the same seed.
 */
public class BenchmarkRunner {

    private static final int WARMUP_RUNS = 10;
    private static final int BENCHMARK_RUNS = 50;
    private static final long SEED = 42;

    public static void main(String[] args) {
        BenchmarkResult result = run(WARMUP_RUNS, BENCHMARK_RUNS, SEED);
        printReport(result, BENCHMARK_RUNS);
    }

    /**
     * Runs {@code warmupRuns} untimed passes (to trigger JIT compilation)
     * followed by {@code benchmarkRuns} timed passes of each model, all seeded
     * with {@code seed}. Safe to call from a live request handler: with the
     * default 10k-packet workload each pass runs in low single-digit
     * milliseconds once warmed up.
     */
    public static BenchmarkResult run(int warmupRuns, int benchmarkRuns, long seed) {
        for (int i = 0; i < warmupRuns; i++) {
            ProceduralSimulator.runQuiet(seed);
            PatternSimulator.runQuiet(seed);
        }

        List<Double> modelATimes = new ArrayList<>();
        for (int i = 0; i < benchmarkRuns; i++) {
            modelATimes.add(ProceduralSimulator.runQuiet(seed).durationMs);
        }

        List<Double> modelBTimes = new ArrayList<>();
        for (int i = 0; i < benchmarkRuns; i++) {
            modelBTimes.add(PatternSimulator.runQuiet(seed).durationMs);
        }

        return new BenchmarkResult(modelATimes, modelBTimes);
    }

    private static void printReport(BenchmarkResult result, int benchmarkRuns) {
        double avgA = result.avgModelAMs();
        double avgB = result.avgModelBMs();

        System.out.println("==================================================");
        System.out.println("  FINAL BENCHMARK SUMMARY (" + benchmarkRuns + " RUNS)");
        System.out.println("==================================================");
        System.out.printf("Model A (Procedural) Avg Latency : %.3f ms%n", avgA);
        System.out.printf("Model B (Patterns)   Avg Latency : %.3f ms%n", avgB);
        System.out.printf("Pattern Overhead                 : +%.3f ms (%.2f%%)%n",
                result.taxMs(), result.taxPercent());
        System.out.println("==================================================");
    }

    /**
     * Holds the raw per-run timings for both models plus derived averages, so
     * callers (CLI report, live API) can compute whatever summary they need
     * from the same real measurements.
     */
    public static class BenchmarkResult {
        public final List<Double> modelATimesMs;
        public final List<Double> modelBTimesMs;

        public BenchmarkResult(List<Double> modelATimesMs, List<Double> modelBTimesMs) {
            this.modelATimesMs = modelATimesMs;
            this.modelBTimesMs = modelBTimesMs;
        }

        public double avgModelAMs() {
            return average(modelATimesMs);
        }

        public double avgModelBMs() {
            return average(modelBTimesMs);
        }

        public double taxMs() {
            return avgModelBMs() - avgModelAMs();
        }

        public double taxPercent() {
            double avgA = avgModelAMs();
            return avgA == 0.0 ? 0.0 : (taxMs() / avgA) * 100.0;
        }

        private static double average(List<Double> values) {
            return values.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
        }
    }
}
