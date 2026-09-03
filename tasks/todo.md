# Task List: Customer Support Investigation Assistant

Companion to `tasks/plan.md`. Standing bar: `.cursor/references/definition-of-done.md`.

Build (once a `pom.xml` exists): `mvn -q compile`

Focused tests (indicative names from `docs/spec.md`; Task 20 records the real class names if they differ):

- Layer 0: `mvn test -Dtest=ToolEvalTest`
- Layer 1: `mvn test -Dtest=RetrievalEvalTest`
- Layer 2: `mvn test -Dtest=PromptEvalTest`
- Layer 3: `mvn test -Dtest=EvaluationHarnessTest`

Run (see [`README.md`](../README.md)):

```bash
mvn compile exec:java
```

---

## Phase 0: Unblock

## Task 1: Canonical H2 schema.sql + data.sql

**Description:** Move field-level H2 DDL out of architecture prose into executable SQL so Playbook fixtures have one contract. Keep the ER diagram in architecture. Seed must make every Playbook §1–8 assertion true without a Java seeder.

**Acceptance criteria:**
- [x] `src/main/resources/schema.sql` creates all nine tables from architecture §4.1 with FKs; allowed values from architecture §4.1 / Playbook (CHECK or VARCHAR + comment)
- [x] `src/main/resources/data.sql` inserts the reviewed seed from `tasks/plan.md`; `ORD-9999` is absent
- [x] `docs/architecture.md` §4.1 keeps the ER diagram, drops duplicated column lists, and links to both SQL files

**Verification:**
- [x] Tests pass: none yet (no app)
- [x] Build succeeds: n/a
- [x] Manual check: every Playbook canonical ID is in `data.sql`; related invented rows match `tasks/plan.md` Proposed seed; human has reviewed before Task 5

**Dependencies:** None

**Files likely touched:**
- `src/main/resources/schema.sql`
- `src/main/resources/data.sql`
- `docs/architecture.md`
- `docs/playbook.md` (one-line pointer that full rows live in `data.sql`, if the summary table stays)

**Estimated scope:** Medium: 3-5 files

## Task 2: ADK 1.9.0 session spike

**Description:** Source-driven confirmation of create-session REST for initial `{"customer_id":"CUST-1001"}`, whether Dev UI can set state, and whether `bind_customer` is required. Unlocks Playbook §8, personalization, and Layer 3 memory assertions.

**Acceptance criteria:**
- [x] Exact create-session path, JSON field names, `appName` / `userId` convention, and a copy-pasteable curl are recorded in `tasks/plan.md` → Spike findings
- [x] Yes/no: Dev UI can set initial session state; yes/no: `bind_customer` required (if yes, tool contract written for Task 19)
- [x] `docs/spec.md` and `docs/playbook.md` placeholders for the session endpoint are replaced with the 1.9.0 facts (not 1.6/1.7 Javadoc)

**Verification:**
- [x] Tests pass: n/a (research task)
- [x] Build succeeds: n/a until Task 3
- [x] Manual check: citations are `google-adk-dev` **1.9.0** `SessionController` / `SessionRequest` (and Dev UI source or docs). Live POST then GET on `stub-agent` proves `customer_id` stuck (verified 2026-09-03; see `README.md`)

**Dependencies:** None for reading; live check depends on Task 3

**Files likely touched:**
- `tasks/plan.md`
- `docs/spec.md`
- `docs/playbook.md`

**Estimated scope:** Small: 1-2 files (docs; three files if both spec and playbook need the curl)

## Task 3: Minimal bootstrap

**Description:** Skeleton so later tasks have a real exec command and package layout. One stub `ROOT_AGENT` only — no tools, no JPA. SQL files from Task 1 may sit unused until Task 5.

**Acceptance criteria:**
- [x] `pom.xml` pins Java 25, `google-adk` + `google-adk-dev` 1.9.0, `exec-maven-plugin` → `com.poc.adk.SupportAssistantApplication`
- [x] `SupportAssistantApplication` uses `scanBasePackages = {"com.poc.adk", "com.google.adk.web"}`; one class exposes `public static final BaseAgent ROOT_AGENT`
- [x] Effective Spring Boot version is recorded (expected 4.0.2; do not override; stop and ask if different); Dev UI dropdown shows the stub; no bean-name collision

