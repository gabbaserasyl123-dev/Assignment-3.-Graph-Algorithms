import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Set;

public final class Algorithms {
    /** DSU with full path compression and union by size/rank; return states. */
    static Map<String, Object> dsu(Map<String, Object> input) {
        int n = Input.vertexCount(input);
        int[][] operations = Input.operations(input);
        int[] parent = new int[n];
        int[] size = new int[n];
        for (int vertex = 0; vertex < n; vertex++) {
            parent[vertex] = vertex;
            size[vertex] = 1;
        }
        int components = n;
        int largest = n == 0 ? 0 : 1;
        int[][] states = new int[operations.length][2];
        for (int index = 0; index < operations.length; index++) {
            int[] operation = operations[index];
            int rootU = find(parent, operation[0]);
            int rootV = find(parent, operation[1]);
            if (rootU != rootV) {
                if (size[rootU] < size[rootV]) {
                    int temporary = rootU;
                    rootU = rootV;
                    rootV = temporary;
                }
                parent[rootV] = rootU;
                size[rootU] += size[rootV];
                components--;
                largest = Math.max(largest, size[rootU]);
            }
            states[index][0] = components;
            states[index][1] = largest;
        }
        return Map.of("states", states);
    }
    /** Iterative find with full path compression. */
    private static int find(int[] parent, int vertex) {
        int root = vertex;
        while (parent[root] != root) {
            root = parent[root];
        }
        while (parent[vertex] != vertex) {
            int next = parent[vertex];
            parent[vertex] = root;
            vertex = next;
        }
        return root;
    }
    /** Kruskal's minimum spanning forest; edge IDs remain their input positions. */
    static Map<String, Object> kruskal(Map<String, Object> input) {
        int n = Input.vertexCount(input);
        int[][] edges = Input.edges(input);
        WeightedEdge[] sorted = new WeightedEdge[edges.length];
        for (int id = 0; id < edges.length; id++) {
            sorted[id] = new WeightedEdge(edges[id][0], edges[id][1], edges[id][2], id);
        }
        Arrays.sort(sorted, Comparator.comparingInt((WeightedEdge edge) -> edge.weight)
                .thenComparingInt(edge -> edge.id));
        int[] parent = new int[n];
        int[] size = new int[n];
        initializeDsu(parent, size);
        int components = n;
        int[] selected = new int[Math.max(0, n - 1)];
        int selectedCount = 0;
        long cost = 0;
        for (WeightedEdge edge : sorted) {
            if (union(parent, size, edge.u, edge.v)) {
                selected[selectedCount++] = edge.id;
                cost += edge.weight;
                components--;
            }
        }
        return Map.of("cost", cost, "edge_ids", Arrays.copyOf(selected, selectedCount),
                "components", components);
    }
    private static void initializeDsu(int[] parent, int[] size) {
        for (int vertex = 0; vertex < parent.length; vertex++) {
            parent[vertex] = vertex;
            size[vertex] = 1;
        }
    }
    private static boolean union(int[] parent, int[] size, int u, int v) {
        int rootU = find(parent, u);
        int rootV = find(parent, v);
        if (rootU == rootV) {
            return false;
        }
        if (size[rootU] < size[rootV]) {
            int temporary = rootU;
            rootU = rootV;
            rootV = temporary;
        }
        parent[rootV] = rootU;
        size[rootU] += size[rootV];
        return true;
    }
    private static final class WeightedEdge {
        final int u;
        final int v;
        final int weight;
        final int id;

        WeightedEdge(int u, int v, int weight, int id) {
            this.u = u;
            this.v = v;
            this.weight = weight;
            this.id = id;
        }
    }
    /** Prim's minimum spanning forest using a lazy priority queue. */
    @SuppressWarnings("unchecked")
    static Map<String, Object> prim(Map<String, Object> input) {
        int n = Input.vertexCount(input);
        int[][] edges = Input.edges(input);
        List<Arc>[] adjacency = (List<Arc>[]) new List<?>[n];
        for (int id = 0; id < edges.length; id++) {
            int u = edges[id][0];
            int v = edges[id][1];
            if (adjacency[u] == null) {
                adjacency[u] = new ArrayList<>();
            }
            if (adjacency[v] == null) {
                adjacency[v] = new ArrayList<>();
            }
            adjacency[u].add(new Arc(v, edges[id][2], id));
            adjacency[v].add(new Arc(u, edges[id][2], id));
        }
        boolean[] visited = new boolean[n];
        int[] selected = new int[Math.max(0, n - 1)];
        int selectedCount = 0;
        int components = 0;
        long cost = 0;
        PriorityQueue<Candidate> queue = new PriorityQueue<>();
        for (int start = 0; start < n; start++) {
            if (visited[start]) {
                continue;
            }
            components++;
            queue.add(new Candidate(0, -1, start));
            while (!queue.isEmpty()) {
                Candidate candidate = queue.remove();
                if (visited[candidate.vertex]) {
                    continue;
                }
                visited[candidate.vertex] = true;
                if (candidate.edgeId >= 0) {
                    selected[selectedCount++] = candidate.edgeId;
                    cost += candidate.weight;
                }
                List<Arc> arcs = adjacency[candidate.vertex];
                if (arcs != null) {
                    for (Arc arc : arcs) {
                        if (!visited[arc.to]) {
                            queue.add(new Candidate(arc.weight, arc.edgeId, arc.to));
                        }
                    }
                }
            }
        }
        return Map.of("cost", cost, "edge_ids", Arrays.copyOf(selected, selectedCount),
                "components", components);
    }
    private static final class Arc {
        final int to;
        final int weight;
        final int edgeId;

