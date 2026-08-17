package com.nettrace.routing;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;

/**
 * A* search over a NetworkGraph edge-cost snapshot.
 *
 * f(n) = g(n) + h(n), where:
 *   g(n) = real accumulated cost so far (sum of snapshot edge costs, i.e.
 *          base latency + live PacketQueue backpressure -- this is what
 *          makes the routing "dynamic": under attack mode, congested edges
 *          cost more and A* routes around them).
 *   h(n) = remaining_hops(n, goal) * minEdgeBaseLatency
 *
 * h(n) is admissible (never overestimates the true remaining cost): every
 * real edge in a snapshot costs AT LEAST minEdgeBaseLatency (queue
 * backpressure only adds, never subtracts), so no real path to the goal
 * can ever cost less than remaining_hops * minEdgeBaseLatency. That
 * admissibility is what makes the path this returns provably optimal for
 * the given snapshot, not just "a path some search happened to find."
 */
public class AStarRouter {

    public record RouteResult(
            List<String> path,
            List<String> displayPath,
            double totalCostMs,
            int nodesExpanded,
            List<Double> edgeCosts,
            boolean bypassedFirewall
    ) {
    }

    private record FrontierEntry(String node, double gCost, double fCost,
                                  List<String> path, List<Double> edgeCosts) {
    }

    public static RouteResult findPath(NetworkGraph graph, Map<NetworkGraph.Edge, Double> edgeCosts,
                                        String start, String goal) {
        double minEdge = graph.minEdgeBaseLatency();
        Map<String, Integer> hopsToGoal = graph.hopDistancesTo(goal);

        PriorityQueue<FrontierEntry> frontier =
                new PriorityQueue<>(Comparator.comparingDouble(FrontierEntry::fCost));
        Map<String, Double> bestKnownCost = new HashMap<>();

        frontier.add(new FrontierEntry(start, 0.0, heuristic(start, hopsToGoal, minEdge), List.of(start), List.of()));
        bestKnownCost.put(start, 0.0);

        int nodesExpanded = 0;

        while (!frontier.isEmpty()) {
            FrontierEntry current = frontier.poll();

            // Stale entry: a cheaper path to this node was already expanded
            // since this entry was queued. Skip instead of re-expanding.
            if (current.gCost() > bestKnownCost.getOrDefault(current.node(), Double.MAX_VALUE)) {
                continue;
            }
            nodesExpanded++;

            if (current.node().equals(goal)) {
                boolean bypassedFirewall = !current.path().contains(NetworkGraph.FIREWALL_NODE);
                List<String> displayPath = current.path().stream().map(graph::displayName).toList();
                return new RouteResult(current.path(), displayPath, current.gCost(),
                        nodesExpanded, current.edgeCosts(), bypassedFirewall);
            }

            for (NetworkGraph.Edge edge : graph.neighbors(current.node())) {
                Double edgeCost = edgeCosts.get(edge);
                if (edgeCost == null) continue; // no snapshot reading for this edge - treat as unavailable
                double tentativeG = current.gCost() + edgeCost;

                if (tentativeG < bestKnownCost.getOrDefault(edge.to(), Double.MAX_VALUE)) {
                    bestKnownCost.put(edge.to(), tentativeG);
                    List<String> newPath = new ArrayList<>(current.path());
                    newPath.add(edge.to());
                    List<Double> newCosts = new ArrayList<>(current.edgeCosts());
                    newCosts.add(edgeCost);
                    double f = tentativeG + heuristic(edge.to(), hopsToGoal, minEdge);
                    frontier.add(new FrontierEntry(edge.to(), tentativeG, f, newPath, newCosts));
                }
            }
        }

        throw new IllegalStateException("No path found from " + start + " to " + goal);
    }

    private static double heuristic(String node, Map<String, Integer> hopsToGoal, double minEdgeWeight) {
        Integer hops = hopsToGoal.get(node);
        if (hops == null) return Double.MAX_VALUE; // unreachable from here
        return hops * minEdgeWeight;
    }
}
