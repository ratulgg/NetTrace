package com.nettrace.routing;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("A* Dynamic Routing Tests")
class AStarRouterTest {

    private NetworkGraph graph;

    @BeforeEach
    void setUp() {
        graph = new NetworkGraph();
    }

    /** Builds a deterministic cost map (no PacketQueue randomness) using
     *  base latency only, for tests that need reproducible costs. */
    private Map<NetworkGraph.Edge, Double> baseCostSnapshot() {
        Map<NetworkGraph.Edge, Double> costs = new HashMap<>();
        for (String node : graph.nodeIds()) {
            for (NetworkGraph.Edge edge : graph.neighbors(node)) {
                costs.put(edge, edge.baseLatencyMs());
            }
        }
        return costs;
    }

    @Nested
    @DisplayName("Basic reachability")
    class Reachability {

        @Test
        @DisplayName("Finds a path from GATEWAY to TARGET")
        void findsAPath() {
            AStarRouter.RouteResult result =
                    AStarRouter.findPath(graph, baseCostSnapshot(), NetworkGraph.GATEWAY, NetworkGraph.TARGET);

            assertEquals(NetworkGraph.GATEWAY, result.path().get(0));
            assertEquals(NetworkGraph.TARGET, result.path().get(result.path().size() - 1));
            assertTrue(result.nodesExpanded() > 0);
            assertEquals(result.path().size() - 1, result.edgeCosts().size());
        }

        @Test
        @DisplayName("Total cost equals the sum of the returned edge costs")
        void totalCostMatchesEdgeCostSum() {
            AStarRouter.RouteResult result =
                    AStarRouter.findPath(graph, baseCostSnapshot(), NetworkGraph.GATEWAY, NetworkGraph.TARGET);

            double sum = result.edgeCosts().stream().mapToDouble(Double::doubleValue).sum();
            assertEquals(sum, result.totalCostMs(), 1e-9);
        }
    }

    @Nested
    @DisplayName("Optimality (brute-force cross-check)")
    class Optimality {

        /** Every simple GATEWAY->TARGET path in the graph, enumerated by hand
         *  from NetworkGraph's fixed edge list, so this test doesn't rely on
         *  AStarRouter or any other pathfinding code to determine "what the
         *  candidates even are." */
        private List<List<String>> allSimplePaths() {
            return List.of(
                    List.of("GATEWAY", "R1", "FW", "EGRESS", "TARGET"),
                    List.of("GATEWAY", "R2", "FW", "EGRESS", "TARGET"),
                    List.of("GATEWAY", "R1", "EGRESS", "TARGET") // bypass, skips FW
            );
        }

        private double costOfPath(List<String> nodeIds, Map<NetworkGraph.Edge, Double> costs) {
            double total = 0;
            for (int i = 0; i < nodeIds.size() - 1; i++) {
                String from = nodeIds.get(i);
                String to = nodeIds.get(i + 1);
                NetworkGraph.Edge edge = graph.neighbors(from).stream()
                        .filter(e -> e.to().equals(to))
                        .findFirst()
                        .orElseThrow(() -> new IllegalStateException("No edge " + from + "->" + to));
                total += costs.get(edge);
            }
            return total;
        }

        @Test
        @DisplayName("A* finds the true minimum-cost path under uniform (base-latency-only) costs")
        void findsTrueMinimumUnderBaseCosts() {
            Map<NetworkGraph.Edge, Double> costs = baseCostSnapshot();
            double bestBruteForce = allSimplePaths().stream()
                    .mapToDouble(p -> costOfPath(p, costs))
                    .min().orElseThrow();

            AStarRouter.RouteResult result = AStarRouter.findPath(graph, costs, NetworkGraph.GATEWAY, NetworkGraph.TARGET);

            assertEquals(bestBruteForce, result.totalCostMs(), 1e-9,
                    "A* should find the same minimum a brute-force scan of every candidate path finds");
        }

