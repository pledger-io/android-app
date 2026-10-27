# Agent feature workflow

Every feature request moves through four stages, each owned by a dedicated agent role. The GitHub issue is the handoff artifact: earlier stages leave their output on the issue and later stages work only from what the issue contains.

| # | Stage | Role | Produces |
|---|-------|------|----------|
| 1 | Analyze | Feature analyst | `## Functional analysis` section on the issue |
| 2 | Design | Solution architect | `## Technical impact & architecture` section on the issue |
| 3 | Implement | Senior Android developer | Pull request linked to the issue |
| 4 | Review | Review agent | PR review verdict against the issue and constraints |

Role definitions live in [`.github/agents/`](../.github/agents/) as GitHub Copilot custom agents. The same contracts apply to any agent working this repository (Devin, Codex, Cursor); `AGENTS.md` points here.

## Pipeline rules

- One GitHub issue per feature request. A stage only starts when the previous stage's artifact is on the issue.
- Each role writes only its own artifact. Roles never rewrite another role's section; disagreements go in issue comments.
- The issue carries the state, not chat history. An agent entering mid-pipeline can reconstruct the work from the issue alone.
- Stage order is strict. A pull request whose issue lacks both analysis sections is not ready for review.

## Stage 1: functional analysis

Input: a feature request (user message, issue draft, or bare idea).

The analyst reads [Features](FEATURES.md) and the relevant [feature references](feature/README.md) to anchor the request in current behavior, then edits the issue body to append:

```markdown
## Functional analysis
- Problem: <user-visible problem or opportunity>
- Actors: <who/what interacts>
- Functional changes: <numbered behavior changes, current vs. desired>
- Acceptance criteria: <observable, testable outcomes>
- Out of scope: <explicit exclusions>
- Open questions: <anything the architect or requester must answer>
```

No technical content — no classes, layers, libraries, or estimates. Ambiguity and conflicts with documented behavior go in `Open questions`, not in guesses.

## Stage 2: technical design

Input: an issue with `## Functional analysis`.

The architect reads [Architecture](ARCHITECTURE.md), [Design constraints](CONSTRAINTS.md), the relevant [ADRs](adr/README.md), and the current source, then appends:

```markdown
## Technical impact & architecture
- Affected components: <packages, screens, use cases, repositories, workers touched>
- Data model: <Room entities/migrations, DataStore, /v2/api contract changes>
- Design: <approach in a few bullets; name the patterns to follow>
- Constraints check: <which CONSTRAINTS.md rows apply and how the design respects them>
- New decisions: <anything that needs an ADR, or "none">
- Test plan: <expected unit and instrumented coverage>
- Risks: <regression, migration, or sync risks>
```

A request that conflicts with a documented constraint is not designed around silently; the conflict is recorded on the issue for the requester to resolve. The architect does not edit application source.

## Stage 3: implementation

Input: an issue with both analysis and design.

The developer works only from the issue: the functional analysis defines *what*, the design section defines *how*. A deviation is written back as an issue comment before coding continues — never silently.

- One branch per issue; the PR body links `Closes #<issue>` and describes the diff for a reader who has not seen it.
- Keep the change scoped to the design; no drive-by refactors.
- Follow ADR-backed conventions: MVVM (ADR-001), single `:app` module (ADR-011), string resources kept aligned across English/Dutch/German (ADR-018), explicit Room migrations with exported schemas.
- `./gradlew testDebugUnitTest --no-daemon` and `./gradlew lintDebug --no-daemon` must pass before the PR opens; new lint errors are not allowed.
- Update documentation when boundaries move: [Features](FEATURES.md) for behavior, [Architecture](ARCHITECTURE.md) for responsibilities, [Constraints](CONSTRAINTS.md) for limits, and a new ADR for significant decisions.

## Stage 4: review

Input: the pull request and its linked issue.

The review agent checks the PR against the issue and the repository's documented boundaries, not personal style preference:

1. **Scope** — every `## Functional analysis` change is implemented, and nothing shipped is outside it.
2. **Design fidelity** — the diff follows `## Technical impact & architecture`; deviations are justified in a PR or issue comment.
3. **Constraints** — [Design constraints](CONSTRAINTS.md) and ADRs are respected: session isolation, offline outbox limits, credential storage, string resources, Room migrations, release transport.
4. **Quality** — the test plan was executed, lint introduces no new errors, no secrets or dead code.
5. **Documentation** — Features/Architecture/Constraints/ADRs updated wherever the change moved a boundary.

The verdict is posted as a PR review: approval when the checklist passes, otherwise a numbered list of gaps mapped back to issue sections. The developer iterates until the review passes.

Return to [documentation home](README.md).
