# Delegation Harness v2 — TDD-contract delegation (Opus writes the test, DeepSeek writes the impl)

**Date:** 2026-06-27 (rev. after adversarial review + a 3-tier capability pilot)
**Type:** Dev tooling — a reusable Claude Code delegation system. **Not** a Hexerei mod feature; the WARRANTLY
Constitution gate does not apply.
**Supersedes:** the v1 design ("DeepSeek with bash, Opus reviews the diff"), which the adversarial review
(`2026-06-27-delegation-harness-review.md`) showed was both unsafe (diff review is not a bash-safety gate)
and likely token-negative (Opus re-reads the diff ~100%, 6–8 touchpoints/delegation). This rev keeps only
what survives that review and the pilot.

---

## The one idea: the test is both the contract and the review

The harness exists to cut **Opus (Anthropic) tokens**. It does that, and avoids "double usage" (paying Opus
to think *and* paying DeepSeek), **only** in this shape:

> **Opus writes the failing test (the contract). DeepSeek writes the implementation until that test passes.
> Opus accepts on the green gate — it never reads the implementation.**

The test-writing is the irreducible cost: it is *intent capture*, which a rigorous workflow pays anyway (it
is exactly what `writing-plans` already produces per task). Everything downstream — implementing, building,
testing, fixing, iterating — happens **inside the MCP server on DeepSeek's tokens**, never entering Opus's
context. Opus's only post-delegation act is to read a compact verdict and accept.

### The three "doubles" and how each is eliminated
| Double | Why it costs Opus | Eliminated by |
|---|---|---|
| Opus writes a detailed *how-to* brief | the brief ≈ the implementation, re-derived in Opus's context | the brief is **the test + a one-line goal**, not a method. From a plan, the test is already written. |
| Opus reads the full diff to review | reading the impl ≈ re-doing it in context | accept = **the gate passed**; the full diff is read only on a red gate or a test-file change. |
| Opus iterates / finishes the work itself | pays for DeepSeek's thrash *and* Opus | the whole **fix→verify→retry loop runs server-side** (capped); Opus sees the task once and the verdict once. |

### Per-delegation Opus budget (the saving is real only for substantial impls)
- **Opus does it directly (TDD):** test (~300 out) + impl (~800–2000 out) + iterate (~500–1500) ≈ **1.6–3.8k out**.
- **Delegated:** test (~300 out, or 0 if from a plan) + handoff (~100) + accept-on-green (~200 in) ≈ **~0.6k**;
  impl + iterate = **0 Opus**. **Breakeven rule: do not delegate when the impl Opus would write is < ~1.5–2k
  output tokens, or when >1 iteration is expected.** Small tasks are net-negative — never delegate them.

---

## Architecture (Opus + DeepSeek only; no Haiku/Sonnet tier)

### 1. The MCP server (`deepseek_task`) — **implemented this rev**
- A **file-only** DeepSeek agent (read/write/edit/glob/grep, path-confined; **no bash**) runs in a throwaway
  git worktree. The **server**, not the agent, runs the gate — so the verdict is authoritative (never the
  agent's self-report) and the agent never gets a shell.
- `deepseek_task(task, base_ref="HEAD", verify_command, verify_cwd, verify_env, verify_timeout=300,
  context_files, max_rounds=3)`. The server loop: run agent → run `verify_command` (local, no-shell arg
  list, timeout-bounded, with an error-excerpt) → if red, feed the excerpt back and retry, up to
  `max_rounds` → commit → return. **All in-process; zero Opus tokens in the loop.**
- Compact return: `{status, verify:{gate_configured,ran,passed,timed_out,excerpt}, summary, rounds,
  agent_iterations, changed_files, test_files_changed, diff_stat, diff, worktree_path, branch, base_sha}`.
  `status` ∈ `verified | verify_failed | unverified | error`. `unverified` = no gate configured (amber).
- `deepseek_cleanup(worktree_path)` removes the worktree **and** its `ds/<uuid>` branch.

### 2. `/delegate` skill (Opus invokes explicitly) — **P2, to build**
The "skill before /workflows". Opus, holding a delegatable task **with a test already written**, invokes
`/delegate`. The skill: applies the **breakeven check** (declines small tasks), resolves the project's
`verify_command`/`verify_cwd`/`verify_env` from a `## Delegation` block in the project's `CLAUDE.md` (never
from task text — a static, project-canonical value), calls `deepseek_task`, and hands Opus the compact
result + the **accept rubric** (below).

### 3. Auto-nudge hook — **P3, scoped**
A `UserPromptSubmit` hook that nudges `/delegate` **only** for prompts that look like a *large* impl task
with a test contract — never the small tasks that lose. Per-project opt-out (`.no-deepseek-nudge` sentinel),
and **off for Hexerei** (the Constitution governs delegatability there). Reminder only; never blocks.

## The accept rubric (how Opus reads the verdict cheaply)
1. `status == verified` **and** `test_files_changed == []` → **accept**, do not read `diff`. (The test Opus
   wrote passing, with the tests untouched, is the full review.)