        Arc(int to, int weight, int edgeId) {
            this.to = to;
            this.weight = weight;
            this.edgeId = edgeId;
        }
    }
    private static final class Candidate implements Comparable<Candidate> {
        final int weight;
        final int edgeId;
        final int vertex;

        Candidate(int weight, int edgeId, int vertex) {
            this.weight = weight;
            this.edgeId = edgeId;
            this.vertex = vertex;
        }
        @Override
        public int compareTo(Candidate other) {
            int byWeight = Integer.compare(weight, other.weight);
            if (byWeight != 0) {
                return byWeight;
            }
            int byEdge = Integer.compare(edgeId, other.edgeId);
            return byEdge != 0 ? byEdge : Integer.compare(vertex, other.vertex);
        }
    }
    /** Kosaraju's algorithm with iterative DFS passes and exact condensation edges. */
    @SuppressWarnings("unchecked")
    static Map<String, Object> scc(Map<String, Object> input) {
        int n = Input.vertexCount(input);
        int[][] edges = Input.edges(input);
        List<Integer>[] graph = (List<Integer>[]) new List<?>[n];
        List<Integer>[] transpose = (List<Integer>[]) new List<?>[n];
        for (int[] edge : edges) {
            int u = edge[0];
            int v = edge[1];
            if (graph[u] == null) {
                graph[u] = new ArrayList<>();
            }
            if (transpose[v] == null) {
                transpose[v] = new ArrayList<>();
            }
            graph[u].add(v);
            transpose[v].add(u);
        }
        boolean[] visited = new boolean[n];
        int[] finishOrder = new int[n];
        int finishCount = 0;
        int[] stackVertex = new int[n];
        int[] stackNext = new int[n];
        for (int start = 0; start < n; start++) {
            if (visited[start]) {
                continue;
            }
            int top = 0;
            stackVertex[top] = start;
            stackNext[top] = 0;
            top++;
            visited[start] = true;
            while (top > 0) {
                int frame = top - 1;
                int vertex = stackVertex[frame];
                List<Integer> neighbors = graph[vertex];
                if (neighbors != null && stackNext[frame] < neighbors.size()) {
                    int next = neighbors.get(stackNext[frame]++);
                    if (!visited[next]) {
                        visited[next] = true;
                        stackVertex[top] = next;
                        stackNext[top] = 0;
                        top++;
                    }
                } else {
                    finishOrder[finishCount++] = vertex;
                    top--;
                }
            }
        }
        int[] component = new int[n];
        Arrays.fill(component, -1);
        int[] stack = new int[n];
        int componentCount = 0;
        for (int orderIndex = finishCount - 1; orderIndex >= 0; orderIndex--) {
            int start = finishOrder[orderIndex];
            if (component[start] >= 0) {
                continue;
            }
            int top = 0;
            stack[top++] = start;
            component[start] = componentCount;
            while (top > 0) {
                int vertex = stack[--top];
                List<Integer> neighbors = transpose[vertex];
                if (neighbors != null) {
                    for (int next : neighbors) {
                        if (component[next] < 0) {
                            component[next] = componentCount;
                            stack[top++] = next;
                        }
                    }
                }
            }
            componentCount++;
        }
        Set<Long> crossComponentEdges = new HashSet<>();
        for (int[] edge : edges) {
            int from = component[edge[0]];
            int to = component[edge[1]];
            if (from != to) {
                crossComponentEdges.add((((long) from) << 32) | (to & 0xffffffffL));
            }
        }
        int[][] condensation = new int[crossComponentEdges.size()][2];
        int index = 0;
        for (long encoded : crossComponentEdges) {
            condensation[index][0] = (int) (encoded >> 32);
            condensation[index][1] = (int) encoded;
            index++;
        }
        return Map.of("component", component, "condensation", condensation);
    }
    /** DFS topological ordering; a directed cycle produces an empty order. */
    static Map<String, Object> topo(Map<String, Object> input) {
        int n = Input.vertexCount(input);
        int[][] edges = Input.edges(input);
        TopologicalResult result = topologicalOrder(n, edges);
        if (result.order == null) {
            return Map.of("acyclic", false, "order", new int[0]);
        }
        return Map.of("acyclic", true, "order", result.order);
    }
    /** Build edge-ID adjacency and a DFS order; a null order indicates a cycle. */
    @SuppressWarnings("unchecked")
    private static TopologicalResult topologicalOrder(int n, int[][] edges) {
        List<Integer>[] outgoingEdgeIds = (List<Integer>[]) new List<?>[n];
        for (int edgeId = 0; edgeId < edges.length; edgeId++) {
            int from = edges[edgeId][0];
            if (outgoingEdgeIds[from] == null) {
                outgoingEdgeIds[from] = new ArrayList<>();
            }
            outgoingEdgeIds[from].add(edgeId);
        }

        byte[] state = new byte[n]; // 0 = unvisited, 1 = active, 2 = finished.
        int[] order = new int[n];
        int orderSize = 0;
        int[] stackVertex = new int[n];
        int[] stackNext = new int[n];
        for (int start = 0; start < n; start++) {
            if (state[start] != 0) {
                continue;
            }
            int top = 0;
            stackVertex[top] = start;
            stackNext[top] = 0;
            top++;
            state[start] = 1;
            while (top > 0) {
                int frame = top - 1;
                int vertex = stackVertex[frame];
                List<Integer> outgoing = outgoingEdgeIds[vertex];
                if (outgoing != null && stackNext[frame] < outgoing.size()) {
                    int edgeId = outgoing.get(stackNext[frame]++);
                    int next = edges[edgeId][1];
                    if (state[next] == 1) {
                        return new TopologicalResult(outgoingEdgeIds, null);
                    }
                    if (state[next] == 0) {
                        state[next] = 1;
                        stackVertex[top] = next;
                        stackNext[top] = 0;
                        top++;
                    }
                } else {
                    state[vertex] = 2;
                    order[orderSize++] = vertex;
                    top--;
                }
            }
        }
        for (int left = 0, right = orderSize - 1; left < right; left++, right--) {
            int temporary = order[left];
            order[left] = order[right];
            order[right] = temporary;
        }
        return new TopologicalResult(outgoingEdgeIds, order);
    }

