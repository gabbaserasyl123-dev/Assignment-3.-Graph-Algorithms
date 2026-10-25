# Assignment 3 — Graph Algorithms: Analysis

Language: **Java 17-compatible code** (`submission.json`: `{"language":"Java"}`), built with `--release 17`.
Algorithms: `src/Java/Algorithms.java` (six methods + route helper `Algorithms.reconstructRoute`).
The supplied adapters (`Main`, `Input`, `Json`) are unchanged.

## 1. Build, run and test commands

All commands are run from the repository root.

```sh
python tools/run.py --setup        # compiles src/Java and checks launch (ping)
python tools/run.py --check        # 36 public cases, batch repeated twice in one process
python tests/check.py              # own tests (see tests/README.md); transcript in tests/check_actual.txt
python tools/run.py --input tests/my_cases.jsonl   # actual outputs of the hand-made inputs
# benchmark, Windows PowerShell (the commands that were run)
New-Item -ItemType Directory -Force .build/tests
javac --release 17 -d .build/tests -cp .build/Java tests/Benchmark.java
java -Xmx384m -cp ".build/Java;.build/tests" Benchmark
# Linux/macOS: mkdir -p .build/tests, and use ':' instead of ';' in -cp
```

Own tests (`tests/`): 19 hand-made requests with hand-derived expected answers, sent twice in one process; a validator self-check
(an alternative valid answer is accepted, an invalid witness is rejected, for MST, SCC, topological order and DAG `parent_edge`);
9 generated requests at the published limits (n = 100 000, m or q = 250 000) with analytically derived answers, including
100 000-vertex chains, one huge cycle and a huge cycle unreachable from the DAG source; direct calls of the route helper.
Non-unique answers are judged by validity with the supplied `tools/public_validate.py`.

## 2. Conventions used by all methods

* Vertices are `0..n-1`; the edge ID is the original row index. IDs are stored inside the objects/lists that are sorted or traversed
  (`WeightedEdge.id`, `Arc.edgeId`, `Candidate.edgeId`, `outgoingEdgeIds`), so reordering never loses the original position.
* Totals and distances are `long` (|sum| ≤ 10^5 · 10^9 = 10^14); `Long[]` carries `null` for unreachable vertices.
* **State reset.** `Algorithms` has no mutable static field. Every method allocates all arrays and collections from the current request,
  and the result is a fresh `Map`. One request therefore cannot affect the next. Checked by the supplied double-batch check
  (`--check`) and by sending the 19 own cases twice in one process (identical answers, `tests/check.py`).
* **Deep DFS.** There is no recursion. Each DFS keeps an explicit stack of frames (`stackVertex[]`, `stackNext[]`), where
  `stackNext[frame]` is the index of the next neighbour to examine, so a suspended vertex continues where it stopped and no
  neighbour is scanned twice. A vertex is pushed only when it is first marked (`visited`/`state = 1`), i.e. at most once, so
  arrays of size `n` always suffice. 100 000-vertex chains and cycles are part of `tests/check.py`.

## 3. Methods

### 3.1 `dsu`

*Steps.* `parent[v] = v`, `size[v] = 1`, `components = n`, `largest = 1` (0 for `n = 0`). For every pair find both roots (`find`: first pass finds
the root, second pass points every vertex of the path to it: full path compression, iterative). If the roots differ, attach the root of the
smaller tree to the larger one, add the sizes, decrement `components`, update `largest`. Store `[components, largest]` after **every**
operation (including repeated and self unions).

*Invariant.* `parent` is a forest whose trees are exactly the connected components of the unions so far; `size[r]` is the number of vertices of the
tree rooted at `r`; `components` is the number of roots; `largest` is the maximum root size.

*Correctness.* A union of two vertices of one tree changes nothing. A union of different trees merges exactly two components, so
`components` decreases by one and `largest = max(largest, new size)` (sizes never shrink). Balancing is by **size**, so the same array answers
the balancing and the `largest` question (rank would not be a size).

*Boundary cases.* `n = 0`; no operations (empty `states`, no initial state); self-union; repeated union; merge of equal-size sets (rank 2, size 4); q = 250 000.

*Complexity.* Time O(n + q·α(n)) amortised; memory O(n + q) (`states` holds q pairs).

### 3.2 `kruskal`

*Steps.* Create a `WeightedEdge(u, v, weight, id)` per edge and sort by weight (ties by ID). Scan in that order; when `find(u) != find(v)` unite the
sets, add the weight to `cost` and the ID to the result. `components = n − accepted`.

*Invariant.* After each scanned edge the accepted edges form a forest contained in some minimum spanning forest; the DSU sets are the trees of that forest.