2. `status == verified` **and** `test_files_changed != []` → read the **diff of those files only**
   (cheap, bounded) — confirm the agent didn't weaken/disable/delete a test to pass. If gamed → reject + feed back.
3. `status == verify_failed` → read `verify.excerpt` (and the diff only if needed) → send back with feedback,
   or, after the skill's `max_retries`, fall back to Opus doing it (the rare expensive tail).
4. `status == unverified` → treat as **amber**: there was no gate; do not accept silently — require a gate or
   an explicit `[UNVERIFIED]` annotation.
On accept: `git merge --no-ff ds/<uuid>` (tagged, so a regression is revertible via `git revert -m 1`), then
`deepseek_cleanup`.

## Hard constraints (from the pilot + review — violating these inverts the economy)
- **Fast-verify only.** The pilot proved DeepSeek writes correct, compiling, version-correct NeoForge 1.21.1
  code (3 tiers, first-shot-green on compile/unit gates). But it also proved the **runtime gate is
  impractical here**: `runGameTestServer` is ~7–9 min/run (≈5 min of it a shutdown world-save) and had a
  pre-existing red required test — an in-MCP iterate loop against that is unworkable and confounded. **Only
  delegate tasks whose gate is fast and reliable** (unit tests, compile, `./gradlew test --tests …`).
  Runtime/in-world/GUI work stays with Opus/host (DeepSeek may *write* it, but it is verified by a human/Opus
  run, not auto-delegated).
- **Tests immutable to the agent.** A green gate is only trustworthy if the agent didn't edit the tests. The
  contract puts the test in `base_ref`; the brief forbids touching tests; the server surfaces
  `test_files_changed` so Opus's rubric catches gaming (step 2). Enforced cheaply, not blindly trusted.
- **Large units only** (breakeven rule above).
- **`verify_command` is static + no-shell.** Sourced from the project's `CLAUDE.md` `## Delegation` block,
  passed as an arg list, run with `shell=False` — never interpolated from task text (shell-injection guard).

## Security posture (honest)
- The agent is **file-only**; the server runs the gate. This **removes** the v1 bash attack surface entirely
  (no exfiltration-via-bash, no persistence, no `crontab`/`authorized_keys` — the agent cannot spawn a
  process). The verify command is the project's own canonical build/test, run locally; it ships no data out.
- The **residual, accepted risk**: the harness's purpose is to send this repo's **private source to
  api.deepseek.com** (an external model). Claude Code's auto-mode classifier hard-blocks this by default; it
  is unblocked only by the user adding `api.deepseek.com` to `autoMode.environment` (done, project-local,
  gitignored). This is a deliberate, scoped trust decision — private code does leave the machine to DeepSeek.
  The only true closure (private code never leaving) is **self-hosted DeepSeek**, out of scope here.

## Pilot evidence (n=3, all first-shot green on the configured gate)
| Tier | Gate | Agent iters | Result |
|---|---|---|---|
| Pure logic (clamp util + JUnit) | `gradlew test` | 6 | ✅ green |
| NeoForge item + registration + multi-file, version-specific API | `gradlew build` | 9 | ✅ green |
| Block + in-world behavior + a GameTest, version-specific in-world API | `gradlew build` (compile) | 31 | ✅ compiles correct; runtime-pass unverified — gate too slow |
Iteration cost scales with difficulty (6→9→31). Confirmed in passing: the diff-bug (fixed this rev) and an
**over-build tendency** (the agent adds models/creative-tab unasked → a design decision Opus's review must
gate, esp. under the Constitution).

## Scope / phasing
- **P1 — server (DONE, commit e751f09):** file-only agent + server-run `verify_command` loop + diff-bug fix +
  `test_files_changed` + timeout + error-excerpt + `status=unverified` for no-gate. 34 unit tests; e2e smoke green.
- **P2 — `/delegate` skill:** breakeven check, `CLAUDE.md ## Delegation` lookup for verify config, the accept
  rubric, the `max_retries` outer cap and the merge-tag/cleanup flow.
- **P3 — nudge hook (scoped) + economy telemetry:** the gated `UserPromptSubmit` nudge; a telemetry hook to
  **measure** actual Opus-token savings — the harness must not be trusted as net-positive until measured.
- **Global relocation (deferred decision):** to serve all projects the server moves to `~/.claude/mcp/deepseek/`
  with a version-controlled source-of-truth (e.g. repo `tools/` + a deploy step) and a `repo_path` param
  (drop server-CWD `git rev-parse`, which breaks when launched from `~/.claude`). Until then it is this repo's
  local tool.

## Out of scope (YAGNI / deferred)
- No bash for the agent (server runs the gate).
- No runtime/GUI auto-delegation (gate too slow; stays with Opus/host).
- No Haiku/Sonnet tier.
- No auto-merge; no heuristic auto-router (Opus decides delegation, and only for large, test-gated units).
- Concurrency (parallel `/delegate`) and a global-install story are P2+/global-relocation concerns, not v2-core.
