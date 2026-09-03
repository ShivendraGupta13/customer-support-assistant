# AGENTS.md

This file guides AI coding agents (Google Antigravity, Claude Code, Cursor, etc.) working in the `customer-support-assistant` repository.

All tasks in this repository must strictly adhere to the **Karpathy Guidelines** and follow the **Agent Skills Lifecycle**.

---

## 1. Karpathy Coding Guidelines (Immutable Standards)

These four rules take priority over speed or speculation on every task:

### I. Think Before Coding
- **Never guess or assume.** State assumptions explicitly before modifying files.
- If requirements or design choices are ambiguous, present options clearly and ask.
- If a simpler approach exists, propose it and push back against unnecessary complexity.
- Name any confusion or uncertainty immediately.

### II. Simplicity First
- **Implement only the minimal code that solves the problem.** Nothing speculative.
- No single-use abstractions, premature flexibility, or unrequested configurability.
- No defensive handling for impossible scenarios.
- Benchmark: *"Would a senior engineer consider this overcomplicated?"* If yes, rewrite and simplify.

### III. Surgical Changes
- **Touch only what is necessary.** Keep git diffs minimal and focused.
- Do not reformat, refactor, or "clean up" adjacent code or comments unless requested.
- Match the existing style and architecture of the codebase.
- Clean up any unused imports or variables introduced by your changes.
- Every modified line must trace directly to the user's objective.

### IV. Goal-Driven Execution
- **Establish verifiable success criteria before coding.**
- Follow a test/verify loop:
  - Adding features: define or write the tests first &rarr; implement &rarr; verify.
  - Bug fixes: reproduce with a failing test or verifiable check &rarr; fix &rarr; verify.
  - Refactoring: ensure all existing tests pass before and after.

---

## 2. Agent Skills Lifecycle & Execution

Modular engineering skills reside in `.agents/skills/`. Use them systematically:

1. **DEFINE** &rarr; Use `.agents/skills/spec-driven-development/SKILL.md`
   - Create crisp specifications, clarify scope, and define contracts before coding.
2. **PLAN** &rarr; Use `.agents/skills/planning-and-task-breakdown/SKILL.md`
   - Deconstruct work into small, independently verifiable tasks.
3. **BUILD** &rarr; Use `.agents/skills/incremental-implementation/SKILL.md` & `.agents/skills/test-driven-development/SKILL.md`
   - Make small, vertical slices of progress with tests validating each step.
4. **VERIFY / DEBUG** &rarr; Use `.agents/skills/debugging-and-error-recovery/SKILL.md`
   - Investigate root causes systematically rather than applying blind fixes.
5. **REVIEW** &rarr; Use `.agents/skills/code-review-and-quality/SKILL.md` & `.agents/skills/code-simplification/SKILL.md`
   - Multi-axis review (correctness, readability, security, architecture, performance).
   - Leverage subagent personas in `.agents/agents/` (`code-reviewer`, `security-auditor`, `test-engineer`).
6. **SHIP** &rarr; Use `.agents/skills/shipping-and-launch/SKILL.md`
   - Final quality gates, validation checklist, and clean commits.

---

## 3. Project Context & Verification

- **Stack**: Java, Spring Boot, Maven, Docker.
- **Verification Commands**:
  - Run full test suite: `mvn test`
  - Run specific test: `mvn test -Dtest=ClassName`
  - Build project: `mvn clean package -DskipTests`
- Always verify builds and tests pass cleanly before completing changes.