*Correctness.* An edge joining two different trees is a lightest edge among the not yet scanned edges that cross the cut (this tree, the rest) —
every lighter edge was scanned and lies inside a tree — so by the cut property the forest stays inside a minimum spanning forest (equal weights are harmless:
the property holds for *some* lightest crossing edge). An edge inside a tree would close a cycle and is skipped. A self-loop always has equal roots and is never taken.
At the end each component is spanned by `n − c` edges.

*Boundary cases.* `n = 0`; no edges (cost 0, components n); self-loops (even with a negative weight); parallel edges; ties; negative weights;
totals above 32 bits (`long cost`).

*Complexity.* Time O(n + m log m) (object sort + near-linear DSU); memory O(n + m).

### 3.3 `prim`

*Steps.* Build `List<Arc>[]` (both directions of every edge; a self-loop adds two arcs to the same vertex). For every unvisited vertex `start` count one component and push
a start candidate `(0, −1, start)` into a `PriorityQueue<Candidate>` ordered by weight, edge ID, vertex. Repeat: remove the minimum candidate; if its vertex is visited,
it is a stale entry and is skipped; otherwise visit it, add its weight and edge ID (not for the start candidate) and push the arcs to unvisited vertices. When the queue is
empty the component is complete and the outer loop moves on (Prim restarts in every component, isolates included).

*Invariant.* The selected edges form a tree on the visited vertices of the current component and lie inside some minimum spanning forest; the queue contains every arc from
the visited set to an unvisited vertex (plus stale entries).

*Correctness.* The smallest non-stale candidate is a lightest edge crossing the cut (visited, unvisited) inside the component, hence a safe edge by the cut property.
Stale entries lead to visited vertices and are discarded. An empty queue means no edge leaves the visited set, i.e. the component is complete.
Self-loop arcs lead to visited vertices and are never selected.

*Boundary cases.* As for Kruskal, plus the restart (disconnected graphs, isolates, `m = 0`).

*Complexity.* Every arc is pushed at most once (from its tail, when the tail is visited), so at most 2m + c queue operations: time O(n + m log m); memory O(n + m).

### 3.4 `scc` (Kosaraju)

*Steps.* (1) Build `graph` and `transpose` as `List<Integer>[]`. (2) Iterative DFS over the whole graph; a vertex is appended to `finishOrder` when its frame is popped.
(3) Scan `finishOrder` from the end; for every unlabelled vertex run a DFS on the transpose over unlabelled vertices; everything it reaches gets the next label.
(4) For every edge `u → v` with `component[u] != component[v]` add `(from << 32) | to` to a `HashSet<Long>`; its elements are the condensation pairs (no duplicates).

*Invariant.* After each start vertex of pass 2, the labelled vertices are a union of complete SCCs and the newly labelled set is exactly one SCC.

*Correctness.* For an edge from SCC C to a different SCC C′ the largest finish time in C is larger than the largest in C′. So the unlabelled vertex with the largest finish time lies in
an SCC that has no incoming edge from other unlabelled SCCs; in the transpose it has no outgoing edge to them, so the DFS reaches exactly that SCC (labelled vertices are skipped).
Induction over the labels finishes the proof. Edges inside a component have equal labels and are excluded; the hash set removes repeated pairs.

*Boundary cases.* `n = 0`; isolated vertices; self-loops and duplicate edges (one pair each); one cycle through all vertices; a 100 000-vertex chain (100 000 SCCs).

*Complexity.* Time O(n + m) expected (hash set); memory O(n + m) (boxed `Integer` lists make the constant larger).

### 3.5 `topo`

*Steps.* `topologicalOrder` stores the outgoing edge IDs of every vertex and runs a DFS with states 0 (unvisited), 1 (active, on the current path), 2 (finished) from every unvisited vertex.
An arc to a state-1 vertex means a directed cycle (a self-loop is the one-vertex case): return `acyclic = false` and an empty `order`. An arc to a state-2 vertex is ignored. A vertex that
has no more arcs gets state 2 and is appended to `order`; at the end `order` is reversed in place (reverse finish order).

*Invariant.* The stack is a path of state-1 vertices in which each vertex has an arc to the next one; a state-2 vertex has all out-neighbours finished.

*Correctness.* An arc to an active vertex closes a cycle with the stack path. Conversely, if a cycle exists, the first of its vertices reached by the DFS becomes active; by the white-path argument the others are
discovered below it, and the arc back to it is examined while it is still active. If no arc to an active vertex occurs, then for every arc `u → v`, `v` finishes before `u` (it was unvisited and finishes inside `u`, or it was already
finished), so the reversed finish order puts `u` before `v`. The outer loop starts from every unvisited vertex, so a cycle in a component that does not contain vertex 0 is found too.

*Boundary cases.* `n = 0`; self-loop; cycle in a later component; diamond and parallel edges (arcs into finished vertices are not cycles); 100 000-vertex chains in both directions and one 100 000-vertex cycle.

