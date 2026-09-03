# Customer Support Investigation Assistant

Google ADK Java (1.9.0) learning POC for **Northwind Retail** — a support agent that investigates orders, payments, shipping, fraud, and policy questions. Interaction surface is the ADK Dev UI and REST API (no custom frontend).

Requirements: [docs/spec.md](docs/spec.md). Manual scenarios: [docs/playbook.md](docs/playbook.md).

## Quick start

1. Pull Ollama models and start the server (see [Prerequisites](#prerequisites)).
2. Start Qdrant and Langfuse: `docker compose up -d`
3. Build and run: `mvn compile exec:java`
4. Open [http://localhost:8000](http://localhost:8000) and confirm all eight `demo-*` agents appear in the dropdown.
5. Follow [docs/playbook.md](docs/playbook.md) from Global Setup.

## Prerequisites

- **Java 25** (`java -version`)
- **Maven 3.9+** (`mvn -v`)
- **Google ADK Java** `google-adk` / `google-adk-dev` **1.9.0** (Spring Boot **4.0.2** transitive — pinned in `pom.xml`)
- **Ollama** with `qwen2.5:7b` (chat) and `nomic-embed-text` (policy embeddings)
- **Docker** for Qdrant (RAG) and Langfuse (traces)

```bash
ollama pull qwen2.5:7b
ollama pull nomic-embed-text
ollama serve
```

Ollama should be at `http://localhost:11434`. Embeddings are required at startup so policy documents can be indexed into Qdrant.

## Local infra (Qdrant + Langfuse)

Pins: Qdrant **v1.15.3** (BM25), Langfuse **3.95.0** (≥ v3.22.0 OTLP).

```bash
docker compose up -d
```

- Qdrant gRPC: `localhost:6334` (REST/dashboard `:6333`)
- Langfuse UI: [http://localhost:3000](http://localhost:3000)

For trace export, create a Langfuse project and set `LANGFUSE_PUBLIC_KEY` / `LANGFUSE_SECRET_KEY` (or edit `observability.public-key` / `observability.secret-key` in `application.yml`). The app starts without keys; spans export only when credentials are set.

## Build and run

```bash
mvn compile exec:java
```

`exec-maven-plugin` launches `com.poc.adk.SupportAssistantApplication`. Agent discovery uses `adk.agents.source-dir=target` (set in `application.yml`). Server listens on [http://localhost:8000](http://localhost:8000).

Equivalent explicit form:

```bash
mvn compile exec:java \
  -Dexec.mainClass=com.poc.adk.SupportAssistantApplication \
  -Dexec.args="--adk.agents.source-dir=target --server.port=8000"
```

Use `target`, not `target/classes` — the latter breaks `CompiledAgentLoader` agent discovery.

H2 domain data persists under `./data/support-assistant` (gitignored).

## Verify

1. Open [http://localhost:8000](http://localhost:8000) and confirm the agent dropdown lists every `demo-*` agent:

   | Agent | Playbook |
   | --- | --- |
   | `demo-single-agent` | §1 |
   | `demo-sequential-investigation` | §2 |
   | `demo-parallel-investigation` | §3 |
   | `demo-dynamic-routing` | §4 |
   | `demo-hitl-approval` | §5 |
   | `demo-loop-refinement` | §6 |
   | `demo-rag-policy` | §7 |
   | `demo-memory-personalization` | §8 |

2. Check app logs: H2 seed data loaded, and policy documents indexed into Qdrant (`policy_chunks`).

Then follow [docs/playbook.md](docs/playbook.md) from Global Setup.

## Creating a customer identity for a session

Long-term preference recall (Playbook §8) uses `customer_id` in **initial session state**. `appName` must match the Dev UI dropdown (`demo-memory-personalization`). `userId` is `playbook-user`.

```bash
# Priya (CUST-1001)
curl -s -X POST "http://localhost:8000/apps/demo-memory-personalization/users/playbook-user/sessions" \
  -H "Content-Type: application/json" \
  -d '{"state":{"customer_id":"CUST-1001"}}'

# Alex (CUST-1002) — new session
curl -s -X POST "http://localhost:8000/apps/demo-memory-personalization/users/playbook-user/sessions" \
  -H "Content-Type: application/json" \
  -d '{"state":{"customer_id":"CUST-1002"}}'

# Prove state stuck: replace SESSION_ID from the create response
curl -s "http://localhost:8000/apps/demo-memory-personalization/users/playbook-user/sessions/SESSION_ID"
```

Expected: each create response includes the matching `customer_id`. Dev UI alternative: **More options → Update state**. `bind_customer` is not required.

## LLM provider switch

No code change — set `llm.provider` in `application.yml` to `ollama`, `gemini`, `anthropic`, or `openrouter`, add the provider API key if needed, and restart.

## Evaluation (Layers 0–3)

Localize a Playbook miss before editing a prompt. Full command reference: [tasks/plan.md](tasks/plan.md) → Evaluation harness commands.

```bash
mvn test -Dtest=ToolEvalTest,GuardrailEvalTest   # Layer 0 — no LLM
mvn test -Dtest=RetrievalEvalTest                # Layer 1 — Qdrant, no chat LLM
mvn test -Dtest=PromptEvalTest                   # Layer 2 — Ollama, temperature 0
mvn test -Dtest=EvaluationHarnessTest            # Layer 3 — ScriptedLlm, no LLM-as-judge
```

`mvn test` (no `-Dtest`) runs Layers 0–1 and the eight Layer 3 `*EvalTest` classes directly; `EvaluationHarnessTest` is excluded so Layer 3 is not executed twice.

Do not use the ADK Web Eval tab (unimplemented in Java: [adk-java#300](https://github.com/google/adk-java/issues/300)).
