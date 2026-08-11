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