*Complexity.* O(n + m) time and memory.

### 3.6 `dag`

*Steps.* Run `topologicalOrder` on the **whole** graph. If it reports a cycle, return `acyclic = false`, `distance = []`, `parent_edge = []`. Otherwise set `reachable[source] = true`, `distance[source] = 0`, `parentEdge` = −1
everywhere, and process the vertices in topological order: skip a vertex that is not reachable; for each outgoing edge ID relax `next` when it is unreachable or `distance[vertex] + w < distance[next]` (strict, so on ties the first
edge found is kept), storing the **edge ID** in `parentEdge[next]`. Unreachable vertices are output as JSON `null`.

*Invariant.* When a vertex is taken from the order, every vertex before it has been processed, so its distance is final (all predecessors come earlier). `parentEdge[v]` is an edge that ends at `v` with
`distance[tail] + w = distance[v]`.

*Correctness.* Standard dynamic programming over a topological order: every path to `v` enters through an in-arc `(u, v)` and `u` was final when that arc was relaxed. The parent edge was set at the last improvement of `v`,
and its tail is never improved afterwards, so the edge stays tight. The tail is strictly earlier in the topological order, so following parent edges ends at a reachable vertex with parent −1; only the source has that (an
edge into the source would start at a vertex that is not reachable from it, and unreachable vertices are skipped). Skipping unreachable vertices stops negative edges that leave them from creating false distances.
`null` (not −1) marks unreachable vertices because −1 can be a real distance.

*Boundary cases.* `n = 1`; unreachable vertices; a cycle (also a self-loop) in a part not reachable from the source; negative distances; parallel and tied edges; ±4·10^9 sums; chains of 100 000 vertices.

*Complexity.* O(n + m) time and memory.

### 3.7 Route helper

`Algorithms.reconstructRoute(source, target, edges, parentEdge)` returns the vertices from `source` to `target`. The route to the source is `[source]`. **No-route result: an empty array** (target unreachable, or an invalid
witness). Pass 1 follows `parentEdge` from `target` to `source` and only counts the vertices (it also rejects an edge that does not end at the current vertex, an out-of-range ID, and, by the length guard, a corrupt cycle);
pass 2 fills the array from the end. Time and output space O(L) for a route of L vertices (no array of size n is allocated). The JSON `distance` still uses `null` for unreachable vertices; the empty array is the helper's own
convention.

## 4. Route helper: actual calls

Output of `RouteHelperCheck` (part of `python tests/check.py`, also in `tests/check_actual.txt`). Besides the expected route, every call checks that each selected edge ends at its vertex and that the total weight equals `distance[target]`.

| Case | Graph (edge IDs by position) | Call | Route | Selected edge IDs | Cost = distance |
|---|---|---|---|---|---|
| reachable target | n=5, source 0: `0→1 (3)`, `0→2 (8)`, `1→2 (−7)`, `2→3 (4)` | `reconstructRoute(0, 3, …)` | `[0, 1, 2, 3]` | `[0, 2, 3]` | 3 − 7 + 4 = 0 = 0 |
| source | same | `reconstructRoute(0, 0, …)` | `[0]` | `[]` | 0 |
| unreachable target | same, vertex 4 isolated | `reconstructRoute(0, 4, …)` | `[]` (no route) | `[]` | `distance[4] = null` |
| tied routes | n=3, source 0: `0→1 (2)` id 0, `0→1 (2)` id 1, `0→2 (5)` id 2, `1→2 (3)` id 3 | `reconstructRoute(0, 2, …)` | `[0, 2]` | `[2]` | 5 = 5 (`0→1→2` costs 2 + 3 = 5 as well; both routes are accepted) |
| parallel edges | same | `reconstructRoute(0, 1, …)` | `[0, 1]` | `[0]` (edge 1 is an equally good alternative) | 2 = 2 |

## 5. Complexity summary

| Method | Time | Memory |
|---|---|---|
| dsu | O(n + q·α(n)) | O(n + q) |
| kruskal | O(n + m log m) | O(n + m) |
| prim | O(n + m log m) | O(n + m) |
| scc | O(n + m) expected | O(n + m) |
| topo | O(n + m) | O(n + m) |
| dag | O(n + m) | O(n + m) |
| route helper | O(L) | O(L) |

## 6. Benchmark: Kruskal vs Prim

**Same graphs.** `tests/Benchmark.java` generates each graph once (seed `20261025`, `java.util.Random`): a random tree (vertex `v` is joined to a random earlier vertex) plus random extra edges up to `m`, weights uniform in
[−10^9, 10^9], edges shuffled; so every graph is connected. Both methods receive the **same** request object. Sizes `(n, m)`: (10 000, 40 000), (50 000, 150 000), (100 000, 250 000).
Before timing, both results are verified (no cycle, `n − c` edges, reported cost equals the sum of selected weights, equal cost and components for Kruskal and Prim).

