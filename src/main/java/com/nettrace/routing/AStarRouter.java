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
 *
 * Two modes are exposed:
 *   - findPath(graph, costs, start, goal)                 -- free search,
 *     may return a path that skips the firewall (bypassedFirewall=true).
 *   - findPath(graph, costs, start, goal, mustPassThrough) -- constrained
 *     search that is guaranteed to route through mustPassThrough (e.g.
 *     NetworkGraph.FIREWALL_NODE), still optimal subject to that
 *     constraint. Use this when the AI threat classifier must never be
 *     skipped, regardless of how much faster the bypass link looks.
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

    /**
     * Free search: A* is allowed to pick whichever path is cheapest overall,
     * even one that skips a security checkpoint like the firewall (see
     * {@code bypassedFirewall} on the result). Equivalent to
     * {@code findPath(graph, edgeCosts, start, goal, null)}.
     */
    public static RouteResult findPath(NetworkGraph graph, Map<NetworkGraph.Edge, Double> edgeCosts,
                                        String start, String goal) {
        return findPath(graph, edgeCosts, start, goal, null);
    }

    /**
     * Search with an optional required waypoint. When {@code mustPassThrough}
     * is non-null, the returned path is the cheapest path from
     * {@code start} to {@code goal} that is GUARANTEED to visit that node
     * (e.g. {@link NetworkGraph#FIREWALL_NODE}) — it is not merely a
     * preference the search happens to satisfy. This is what makes
     * "enforce firewall inspection" mode a real constraint rather than a
     * warning label on a path that could still skip it.
     * <p>
     * Implementation: run A* twice — start→waypoint, then waypoint→goal —
     * and concatenate. Each leg is independently cost-optimal for the same
     * live edge-cost snapshot, so the concatenation is the cheapest path
     * through that waypoint (a textbook reduction: "shortest path visiting
     * node X" decomposes into "shortest path to X" + "shortest path from X",
     * both of which A* already solves optimally). {@code bypassedFirewall}
     * on the result is always {@code false} in this mode, since the path is
     * constructed to pass through the waypoint by construction, not merely
     * observed to.
     */
    public static RouteResult findPath(NetworkGraph graph, Map<NetworkGraph.Edge, Double> edgeCosts,
                                        String start, String goal, String mustPassThrough) {
        if (mustPassThrough == null || mustPassThrough.equals(start) || mustPassThrough.equals(goal)) {
            return search(graph, edgeCosts, start, goal);
        }

        RouteResult firstLeg = search(graph, edgeCosts, start, mustPassThrough);
        RouteResult secondLeg = search(graph, edgeCosts, mustPassThrough, goal);

        List<String> combinedPath = new ArrayList<>(firstLeg.path());
        combinedPath.addAll(secondLeg.path().subList(1, secondLeg.path().size()));

        List<String> combinedDisplayPath = new ArrayList<>(firstLeg.displayPath());
        combinedDisplayPath.addAll(secondLeg.displayPath().subList(1, secondLeg.displayPath().size()));

        List<Double> combinedEdgeCosts = new ArrayList<>(firstLeg.edgeCosts());
        combinedEdgeCosts.addAll(secondLeg.edgeCosts());

        return new RouteResult(
                combinedPath,
                combinedDisplayPath,
                firstLeg.totalCostMs() + secondLeg.totalCostMs(),
                firstLeg.nodesExpanded() + secondLeg.nodesExpanded(),
                combinedEdgeCosts,
                false
        );
    }

    private static RouteResult search(NetworkGraph graph, Map<NetworkGraph.Edge, Double> edgeCosts,
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
