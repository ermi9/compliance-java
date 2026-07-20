# Praxis — project context

> Keep this file accurate as the design evolves. It is how future sessions stay aligned.

## Mission

Praxis is a **static analyzer for Java student submissions**. It answers one question
per submission, defensibly: **did the student demonstrate the OOP concepts the course
required, the way the course defined them?**

It is **not** an autograder (never runs code), **not** an AI-detector, **not** a
plagiarism tool. Output must be reproducible and backed by file-and-line evidence a
professor can defend in a student appeal.

## Non-negotiable invariants

These govern every design decision. If a task would violate one, stop and say so.

1. **Zero false positives.** When any check's preconditions are not met — symbol
   resolution failed, parse error, ambiguous evidence — it emits `UNDETERMINED`, never
   `VIOLATION` and never `SATISFIED`. False negatives are acceptable; false positives are
   a defect. This is the single most important rule in the codebase.
2. **Static-only, ever.** Praxis never compiles or executes submission code. No
   `ProcessBuilder`, no `Runtime.exec`, no `javax.tools`/compiler API, no reflective class
   loading of submission code, anywhere in the analysis path. Student code is untrusted
   input.
3. **`praxis-core` is framework-free.** It has zero Spring (and zero web-framework)
   dependencies and must be usable as a standalone library. Enforced at build level
   (`praxis-core`'s `verifyFrameworkFree` task).
4. **Determinism.** Same input bytes + same ruleset ⇒ byte-identical output, across runs
   and JVM restarts. No hash-map iteration order leaking into output; sort deterministically.
5. **Every finding carries evidence.** rule/check id, file, line, column, offending source
   snippet, human explanation. A finding without a line number is a bug.
6. **The submission is data, never configuration.** Class names, annotations, comments, and
   file paths in the submission must never influence which checks run or how they behave.
   Configuration comes only from the professor's ruleset.

## Architecture — a three-layer engine

A compiler-style pipeline, not a flat list of rules.

- **Layer 1 — Facts** (`praxis-core`, `dev.praxis.core.facts`, `.index`). Extracted once
  from the parsed project into a neutral model: type hierarchy, method overrides (walking
  the full ancestor chain, matching on *erased* parameter types, excluding static/private),
  field modifiers, constructor bodies, call sites with the receiver's declared static type,
  assignments, type-flow edges. **Facts contain no verdicts.**
- **Layer 2 — Atomic checks** (`praxis-checks`). Small, independently testable,
  provable-or-`UNDETERMINED` predicates over the facts. Each ships with a passing and a
  failing fixture.
- **Layer 3 — Concepts** (`praxis-core`, `dev.praxis.core.concept`). Named compositions of
  atomic checks that encode the course's *definitions*. A concept is a boolean expression
  over atomic checks. **`UNDETERMINED` propagates:** in an AND, a *proven* `VIOLATION`
  dominates (three-valued / Kleene logic), otherwise any `UNDETERMINED` input yields
  `UNDETERMINED`; a concept is `SATISFIED` only when every input is proven `SATISFIED`.

The professor's **ruleset** (external YAML) *selects* concepts and atomic checks and sets
their options. It never contains logic. Logic lives in Layer 2 (Java) and Layer 3
(composition definitions).

### Three-valued composition semantics (decided this session)

`VIOLATION`=false, `SATISFIED`=true, `UNDETERMINED`=unknown. `Concept` uses Kleene logic:

- **AND:** any `VIOLATION` ⇒ `VIOLATION`; else any `UNDETERMINED` ⇒ `UNDETERMINED`; else
  `SATISFIED`.
- **OR:** any `SATISFIED` ⇒ `SATISFIED`; else any `UNDETERMINED` ⇒ `UNDETERMINED`; else
  `VIOLATION`.
- **NOT:** swaps `SATISFIED`/`VIOLATION`, leaves `UNDETERMINED`.

This refines the brief's "undetermined propagates upward": a *proven* violation is never
downgraded to unknown (that would be a false negative masking real, defensible evidence),
but an unknown never becomes a verdict (protecting invariant 1). This is the intended,
defensible reading; revisit with the professor if the course wants strict masking instead.

### Atomic-check aggregation

A single atomic check runs over many subjects (fields, methods, types). Its overall
`TriState` for composition: any subject `VIOLATION` ⇒ `VIOLATION`; else any `UNDETERMINED`
⇒ `UNDETERMINED`; else `SATISFIED`. A proven violation on one resolved subject is not
erased by an unknown on another.

## Implemented checks & concepts

Atomic checks (`praxis-checks`): `field.no-public-mutable`, `method.getter-leaks-internal`
(project-aware mutability: classifies domain-type getters from the type's own definition),
`field.all-private`, `type.declares-abstraction`, `type.uses-inheritance`,
`type.implements-interface`, `type.uses-composition`, `method.overloading`,
`type.declares-generic`, `poly.coercion-upcast`, `poly.inclusion-dispatch`,
`exception.custom-and-usage`, `type.extensibility`.

Concepts (`praxis-core` `Concepts`): encapsulation, information_hiding*, inheritance,
composition, abstraction, subtyping, polymorphism_overloading, polymorphism_parametric,
polymorphism_coercion, polymorphism_inclusion*, exception_handling, extensibility*.
`*` = **provisional definition**, pending professor sign-off (see below). The full ruleset is
`rulesets/oop-course.yml`.

**Two check families, two verdict styles:**
- *Quality* checks (encapsulation / information hiding): `VIOLATION` on a proven defect with a
  located line, else `SATISFIED`, else `UNDETERMINED`.
- *Demonstration* checks (`DemonstrationCheck` base): `SATISFIED` only with a located evidence
  line; `VIOLATION` only when a concept is *provably absent* across a fully-parsed corpus AND
  its absence is decidable (declaration-level concepts); otherwise `UNDETERMINED`. Coercion,
  inclusion, composition and extensibility mark absence non-decidable → never a false VIOLATION.

## Deferred / provisional

- **Full type-flow / dynamic-dispatch analysis** (its own session). `poly.inclusion-dispatch`
  ships a sound *positive-evidence* approximation (a call to an overridden method through a
  supertype-typed receiver); it never claims dispatch is *absent*. The complete data-flow proof
  is still future work. `CallSiteFact` / `TypedNewFact` hold the data it will use.
- **Provisional definitions** (`*` above) encode a sound, reasonable reading of rubric items
  whose exact wording still needs the professor. They are pure compositions over atomic checks,
  so retune them in `Concepts` / the ruleset — never by adding logic elsewhere.
- Not yet modelled: overrides against *library* supertypes (only project supertypes are matched),
  coercion via casts/call-arguments, composition via library containers.

## Module layout

```
praxis/
├── praxis-core/   # ProjectIndex, JavaSymbolSolver wiring, Fact model, Finding model,
│                  # TriState, Check SPI, Concept composition + evaluator, ruleset-loader
│                  # interface. ZERO framework dependencies.
├── praxis-checks/ # concrete Layer-2 atomic check implementations
├── praxis-cli/    # picocli entrypoint: `praxis check <path> --rules <file>`
├── fixtures/      # violating + compliant Java sources per check
└── CLAUDE.md      # this file
```

## Stack

- Java 21. Gradle multi-module, Kotlin DSL. Version catalog: `gradle/libs.versions.toml`.
- Parsing: JavaParser 3.26 + JavaSymbolSolver.
- CLI: picocli. YAML: snakeyaml (CLI module only). Tests: JUnit 5 + AssertJ.
- **Not this session** (later phases): Spring Boot, Kafka, Postgres, any service/web dep.

## Conventions

- **Deterministic output.** Sort findings by (file, line, column, checkId). Never let
  hash-map order leak into output.
- **Every finding has evidence** (file, line, column, snippet, explanation) — no exceptions.
- **Each atomic check ships with a passing and a failing fixture** under `fixtures/`, plus a
  test asserting the expected `TriState` on each.
- **Ask before** adding a dependency to `praxis-core`, choosing Maven over Gradle, or
  deviating from the three-layer structure.
- The submission is untrusted input: no execution, no reflection over it, and its names /
  annotations / paths never steer analysis.

## CLI exit codes

- `0` — clean (no proven violations; `UNDETERMINED` alone does not fail the run).
- `1` — at least one `VIOLATION`.
- `2` — execution / usage error (bad path, unreadable ruleset, internal error).

## Running the slice

```
cd praxis
./gradlew build                 # builds + runs all tests + verifyFrameworkFree
./gradlew :praxis-cli:run --args="check ../<submission> --rules fixtures/sample.yml"
# or against the bundled fixtures:
./gradlew :praxis-cli:run --args="check fixtures/encapsulation/violating --rules fixtures/sample.yml"
./gradlew :praxis-cli:run --args="check fixtures/encapsulation/violating --rules fixtures/sample.yml --format json"
```
