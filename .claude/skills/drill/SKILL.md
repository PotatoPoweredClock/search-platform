---
name: drill
description: Plants exactly one deliberate, realistic bug in the user's own hand-written milestone code on a fresh drill/<milestone> branch for later debugging practice, and writes the answer key to gitignored .drills/<milestone>.md. Trigger this whenever the user explicitly asks to plant a drill, run a drill, start a drill for a milestone (e.g. "/drill m1", "plant a drill for M1", "let's do this milestone's drill"), or otherwise invokes the drill habit described in this repo's CLAUDE.md. Never trigger this on your own initiative — the whole point of a drill is that the user doesn't know one is coming or where it lands, so only run it when they explicitly ask.
---

# Drill: plant a milestone bug

This encodes the habit from this repo's `CLAUDE.md`:

> Once per milestone, when I ask, plant a bug on a branch named `drill/<milestone>` without telling me where. Put the answer in `.drills/` (gitignored). Later the drills become the triage agent's eval set.

The user is writing this project's core backend by hand to learn it deeply. A drill is a deliberate, realistic bug dropped into their own code so they get practice diagnosing something that actually behaves wrong — not a puzzle you solve for them. Every step below exists to protect that: the bug has to be real enough to be worth finding, and you have to actually keep your mouth shut about it afterward.

**Status: v1 draft.** This was written from the CLAUDE.md spec alone, before any real drill had been run. Refine it (bug-selection heuristics, the answer-key shape, how milestone code gets detected) once the user has actually gone through finding one — this is a plan, not a track record yet.

## Before anything else: git safety

You're about to create a branch and commit to it. Run `git status` first.

- If the working tree is dirty, stop and tell the user — don't stash or commit their in-progress work on their behalf without asking.
- If `drill/<milestone>` already exists, ask whether to reuse it, delete and replant, or pick a different milestone. Don't delete an existing branch without a clear yes.

## Step 1: Resolve the milestone

Use the milestone the user names (e.g. `/drill m1` → `M1`). If they just say "plant a drill" with no milestone, read the `## Current milestone` section of `CLAUDE.md` and use that one. If it's genuinely ambiguous (e.g. they name a milestone that isn't the current one, or CLAUDE.md's current milestone is unclear), ask — don't guess at something this consequential.

## Step 2: Confirm there's real code to break

CLAUDE.md's "hand-written by me" list is the target zone: `libs/core-model`, `libs/connector-api`, `libs/connector-*`, the concurrent fetcher, outbox relay, Kafka consumers, bulk indexer, reindexing, query building, Spring Security config, custom metrics/tracing, the RAG pipeline and `agents/agent-core`. Look at what actually exists in those paths for the milestone in question.

If it's still just scaffolding (empty modules, skeleton POMs, no real logic) — there's nothing to plant a bug *in*. Say so plainly and stop, rather than inventing a bug in generated boilerplate or forcing one in somewhere it doesn't belong.

## Step 3: Create the branch

Branch `drill/<milestone>` off the current tip of the branch the user's real milestone work lives on (typically `main`). Do this only once the working tree is confirmed clean in Step 0.

## Step 4: Pick and plant exactly one bug

Constraints, in order of importance:

- **Lives in hand-written source, never in a test file.** The user's own tests are one of the tools they'll use to catch this — a bug injected into a test isn't a production bug, it's a broken test.
- **One bug, minimal footprint.** Ideally a single file, a small diff. A drill that touches five files stops looking like an accident.
- **Realistic, not contrived.** Something that could plausibly survive code review, not `throw new RuntimeException("bug")` or a variable renamed to garbage. Think about what actually breaks in systems like this one.
- **Compiles and generally runs.** The build should still succeed. The bug should be a behavioral or logical fault, not a compile error — the point is diagnosis, not "why won't this build."
- **Actually findable.** Should be catchable through the kind of investigation the user would really do: reading the diff, running their tests, exercising the code, checking logs/metrics. Not something that requires reading your mind.

Categories worth drawing from, matched to what's likely hand-written at each milestone:

- Concurrency: a race on shared state in the fetcher, a semaphore/permit released twice or never, a missing volatile/synchronized, a bounded queue sized wrong.
- Outbox/Kafka: `FOR UPDATE SKIP LOCKED` dropped or misapplied, an off-by-one in cursor/offset handling, a consumer that acks before processing, a missing idempotency check that lets a retry double-index.
- Indexing/reindexing: wrong field mapped, an alias swap that points at the wrong index, a bulk request that silently drops failed items.
- Query building: a wrong boost/analyzer, a filter that's supposed to be a `must` and is a `should` (or vice versa), pagination that's off by one.
- Security: a filter ordered wrong so an admin endpoint slips through unauthenticated, a method-security annotation on the wrong method, an overly permissive CORS entry.
- General logic: an inverted comparison, a null check that's one branch too late, a resource that isn't closed under one specific code path.

Pick whichever fits the actual code in front of you — don't force a category that doesn't match what exists.

## Step 5: Commit it

Write a commit message that would look normal in any other commit on this project — describe the area touched, not the bug. E.g. "Adjust retry backoff constants" or "Tidy up connector cursor handling" is fine; "Introduce off-by-one bug" is not. The message shouldn't lie about what file or area changed, just stay quiet about the fact that it's now wrong.

## Step 6: Write the answer key

Create `.drills/` if it doesn't exist (already gitignored — verify with `git check-ignore .drills` if unsure) and write `.drills/<milestone>.md`:

```markdown
---
milestone: M1
branch: drill/m1
date: 2026-09-26
file: services/ingest-service/src/main/java/.../CursorStore.java
lines: 42-47
category: concurrency
---

## The bug

What's actually wrong, precisely.

## Why it's subtle

Why this would plausibly pass a quick review or casual testing.

## How to detect it

What symptom shows up, and what investigation actually surfaces the cause
(a specific test that should fail, a log line, a race that needs load to trigger, etc.)

## How to fix it

The actual fix, briefly.
```

Keep the frontmatter fields consistent across drills (`milestone`, `branch`, `date`, `file`, `lines`, `category`) — this is what turns into the triage agent's eval set later, so a future pass will want to parse these in bulk.

## Step 7: Return to the base branch

Switch back to the branch you started on (e.g. `main`) so the user's normal working state isn't sitting on the drill branch. They'll check out `drill/<milestone>` themselves when they're ready to hunt.

## Step 8: Report back — and only this

Confirm the branch exists and is ready. Do not name the file, the line, the category, or drop any hint, even an oblique one. Something like:

> Drill planted on `drill/m1`. Check it out whenever you're ready.

## Hard constraints, restated

- Never reveal the bug's location or nature anywhere the user can see it outside `.drills/<milestone>.md` — not in chat, not in the commit message, not in a PR description, not in a code comment.
- Never push the branch or open a PR for it without being explicitly asked.
- Exactly one bug per drill.
- If anything about the milestone, the branch state, or where to plant it is unclear, stop and ask rather than guessing — this is the one workflow where a wrong guess (planting in the wrong place, clobbering an existing branch) is annoying to undo cleanly.