**Verification:**
- [x] Tests pass: none required
- [x] Build succeeds: `mvn -q compile` and `mvn -q dependency:tree -Dincludes=org.springframework.boot`
- [x] Manual check: `mvn compile exec:java` with `--adk.agents.source-dir=target --server.port=8000`; open `http://localhost:8000`; dropdown populated; logs have no `ConflictingBeanDefinitionException`

**Dependencies:** None

**Files likely touched:**
- `pom.xml`
- `src/main/java/com/poc/adk/SupportAssistantApplication.java`
- `src/main/java/com/poc/adk/agents/singleagent/SingleAgent.java` (or equivalent stub)
- `src/main/resources/application.yml`

**Estimated scope:** Medium: 3-5 files

## Checkpoint: After Tasks 1-3

- [x] Seed reviewed against Playbook IDs
- [x] Spike findings section in `tasks/plan.md` filled; live POST/GET verified on `stub-agent` (2026-09-03)
- [x] Stub boots, dropdown works, Boot version known
- [ ] Review with human before proceeding

---

## Phase 1: Shared infrastructure

## Task 4: Docker Compose for Qdrant and Langfuse

**Description:** Pin the local vector store and trace UI so RAG and observability have somewhere to talk to.

**Acceptance criteria:**
- [ ] `docker/docker-compose.yml` pins Qdrant **≥ 1.15.2** (BM25 inference) and Langfuse **≥ v3.22.0** (OTLP/HTTP)
- [ ] Qdrant gRPC `:6334` and Langfuse UI `:3000` come up with `docker compose -f docker/docker-compose.yml up -d`

**Verification:**
- [ ] Tests pass: n/a
- [ ] Build succeeds: n/a
- [ ] Manual check: both containers healthy; ports match `docs/architecture.md` §1 / §7.2

**Dependencies:** None

**Files likely touched:**
- `docker/docker-compose.yml`

**Estimated scope:** Small: 1-2 files

## Task 5: domain-data — JPA matching schema.sql

**Description:** Map frozen SQL to Spring Data JPA. Prove Spring Boot loaded Playbook fixtures from `data.sql`. Do not add a second Java seeder for those tables.

**Acceptance criteria:**
- [ ] Entities + repositories match `schema.sql` 1:1, co-located by subdomain (`commerce/`, `support/`, `risk/`, `platform/` — see `architecture.md` §3); H2 file-mode `jdbc:h2:file:./data/support-assistant`; `ddl-auto=validate`; `spring.jpa.defer-datasource-initialization=true`
- [ ] Load test proves ORD-5001 DELAYED, CUST-1001 EMAIL, ORD-5010 amount 350, fraud 0.82 on ORD-5002, ORD-9999 missing
- [ ]Do not `save()` customers/orders/preferences (or any other `data.sql` row). Qdrant policy indexing is Task 11c (`PolicyChunkIndexer`), not this task.

**Verification:**
- [ ] Tests pass: seed/load test (JDBC or repositories) on H2
- [ ] Build succeeds: `mvn -q test` (or focused test class)
- [ ] Manual check: after app start, H2 console or log shows seed counts matching `data.sql`

**Dependencies:** Task 1, Task 3

**Files likely touched:**
- `src/main/resources/application.yml`
- `src/main/java/com/poc/adk/commerce/` (`customer/`, `order/`, `payment/`, `shipment/`)
- `src/main/java/com/poc/adk/support/ticket/`
- `src/main/java/com/poc/adk/risk/fraud/`
- `src/main/java/com/poc/adk/platform/` (`audit/`, `evaluation/`)
- `src/test/java/.../SeedLoadTest.java` (name may vary)

**Estimated scope:** Large: many small files (entity + repo co-located per subdomain; do not invent a second seed path)

## Task 6: model-routing

**Description:** One config property selects the ADK `BaseLlm`. Agents never hard-code a vendor. Confirm 1.9.0 builder APIs from Javadoc when writing (`spec.md` Boundaries → Always).

**Acceptance criteria:**
- [ ] `llm.provider` switches `ollama` | `gemini` | `anthropic` | `openrouter` via `ModelFactory` + `ModelRoutingProperties`
- [ ] Default is Ollama `qwen2.5:7b` at `http://localhost:11434/v1/` as in spec
- [ ] `ModelFactory` is reachable via `LlmContext` (agents are not Spring beans). `LlmContext` is populated by a `@Bean` during context refresh, not an `ApplicationRunner`. Unit test covers the enum switch with no network

