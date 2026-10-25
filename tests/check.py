#!/usr/bin/env python3
"""Minimal own tests. Run from the repository root:  python tests/check.py

 1. tests/my_cases.jsonl vs tests/my_cases_expected.jsonl, sent TWICE in one process
    (repeated mixed requests; the two passes must be valid and identical).
 2. Validator self-check: an alternative valid answer is accepted, an invalid witness is rejected.
 3. Generated requests at the published limits (n = 100000, m or q = 250000) incl. deep chains.
 4. The route helper is called directly (tests/RouteHelperCheck.java).

Non-unique answers (forest edge IDs, SCC labels, topological order, parent_edge) are judged by the
supplied validator tools/public_validate.py, which checks VALIDITY against hand-derived expected values.
The transcript is saved to tests/check_actual.txt, the actual outputs of part 1 to tests/my_cases_actual.jsonl.
"""
import json
import os
import pathlib
import random
import subprocess
import sys
import time

ROOT = pathlib.Path(__file__).resolve().parents[1]
TESTS = ROOT / "tests"
sys.path.insert(0, str(ROOT / "tools"))
import run as launcher  # noqa: E402
from public_validate import check  # noqa: E402

N, M, W = 100_000, 250_000, 10**9
lines, failures = [], 0


def say(text):
    print(text, flush=True)
    lines.append(text)


def verdict(passed, text):
    global failures
    failures += not passed
    say(("PASS " if passed else "FAIL ") + text)


def read_jsonl(path):
    return [json.loads(line) for line in path.read_text(encoding="utf-8").splitlines() if line.strip()]


# ---------------------------------------------------------------- 1. hand-made cases
def hand_made(command):
    say("== 1. hand-made cases (my_cases.jsonl), sent twice in one process")
    cases = read_jsonl(TESTS / "my_cases.jsonl")
    expected = read_jsonl(TESTS / "my_cases_expected.jsonl")
    assert len(cases) == len(expected), "inputs and expected answers differ in count"
    answers = launcher.execute(command, cases + cases)
    first, second = answers[:len(cases)], answers[len(cases):]
    (TESTS / "my_cases_actual.jsonl").write_text(
        "".join(json.dumps(a, separators=(",", ":")) + "\n" for a in first), encoding="utf-8")
    for number, (request, exp, a, b) in enumerate(zip(cases, expected, first, second), 1):
        verdict(check(request, a, exp) and check(request, b, exp),
                f"case {number}: {request['task']} (valid in both passes)")
    verdict(first == second, "second pass returned exactly the same answers (state is reset between requests)")


# ---------------------------------------------------------------- 2. validator self-check
SELF = [
    ("MST", {"task": "kruskal", "n": 3, "edges": [[0, 1, 1], [1, 2, 1], [0, 2, 1]]}, {"cost": 2, "components": 1},
     {"cost": 2, "components": 1, "edge_ids": [1, 2]},
     {"cost": 2, "components": 1, "edge_ids": [0, 1, 2]}),
    ("SCC", {"task": "scc", "n": 4, "edges": [[0, 1], [1, 0], [1, 2], [0, 2], [2, 3]]}, {"component": [0, 0, 1, 2]},
     {"component": [7, 7, 3, 5], "condensation": [[3, 5], [7, 3]]},
     {"component": [7, 7, 3, 5], "condensation": [[7, 3], [7, 3], [3, 5]]}),
    ("topological order", {"task": "topo", "n": 4, "edges": [[0, 2], [1, 2], [2, 3]]}, {"acyclic": True},
     {"acyclic": True, "order": [1, 0, 2, 3]},
     {"acyclic": True, "order": [0, 2, 1, 3]}),
    ("DAG parent_edge", {"task": "dag", "n": 3, "source": 0, "edges": [[0, 1, 2], [0, 1, 2], [0, 2, 5], [1, 2, 3]]},
     {"acyclic": True, "distance": [0, 2, 5]},
     {"acyclic": True, "distance": [0, 2, 5], "parent_edge": [-1, 1, 3]},
     {"acyclic": True, "distance": [0, 2, 5], "parent_edge": [-1, 0, 0]}),
]


def self_check():
    say("== 2. validator self-check (alternative valid answer / invalid witness)")
    for name, request, exp, alternative, invalid in SELF:
        verdict(check(request, alternative, exp) and not check(request, invalid, exp),
                f"{name}: alternative valid answer accepted, invalid witness rejected")


