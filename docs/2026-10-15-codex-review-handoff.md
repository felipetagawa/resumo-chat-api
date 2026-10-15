# Codex review handoff — Smart Reply API

Date: 2026-10-15. Audience: Sonnet 5.5 / implementing agent.

The user requested a file handoff and will direct the implementing agent to make fixes. **No implementation changes were made by this handoff.**

Reviewed API range: `56ecda9..0a25be4`.

## Re-review status — 2026-10-15

The uncommitted ReplyPolish fixes address both API findings below: ordinary `pode me chamar` closers are removed, and boundary removal repeats until no progress occurs. New tests cover preservation of substantive instructions and more than three consecutive formulas. Java 21 `sh mvnw -o test` passed 119 tests, zero failures/errors/skips; all 53 offline evaluator tests passed with Node 22.23.2.

The original findings below are retained as history. The extension's sensitive URL exclusion remains partially open; see the canonical handoff for new reproductions. No source edits or real provider/browser acceptance occurred in the re-review.

Canonical cross-repository review, including four extension findings:
`/Users/felipeokamoto/Documents/Repositorios/resumo-chat-gemini/resumo-chat-gemini/docs/superpowers/plans/2026-10-15-codex-review-handoff.md`.

Requirements are in that extension repository:
- `docs/superpowers/specs/2026-10-15-contexto-fila-design.md`
- `docs/superpowers/specs/2026-10-15-sugerir-resposta-design.md`, particularly sections 2 and 4.3.

## [P2] ReplyPolish leaves ordinary prohibited closers intact

Location: `src/main/java/com/soften/support/gemini_resumo/service/ReplyPolish.java:67-71` (`isClosingSentence()`), and the `FILLER` set above it.

Reproduced by executing the actual class source in a temporary standalone Java probe:

```text
Input:  Pode mandar o XML. Qualquer dúvida, pode me chamar!
Actual: Pode mandar o XML. Qualquer dúvida, pode me chamar!
```

The word `pode` is absent from `FILLER`, causing a sentence that is entirely a stock closer to survive. This breaks the specified deterministic guarantee at response boundaries.

Required outcome: remove this ordinary stock closer while retaining substantive sentences and requests. Add regression coverage for this input and representative variants, alongside preservation cases. Avoid expanding removal in a way that drops real technical instructions.

## [P2] The three-pass limit leaves prohibited boundary formulas

Location: the same file, lines 39-48 (`apply()`).

Reproduced:

```text
Input:  Certo! Perfeito! Claro! Compreendo. Pode mandar o XML.
Actual: Compreendo. Pode mandar o XML.
```

Four trailing stock closers similarly leave `Fico à disposição!` after the three permitted removals. The spec requires repeated removal while prohibited closers remain. The plan's example also contains the three-pass limit, so following that example alone does not satisfy the spec.

Required outcome: repeat until no further removal occurs, using progress checks to ensure termination. Cover more than three consecutive openers and closers, plus all-boilerplate output that must become the existing 502 rejection.

## Existing verification and boundaries

- Java 21, `sh mvnw -o test`: 116 tests passed, zero failures/errors/skips.
- Node 22.23.2, `node --test eval/smart-reply/*.test.mjs`: 53 tests passed, zero failures.
- Extension suite: 279 tests passed.
- Both repositories passed `git diff --check`.
- These checks predate any fixes; the demonstrated failures are coverage gaps.
- Contract handling, JSON-encoded user data, factual safeguards and rejection of empty polished replies aligned with the inspected requirements.
- No real Gemini requests, authenticated SZ QA, source edits, push or deployment occurred during the review.

Potential transcript disclosure through invalid problem-line Bean Validation logs was considered but **not demonstrated**; do not report it as a confirmed finding. Internal problem-line provider retry wording also remains ambiguous between plan and spec. These are not included among the five confirmed cross-repository findings.

Review verdict: **with fixes**. After addressing the findings, rerun relevant regression suites and leave real model/browser acceptance explicitly pending.
