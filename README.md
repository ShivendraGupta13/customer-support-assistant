# Customer Support Investigation Assistant

Google ADK Java (1.9.0) POC for **Northwind Retail** — a support agent that investigates orders, payments, shipping, fraud, and policy questions. Interaction surface is the ADK Dev UI and REST API (no custom frontend).

**Status:** Phase 0 bootstrap complete (`stub-agent` only). Full Playbook scenarios land in later tasks.

## Prerequisites

- **Java 25** (`java -version`)
- **Maven 3.9+** (`mvn -v`)

Ollama, Docker (Qdrant, Langfuse), and H2/JPA are **not** required for the bootstrap stub or session API check below.

## Local infra (Qdrant + Langfuse)

Needed from Phase 1 onward (RAG / observability). Pins: Qdrant **v1.15.2** (BM25), Langfuse **3.95.0** (≥ v3.22.0 OTLP).

```bash
docker compose up -d
```

- Qdrant gRPC: `localhost:6334` (REST/dashboard `:6333`)
- Langfuse UI: http://localhost:3000

## Build and run

```bash
mvn compile exec:java
```

`exec-maven-plugin` launches `com.poc.adk.SupportAssistantApplication`. Agent discovery uses `adk.agents.source-dir=target` (set in `application.yml`). Server listens on **http://localhost:8000**.

Equivalent explicit form:

```bash
mvn compile exec:java \
  -Dexec.mainClass=com.poc.adk.SupportAssistantApplication \
  -Dexec.args="--adk.agents.source-dir=target --server.port=8000"
```

Use `target`, not `target/classes` — the latter breaks `CompiledAgentLoader` agent discovery.

## Verify bootstrap

1. Open http://localhost:8000
2. Agent dropdown lists **`stub-agent`**
3. Logs show `CompiledAgentLoader initialized with 1 agents: [stub-agent]` and no `ConflictingBeanDefinitionException`

The stub has no LLM configured; chatting will fail until `model-routing` (Task 6). Discovery and session APIs work without Ollama.

## Verify session state (Task 2 spike)

Playbook §8 binds `customer_id` in **initial session state** at create time. With the app running:

```bash
# Create session for Priya (appName = agent name in dropdown)
curl -s -X POST "http://localhost:8000/apps/stub-agent/users/playbook-user/sessions" \
  -H "Content-Type: application/json" \
  -d '{"state":{"customer_id":"CUST-1001"}}'

# Replace SESSION_ID from the "id" field in the response
curl -s "http://localhost:8000/apps/stub-agent/users/playbook-user/sessions/SESSION_ID"
```

Expected: both responses include `"state":{"customer_id":"CUST-1001"}`.

**Live verified:** 2026-09-03 — POST + GET on `stub-agent` returned `customer_id=CUST-1001` (ADK 1.9.0 `SessionController` / `SessionRequest`).

For Playbook §8 (after Task 19), use `appName=demo-memory-personalization` instead of `stub-agent`.

## Project docs

| Doc | Purpose |
| --- | --- |
| [`docs/spec.md`](docs/spec.md) | Requirements and success criteria |
| [`docs/architecture.md`](docs/architecture.md) | Runtime design and diagrams |
| [`docs/playbook.md`](docs/playbook.md) | Manual test script per feature |
| [`tasks/plan.md`](tasks/plan.md) | Implementation plan and ADK session spike findings |
| [`tasks/todo.md`](tasks/todo.md) | Task checklist |

H2 DDL and seed: [`src/main/resources/schema.sql`](src/main/resources/schema.sql), [`src/main/resources/data.sql`](src/main/resources/data.sql).

## Tech stack (pinned)

| Piece | Version |
| --- | --- |
| Java | 25 |
| `google-adk` / `google-adk-dev` | 1.9.0 |
| Spring Boot (transitive) | 4.0.2 |
