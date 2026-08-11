package com.nettrace.benchmark;

import com.nettrace.modelA_baseline.ProceduralSimulator;
import com.nettrace.modelB_patterns.PatternSimulator;

import java.util.ArrayList;
import java.util.List;

public class BenchmarkRunner {

    public static BenchmarkResult run(int warmupRuns, int measuredRuns, long seed) {
        // 1. JVM Warmup Phase -- run the REAL Model A / Model B engines so the
        // JIT compiles the actual Factory + Observer dispatch path, not a
        // throwaway stand-in loop.
        int effectiveWarmup = Math.max(warmupRuns, 25);
        for (int i = 0; i < effectiveWarmup; i++) {
            ProceduralSimulator.runQuiet(seed);
            PatternSimulator.runQuiet(seed);
        }

        List<Double> timesA = new ArrayList<>();
        List<Double> timesB = new ArrayList<>();

        // 2. Timed Measurement Phase -- each engine times itself internally
        // (see ProceduralSimulator/PatternSimulator.runQuiet), so the
        // "Observer Overhead" reflects Factory/Observer dispatch cost only.
        for (int i = 0; i < measuredRuns; i++) {
            timesA.add(ProceduralSimulator.runQuiet(seed + i).durationMs);
            timesB.add(PatternSimulator.runQuiet(seed + i).durationMs);
        }

        // 3. Outlier Rejection for Cloud Deployments (Trimmed Mean)
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

    public static class BenchmarkResult {
        public double avgModelAMs;
        public double avgModelBMs;
        public double taxMs;
        public double taxPercent;
        public List<Double> modelATimesMs;
        public List<Double> modelBTimesMs;

        // 6-argument constructor used by BenchmarkRunner.run()
        public BenchmarkResult(double avgModelAMs, double avgModelBMs, double taxMs, double taxPercent,
                               List<Double> modelATimesMs, List<Double> modelBTimesMs) {
            this.avgModelAMs = avgModelAMs;
            this.avgModelBMs = avgModelBMs;
            this.taxMs = taxMs;
            this.taxPercent = taxPercent;
            this.modelATimesMs = modelATimesMs;
            this.modelBTimesMs = modelBTimesMs;
        }

        // 2-argument constructor used by PatternEngineTest.java -- derives
        // averages/tax from the raw timing lists instead of leaving them at 0.
        public BenchmarkResult(List<Double> modelATimesMs, List<Double> modelBTimesMs) {
            this.modelATimesMs = modelATimesMs;
            this.modelBTimesMs = modelBTimesMs;
            this.avgModelAMs = average(modelATimesMs);
            this.avgModelBMs = average(modelBTimesMs);
            this.taxMs = this.avgModelBMs - this.avgModelAMs;
            this.taxPercent = (this.avgModelAMs == 0.0) ? 0.0 : (this.taxMs / this.avgModelAMs) * 100.0;
        }

        private static double average(List<Double> values) {
            if (values == null || values.isEmpty()) return 0.0;
            double sum = 0.0;
            for (double v : values) sum += v;
            return sum / values.size();
        }

        // Accessor methods -- kept alongside the public fields above so both
        // field access (NetTraceServer) and method-call access (PatternEngineTest)
        // work against the same object.
        public double avgModelAMs() { return avgModelAMs; }
        public double avgModelBMs() { return avgModelBMs; }
        public double taxMs() { return taxMs; }
        public double taxPercent() { return taxPercent; }
    }
}