import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Kruskal vs Prim on the SAME graphs at three sizes.
 * Timing boundary (identical for both methods): from the call Algorithms.kruskal(request) /
 * Algorithms.prim(request) until it returns its result map. It includes Input.edges (list -> int[][]
 * conversion done by the supplied adapter) and the algorithm itself. It excludes graph generation,
 * JSON parsing and JSON output, JVM start and compilation. Timer: System.nanoTime().
 */
public final class Benchmark {
    private static final long SEED = 20261025L;
    private static final int WARMUP = 20;
    private static final int RUNS = 7;
    private static final int[][] SIZES = {{10_000, 40_000}, {50_000, 150_000}, {100_000, 250_000}};

    /** Random connected graph: a random tree (n-1 edges) plus random extra edges, then shuffled. */
    private static List<Object> generate(int n, int m, Random random) {
        List<int[]> edges = new ArrayList<>();
        for (int v = 1; v < n; v++) {
            edges.add(new int[] {random.nextInt(v), v, weight(random)});
        }
        while (edges.size() < m) {
            edges.add(new int[] {random.nextInt(n), random.nextInt(n), weight(random)});
        }
        java.util.Collections.shuffle(edges, random);
        List<Object> rows = new ArrayList<>();
        for (int[] e : edges) {
            rows.add(new ArrayList<Object>(List.of((long) e[0], (long) e[1], (long) e[2])));
        }
        return rows;
    }

    private static int weight(Random random) {
        return random.nextInt(2_000_000_001) - 1_000_000_000;
    }

    private static Map<String, Object> request(String task, int n, List<Object> edges) {
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("task", task);
        request.put("n", (long) n);
        request.put("edges", edges);
        return request;
    }

    private static int find(int[] parent, int x) {
        while (parent[x] != x) {
            parent[x] = parent[parent[x]];
            x = parent[x];
        }
        return x;
    }

    /** Checks that the result is a spanning forest with the reported cost; returns the component count. */
    private static int verify(int n, int[][] edges, Map<String, Object> result) {
        int[] ids = (int[]) result.get("edge_ids");
        int[] parent = new int[n];
        for (int i = 0; i < n; i++) {
            parent[i] = i;
        }
        long cost = 0;
        for (int id : ids) {
            int a = find(parent, edges[id][0]);
            int b = find(parent, edges[id][1]);
            if (a == b) {
                throw new IllegalStateException("selected edges contain a cycle");
            }
            parent[a] = b;
            cost += edges[id][2];
        }
        int components = (Integer) result.get("components");
        if (cost != (Long) result.get("cost") || ids.length != n - components) {
            throw new IllegalStateException("cost or edge count is inconsistent");
        }
        return components;
    }

    private static double median(double[] values) {
        double[] sorted = values.clone();
        Arrays.sort(sorted);
        return sorted[sorted.length / 2];
    }

    public static void main(String[] args) {
        System.out.println("seed=" + SEED + " warmup=" + WARMUP + " timedRuns=" + RUNS
                + " java=" + System.getProperty("java.version") + " os=" + System.getProperty("os.name")
                + " cpus=" + Runtime.getRuntime().availableProcessors()
                + " maxHeapMB=" + Runtime.getRuntime().maxMemory() / (1024 * 1024));
        Random random = new Random(SEED);
        System.out.println("| n | m | method | timings (ms) | median (ms) | cost |");
        System.out.println("|---|---|---|---|---|---|");
        for (int[] size : SIZES) {
            int n = size[0];
            int m = size[1];
            List<Object> edges = generate(n, m, random);
            Map<String, Object> kruskalRequest = request("kruskal", n, edges);
            Map<String, Object> primRequest = request("prim", n, edges); // same edge lists

            int[][] rows = Input.edges(kruskalRequest);
            Map<String, Object> kr = Algorithms.kruskal(kruskalRequest);
            Map<String, Object> pr = Algorithms.prim(primRequest);
            int kc = verify(n, rows, kr);
            int pc = verify(n, rows, pr);
            if (!kr.get("cost").equals(pr.get("cost")) || kc != pc) {
                throw new IllegalStateException("Kruskal and Prim disagree");
            }

            for (int i = 0; i < WARMUP; i++) { // JIT warm-up, not timed
                Algorithms.kruskal(kruskalRequest);
                Algorithms.prim(primRequest);
            }
            double[] kt = new double[RUNS];
            double[] pt = new double[RUNS];
            for (int i = 0; i < RUNS; i++) { // alternate the methods to share any machine noise
                long t0 = System.nanoTime();
                Algorithms.kruskal(kruskalRequest);
                kt[i] = (System.nanoTime() - t0) / 1e6;
                t0 = System.nanoTime();
                Algorithms.prim(primRequest);
                pt[i] = (System.nanoTime() - t0) / 1e6;
            }
            System.out.println("| " + n + " | " + m + " | Kruskal | " + fmt(kt) + " | "
                    + String.format("%.1f", median(kt)) + " | " + kr.get("cost") + " |");
            System.out.println("| " + n + " | " + m + " | Prim | " + fmt(pt) + " | "
                    + String.format("%.1f", median(pt)) + " | " + pr.get("cost") + " |");
        }
    }

    private static String fmt(double[] values) {
        StringBuilder text = new StringBuilder();
        for (double value : values) {
            text.append(text.length() == 0 ? "" : ", ").append(String.format("%.1f", value));
        }
        return text.toString();
    }
}
