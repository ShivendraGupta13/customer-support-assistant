# Implementation Plan: Customer Support Investigation Assistant

## Overview

Build a single Spring Boot / Maven POC that hosts Google ADK Java (1.9.0) Dev UI and REST, using the Northwind Retail order-investigation scenario to exercise every topic in `Google ADK Java POC.md`. Requirements live in `docs/spec.md`, runtime design in `docs/architecture.md`, and manual proofs in `docs/playbook.md`. This file is the Plan-phase artifact those docs already refer to. Task details and checkpoints live in `tasks/todo.md`.

Sources of truth for this plan: approved spec / architecture / playbook, plus the planning discussion that chose executable H2 SQL over a separate `schema.md`. Cursor plan-mode notes may be consulted; they are not the skill output.

## Architecture Decisions

- **H2 contract is SQL, not a new markdown schema doc.** `src/main/resources/schema.sql` is the column/FK contract entities must match. Hibernate `ddl-auto=create-drop` creates tables from those entities at startup. `src/main/resources/data.sql` is the only place customers, orders, payments, shipments, tickets, preferences, and fraud rows are inserted.
- **No `bootstrap/AppServices` / `ToolDependencies`.** Agent classes are not Spring beans, so they cannot `@Autowired`. Chat/OTel/Qdrant use small static bridges in `integration/adk/` (`LlmContext`, `TracingContext`, `VectorContext`), populated by a `@Bean` during context refresh — not by an `ApplicationRunner`. Each tool is its own Spring bean and receives only the repository it needs. Tool methods stay static so `FunctionTool.create(Class, methodName)` can run while `CompiledAgentLoader` reads `ROOT_AGENT` (constructor-time, before a request); the repo is resolved at invocation.
- **Qdrant indexing is a separate runner.** `PolicyChunkIndexer` (Task 11c) is an `ApplicationRunner` that chunks/embeds policy markdown into `policy_chunks`. It must never `save()` / `INSERT` Northwind tables. A Java seeder plus `data.sql` would drift.
- **Domain packages are subdomain-colocated.** Entity + repository live together under `commerce/`, `support/`, `risk/`, and `platform/` — not a flat `domain/` + `domain/repository/`. `@SpringBootApplication` on `com.poc.adk` is enough for JPA scans.
- **ER diagram stays in `architecture.md` §4.1.** Column/enum tables that duplicate DDL are removed and replaced with pointers to the SQL files.
- **Module build order follows `spec.md`.** Do not reorder. Each `demo-*` agent is one Playbook scenario — that is this POC’s vertical slice (ADK Web UI is the only interaction surface).
- **Session identity for Playbook §8** is initial `session.state.customer_id` at session create. Exact REST path/body, Dev UI state support, and whether `bind_customer` is needed are confirmed in Task 2 against `google-adk-dev` **1.9.0** (not older Javadoc). `bind_customer` is not built unless that spike says Dev UI cannot set state; if needed it lands in Task 19, not bootstrap.
- **Do not declare** beans named `sessionService`, `artifactService`, `memoryService`, `objectMapper`, `mappingJackson2HttpMessageConverter`, `openTelemetrySdk`, `sdkTracerProvider`, `apiServerSpanExporter`, or `apiServerSpanExporterConfig`.
- **Plan-phase pins (Architecture §9):** `google-adk` / `google-adk-dev` **1.9.0**, `java.version` **25**, Qdrant image **≥ 1.15.2**, Langfuse **≥ v3.22.0**. **Effective Spring Boot (Task 3):** **4.0.2** via `google-adk-dev` (confirmed with `mvn dependency:tree -Dincludes=org.springframework.boot`; do not override). `CompiledAgentLoader` requires `--adk.agents.source-dir=target` (the Maven `target/` dir). Passing `target/classes` treats package roots (`com/`) as agent units and finds **0** agents.
- **Hybrid retrieval** is BM25 sparse (`qdrant/bm25`) + nomic dense (768-d) fused with RRF `k=60`. Semantic memory is `policy_chunks` only. Long-term memory is H2 `CustomerPreference`.
- **Later implementation skills (not this session):** `source-driven-development` for every ADK/Qdrant/Langfuse API; `incremental-implementation` + `test-driven-development` per task; `browser-testing-with-devtools` for Dev UI checks; standing bar is `.cursor/references/definition-of-done.md`.

### Dependency graph

```
schema.sql + data.sql (Task 1)
    │
    ├── domain-data / JPA (Task 5)
    │       ├── shared-tools (Task 8)
    │       ├── memory-services (Task 9)
    │       └── guardrail audit tables (Task 10)
    │
    └── (unused until Task 5; files may exist from Task 1)

ADK session contract (Task 2) ──► Playbook §8, Task 19, Layer 3 memory tests

bootstrap stub (Task 3)
    ├── docker-compose (Task 4, parallel)
    ├── model-routing (Task 6, needs 3 — ModelFactory + LlmContext via @Bean)
    └── observability (Task 7, needs 4 + 5 + 6)

rag-index (Tasks 11a–11c) needs 3, 4, 5
demo-single-agent (Task 12) needs 6–10
later demos need 12’s infra; rag-policy needs 11c; personalization needs 2 + 9
evaluation-harness (Task 20) needs all demos
```

