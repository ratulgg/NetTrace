package com.nettrace.benchmark;

import com.nettrace.modelA_baseline.ProceduralSimulator;
import com.nettrace.modelB_patterns.PatternSimulator;

import java.util.ArrayList;
import java.util.List;

public class BenchmarkRunner {

    public static BenchmarkResult run(int warmupRuns, int measuredRuns, long seed) {
        // 1. Silent mode: Disable System.out logs to prevent I/O blocking
        ProceduralSimulator.setSilent(true);
        PatternSimulator.setSilent(true);

        // 2. JVM Warmup Phase: Force at least 25 runs to kill the 43ms JIT spike!
        int effectiveWarmup = Math.max(warmupRuns, 25); 
        for (int i = 0; i < effectiveWarmup; i++) {
            ProceduralSimulator.runWithSeed(seed, 2000);
            PatternSimulator.runWithSeed(seed, 2000);
        }

        List<Double> timesA = new ArrayList<>();
        List<Double> timesB = new ArrayList<>();
        double totalTimeA = 0;
        double totalTimeB = 0;

        // 3. Timed Measurement Phase
        for (int i = 0; i < measuredRuns; i++) {
            long startA = System.nanoTime();
            ProceduralSimulator.runWithSeed(seed, 2000);
            double msA = (System.nanoTime() - startA) / 1_000_000.0;
            timesA.add(msA);
            totalTimeA += msA;

            long startB = System.nanoTime();
            PatternSimulator.runWithSeed(seed, 2000);
            double msB = (System.nanoTime() - startB) / 1_000_000.0;
            timesB.add(msB);
            totalTimeB += msB;
        }

        // Re-enable console logging
        ProceduralSimulator.setSilent(false);
        PatternSimulator.setSilent(false);

        // Calculate final metrics
        double avgA = totalTimeA / measuredRuns;
        double avgB = totalTimeB / measuredRuns;
        double taxMs = avgB - avgA;
        double taxPercent = (taxMs / avgA) * 100.0;

        return new BenchmarkResult(avgA, avgB, taxMs, taxPercent, timesA, timesB);
    }

    // Matches NetTraceServer.java exactly!
    public record BenchmarkResult(
        double avgModelAMs, 
        double avgModelBMs, 
        double taxMs, 
        double taxPercent,
        List<Double> modelATimesMs,
        List<Double> modelBTimesMs
    ) {}
}