**Verification:**
- [ ] Tests pass: `ModelFactory` unit test
- [ ] Build succeeds: `mvn -q test`
- [ ] Manual check: provider switch (Playbook D11) is Task 12

**Dependencies:** Task 3, Task 5

**Files likely touched:**
- `src/main/java/com/poc/adk/config/ModelRoutingProperties.java`
- `src/main/java/com/poc/adk/config/ModelFactory.java`
- `src/main/java/com/poc/adk/integration/adk/LlmContext.java`
- `src/main/java/com/poc/adk/integration/config/LlmIntegrationConfig.java`
- `src/main/resources/application.yml`
- `src/test/java/.../ModelFactoryTest.java`

**Estimated scope:** Medium: 3-5 files

## Task 7: observability

**Description:** Every later agent run can show traces in Langfuse. Export is OTLP/HTTP protobuf, not gRPC.

**Acceptance criteria:**
- [ ] `ObservabilityConfig` builds OpenTelemetry SDK with OTLP/HTTP to Langfuse `/api/public/otel`, Basic Auth, header `x-langfuse-ingestion-version: 4`
- [ ] Tracer is reachable via `TracingContext` for agent/tool/model spans. `TracingContext` is populated by a `@Bean` during context refresh, not an `ApplicationRunner`

**Verification:**
- [ ] Tests pass: config/smoke test if practical without a live Langfuse; otherwise compile + Task 12 Langfuse check
- [ ] Build succeeds: `mvn -q compile`
- [ ] Manual check: Langfuse UI reachable; a later demo (Task 12) shows one trace per turn

**Dependencies:** Task 3, Task 4, Task 5, Task 6

**Files likely touched:**
- `src/main/java/com/poc/adk/config/ObservabilityConfig.java`
- `src/main/java/com/poc/adk/integration/adk/TracingContext.java`
- `src/main/java/com/poc/adk/integration/config/ObservabilityIntegrationConfig.java`
- `src/main/resources/application.yml`

**Estimated scope:** Medium: 3-5 files

## Checkpoint: After Tasks 4-7

- [ ] Compose up; H2 seed load test green; ModelFactory test green
- [ ] Application still boots
- [ ] Review with human before tools/guardrails/RAG

## Task 8: shared-tools + Layer 0

**Description:** Java function tools wrap domain data so demo agents can look up orders, payments, shipments, and fraud without hallucinating rows.

**Acceptance criteria:**
- [ ] `OrderLookupTool`, `PaymentHistoryTool`, `ShipmentTrackingTool`, `FraudSignalTool` read via `ToolDependencies` / repositories (`ToolDependencies` populated by `ToolIntegrationConfig` `@Bean` during context refresh)
- [ ] `ToolEvalTest`: `orderLookup("ORD-5001")` → DELAYED; empty/not-found for `ORD-9999`; fraud `MULTIPLE_SHIPPING_ADDRESSES` score 0.82 on `ORD-5002`; payment for `ORD-5001` captured; shipment `SHP-7001` is `IN_TRANSIT_DELAYED` / 6 days

**Verification:**
- [ ] Tests pass: `mvn test -Dtest=ToolEvalTest`
- [ ] Build succeeds: `mvn -q test`
- [ ] Manual check: n/a (Layer 0 is the proof)

**Dependencies:** Task 5

**Files likely touched:**
- `src/main/java/com/poc/adk/tools/OrderLookupTool.java`
- `src/main/java/com/poc/adk/tools/PaymentHistoryTool.java`
- `src/main/java/com/poc/adk/tools/ShipmentTrackingTool.java`
- `src/main/java/com/poc/adk/tools/FraudSignalTool.java`
- `src/main/java/com/poc/adk/integration/adk/ToolDependencies.java`
- `src/main/java/com/poc/adk/integration/config/ToolIntegrationConfig.java`
- `src/test/java/.../ToolEvalTest.java`

**Estimated scope:** Medium: 3-5 files

## Task 9: memory-services

**Description:** Long-term preference lookup reads `customer_id` from session state (`ToolContext`), not from the user message.

**Acceptance criteria:**
- [ ] `CustomerPreferenceTool` returns EMAIL for CUST-1001 and SMS for CUST-1002 from H2 via `ToolDependencies` (preference repo lives in `commerce/customer/`; do not add a second holder)
- [ ] Tool does not parse customer id from chat text