        @Test
        @DisplayName("A* finds the true minimum-cost path under skewed (congested) costs")
        void findsTrueMinimumUnderSkewedCosts() {
            Map<NetworkGraph.Edge, Double> costs = baseCostSnapshot();
            // Make the R2->FW edge (normally the cheapest way into the firewall)
            // artificially congested, and confirm A* still finds whatever the
            // true minimum is across ALL three candidate paths, not just the
            // one that used to be cheapest.
            for (String node : graph.nodeIds()) {
                for (NetworkGraph.Edge edge : graph.neighbors(node)) {
                    if (edge.from().equals("R2") && edge.to().equals(NetworkGraph.FIREWALL_NODE)) {
                        costs.put(edge, 999.0);
                    }
                }
            }

            double bestBruteForce = allSimplePaths().stream()
                    .mapToDouble(p -> costOfPath(p, costs))
                    .min().orElseThrow();

            AStarRouter.RouteResult result = AStarRouter.findPath(graph, costs, NetworkGraph.GATEWAY, NetworkGraph.TARGET);

            assertEquals(bestBruteForce, result.totalCostMs(), 1e-9);
        }
    }

    @Nested
    @DisplayName("Congestion-responsiveness (the 'dynamic' in dynamic routing)")
    class CongestionResponsiveness {

        @Test
        @DisplayName("Route changes when the currently-chosen path becomes congested")
        void routeChangesUnderCongestion() {
            Map<NetworkGraph.Edge, Double> lightCosts = baseCostSnapshot();
            AStarRouter.RouteResult uncongested =
                    AStarRouter.findPath(graph, lightCosts, NetworkGraph.GATEWAY, NetworkGraph.TARGET);

            // Congest every edge used by the uncongested route's first hop
            // out of GATEWAY, forcing A* to prefer a different first hop.
            String firstHopTaken = uncongested.path().get(1);
            Map<NetworkGraph.Edge, Double> congestedCosts = new HashMap<>(lightCosts);
            for (NetworkGraph.Edge edge : graph.neighbors(NetworkGraph.GATEWAY)) {
                if (edge.to().equals(firstHopTaken)) {
                    congestedCosts.put(edge, 500.0);
                }
            }

            AStarRouter.RouteResult congested =
                    AStarRouter.findPath(graph, congestedCosts, NetworkGraph.GATEWAY, NetworkGraph.TARGET);

            assertNotEquals(firstHopTaken, congested.path().get(1),
                    "Router should avoid the artificially congested first hop when a cheaper alternative exists");
        }

        @Test
        @DisplayName("Live snapshot from the real graph (attack mode) still resolves to a valid path")
        void liveAttackModeSnapshotResolves() {
            Map<NetworkGraph.Edge, Double> costs = graph.snapshotEdgeCosts(true);
            AStarRouter.RouteResult result =
                    AStarRouter.findPath(graph, costs, NetworkGraph.GATEWAY, NetworkGraph.TARGET);

            assertEquals(NetworkGraph.TARGET, result.path().get(result.path().size() - 1));
            assertTrue(result.totalCostMs() > 0);
        }
    }

    @Nested
    @DisplayName("Firewall bypass flag")
    class FirewallBypassFlag {

        @Test
        @DisplayName("bypassedFirewall is true when the chosen path skips FW")
        void bypassFlagTrueWhenSkippingFirewall() {
            Map<NetworkGraph.Edge, Double> costs = baseCostSnapshot();
            // Make the bypass link (R1->EGRESS) dramatically cheaper than
            // anything that goes through the firewall.
            for (NetworkGraph.Edge edge : graph.neighbors("R1")) {
                if (edge.to().equals("EGRESS")) costs.put(edge, 0.01);
            }
            for (NetworkGraph.Edge edge : graph.neighbors(NetworkGraph.GATEWAY)) {
                if (edge.to().equals("R1")) costs.put(edge, 0.01);
            }

            AStarRouter.RouteResult result = AStarRouter.findPath(graph, costs, NetworkGraph.GATEWAY, NetworkGraph.TARGET);

            assertTrue(result.bypassedFirewall());
            assertFalse(result.path().contains(NetworkGraph.FIREWALL_NODE));
        }

        @Test
        @DisplayName("bypassedFirewall is false when the chosen path goes through FW")
        void bypassFlagFalseWhenThroughFirewall() {
            Map<NetworkGraph.Edge, Double> costs = baseCostSnapshot();
            // Make the bypass link dramatically more expensive than the firewall route.
            for (NetworkGraph.Edge edge : graph.neighbors("R1")) {
                if (edge.to().equals("EGRESS")) costs.put(edge, 9999.0);
            }

            AStarRouter.RouteResult result = AStarRouter.findPath(graph, costs, NetworkGraph.GATEWAY, NetworkGraph.TARGET);

            assertFalse(result.bypassedFirewall());
            assertTrue(result.path().contains(NetworkGraph.FIREWALL_NODE));
        }
    }
}
