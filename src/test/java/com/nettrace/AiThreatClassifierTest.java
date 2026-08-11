package com.nettrace;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("AiThreatClassifier — Logistic Regression Inference Tests")
class AiThreatClassifierTest {

    @Test
    @DisplayName("Should score a clean, small, ACK-flagged TCP packet as benign")
    void testBenignPacketScoresLow() {
        double score = AiThreatClassifier.scorePacket("TCP", "ACK", 64, 8080, 443);
        assertTrue(score < AiThreatClassifier.THREAT_THRESHOLD,
                "Small benign-looking packet should score below the threat threshold, got " + score);
        assertFalse(AiThreatClassifier.isThreat("TCP", "ACK", 64, 8080, 443));
    }

    @Test
    @DisplayName("Should score a large SYN packet on a suspicious port from a high source port as a threat")
    void testAttackLikePacketScoresHigh() {
        double score = AiThreatClassifier.scorePacket("ICMP", "SYN", 1400, 62000, 31337);
        assertTrue(score > AiThreatClassifier.THREAT_THRESHOLD,
                "Large multi-signal packet should score above the threat threshold, got " + score);
        assertTrue(AiThreatClassifier.isThreat("ICMP", "SYN", 1400, 62000, 31337));
    }

    @Test
    @DisplayName("Score should always be a valid probability in [0, 1]")
    void testScoreIsValidProbability() {
        double score1 = AiThreatClassifier.scorePacket("UDP", "N/A", 512, 33000, 8080);
        double score2 = AiThreatClassifier.scorePacket("TCP", "SYN", 1464, 65000, 4444);

        assertTrue(score1 >= 0.0 && score1 <= 1.0);
        assertTrue(score2 >= 0.0 && score2 <= 1.0);
    }

    @Test
    @DisplayName("Adding more suspicious signals to the same packet should never decrease its threat score")
    void testMoreSuspiciousSignalsIncreaseScore() {
        double base = AiThreatClassifier.scorePacket("TCP", "ACK", 500, 20000, 80);
        double withSyn = AiThreatClassifier.scorePacket("TCP", "SYN", 500, 20000, 80);
        double withSynAndPort = AiThreatClassifier.scorePacket("TCP", "SYN", 500, 20000, 4444);
        double withEverything = AiThreatClassifier.scorePacket("ICMP", "SYN", 1400, 62000, 4444);

        assertTrue(withSyn >= base, "Adding a SYN flag should not lower the score");
        assertTrue(withSynAndPort >= withSyn, "Adding a suspicious port should not lower the score");
        assertTrue(withEverything >= withSynAndPort, "Adding more signals should not lower the score");
    }
}