**Verification:**
- [ ] Tests pass: Layer 0 preference assertions (same `ToolEvalTest` or a focused test)
- [ ] Build succeeds: `mvn -q test`
- [ ] Manual check: n/a until Task 19

**Dependencies:** Task 5

**Files likely touched:**
- `src/main/java/com/poc/adk/memory/CustomerPreferenceTool.java`
- `src/test/java/.../` (preference cases)

**Estimated scope:** Small: 1-2 files

## Task 10: guardrails

**Description:** Deterministic input/output callbacks applied through a shared builder helper so no demo agent is exempt.

**Acceptance criteria:**
- [ ] `InputGuardrailCallback` / `OutputGuardrailCallback`, `PiiMasker`, `PromptInjectionHeuristics`, `GuardrailAuditService` → H2
- [ ] `beforeToolCallback` / `afterToolCallback` are audit-only hooks (no second safety model); a tool invocation writes an audit row
- [ ] Layer 0 tests: injection/jailbreak flagged; card number masked; audit row written. No second LLM judge

**Verification:**
- [ ] Tests pass: focused guardrail unit tests (no LLM)
- [ ] Build succeeds: `mvn -q test`
- [ ] Manual check: Playbook guardrail queries are Task 12

**Dependencies:** Task 5, Task 6

**Files likely touched:**
- `src/main/java/com/poc/adk/guardrails/InputGuardrailCallback.java`
- `src/main/java/com/poc/adk/guardrails/OutputGuardrailCallback.java`
- `src/main/java/com/poc/adk/guardrails/PiiMasker.java`
- `src/main/java/com/poc/adk/guardrails/PromptInjectionHeuristics.java`
- `src/main/java/com/poc/adk/guardrails/GuardrailAuditService.java`

**Estimated scope:** Medium: 3-5 files

## Task 11a: Policy markdown fixtures

**Description:** Author the four policy files so chunking, citations, and the hybrid trap are real — not invented at retrieval time.

**Acceptance criteria:**
- [ ] `refund-policy.md`, `shipping-policy.md`, `fraud-policy.md`, `loyalty-policy.md` use `##` / `###` sections
- [ ] Tokens `NW-SHIP-EXC-04` and `NW-HP-1001` appear **only** in `loyalty-policy.md`; `shipping-policy.md` has weather-delay prose without those tokens

**Verification:**
- [ ] Tests pass: grep/assert in Task 11c; this task is content
- [ ] Build succeeds: n/a
- [ ] Manual check: files are under `src/main/resources/policies/`

**Dependencies:** None (content). Indexing needs Task 3–5 later.

**Files likely touched:**
- `src/main/resources/policies/refund-policy.md`
- `src/main/resources/policies/shipping-policy.md`
- `src/main/resources/policies/fraud-policy.md`
- `src/main/resources/policies/loyalty-policy.md`

**Estimated scope:** Medium: 3-5 files

## Task 11b: Chunking + EmbeddingClient

**Description:** Header-aware chunks with citation metadata, and a 768-dim embedding client that fails fast on mismatch.

**Acceptance criteria:**
- [ ] `ChunkingService` splits on `##` then `###`, caps ~400 tokens, ~50-token overlap only on size-splits; payload fields `doc_id`, `source_path`, `section_heading`, `chunk_index`
- [ ] `EmbeddingClient` calls Ollama embeddings and asserts `vector.length == 768` on first call

**Verification:**
- [ ] Tests pass: chunking unit tests (no Qdrant); embedding dimension test may mock HTTP
- [ ] Build succeeds: `mvn -q test`
- [ ] Manual check: n/a

**Dependencies:** Task 3, Task 11a

**Files likely touched:**
- `src/main/java/com/poc/adk/rag/ChunkingService.java`
- `src/main/java/com/poc/adk/rag/EmbeddingClient.java`
- `src/test/java/.../ChunkingServiceTest.java`

**Estimated scope:** Medium: 3-5 files

## Task 11c: Hybrid retriever + Layer 1

**Description:** One Qdrant `queryAsync` with dense prefetch + BM25 prefetch + RRF. Prove hybrid beats dense-only on the planted codes without an LLM.