**Protocol.** 20 untimed warm-up runs per method and size, then 7 timed runs per method and size, alternating Kruskal and Prim. Timer: `System.nanoTime()`. Command (PowerShell): `java -Xmx384m -cp ".build/Java;.build/tests" Benchmark`.
Compilation is excluded (classes are built beforehand).

**Timing boundary (the same for both).** From the call `Algorithms.kruskal(request)` / `Algorithms.prim(request)` to its return. It **includes** the supplied `Input.edges` conversion (parsed lists → `int[][]`) and the algorithm itself;
it **excludes** graph generation, JSON parsing, JSON output, JVM start-up and compilation.

**Environment.** Windows 11 Home (single language), AMD Ryzen 5 7520U with Radeon Graphics (8 logical processors reported by Java), about 15 GB RAM.
Java 25.0.1 LTS (`java version "25.0.1" 2025-10-21 LTS`, HotSpot 64-Bit Server VM, mixed mode); the classes were compiled with `--release 17`; heap limit `-Xmx384m`.
Background load was not measured.

**Results** (all times in ms; the benchmark printed `seed=20261025 warmup=20 timedRuns=7`):

| n | m | Method | Timings (ms) | Median (ms) | Cost |
|---|---|---|---|---|---|
| 10 000 | 40 000 | Kruskal | 11.3, 10.7, 10.9, 11.2, 11.4, 10.8, 10.5 | **10.9** | −6994544373117 |
| 10 000 | 40 000 | Prim | 15.8, 19.3, 20.9, 18.4, 17.2, 14.8, 16.8 | **17.2** | −6994544373117 |
| 50 000 | 150 000 | Kruskal | 68.7, 71.9, 62.7, 77.0, 57.2, 69.8, 55.5 | **68.7** | −30347413080888 |
| 50 000 | 150 000 | Prim | 102.3, 105.0, 122.0, 117.0, 106.0, 105.0, 120.3 | **106.0** | −30347413080888 |
| 100 000 | 250 000 | Kruskal | 113.0, 115.2, 116.8, 130.7, 115.0, 117.5, 141.0 | **116.8** | −53483312763060 |
| 100 000 | 250 000 | Prim | 201.9, 224.5, 214.3, 218.1, 233.3, 224.1, 221.1 | **221.1** | −53483312763060 |

For both methods and every size the cost is identical, as it must be for the same graph.

**Asymptotic argument.** Both methods are O(n + m log m) in time and O(n + m) in memory, so the analysis alone predicts the same growth and no winner.

**Observations (measured).** Kruskal was faster at every size. The ratio of the medians Prim / Kruskal is 1.58 at m = 40 000, 1.54 at m = 150 000 and 1.89 at m = 250 000.
From the smallest to the middle size (m ×3.75) Kruskal's median grew ×6.3 and Prim's ×6.2, more than the ×4.2 that m·log m alone would give.
From the middle to the largest size (m ×1.67; m·log m ×1.74) Kruskal grew ×1.70, close to m·log m, and Prim ×2.09, faster than m·log m.

**Likely reasons (guesses; not profiled).** The larger graphs probably no longer fit in the CPU caches, which would explain the first jump. Prim builds up to 2m `Arc` objects in per-vertex lists, creates up to 2m `Candidate` objects
and does binary-heap operations with a three-field comparator, while Kruskal does one object sort and a sequential scan with near-constant DSU operations; more allocation and more cache misses for Prim would fit the observed gap. These explanations were not tested.

**Limitations.** One generator seed per size and one laptop; seven runs give a median, not a confidence interval; random sparse graphs with uniformly random weights (m/n between 2.5 and 4); dense graphs, chains or equal weights may
give other ratios; background load, JIT and GC add noise. The spread is visible in the table: Kruskal at n = 50 000 ranged from 55.5 to 77.0 ms, and Kruskal at n = 100 000 had one slow run (141.0 ms against the median 116.8 ms), so the
×6.3 growth of Kruskal between the first two sizes should be read with caution.

## 7. Sources and AI assistance

* Algorithms: course lectures and the assignment specification (`README.md`), the standard textbook descriptions of DSU with union by size and path compression, Kruskal, Prim, Kosaraju and DFS-based topological sorting
  (e.g. Cormen, Leiserson, Rivest, Stein, *Introduction to Algorithms*).
* AI assistance: the AI assistant Claude was used in debugging and code suggesting for tests, and creating a draft for ANALYSIS.md.
* All numbers in sections 1 and 6 are outputs of the commands listed in section 1 that were run on the machine described in section 6.
