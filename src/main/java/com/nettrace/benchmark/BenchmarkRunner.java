package com.nettrace.benchmark;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class BenchmarkRunner {

    public static BenchmarkResult run(int warmupRuns, int measuredRuns, long seed) {
        // 1. JVM Warmup Phase
        int effectiveWarmup = Math.max(warmupRuns, 25); 
        for (int i = 0; i < effectiveWarmup; i++) {
            runProceduralSimulation(seed);
            runPatternSimulation(seed);
        }

        List<Double> timesA = new ArrayList<>();
        List<Double> timesB = new ArrayList<>();

        // 2. Timed Measurement Phase
        for (int i = 0; i < measuredRuns; i++) {
            long startA = System.nanoTime();
            runProceduralSimulation(seed + i);
            double msA = (System.nanoTime() - startA) / 1_000_000.0;
            timesA.add(msA);

            long startB = System.nanoTime();
            runPatternSimulation(seed + i);
            double msB = (System.nanoTime() - startB) / 1_000_000.0;
            timesB.add(msB);
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

    private static void runProceduralSimulation(long seed) {
        Random rand = new Random(seed);
        double val = 0;
        for (int i = 0; i < 2000; i++) {
            val += Math.sin(rand.nextDouble() * i);
        }
    }

    private static void runPatternSimulation(long seed) {
        Random rand = new Random(seed);
        SimulationContext ctx = new SimulationContext(rand);
        for (int i = 0; i < 2000; i++) {
            ctx.processStep(i);
        }
    }

    private static class SimulationContext {
        private final Random rand;
        private double accumulator = 0;

        public SimulationContext(Random rand) {
            this.rand = rand;
        }

        public void processStep(int i) {
            accumulator += Math.sin(rand.nextDouble() * i);
        }
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

        // 2-argument constructor used by PatternEngineTest.java
        public BenchmarkResult(List<Double> modelATimesMs, List<Double> modelBTimesMs) {
            this.modelATimesMs = modelATimesMs;
            this.modelBTimesMs = modelBTimesMs;
            this.avgModelAMs = 0.0;
            this.avgModelBMs = 0.0;
            this.taxMs = 0.0;
            this.taxPercent = 0.0;
        }
    }
}