**Acceptance criteria:**
- [ ] `HybridRetriever` + `CitationFormatter`; collection `policy_chunks` (`dense` 768 Cosine, `lexical` BM25); `PolicyChunkIndexer` `ApplicationRunner` indexes policy markdown (not SQL, not `AppServicesInitializer`); `VectorContext` holds `QdrantClient`
- [ ] `RetrievalEvalTest`: hybrid top-1 is the loyalty courtesy chunk; dense-only top-1 is not. If dense-only already wins, strengthen shipping-policy distractor — do not edit the agent prompt

**Verification:**
- [ ] Tests pass: `mvn test -Dtest=RetrievalEvalTest` (Qdrant up, no chat LLM)
- [ ] Build succeeds: `mvn -q test`
- [ ] Manual check: startup logs show collection populated

**Dependencies:** Task 4, Task 5, Task 11a, Task 11b

**Files likely touched:**
- `src/main/java/com/poc/adk/rag/HybridRetriever.java`
- `src/main/java/com/poc/adk/rag/CitationFormatter.java`
- `src/main/java/com/poc/adk/rag/PolicyChunkIndexer.java`
- `src/main/java/com/poc/adk/config/QdrantConfig.java`
- `src/main/java/com/poc/adk/integration/adk/VectorContext.java`
- `src/test/java/.../RetrievalEvalTest.java`

**Estimated scope:** Medium: 3-5 files

## Checkpoint: Layers 0-1

- [ ] `mvn test -Dtest=ToolEvalTest,RetrievalEvalTest` pass with no chat LLM
- [ ] Application builds; Qdrant `policy_chunks` populated on start
- [ ] Review with human before demo agents

---

## Phase 2: Demo agents

Each demo: versioned `src/main/resources/prompts/{agent}.v1.md`, `public static final BaseAgent ROOT_AGENT`, Layer 2 fixture beside the prompt, Layer 3 `InMemoryRunner` event assertions from `docs/spec.md`. Guardrails + OTel on every agent. Eval temperature 0.

## Task 12: demo-single-agent (Playbook §1)

**Description:** Hello-world `LlmAgent` with order lookup and short-term memory (turn 2 must not re-ask which order).

**Acceptance criteria:**
- [ ] Agent selectable in Dev UI; Playbook §1 step 1 reports DELAYED via `order_lookup`; step 2 resolves “it” to ORD-5001
- [ ] Layer 2 frozen-tool fixture and Layer 3 events include `tool_call` → `order_lookup` for step 1
- [ ] Playbook guardrail queries (Ignore instructions / DAN / card `4111-…`) refused or masked; H2 audit row where applicable
- [ ] Langfuse: one trace per turn with tool and model child spans; token usage / latency populated where available
- [ ] If a cloud key is set: switch `llm.provider`, restart, rerun §1 — same behavior, no code change (Playbook D11)

**Verification:**
- [ ] Tests pass: Layer 2/3 cases for this agent
- [ ] Build succeeds: `mvn -q test`
- [ ] Manual check: Playbook §1 + Cross-Cutting Guardrails + Observability D10; D11 if a cloud key is present

**Dependencies:** Tasks 6, 7, 8, 9, 10

**Files likely touched:**
- `src/main/java/com/poc/adk/agents/singleagent/`
- `src/main/resources/prompts/demo-single-agent.v1.md`
- `src/test/resources/eval/demo-single-agent.v1.eval.json`
- Layer 3 test class (or shared harness started here)

**Estimated scope:** Medium: 3-5 files

## Task 13: demo-sequential-investigation (Playbook §2)

**Description:** `SequentialAgent` as `ROOT_AGENT`; gather → policy → draft via `outputKey`. Missing order does not crash the pipeline.

**Acceptance criteria:**
- [ ] Stages run gather → policy_check → draft; ORD-9999 yields not-found, pipeline completes
- [ ] Layer 3 asserts stage order / `outputKey` chaining

**Verification:**
- [ ] Tests pass: Layer 3 sequential event assertions
- [ ] Build succeeds: `mvn -q test`
- [ ] Manual check: Playbook §2; Langfuse sequential spans

**Dependencies:** Task 12, Task 11c (policy stage)

**Files likely touched:**
- `src/main/java/com/poc/adk/agents/sequential/`
- `src/main/resources/prompts/` (gather / policy / draft)
- eval fixture + Layer 3 test

