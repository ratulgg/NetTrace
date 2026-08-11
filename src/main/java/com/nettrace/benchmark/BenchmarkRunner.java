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

        // 2. JVM Warmup Phase: Force at least 25 runs (though NetTraceServer uses 200 now)
        int effectiveWarmup = Math.max(warmupRuns, 25); 
        for (int i = 0; i < effectiveWarmup; i++) {
            ProceduralSimulator.runWithSeed(seed, 2000);
            PatternSimulator.runWithSeed(seed, 2000);
        }

        List<Double> timesA = new ArrayList<>();
        List<Double> timesB = new ArrayList<>();

        // 3. Timed Measurement Phase
        for (int i = 0; i < measuredRuns; i++) {
            long startA = System.nanoTime();
            ProceduralSimulator.runWithSeed(seed, 2000);
            double msA = (System.nanoTime() - startA) / 1_000_000.0;
            timesA.add(msA);

            long startB = System.nanoTime();
            PatternSimulator.runWithSeed(seed, 2000);
            double msB = (System.nanoTime() - startB) / 1_000_000.0;
            timesB.add(msB);
        }

        // Re-enable console logging
        ProceduralSimulator.setSilent(false);
        PatternSimulator.setSilent(false);

        // --- OUTLIER REJECTION FOR CLOUD DEPLOYMENTS ---
        // Create isolated copies so the frontend JSON order is preserved
        List<Double> sortedA = new ArrayList<>(timesA);
        List<Double> sortedB = new ArrayList<>(timesB);
        sortedA.sort(Double::compareTo);
        sortedB.sort(Double::compareTo);

        // Remove the massive JIT spike (the highest value) from both lists
        if (sortedA.size() > 2) sortedA.remove(sortedA.size() - 1);
        if (sortedB.size() > 2) sortedB.remove(sortedB.size() - 1);

        // Calculate the smooth averages ignoring the spikes
        double sumA = 0, sumB = 0;
        for (double a : sortedA) sumA += a;
        for (double b : sortedB) sumB += b;
        
        double avgA = sumA / sortedA.size();
        double avgB = sumB / sortedB.size();
        
        double taxMs = avgB - avgA;
        double taxPercent = (taxMs / avgA) * 100.0;
        // ------------------------------------------------

        return new BenchmarkResult(avgA, avgB, taxMs, taxPercent, timesA, timesB);
    }

    public record BenchmarkResult(
        double avgModelAMs, 
        double avgModelBMs, 
        double taxMs, 
        double taxPercent,
        List<Double> modelATimesMs,
        List<Double> modelBTimesMs
    ) {}
}