### Proposed seed (review in Task 1)

Playbook IDs kept exactly. Related rows Playbook omitted:

- Customers: `CUST-1001` Priya Shah / `priya.shah@example.com` / GOLD; `CUST-1002` Alex Kim / `alex.kim@example.com` / SILVER
- Orders: `ORD-5001` Wireless Headphones **129.99 DELAYED**; `ORD-5010` Wireless Headphones **350.00 REFUND_REQUESTED**; `ORD-5002` Smart Watch **249.00 PLACED** (invented so parallel risk assessment has a product/amount). **Never seed `ORD-9999`.**
- Payments: `PAY-9001` captured 129.99 on 5001; `PAY-9010` captured 350 on 5010; `PAY-9002` captured 249 on 5002
- Shipments: `SHP-7001` SwiftShip `IN_TRANSIT_DELAYED` days_delayed=6; `SHP-7002` for 5002 (parallel tool). No shipment on 5010 (HITL does not need it).
- Ticket: `TCK-3001` Priya / ORD-5001 / `shipping_delay` / `RESOLVED` (domain data only, not a memory demo)
- Fraud: `FRD-8002` on ORD-5002, `MULTIPLE_SHIPPING_ADDRESSES`, **0.82**
- Preferences: CUST-1001 **EMAIL**, CUST-1002 **SMS**
- `GUARDRAIL_AUDIT_LOG` / `EVALUATION_RUN`: empty at seed

## Task List

Index only. Full acceptance criteria, verification, dependencies, and files are in `tasks/todo.md`.

### Phase 0: Unblock

- [x] Task 1: Canonical H2 `schema.sql` + `data.sql`; architecture ER points at SQL
- [x] Task 2: ADK 1.9.0 session spike
- [x] Task 3: Minimal bootstrap (pom + `SupportAssistantApplication` + one stub `ROOT_AGENT`)

### Checkpoint: After Tasks 1–3

- [x] Seed reviewed against Playbook IDs
- [x] Spike curl written; live POST/GET verified on `stub-agent` (2026-09-03)
- [x] Stub boots; dropdown loads; no bean collisions; effective Boot version known
- [ ] Review with human before Phase 1
### Phase 1: Shared infrastructure

- [x] Task 4: Docker Compose (Qdrant ≥ 1.15.2, Langfuse ≥ v3.22.0)
- [x] Task 5: `domain-data` — JPA matches `schema.sql`; `data.sql` load proven
- [x] Task 6: `model-routing`
- [x] Task 7: `observability`
- [x] Task 8: `shared-tools` + Layer 0
- [x] Task 9: `memory-services`
- [x] Task 10: `guardrails`
- [x] Task 11a: Policy markdown fixtures
- [x] Task 11b: Chunking + `EmbeddingClient`
- [x] Task 11c: Hybrid retriever + Layer 1

### Checkpoint: Layers 0–1

- [x] `mvn test -Dtest=ToolEvalTest,RetrievalEvalTest` pass with no chat LLM
- [ ] Qdrant `policy_chunks` populated on app start
- [ ] Review with human before demo agents

### Phase 2: Demo agents (one Playbook section each)

- [x] Task 12: `demo-single-agent` (Playbook §1)
- [x] Task 13: `demo-sequential-investigation` (§2)
- [x] Task 14: `demo-parallel-investigation` (§3)
- [x] Task 15: `demo-dynamic-routing` (§4)
- [x] Task 16: `demo-hitl-approval` (§5)
- [x] Task 17: `demo-loop-refinement` (§6)
- [ ] Task 18: `demo-rag-policy` (§7)
- [ ] Task 19: `demo-memory-personalization` (§8)

### Checkpoint: Demos

- [ ] Dropdown lists every `demo-*`
- [ ] Layers 2–3 pass on qwen / `TestLlm` per spec
- [ ] Playbook §1–8 runnable; weak prose uses spec cloud-fallback table, not prompt stuffing

### Phase 3: Harness and docs

- [ ] Task 20: `evaluation-harness` consolidation
- [ ] Task 21: Playbook session curl + README closeout

### Checkpoint: Complete

- [ ] Spec success criteria met
- [ ] Definition of Done cleared (correctness, docs, human review)
- [ ] Ready for review — not a production launch

## Parallelization Opportunities

- **Safe to parallelize:** Task 1 ∥ Task 2; Task 4 ∥ Tasks 1–3; Tasks 8 ∥ 9 after Task 5; Task 11a ∥ early Phase 1
- **Must be sequential:** Task 5 after 1 and 3; Task 6 after 5; Task 7 after 4, 5, and 6; Task 11c after 11a/11b and 4; each demo after its infra; Task 19 after Task 2’s bind_customer decision
- **Needs coordination:** session JSON contract (Task 2) before personalization and Layer 3 memory tests

