package com.nettrace.routing;

import com.nettrace.PacketQueue;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * A small multi-path network mesh, used to demonstrate A*-based dynamic
 * routing.
 *
 * This is deliberately a SEPARATE structure from TopologyEngine's fixed
 * 5-hop chain: TopologyEngine has no branching (Client Gateway -> Ingress
 * Router -> Core AI Firewall -> Egress Switch -> Target Server, always in
 * that order), so a search algorithm has nothing to choose between there.
 * A* only demonstrates something meaningful when there are multiple
 * candidate routes with different costs -- which is what this graph adds:
 * two ingress routers, and a bypass link that reaches Egress WITHOUT going
 * through the firewall at all (faster on paper, but skips threat
 * detection -- a genuine, discussable trade-off, not a bug).
 *
 * Each edge owns its own PacketQueue, so routing cost responds to the same
 * kind of live congestion signal TopologyEngine's hop delays already use.
 */
public class NetworkGraph {

    public static final String GATEWAY = "GATEWAY";
    public static final String TARGET = "TARGET";
    public static final String FIREWALL_NODE = "FW";

    public record Edge(String from, String to, double baseLatencyMs, PacketQueue queue) {
    }

    private final Map<String, String> displayNames = new LinkedHashMap<>();
    private final Map<String, List<Edge>> adjacency = new LinkedHashMap<>();
    private final List<Edge> allEdges = new ArrayList<>();

    public NetworkGraph() {
        addNode(GATEWAY, "Client Gateway");
        addNode("R1", "Ingress Router A");
        addNode("R2", "Ingress Router B");
        addNode(FIREWALL_NODE, "Core AI Firewall");
        addNode("EGRESS", "Egress Switch");
        addNode(TARGET, "Target Server");

        addEdge(GATEWAY, "R1", 2.0, 16);
        addEdge(GATEWAY, "R2", 3.2, 24);
        addEdge("R1", FIREWALL_NODE, 4.5, 16);
        addEdge("R2", FIREWALL_NODE, 2.1, 16);
        addEdge("R1", "EGRESS", 5.0, 12);   // bypass link: reaches Egress WITHOUT the firewall
        addEdge(FIREWALL_NODE, "EGRESS", 2.0, 20);
        addEdge("EGRESS", TARGET, 1.0, 16);
    }

    private void addNode(String id, String displayName) {
        displayNames.put(id, displayName);
        adjacency.put(id, new ArrayList<>());
    }

    private void addEdge(String from, String to, double baseLatencyMs, int queueCapacity) {
        Edge edge = new Edge(from, to, baseLatencyMs, new PacketQueue(queueCapacity));
        adjacency.get(from).add(edge);
        allEdges.add(edge);
    }

    public List<Edge> neighbors(String nodeId) {
        return adjacency.getOrDefault(nodeId, List.of());
    }

    public Set<String> nodeIds() {
        return displayNames.keySet();
    }

    public String displayName(String nodeId) {
        return displayNames.getOrDefault(nodeId, nodeId);
    }

    /**
     * Takes a live congestion reading (base latency + current PacketQueue
     * backpressure) for every edge in the graph, and returns it as a
     * standalone cost map. AStarRouter is handed this snapshot rather than
     * the graph + a "go read the live queue" callback, so a single search
     * sees a fixed, self-consistent cost landscape (calling a stateful,
     * randomized PacketQueue.processPacket() again mid-search -- e.g. if
     * relaxing the same edge from two different frontier paths -- would let
     * the "same" edge cost something different within one search, breaking
     * both correctness and repeatability of the result).
     */
    public Map<Edge, Double> snapshotEdgeCosts(boolean attackMode) {
        Map<Edge, Double> costs = new LinkedHashMap<>();
        for (Edge edge : allEdges) {
            PacketQueue.QueueResult qr = edge.queue().processPacket(attackMode);
            costs.put(edge, edge.baseLatencyMs() + qr.queueDelayMs);
        }
        return costs;
    }

    /**
     * Smallest possible single-edge base latency in the graph. Used to
     * build an admissible A* heuristic (see AStarRouter) -- since queue
     * backpressure only ever ADDS to baseLatencyMs (never subtracts), no
     * real edge in a snapshot can ever cost less than this value.
     */
    public double minEdgeBaseLatency() {
        return allEdges.stream().mapToDouble(Edge::baseLatencyMs).min().orElse(0.0);
    }

    /**
     * Unweighted BFS hop-distance from every node to {@code goal}. Combined
     * with minEdgeBaseLatency(), gives the step-count term of the
     * admissible heuristic: h(n) = hopsToGoal(n) * minEdgeBaseLatency.
     */
    public Map<String, Integer> hopDistancesTo(String goal) {
        Map<String, List<String>> reverse = new HashMap<>();
        for (String node : nodeIds()) reverse.put(node, new ArrayList<>());
        for (Edge e : allEdges) reverse.get(e.to()).add(e.from());

        Map<String, Integer> dist = new HashMap<>();
        Deque<String> queue = new ArrayDeque<>();
        dist.put(goal, 0);
        queue.add(goal);
        while (!queue.isEmpty()) {
            String current = queue.poll();
            for (String prev : reverse.get(current)) {
                if (!dist.containsKey(prev)) {
                    dist.put(prev, dist.get(current) + 1);
                    queue.add(prev);
                }
            }
        }
        return dist;
    }
}
