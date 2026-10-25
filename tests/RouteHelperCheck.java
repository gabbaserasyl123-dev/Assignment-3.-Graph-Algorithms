import java.util.Arrays;
import java.util.Map;

/**
 * Calls the route helper directly, on the parent_edge array produced by the real dag method.
 * A route must start at the source, end at the target, follow real edges (parent edge of every vertex
 * ends at that vertex) and have total weight equal to distance[target]. Unreachable target: empty route.
 */
public final class RouteHelperCheck {
    private static int failures = 0;

    /** The only line to change if your helper has another name or parameter order. */
    private static int[] route(int source, int target, int[][] edges, int[] parentEdge) {
        return Algorithms.reconstructRoute(source, target, edges, parentEdge);
    }

    private static void check(String label, String request, int target, int[]... allowedRoutes) {
        Map<String, Object> parsed = Json.parseRequest(request);
        Map<String, Object> result = Algorithms.dag(parsed);
        int[] parentEdge = (int[]) result.get("parent_edge");
        Long[] distance = (Long[]) result.get("distance");
        int[][] edges = Input.edges(parsed);
        int source = Input.source(parsed);

        int[] actual = route(source, target, edges, parentEdge);
        boolean ok = false;
        for (int[] allowed : allowedRoutes) {
            ok |= Arrays.equals(actual, allowed);
        }
        long cost = 0;
        int[] edgeIds = new int[Math.max(actual.length - 1, 0)];
        for (int i = 1; i < actual.length; i++) {
            int id = parentEdge[actual[i]];
            edgeIds[i - 1] = id;
            ok &= edges[id][0] == actual[i - 1] && edges[id][1] == actual[i];
            cost += edges[id][2];
        }
        if (actual.length == 0) {
            ok &= distance[target] == null;
        } else {
            ok &= actual[0] == source && actual[actual.length - 1] == target
                    && distance[target] != null && cost == distance[target];
        }
        System.out.println((ok ? "PASS " : "FAIL ") + label + ": route=" + Arrays.toString(actual)
                + " edgeIds=" + Arrays.toString(edgeIds) + " cost=" + cost + " distance=" + distance[target]);
        if (!ok) {
            failures++;
        }
    }

    public static void main(String[] args) {
        String example = "{\"task\":\"dag\",\"n\":5,\"source\":0,\"edges\":[[0,1,3],[0,2,8],[1,2,-7],[2,3,4]]}";
        String ties = "{\"task\":\"dag\",\"n\":3,\"source\":0,\"edges\":[[0,1,2],[0,1,2],[0,2,5],[1,2,3]]}";
        check("reachable target 3", example, 3, new int[] {0, 1, 2, 3});
        check("target is the source", example, 0, new int[] {0});
        check("unreachable target 4", example, 4, new int[0]);
        check("tied routes to 2 (edge 2 directly, or 0->1->2)", ties, 2, new int[] {0, 2}, new int[] {0, 1, 2});
        check("parallel edges to 1", ties, 1, new int[] {0, 1});
        System.exit(failures == 0 ? 0 : 1);
    }
}