    private static final class TopologicalResult {
        final List<Integer>[] outgoingEdgeIds;
        final int[] order;

        TopologicalResult(List<Integer>[] outgoingEdgeIds, int[] order) {
            this.outgoingEdgeIds = outgoingEdgeIds;
            this.order = order;
        }
    }

    /** DAG shortest paths, after checking the entire graph for cycles. */
    static Map<String, Object> dag(Map<String, Object> input) {
        int n = Input.vertexCount(input);
        int source = Input.source(input);
        int[][] edges = Input.edges(input);
        TopologicalResult result = topologicalOrder(n, edges);
        if (result.order == null) {
            return Map.of("acyclic", false, "distance", new Long[0],
                    "parent_edge", new int[0]);
        }

        boolean[] reachable = new boolean[n];
        long[] distance = new long[n];
        int[] parentEdge = new int[n];
        Arrays.fill(parentEdge, -1);
        reachable[source] = true;
        for (int vertex : result.order) {
            if (!reachable[vertex]) {
                continue;
            }
            List<Integer> outgoing = result.outgoingEdgeIds[vertex];
            if (outgoing == null) {
                continue;
            }
            for (int edgeId : outgoing) {
                int[] edge = edges[edgeId];
                int next = edge[1];
                long candidate = distance[vertex] + edge[2];
                if (!reachable[next] || candidate < distance[next]) {
                    reachable[next] = true;
                    distance[next] = candidate;
                    parentEdge[next] = edgeId;
                }
            }
        }

        Long[] outputDistance = new Long[n];
        for (int vertex = 0; vertex < n; vertex++) {
            if (reachable[vertex]) {
                outputDistance[vertex] = distance[vertex];
            }
        }
        return Map.of("acyclic", true, "distance", outputDistance,
                "parent_edge", parentEdge);
    }

    /**
     * Reconstruct a source-to-target route from DAG parent edge IDs.
     * Returns an empty array when the target is unreachable or the witness is invalid.
     */
    static int[] reconstructRoute(int source, int target, int[][] edges, int[] parentEdge) {
        if (source == target) {
            return new int[] {source};
        }
        if (target < 0 || target >= parentEdge.length) {
            return new int[0];
        }
        // Pass 1: count the vertices of the route; reject invalid or cyclic witnesses.
        int length = 1;
        int vertex = target;
        while (vertex != source) {
            int edgeId = parentEdge[vertex];
            if (length > parentEdge.length || edgeId < 0 || edgeId >= edges.length
                    || edges[edgeId][1] != vertex) {
                return new int[0];
            }
            vertex = edges[edgeId][0];
            if (vertex < 0 || vertex >= parentEdge.length) {
                return new int[0];
            }
            length++;
        }
        // Pass 2: fill the route from the end.
        int[] route = new int[length];
        vertex = target;
        for (int i = length - 1; i >= 0; i--) {
            route[i] = vertex;
            if (i > 0) {
                vertex = edges[parentEdge[vertex]][0];
            }
        }
        return route;
    }
}