**Estimated scope:** Medium: 3-5 files

## Task 14: demo-parallel-investigation (Playbook §3)

**Description:** Fan-out payment / shipment / fraud inside a `SequentialAgent` root, then aggregator. Seeded fraud on ORD-5002 must appear.

**Acceptance criteria:**
- [ ] Response cites `MULTIPLE_SHIPPING_ADDRESSES` score 0.82 plus payment/shipment
- [ ] Layer 3: three check tool calls in one turn without requiring A→B→C order; aggregator follows

**Verification:**
- [ ] Tests pass: Layer 3 parallel event assertions
- [ ] Build succeeds: `mvn -q test`
- [ ] Manual check: Playbook §3; Langfuse child spans overlap in time

**Dependencies:** Task 12

**Files likely touched:**
- `src/main/java/com/poc/adk/agents/parallel/`
- prompts + eval + Layer 3 test

**Estimated scope:** Medium: 3-5 files

## Checkpoint: After Tasks 12-14

- [ ] §1–3 Playbook paths work; Layers 2–3 for these agents green
- [ ] Review with human before routing/HITL/loop

## Task 15: demo-dynamic-routing (Playbook §4)

**Description:** Coordinator emits `transfer_to_agent`; ADK `AutoFlow` resolves specialists. CI may use `TestLlm` if qwen routing is flaky.

**Acceptance criteria:**
- [ ] Refund query → billing; delay query → shipping; preference query → account
- [ ] Layer 3 asserts transfer target; flaky cases use `TestLlm`

**Verification:**
- [ ] Tests pass: Layer 2 coordinator fixture + Layer 3 transfer events
- [ ] Build succeeds: `mvn -q test`
- [ ] Manual check: Playbook §4; Langfuse nested coordinator then specialist

**Dependencies:** Task 12

**Files likely touched:**
- `src/main/java/com/poc/adk/agents/routing/`
- prompts for coordinator + specialists
- eval + Layer 3 test

**Estimated scope:** Medium: 3-5 files

## Task 16: demo-hitl-approval (Playbook §5)

**Description:** Refund above $200 pauses for confirmation. Threshold lives inside `RefundTool`, not a static `requireConfirmation` flag. Reject must not write H2.

**Acceptance criteria:**
- [ ] ORD-5010 (350) emits `adk_request_confirmation`; FunctionResponse shape matches architecture §2.3
- [ ] Approve path refunds; reject path leaves H2 unchanged; live demo is Web UI dialog, not Postman
- [ ] Layer 2 fixture: frozen order JSON amount 350 / threshold 200 → requests approval; does not claim refund completed

**Verification:**
- [ ] Tests pass: Layer 2 eval fixture + Layer 3 `InMemoryRunner` injects `confirmed: true/false`
- [ ] Build succeeds: `mvn -q test`
- [ ] Manual check: Playbook §5 Approve and Reject in Dev UI

**Dependencies:** Task 12, Task 5

**Files likely touched:**
- `src/main/java/com/poc/adk/tools/RefundTool.java`
- `src/main/java/com/poc/adk/agents/hitl/`
- prompts + `demo-hitl-approval.v1.eval.json` + Layer 3 test

**Estimated scope:** Medium: 3-5 files

## Task 17: demo-loop-refinement (Playbook §6)

**Description:** `LoopAgent` draft → critique until escalate/`exit_loop` or `maxIterations = 3`. Publisher sibling emits only the final draft.

**Acceptance criteria:**
- [ ] User sees final refined draft only; Langfuse shows 2+ iterations on the happy path
- [ ] Forced-fail / unsatisfiable case hits `max_iterations` and still publishes a best-effort draft

**Verification:**
- [ ] Tests pass: Layer 3 iteration-count / escalate assertions
- [ ] Build succeeds: `mvn -q test`
- [ ] Manual check: Playbook §6

**Dependencies:** Task 12

**Files likely touched:**
- `src/main/java/com/poc/adk/agents/loop/`
- prompts for drafter / critic / publisher
- Layer 3 test

**Estimated scope:** Medium: 3-5 files

## Checkpoint: After Tasks 15-17

- [ ] Routing, HITL, loop Layer 3 green
- [ ] Review with human before RAG + personalization demos

## Task 18: demo-rag-policy (Playbook §7)

