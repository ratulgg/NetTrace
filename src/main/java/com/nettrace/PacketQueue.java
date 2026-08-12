package com.nettrace;

import java.util.Random;

public class PacketQueue {
    private final int capacity;
    private int currentDepth;
    private final Random rand = new Random();

    public PacketQueue(int capacity) {
        this.capacity = capacity;
        this.currentDepth = 0;
    }

    public synchronized QueueResult processPacket(boolean attackMode) {
        // Queue depth increases under attack mode
        double fillRatio = attackMode ? (0.65 + rand.nextDouble() * 0.35) : (0.10 + rand.nextDouble() * 0.30);
        this.currentDepth = (int) Math.round(this.capacity * fillRatio);

        boolean overflowDrop = this.currentDepth >= this.capacity;
        double queueDelayMs = (this.currentDepth / (double) this.capacity) * 2.5; // Backpressure queuing delay

        return new QueueResult(this.currentDepth, this.capacity, overflowDrop, queueDelayMs);
    }

    /**
     * Runs {@code batchSize} independent synthetic packets through this
     * node's queue (same fillRatio/overflowDrop math as processPacket(),
     * called once per packet instead of once per hop) and returns how many
     * of them hit an overflowed queue. This is what backs packet_loss_pct:
     * a per-packet drop count across the actual batch size the client
     * requested, rather than a per-hop count that ignores batchSize
     * entirely and produces only a 0/20/40/60/80/100% step function across
     * this topology's 5 hops.
     *
     * Note this does NOT mutate this.currentDepth/QueueResult state used by
     * processPacket() -- it's a separate statistical sample over the same
     * distribution, used only for the aggregate loss estimate.
     */
    public synchronized int countOverflowDrops(int batchSize, boolean attackMode) {
        int drops = 0;
        for (int i = 0; i < batchSize; i++) {
            double fillRatio = attackMode ? (0.65 + rand.nextDouble() * 0.35) : (0.10 + rand.nextDouble() * 0.30);
            int depth = (int) Math.round(this.capacity * fillRatio);
            if (depth >= this.capacity) drops++;
        }
        return drops;
    }

    public static class QueueResult {
        public int depth;
        public int capacity;
        public boolean overflowDrop;
        public double queueDelayMs;

        public QueueResult(int depth, int capacity, boolean overflowDrop, double queueDelayMs) {
            this.depth = depth;
            this.capacity = capacity;
            this.overflowDrop = overflowDrop;
            this.queueDelayMs = queueDelayMs;
        }
    }
}