## Risks and Mitigations

| Risk | Impact | Mitigation |
|---|---|---|
| ADK 1.9.0 session/Dev UI differs from older Javadoc | High | Task 2 fails fast against 1.9.0 source; Playbook §8 blocked until recorded |
| Bean-name collision on bootstrap | High | Task 3 is the fail-fast; never add competing `@Bean` names |
| Dual seed (Java `save()` plus `data.sql`) | High | Only `data.sql` inserts H2 business rows. `PolicyChunkIndexer` later upserts Qdrant policy chunks. No `AppServices` holder. |
| Hybrid fixture too weak (dense-only already ranks loyalty #1) | Med | Strengthen `shipping-policy.md` distractor; never fix via agent prompt |
| Task 5 entity/repo file count exceeds ~5 | Low | Schema is frozen in Task 1; one load-test is the slice. Split only if the session cannot finish. |
| qwen2.5:7b weak prose | Low | Layers 0–1 never need cloud; Layer 4 is best-effort per spec |

## Open Questions

- ~~Exact `appName` and `userId` for the create-session curl — Task 2~~ → resolved in Spike findings (`appName` = agent `name()`, `userId` = `playbook-user`)
- ~~Whether Dev UI can set initial session state — Task 2~~ → yes (Update state → `stateDelta`)
- ~~Whether `bind_customer` is required — Task 2~~ → no
- `ORD-5002` product/amount invented in Proposed seed — change in Task 1 if a different fixture is preferred (kept as Smart Watch / 249.00)

## Spike findings (fill in Task 2)

- **Create-session path:** `POST /apps/{appName}/users/{userId}/sessions` (service-generated id). Alternate: `POST /apps/{appName}/users/{userId}/sessions/{sessionId}` when the client supplies the id.
- **Request body (JSON field names):** optional `SessionRequest` with a single field `state` (map). Example: `{"state":{"customer_id":"CUST-1001"}}`. Omitting the body (or `state: null`) yields empty initial state.
- **Copy-paste curl for `CUST-1001`:**

```bash
# appName must equal the selected agent's BaseAgent.name() (Dev UI dropdown value).
# After Task 3 stub: stub-agent. For Playbook §8: demo-memory-personalization (once Task 19 lands).
curl -s -X POST "http://localhost:8000/apps/stub-agent/users/playbook-user/sessions" \
  -H "Content-Type: application/json" \
  -d '{"state":{"customer_id":"CUST-1001"}}'

# Prove state stuck (after Task 3 boot): replace SESSION_ID from the create response.
curl -s "http://localhost:8000/apps/stub-agent/users/playbook-user/sessions/SESSION_ID"
```

- **`appName` / `userId` convention:** `appName` = `ROOT_AGENT.name()` as registered by `CompiledAgentLoader` (same string as the Dev UI agent dropdown). `userId` is an opaque path segment; Playbook demos use `playbook-user`.
- **Dev UI can set initial state: yes** — not via New Session create (that posts empty/`__session_metadata__` only). Use **More options → Update state**, edit JSON (e.g. `{"customer_id":"CUST-1001"}`), then send the first message; Dev UI applies it as `stateDelta` on `/run` / `/run_sse`. Prefer the REST create-session curl above for Playbook §8 reproducibility.
- **`bind_customer` required: no** — REST create with `state.customer_id` and/or Dev UI Update state are sufficient. No tool contract for Task 19.
- **1.9.0 sources cited:**
  - [`SessionController.java` @ v1.9.0](https://github.com/google/adk-java/blob/v1.9.0/dev/src/main/java/com/google/adk/web/controller/SessionController.java) — `@PostMapping("/apps/{appName}/users/{userId}/sessions")`, `@GetMapping(".../sessions/{sessionId}")`
  - [`SessionRequest.java` @ v1.9.0](https://github.com/google/adk-java/blob/v1.9.0/dev/src/main/java/com/google/adk/web/dto/SessionRequest.java) — `@JsonProperty("state")`
  - Dev UI `dev/browser/main-*.js` @ v1.9.0 — `createSession(userId, appName, state?)`; `updateState()` dialog → `updatedSessionState` → `stateDelta` on run
- **Live verification (Tasks 2 + 3):** 2026-09-03 — booted `stub-agent`; `POST /apps/stub-agent/users/playbook-user/sessions` with `{"state":{"customer_id":"CUST-1001"}}`; `GET .../sessions/{id}` returned the same `customer_id`. Recorded in [`README.md`](../README.md).

## Standing Definition of Done

Every task is done only when its acceptance criteria **and** `.cursor/references/definition-of-done.md` are met (runtime verification, tests that failed without the change, no unrelated refactors, docs match current behavior).
