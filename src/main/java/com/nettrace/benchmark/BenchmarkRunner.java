package com.nettrace.benchmark;

import com.nettrace.modelA_baseline.ProceduralSimulator;
import com.nettrace.modelB_patterns.PatternSimulator;

import java.util.ArrayList;
import java.util.List;

public class BenchmarkRunner {

    public static BenchmarkResult run(int warmupRuns, int measuredRuns, long seed) {
        // 1. JVM Warmup Phase
        int effectiveWarmup = Math.max(warmupRuns, 25); 
        for (int i = 0; i < effectiveWarmup; i++) {
            ProceduralSimulator.run();
            PatternSimulator.run();
        }

        List<Double> timesA = new ArrayList<>();
        List<Double> timesB = new ArrayList<>();

        // 2. Timed Measurement Phase
        for (int i = 0; i < measuredRuns; i++) {
            long startA = System.nanoTime();
            ProceduralSimulator.run();
            double msA = (System.nanoTime() - startA) / 1_000_000.0;
            timesA.add(msA);

            long startB = System.nanoTime();
            PatternSimulator.run();
            double msB = (System.nanoTime() - startB) / 1_000_000.0;
            timesB.add(msB);
        }

        // 3. Outlier Rejection for Cloud Deployments
        List<Double> sortedA = new ArrayList<>(timesA);
        List<Double> sortedB = new ArrayList<>(timesB);
        sortedA.sort(Double::compareTo);
        sortedB.sort(Double::compareTo);

        if (sortedA.size() > 4) {
            sortedA = sortedA.subList(1, sortedA.size() - 3);
        }
        if (sortedB.size() > 4) {
            sortedB = sortedB.subList(1, sortedB.size() - 3);
        }

        double sumA = 0, sumB = 0;
        for (double a : sortedA) sumA += a;
        for (double b : sortedB) sumB += b;
        
        double avgA = sumA / sortedA.size();
        double avgB = sumB / sortedB.size();
        
        double taxMs = avgB - avgA;
        double taxPercent = (taxMs / avgA) * 100.0;

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