**Description:** Policy Q&A grounded in `HybridRetriever` with citations from payload metadata, including the hybrid trap and the not-covered case.

**Acceptance criteria:**
- [ ] §7.1 cites refund-policy section; §7.2 uses both shipping and refund docs; §7.3 cites loyalty not shipping for the exception codes; §7.4 says not covered
- [ ] Layer 2 frozen-chunk fixtures; Layer 3 retrieval span / citation assertions

**Verification:**
- [ ] Tests pass: Layer 2 RAG fixtures + Layer 3
- [ ] Build succeeds: `mvn -q test`
- [ ] Manual check: Playbook §7; if §7.3 cites shipping weather, re-run Layer 1 first — do not stuff codes into the prompt

**Dependencies:** Task 11c, Task 12

**Files likely touched:**
- `src/main/java/com/poc/adk/agents/rag/`
- `src/main/resources/prompts/demo-rag-policy.v1.md`
- `src/test/resources/eval/demo-rag-policy.v1.eval.json`

**Estimated scope:** Medium: 3-5 files

## Task 19: demo-memory-personalization (Playbook §8)

**Description:** Cross-session recall of contact preference using `customer_id` from initial session state (Task 2 curl). User message must not contain the preference or customer id.

**Acceptance criteria:**
- [ ] Session CUST-1001 → email; new session CUST-1002 → SMS
- [ ] If Task 2 required `bind_customer`, that tool exists and Playbook documents it; otherwise REST/Dev UI state only

**Verification:**
- [ ] Tests pass: Layer 3 `InMemoryRunner` initial state assertions
- [ ] Build succeeds: `mvn -q test`
- [ ] Manual check: Playbook §8 using the Task 2 curl (or Dev UI if spike allowed it)

**Dependencies:** Task 2, Task 9, Task 12

**Files likely touched:**
- `src/main/java/com/poc/adk/agents/personalization/`
- prompt + Layer 3 test
- optional `bind_customer` tool only if spike required it

**Estimated scope:** Medium: 3-5 files

## Checkpoint: Demos

- [ ] Dropdown lists every `demo-*`
- [ ] Layers 2–3 pass on qwen / `TestLlm` per spec
- [ ] Playbook §1–8 runnable; weak prose → spec cloud-fallback table, not prompt stuffing
- [ ] Review with human before harness closeout

---

## Phase 3: Harness and docs

## Task 20: evaluation-harness consolidation

**Description:** Golden datasets and layered commands live in one place so a Playbook miss can be localized to Layer 0–3. Do not use the ADK Web Eval tab (unimplemented in Java).

**Acceptance criteria:**
- [ ] Fixtures under `src/test/resources/eval/`; `tasks/plan.md` records actual `mvn test -Dtest=...` names if they differ from spec
- [ ] Layers 0–3 runnable independently; no LLM-as-judge merge gate

**Verification:**
- [ ] Tests pass: `mvn test` Layers 0–3 as documented
- [ ] Build succeeds: `mvn -q test`
- [ ] Manual check: D12 flow matches spec Testing Strategy

**Dependencies:** Tasks 12–19

**Files likely touched:**
- `src/test/java/.../EvaluationHarnessTest.java` (and related)
- `src/test/resources/eval/`
- `tasks/plan.md` (command names)

**Estimated scope:** Medium: 3-5 files

## Task 21: Playbook session curl + README closeout

**Description:** A new session can start the app and run the Playbook without reading architecture. Paste the Task 2 curl into Playbook Global Setup.

**Acceptance criteria:**
- [ ] Playbook Global Setup has the exact create-session curl; README covers Ollama, `docker compose`, and the `exec:java` command
- [ ] Spec success checklist still holds; `docs/spec.md` project-structure line points at `tasks/plan.md` (not a stale `docs/plan.md`)

**Verification:**
- [ ] Tests pass: existing suite still green
- [ ] Build succeeds: `mvn -q test`
- [ ] Manual check: follow README from a clean checkout through Playbook verify steps 1–2

**Dependencies:** Task 2, Task 3, Task 20

**Files likely touched:**
- `docs/playbook.md`
- `README.md`
- `docs/spec.md` (layout pointer only)

**Estimated scope:** Small: 1-2 files (three if spec layout is updated here)

## Checkpoint: Complete

- [ ] Spec success criteria met
- [ ] Definition of Done cleared
- [ ] Ready for review — not a production launch