# ---------------------------------------------------------------- 3. full limits
def limit_cases():
    rng = random.Random(1)
    chain = N - 1
    forward = lambda: (lambda u: (u, rng.randint(u + 1, N - 1)))(rng.randrange(N - 1))
    cost_edges = [[i, i + 1, -W] for i in range(chain)] + \
                 [[rng.randrange(N), rng.randrange(N), rng.randint(-W + 1, W)] for _ in range(M - chain)]
    singles = [[i, i + 1] for i in range(chain)] + [list(forward()) for _ in range(M - chain)]
    cycle = [[i, i + 1] for i in range(chain)] + [[N - 1, 0]] + \
            [[rng.randrange(N), rng.randrange(N)] for _ in range(M - N)]
    backward = [[i + 1, i] for i in range(chain)] + [list(reversed(forward())) for _ in range(M - chain)]
    forward_cycle = [[i, i + 1] for i in range(chain)] + [[N - 1, 0]] + [list(forward()) for _ in range(M - N)]
    dag_edges = [[i, i + 1, -W] for i in range(chain)] + \
                [list(forward()) + [rng.randint(-W, W)] for _ in range(M - chain)]
    half = N // 2
    far_cycle = [[i, i + 1, 1] for i in range(half - 1)] + [[i, i + 1, 1] for i in range(half, N - 1)] + [[N - 1, half, 1]]
    far_cycle += [list(map(int, (u, rng.randint(u + 1, half - 1)))) + [rng.randint(1, 10)]
                  for u in (rng.randrange(half - 1) for _ in range(M - len(far_cycle)))]
    ops = [[i, i + 1] for i in range(chain)] + [[0, N - 1]] * (M - chain)
    states = [[N - 1 - k, k + 2] for k in range(chain)] + [[1, N]] * (M - chain)
    return [
        ("dsu: chain of 99999 unions + repeats", {"task": "dsu", "n": N, "operations": ops}, {"states": states}),
        ("kruskal: chain of -10^9 edges + 150001 random heavier edges (cost -99999*10^9)",
         {"task": "kruskal", "n": N, "edges": cost_edges}, {"cost": -chain * W, "components": 1}),
        ("prim: same graph", {"task": "prim", "n": N, "edges": cost_edges}, {"cost": -chain * W, "components": 1}),
        ("scc: chain + forward edges (100000 singleton SCCs)", {"task": "scc", "n": N, "edges": singles},
         {"component": list(range(N))}),
        ("scc: one cycle through all vertices + random edges (one SCC)", {"task": "scc", "n": N, "edges": cycle},
         {"component": [0] * N}),
        ("topo: reversed chain + backward edges (acyclic)", {"task": "topo", "n": N, "edges": backward}, {"acyclic": True}),
        ("topo: chain + back edge (one huge cycle)", {"task": "topo", "n": N, "edges": forward_cycle}, {"acyclic": False}),
        ("dag: chain of -10^9 edges + forward edges", {"task": "dag", "n": N, "source": 0, "edges": dag_edges},
         {"acyclic": True, "distance": [-i * W for i in range(N)]}),
        ("dag: huge cycle unreachable from source 0", {"task": "dag", "n": N, "source": 0, "edges": far_cycle},
         {"acyclic": False}),
    ]


def limits(command):
    say(f"== 3. published limits: n = {N}, m or q = {M} (generated, seed 1)")
    for name, request, exp in limit_cases():
        size = len(request.get("edges", request.get("operations", [])))
        began = time.perf_counter()
        try:
            answer = launcher.execute(command, [request])[0]
            passed = check(request, answer, exp)
        except (ValueError, subprocess.TimeoutExpired) as error:
            passed = False
            say(f"     {str(error)[:200]}")
        verdict(passed, f"{name}; m/q={size}; {time.perf_counter() - began:.1f}s")


# ---------------------------------------------------------------- 4. route helper
def route_helper():
    say("== 4. route helper (RouteHelperCheck.java calls the helper directly)")
    if launcher.select_language(None) != "Java":
        say("SKIP: submission.json does not select Java; call your helper directly from your own test code")
        return
    classes = ROOT / ".build" / "Java"
    out = ROOT / ".build" / "tests"
    out.mkdir(parents=True, exist_ok=True)
    subprocess.run(["javac", "--release", "17", "-encoding", "UTF-8", "-d", str(out), "-cp", str(classes),
                    str(TESTS / "RouteHelperCheck.java")], check=True)
    done = subprocess.run(["java", "-cp", os.pathsep.join([str(classes), str(out)]), "RouteHelperCheck"],
                          capture_output=True, text=True, encoding="utf-8")
    for line in done.stdout.splitlines():
        say(line)
    if done.returncode:
        say(done.stderr.strip())
    verdict(done.returncode == 0, "route helper checks")


def main():
    command = launcher.build(launcher.select_language(None))
    hand_made(command)
    self_check()
    limits(command)
    route_helper()
    say("ALL TESTS PASSED" if not failures else f"{failures} CHECK(S) FAILED")
    (TESTS / "check_actual.txt").write_text("\n".join(lines) + "\n", encoding="utf-8")
    return int(failures > 0)


if __name__ == "__main__":
    sys.exit(main())
