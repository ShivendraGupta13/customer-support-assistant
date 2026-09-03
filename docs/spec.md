# Spec: Customer Support Investigation Assistant (Google ADK Java POC)

## Objective

Build a single Spring Boot / Maven application as the interaction surface for learning every major capability in `Google ADK Java POC.md`:

- Agent workflows (sequential / parallel / loop / routing / HITL)
- Multi-agent composition and tool calling
- Three memory types (short-term, long-term, semantic), RAG, guardrails
- Observability (OTel + Langfuse) and evaluation

Our own `@SpringBootApplication` scans both `com.poc.adk` and ADK's `com.google.adk.web` (from `google-adk-dev`), so the Dev UI and REST API come up in our context. One concrete business scenario teaches all of the above.

This is a **technology-learning POC**, not a production system.

**Success is measured by:** for every topic in the POC checklist, there is:

1. A runnable demo you can drive from the ADK Web UI / REST API
2. A Playbook entry telling you exactly what to type and what to expect
3. An Architecture diagram showing the runtime path that query takes

**Audience and scenario:**

- **User** — you (the developer), using the ADK Web UI / REST API directly; no custom frontend
- **Business scenario** — e-commerce order investigation: a support agent investigates order status, payment/refund disputes, shipping delays, and fraud flags for fictional online store "Northwind Retail", backed entirely by synthetic/mocked data



## Tech Stack


| Concern          | Choice                                                                                                                                                                                                                                     | Notes                                                                                                                                                                                                                                                                                                                  |
| ---------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------ | ---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| Language         | Java 25                                                                                                                                                                                                                                    | `google-adk` jars are compiled for Java 17 target; run fine as a dependency on a Java 25 JDK (forward-compatible bytecode).                                                                                                                                                                                            |
| Framework        | Spring Boot **4.0.2** (transitive via `google-adk-dev` 1.9.0 — do not override unless Architecture spike validates)                                                                                                                        | `SupportAssistantApplication` **is** the `@SpringBootApplication`, with `scanBasePackages = {"com.poc.adk", "com.google.adk.web"}`. See Application Bootstrap below.                                                                                                                                           |
| Build            | Maven                                                                                                                                                                                                                                      | Single module, single `pom.xml`. `exec-maven-plugin` launches `com.poc.adk.SupportAssistantApplication`. Pin: `google-adk` / `google-adk-dev` **1.9.0**, `java.version` **25**. Run `mvn -q dependency:tree -Dincludes=org.springframework.boot` after first build to confirm effective Spring Boot version. |
| Agent framework  | `google-adk` + `google-adk-dev` (currently 1.9.x)                                                                                                                                                                                          | Core agents/tools/workflows + dev web server & REST API.                                                                                                                                                                                                                                                               |
| Default LLM      | Ollama, `qwen2.5:7b`, via local `ChatCompletionsLlm` over ADK `ChatCompletionsHttpClient` (ADK 1.9.0 has no `OpenAiCompatibleLlm` yet)                                                                                                                                                                             | Ollama must already be running locally (`ollama serve`, model pulled) — out of scope for us to install.                                                                                                                                                                                                                |
| Alternate LLMs   | Gemini (native ADK `Gemini`), Anthropic (native ADK `Claude`), OpenRouter (same `ChatCompletionsLlm` path as Ollama)                                                                                                              | Switchable via one config property, no code change.                                                                                                                                                                                                                                                                    |
| Relational store | H2 (file-mode, persisted to disk, not in-memory-only)                                                                                                                                                                                      | Sufficient for a single-instance POC: mock domain data, long-term memory (customer preferences), guardrail & eval logs.                                                                                                                                                                                                  |
| Vector store     | Qdrant (Docker) via `io.qdrant:qdrant-client` (Java)                                                                                                                                                                                       | Semantic memory + RAG document embeddings.                                                                                                                                                                                                                                                                             |
| Observability    | OpenTelemetry SDK (Java) → OTLP → Langfuse (Docker, self-hosted)                                                                                                                                                                           | Traces/spans for agent runs, tool calls, model calls; Langfuse as the trace UI + LLM-specific analytics (cost, tokens).                                                                                                                                                                                                |
| Embeddings       | Ollama `nomic-embed-text` via a small `EmbeddingClient` (HTTP to Ollama `/api/embeddings` or the OpenAI-compatible `/v1/embeddings` endpoint). **Not** routed through ADK `BaseLlm` — that abstraction is chat completion, not embeddings. | 768-dim dense vectors. Swap the client implementation later if a cloud key is set; do not block the default path on a cloud embedder.                                                                                                                                                                                  |




## Business Scenario & Mock Domain

Fictional store **Northwind Retail**. Synthetic data, owned entirely by us — field-level schema is an Architecture-phase concern, not a Spec concern.

### Relational domain data (H2)

Seeded into H2:

- Customer, Order, Payment, Shipment
- Ticket (support-case domain data — not a memory demo; no ticket-history memory tool)
- Customer Preference (long-term memory substrate)
- Fraud Signal (feeds the fraud-check specialist)

### Policy knowledge base (Qdrant)

Markdown documents, not database rows:

- Refund policy, shipping policy, fraud policy, loyalty terms
- Authored with `##` sections so header-aware chunking and citations are real
- `loyalty-policy.md` also holds the planted hybrid-search clause (`NW-SHIP-EXC-04` / `NW-HP-1001`); see RAG Indexing

These files are what RAG chunks, embeds, and indexes into Qdrant — no relational table involved.

There is no real external system to call; everything above is synthetic and generated/authored by us.

## Capability Map

This request bundles many independently testable capabilities. Below is the decomposition into build modules (Java packages within the single app), each traceable to POC topics. It is the backbone of the Playbook and Architecture.

### Modules

| Module id                       | Responsibility                                                                                                                                                                       | POC topics covered                                                                                         | Depends on                                                                        |
| ------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------ | ---------------------------------------------------------------------------------------------------------- | --------------------------------------------------------------------------------- |
| `domain-data`                   | JPA entities + H2 schema + synthetic seed data                                                                                                                                       | — (foundation)                                                                                             | —                                                                                 |
| `model-routing`                 | `llm.provider` config + `ModelFactory` producing the right ADK `BaseLlm` (Ollama/Gemini/Anthropic/OpenRouter)                                                                        | Model configuration                                                                                        | —                                                                                 |
| `observability`                 | OTel SDK setup, OTLP exporter to Langfuse, span/event capture around agent runs, tool calls, model calls                                                                             | Observability (traces, agent events, tool execution, token usage, latency, errors, cost, logs, metrics)    | `model-routing`                                                                   |
| `shared-tools`                  | Java function tools wrapping domain data: order lookup, payment history, shipment tracking, fraud signal check                                                                       | Tool Calling (Java Functions, Database Tool, Custom Tools)                                                 | `domain-data`                                                                     |
| `memory-services`               | Short-term (session state helpers), Long-term (H2-backed customer preferences via `CustomerPreferenceTool`). Semantic memory is RAG over `policy_chunks` (`rag-index`), not a second Qdrant collection | Memory (3 types) + comparison matrix — see [Memory](#memory) below and `architecture.md` §6.8 (D8) | `domain-data`                                                                     |
| `guardrails`                    | Before/after model & tool callbacks: input/output guardrails, PII masking, prompt-injection/jailbreak heuristics, basic content moderation                                           | Guardrails (all sub-topics)                                                                                | `model-routing`                                                                   |
| `rag-index`                     | Markdown-header chunking + dense embeddings + BM25 sparse + RRF hybrid retrieval + citation formatting                                                                               | RAG (retrieval, embeddings, vector store, hybrid search, context injection, citation)                      | `domain-data`                                                                     |
| `demo-single-agent`             | Single `LlmAgent` + tool calling + short-term memory — the "hello world" baseline                                                                                                    | Single Agent, Prompt management, Context window                                                            | `shared-tools`, `memory-services`, `guardrails`, `observability`, `model-routing` |
| `demo-sequential-investigation` | `SequentialAgent`: gather order+payment+shipment → check policy (RAG) → draft resolution                                                                                             | Sequential workflow, Agent composition via `outputKey` chaining, Retry & error recovery on tool failure    | `demo-single-agent`'s shared infra                                                |
| `demo-parallel-investigation`   | `ParallelAgent` fan-out (payment / shipment / fraud checks) → aggregator                                                                                                             | Parallel workflow, fan-out/fan-in composition                                                              | same infra                                                                        |
| `demo-dynamic-routing`          | Coordinator `LlmAgent` delegating to specialist sub-agents (billing / shipping / account) based on query classification                                                              | Dynamic routing, Coordinator/Specialist pattern, Agent delegation, Conditional branching, Nested workflows | same infra                                                                        |
| `demo-hitl-approval`            | `LlmAgent` + `ToolConfirmation` on refund above threshold (Web UI dialog for live demo)                                                                                              | Human-in-the-loop, Retry & error recovery (approve/reject paths)                                           | same infra                                                                        |
| `demo-loop-refinement`          | `LoopAgent`: draft customer-facing reply → critique → refine until policy-compliant or `max_iterations`                                                                              | (Loop workflow, iterative self-correction — implied by "LoopAgent" in your ask)                            | same infra                                                                        |
| `demo-rag-policy`               | Agent answering policy questions using `rag-index`, with citations                                                                                                                   | RAG end-to-end, Semantic memory                                                                            | `rag-index`                                                                       |
| `demo-memory-personalization`   | Agent recalling long-term preferences across sessions (`customer_id` in `session.state` at session create)                                                                           | Long-term memory, cross-session recall                                                                     | `memory-services`                                                                 |
| `evaluation-harness`            | Layered JUnit harness: tool eval, retrieval eval (incl. hybrid vs dense-only), prompt eval (frozen context), agent eval (full loop)                                                  | Evaluation (golden datasets, prompt evaluation, agent evaluation, tool evaluation)                         | all `demo-*` modules, `rag-index`, `shared-tools`                                 |




### Build order

`domain-data`, `model-routing`, `observability` → `shared-tools`, `memory-services`, `guardrails` → `rag-index` → `demo-single-agent` → `demo-sequential-investigation` → `demo-parallel-investigation` → `demo-dynamic-routing` → `demo-hitl-approval` → `demo-loop-refinement` → `demo-rag-policy` → `demo-memory-personalization` → `evaluation-harness`.

### Agent registration

Each `demo-*` module registers its own `public static final BaseAgent ROOT_AGENT`, so all demo agents appear as separate selectable entries in the ADK Web UI's agent dropdown — each Playbook scenario tells you exactly which one to pick.

## Application Bootstrap

`SupportAssistantApplication` is the sole `@SpringBootApplication`, scanning `com.poc.adk` and `com.google.adk.web`. That pulls ADK's Dev UI (`/dev-ui`), REST controllers (`/run`, `/run_sse`), and its beans into **our** Spring context — no `AdkWebServer.start(...)` call ([spring-boot#39943](https://github.com/spring-projects/spring-boot/issues/39943)).

```java
@SpringBootApplication(scanBasePackages = {"com.poc.adk", "com.google.adk.web"})
public class SupportAssistantApplication {
  public static void main(String[] args) {
    SpringApplication.run(SupportAssistantApplication.class, args);
  }
}
```

```bash
mvn compile exec:java -Dexec.mainClass=com.poc.adk.SupportAssistantApplication \
  -Dexec.args="--adk.agents.source-dir=target --server.port=8000"
```

- **Agent loading** — default `CompiledAgentLoader` scans `--adk.agents.source-dir` for `public static final BaseAgent ROOT_AGENT` on each `demo-*` class.
- **Spring wiring** — `ROOT_AGENT` is not a Spring bean. Chat/OTel/Qdrant use module-scoped static bridges in `integration/adk/` (`LlmContext`, `TracingContext`, `VectorContext`), each populated by a `@Bean` during context refresh. Each function tool is its own Spring bean and receives only the repository it needs; methods stay static so `FunctionTool.create(Class, methodName)` does not capture the instance while `CompiledAgentLoader` constructs `ROOT_AGENT`. Do not use a single `AppServices` / `ToolDependencies` holder or an `ApplicationRunner` for this wiring.
- **Bean-name collisions** — do not define `sessionService`, `artifactService`, `memoryService`, `objectMapper`, `mappingJackson2HttpMessageConverter`, `openTelemetrySdk`, `sdkTracerProvider`, `apiServerSpanExporter`, or `apiServerSpanExporterConfig` in `com.poc.adk`; `AdkWebServer` / `OpenTelemetryConfig` already register them.

## Memory

### Comparison matrix (Northwind examples)

| Memory type | Northwind example | Store | How this POC accesses it |
| ----------- | ----------------- | ----- | ------------------------ |
| **Short-term** | Turn 2: "Who is it for?" resolves `ORD-5001` from the prior turn | ADK `InMemorySessionService` — conversation + `session.state` | Default ADK session; Playbook §1 |
| **Long-term** | "Best way to reach me is **email**" (stable preference) | H2 `CustomerPreference` | `CustomerPreferenceTool` reads `customer_id` from `session.state` — Playbook §8 |
| **Semantic** | "Refund window is 30 days" from policy docs | Qdrant `policy_chunks` | `HybridRetriever` — Playbook §7 |

Diagram: `architecture.md` §6.8 (D8).

### Session identity (`customer_id`)

Long-term personalization does **not** infer customer from chat text in Playbook §8. Bind identity once per session:

1. **At session creation** — set initial `session.state`, e.g. `{"customer_id": "CUST-1001"}`.
2. **Memory tools** — `CustomerPreferenceTool` reads `customer_id` from session state (via `ToolContext`), not from the user message.
3. **Change customer** — create a **new session** with a different `customer_id` (same pattern as logging in as another user). Do not switch mid-session for Playbook demos.

**Manual (Playbook §8):** create the session with initial state via REST (confirmed against `google-adk-dev` **1.9.0** `SessionController` / `SessionRequest` — see `tasks/plan.md` → Spike findings):

```bash
# 1. Create session for Priya (appName = selected agent's name() / Dev UI dropdown)
curl -s -X POST "http://localhost:8000/apps/demo-memory-personalization/users/playbook-user/sessions" \
  -H "Content-Type: application/json" \
  -d '{"state": {"customer_id": "CUST-1001"}}'

# 2. Send §8.1 query on the returned session id via /run or /run_sse
```

**Web UI:** New Session does not accept custom initial state. Use **More options → Update state** to set `{"customer_id":"CUST-1001"}` before the first message (applied as `stateDelta`), or use the REST curl above. `bind_customer` is **not** required (Task 2 spike).

**Layer 3 tests:** `InMemoryRunner` sets the same initial state programmatically — no special-case agent code.

## Model Routing Config

Defined in `architecture.md` §5 (D11).

### Configuration

```yaml
llm:
  provider: ollama   # ollama | gemini | anthropic | openrouter
  ollama:
    base-url: http://localhost:11434/v1/
    model: qwen2.5:7b
  gemini:
    api-key: ${GEMINI_API_KEY:}
    model: gemini-2.0-flash
  anthropic:
    api-key: ${ANTHROPIC_API_KEY:}
    model: claude-3-7-sonnet-20250219
  openrouter:
    api-key: ${OPENROUTER_API_KEY:}
    base-url: https://openrouter.ai/api/v1/
    model: openrouter/auto
```

### Switching providers

Change `llm.provider` (and possibly the model string) in `application.yml` or via an environment variable override — no code change, no rebuild logic beyond a `ModelFactory` that switches on the enum.

### Dev model vs quality bar

`qwen2.5:7b` (Ollama) is the **default dev model** — free, local, good enough to exercise ADK wiring. It is **not** the quality bar for final prose, routing accuracy, or citation formatting. Layers 0–3 must pass on qwen (or `TestLlm` for deterministic graph tests); Layer 4 (manual Playbook) is best-effort on qwen. If Layer 3 passes but the answer "sounds wrong," treat it as model capacity or prompt polish — check Langfuse first; correct tool and retrieval spans mean the pipeline worked.

### Cloud-model fallbacks (demo polish)

Switch `llm.provider` when Langfuse shows correct tools/retrieval but prose or routing UX is weak. Layers 0–1 never need cloud models.

| Playbook | Agent | Rerun with `gemini` or `anthropic` when |
| -------- | ----- | --------------------------------------- |
| §1 | `demo-single-agent` | Tool args wrong after prompt fix (rare) |
| §2 | `demo-sequential-investigation` | Final prose muddled but all stages completed in trace |
| §3 | `demo-parallel-investigation` | Aggregator omits fraud score despite correct tool JSON |
| §4 | `demo-dynamic-routing` | Coordinator routes to wrong specialist on 2+ of 3 queries (use `TestLlm` in CI) |
| §5 | `demo-hitl-approval` | — (confirmation is structural) |
| §6 | `demo-loop-refinement` | Refined apology still violates tone after `max_iterations` |
| §7.2–7.3 | `demo-rag-policy` | `RetrievalEvalTest` passes but answer cites wrong doc or omits `source_path` / section |
| §8 | `demo-memory-personalization` | Recalls wrong preference despite correct DB read in trace |

**Suggested demo flow:** build and debug on qwen → run Layers 0–3 → for stakeholders, rerun §4, §7.2–7.3, and §6 once on a cloud model.

## Persistence Plan

### H2 (relational + memory logs)

- File-mode: `jdbc:h2:file:./data/support-assistant`
- Spring Data JPA
- Holds: relational domain data (customers / orders / payments / shipments / tickets), long-term memory (customer preferences), guardrail audit log, evaluation run results
- No policy content lives here

### Qdrant (vectors)

- One collection (`policy_chunks`) for policy-document embeddings (RAG, sourced from plain-text policy files, not H2). That collection is also the semantic-memory demo (facts / business rules / policy knowledge). No second collection; long-term memory stays in H2.
- Exact collection schema is in `architecture.md` §4.2.



## RAG Indexing

Spec-level contracts so Architecture does not invent a different retrieval story. Implementation details (Qdrant collection names, vector sizes, RRF `k`) stay in Architecture.

### Chunking strategy

Policy files are authored as Markdown with `##` / `###` sections. Indexer behavior:

1. Split first on headings (`##` then `###`) so each chunk maps to a citable section, not an arbitrary token window.
2. If a section is still larger than **~400 tokens (~1,500 characters)**, split on paragraphs, then sentences, until it fits.
3. Apply **~50 token (~200 character) overlap** on those size-splits only, so a sentence that sits on a boundary is not lost.
4. Persist payload metadata on every chunk: `doc_id`, `source_path`, `section_heading`, `chunk_index`. Citations are formatted from this metadata, not guessed by the model.

#### Rejected alternatives

| Rejected                             | Why not, for this POC                                                                                                      |
| ------------------------------------ | -------------------------------------------------------------------------------------------------------------------------- |
| One chunk per file                   | Too coarse for citations, and Playbook RAG step 2 (two policies in one answer) becomes "stuff both files into the prompt." |
| Fixed-size windows with no structure | Splits mid-section; citations degrade to "`refund-policy.md` chunk 3."                                                     |
| Semantic / embedding-based chunking  | Needs a second model and tuning, for four short files we author ourselves. Not earned complexity.                          |

We will write the policy files with several `##` sections on purpose, including the hybrid-search trap section below. Chunking is only demonstrable if the source docs actually have sections.

### Hybrid search

"Hybrid" here means **keyword retrieval + vector retrieval, then fusion** — not "the agent read two documents." Qdrant's documented pattern is a dense prefetch plus a lexical/sparse prefetch, merged with RRF ([Hybrid queries](https://qdrant.tech/documentation/search/hybrid-queries/)).

#### Default implementation

Dense cosine search on `nomic-embed-text` vectors **plus** Qdrant BM25 (named sparse vector, server-side `qdrant/bm25` inference), fused with RRF in one Qdrant `query` (two prefetches). A payload full-text index is a filter and does not produce ranks, so it cannot feed RRF — see `architecture.md` §4.3. The Playbook contract does not change.

#### Why hybrid is in the POC

Dense search misses rare exact tokens (policy exception codes, SKUs). Lexical search misses paraphrases ("how long can I send this back" vs "refund request window"). Support-policy Q&A needs both.

#### Required fixture

Authored into `loyalty-policy.md`, *not* `shipping-policy.md`:

- A GOLD-member courtesy clause that is the **only** place the tokens `NW-SHIP-EXC-04` and `NW-HP-1001` appear
- `shipping-policy.md` discusses weather delays in natural language **without** those codes, so it is a dense-search distractor
- Test query: *"Does exception code NW-SHIP-EXC-04 apply to SKU NW-HP-1001?"*

Expected results:

- Dense-only top-1 prefers the shipping-policy weather section (semantic match, wrong doc)
- Hybrid top-1 is the loyalty courtesy-clause chunk (lexical hit on the codes)
- Asserted by a **retrieval unit test with no LLM**; the Playbook RAG scenario is the agent-level wrapping of the same fixture

#### Validating the fixture

If a first implementation's dense-only already ranks the loyalty chunk #1, the fixture is too weak — add distractor weather-refund language to `shipping-policy.md` until the retrieval test fails on dense-only and passes on hybrid. Do not "fix" it by editing the agent prompt.

## Cross-Cutting Concerns

Apply to every `demo-*` agent — not separate demos.

### Guardrails

- **Input** — prompt-injection/jailbreak heuristics, PII detection on inbound text
- **Output** — PII masking, lightweight heuristic checks (uncited factual claims, policy-keyword mismatches), plus audit log

These are **not** a second-LLM safety judge. Hallucination mitigation for RAG is proven by retrieval + Playbook §7.4 (manual: empty retrieval → "not covered" response), not by a 7B model grading another 7B's output.

### Observability

Every agent run, tool call, and model call emits an OTel span with token usage / latency / cost attributes where available, exported via OTLP to Langfuse. No agent is exempt.

### Prompts

Versioned files under `src/main/resources/prompts/{agent}.v1.md`, not string literals edited in the Web UI. Changing a prompt is a code change that must pass Layer 2 before it is considered done.

Techniques used consistently across agents:

1. **One job per agent** — specialists state what they do and what they never do.
2. **Ground in tool/RAG output** — answer only from `tool_results` / `retrieved_chunks`; if missing, say so.
3. **Explicit citation template** — e.g. `Source: {source_path} — {section_heading}` so Layer 2 can regex-check format.
4. **One–two few-shot examples** per specialist (short input → expected tool call or transfer).
5. **Temperature 0** in eval (`@Tag("llm")`); **0–0.3** for manual demos.
6. **Version prompts** — change prompt ⇒ update matching `.eval.json` ⇒ re-run Layer 2 before Layer 3.
7. **Negative instructions sparingly** — prefer "use only tool JSON fields X, Y" over long "do not hallucinate" lists.

Workflow composition (sequential/parallel/routing/RAG) reduces per-call instruction load and fixes stage order, but does not remove the need for Layer 3 event assertions or occasional cloud-model reruns for demo polish.

### Evaluation

The layered JUnit harness (see Testing Strategy) — run via `mvn test`, independent of the running server process. The Web UI is manual confirmation, not the inner loop.



## Testing Strategy

### Guiding principle

The failure mode to avoid: something looks wrong in the UI → tweak the system prompt → try again → cannot tell whether retrieval, tools, routing, or the prompt broke.

**Localize first, then change the layer that actually failed.** Most tests must not call an LLM.

### Test layers

Evaluate flakiness by **layer**, not by "did the Playbook look good once."

| Layer              | What it proves                                                                                                      | LLM?                               | Pass on qwen? | Command (see `tasks/plan.md` → Evaluation harness commands) | If it fails |
| ------------------ | ------------------------------------------------------------------------------------------------------------------- | ---------------------------------- | ------------- | -------------------------------------------- | ----------- |
| 0. Tool eval       | Domain tools + guardrail helpers return the right records / masks                                                   | No                                 | Must pass     | `mvn test -Dtest=ToolEvalTest,GuardrailEvalTest`               | Fix tool/repo — not a model issue |
| 1. Retrieval eval  | Chunking, citation metadata, dense vs hybrid ranking                                                                | No                                 | Must pass     | `mvn test -Dtest=RetrievalEvalTest`          | Fix indexer/retriever — not a model issue |
| 2. Prompt eval     | Given **frozen** context (canned tool JSON or policy chunks), does the **wording** of the reply match requirements? | Yes (`@Tag("llm")`, temperature 0) | After tuning  | `mvn test -Dtest=PromptEvalTest`             | Edit versioned prompt + eval fixture |
| 3. Agent eval      | Full `Runner` loop: did the agent **choose** the right tools/routes/workflow steps, and produce acceptable output?  | Yes (`@Tag("llm")`)                | Must pass     | `mvn test -Dtest=EvaluationHarnessTest`      | Fix graph/prompt — not a retrieval issue |
| 4. Manual Playbook | UX, Langfuse span shape, provider switch                                                                            | Yes, human                         | Best-effort   | Playbook steps in the Web UI                 | Not a regression if Layers 0–3 pass; see cloud fallbacks in Model Routing Config |

### Prompt eval fixtures

Live next to the prompt files (same version suffix, e.g. `demo-rag-policy.v1.md` + `demo-rag-policy.v1.eval.json`). Each case supplies:

- System prompt file id
- Canned user message
- Canned context (chunks or tool results)
- `must_contain`, `must_not_contain`, optional citation pattern

The model is called **once per case** with that frozen input — retrieval and tools are not in the path. If prompt eval passes and agent eval fails, the prompt is not the bug.

Minimum coverage:

| Agent | Frozen input | Must hold |
| ----- | ------------ | --------- |
| `demo-single-agent` | Fake tool JSON: `ORD-5001` → DELAYED | Reply mentions DELAYED; does not invent carrier/amount |
| `demo-rag-policy` | Refund-window chunk + citation metadata | States window from fixture; cites `refund-policy.md` + section heading |
| `demo-rag-policy` | Loyalty courtesy chunk for `NW-SHIP-EXC-04` | Exception applies to `NW-HP-1001`; cites loyalty, not shipping |
| `demo-dynamic-routing` | Coordinator instruction + refund query for `ORD-5001` | Transfer target is billing, not shipping |
| `demo-hitl-approval` | Fake order JSON: amount 350, threshold 200 | Output requests approval; does not claim refund completed |

### What each layer tests (and what it does not)

| Question                                                                           | Answer                                                                                                                                                                |
| ---------------------------------------------------------------------------------- | --------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| Does the tool **work** when called directly?                                       | **Layer 0** — call `orderLookup("ORD-5001")`, assert `DELAYED`. No prompt, no agent.                                                                                  |
| Does the agent **decide to call** the tool and pass the right args?                | **Layer 3** — run `InMemoryRunner`, assert events contain `tool_call` for `order_lookup` with `order_id=ORD-5001`.                                                    |
| Does the prompt produce good text **if the tool already returned the right JSON**? | **Layer 2** — inject fake tool JSON `{"status":"DELAYED"}`, ask model to answer; assert reply mentions DELAYED. **This does not prove the tool was called.**          |
| Does sequential / parallel / routing **topology** work?                            | **Layer 3** — assert **event order and shape** (see Workflow testing below). Optional **Layer 3b** with `TestLlm` for fully deterministic graph tests without Ollama. |

### Workflow testing (Layer 3)

Run the real agent graph through `InMemoryRunner` (or `Runner` + Ollama for `@Tag("llm")`) and assert on the **event stream**, not on exact final prose:

| Playbook scenario  | Workflow assertion (examples)                                                                                                                                                                                                                                       |
| ------------------ | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| §1 Single agent    | Events include `tool_call` → `order_lookup`; final text contains `DELAYED`.                                                                                                                                                                                         |
| §2 Sequential      | Sub-agent / stage events appear in order: gather → policy (RAG tool or retrieval span) → draft; later stage input references prior `outputKey` in session state.                                                                                                    |
| §3 Parallel        | Payment, shipment, fraud tool calls appear in the same turn without strict A→B→C ordering; aggregator event follows all three.                                                                                                                                      |
| §4 Dynamic routing | Event shows `transfer` (or equivalent) to **billing** specialist for refund query, **shipping** for delay query.                                                                                                                                                    |
| §5 HITL            | Event shows `adk_request_confirmation` (or pending confirmation) before refund; after programmatic `confirmed: true`, refund tool runs; after `confirmed: false`, refund tool never runs and H2 unchanged. **Live demo:** same flow via Web UI dialog, not Postman. |
| §6 Loop            | ≥2 draft/critique iterations in events, then exit; forced-fail case hits `max_iterations`.                                                                                                                                                                          |
| §7 RAG             | Retrieval tool or RAG span returns chunks from expected `source_path`; hybrid case cites loyalty doc for code query.                                                                                                                                                |
| §8 Memory          | `customer_preference` tool or DB read for `customer_id` from session state; reply states email (CUST-1001) or SMS (CUST-1002) without those facts in the user message. |

For flaky routing tests, use `TestLlm` to return a fixed `transfer_to_agent` response so Layer 3 can verify graph wiring without depending on qwen2.5:7b mood.

### When a Playbook step fails

Do not edit the prompt first. Check Langfuse:

- Missing tool span → Layer 0 / 3
- Wrong chunk in the retrieved-context span → Layer 1
- Right chunks/tools but wrong wording/citation → Layer 2

Only then change the prompt file and re-run Layer 2, then Layer 3.

### Out of scope

- LLM-as-judge as the merge gate (noisy on a local 7B, and it hides which layer failed)
- Exact prose snapshots
- Using the ADK Web "Eval" tab as the harness ([adk-java#300](https://github.com/google/adk-java/issues/300) — eval REST endpoints are unimplemented in Java)

### Test types

- **Unit tests** — Layers 0–1, plus `ModelFactory` and memory services; H2 in-memory profile, no Ollama required
- **Integration tests** — Layer 3 graph checks (e.g. `SequentialAgent` `outputKey` chaining) against local Ollama, `@Tag("llm")`
- **Manual verification** — the Playbook



## Project Structure

Indicative layout — see `architecture.md` §3.

```
pom.xml
src/main/java/com/poc/adk/
  SupportAssistantApplication.java  → `@SpringBootApplication(scanBasePackages = {..., "com.google.adk.web"})`
  commerce/ support/ risk/ platform/  → JPA entities + repositories, co-located by subdomain
  config/       → Spring `@Configuration` / `@ConfigurationProperties` (model-routing, observability, qdrant)
  integration/  → module-scoped ADK bridges (`adk/` holders + `config/` `@Bean` wiring)
  tools/        → shared Java function tools
  memory/       → long-term preference tool (H2); semantic memory is `rag/` over policy_chunks
  guardrails/   → callbacks + PII/prompt-injection utilities
  rag/          → chunking, embedding, retrieval (dense + lexical + RRF), citation formatting, policy indexer
  agents/
    singleagent/  sequential/  parallel/  routing/  hitl/  loop/  rag/  personalization/
src/main/resources/policies/   → markdown policy documents (RAG source, not H2)
src/main/resources/prompts/    → versioned agent instructions (`{agent}.v1.md`)
src/test/resources/eval/       → golden datasets + prompt-eval fixtures
src/test/java/...
docs/           → spec.md, playbook.md, architecture.md
tasks/          → plan.md (implementation plan), todo.md
docker-compose.yml → Qdrant + Langfuse (repo root)
data/           → H2 file-mode database (gitignored)
```



## Boundaries

### Always

- Keep every ADK/API fact verified against current `google-adk` / `google-adk-dev` docs or source before writing it into Architecture/Plan (APIs are young and shift between versions)
- Keep the mock domain data synthetic (no real customer data)
- Keep guardrails/observability applied uniformly — never skipped for a demo "to save time"

### Ask first

- Adding any new external dependency not already named in this spec
- Changing the H2 → Postgres decision
- Changing the embedding-model choice
- Changing the chunking/hybrid contract above
- Deviating from the agreed module build order
- "Fixing" a failing demo by editing a prompt without a failing Layer 2 fixture

### Never

- Commit API keys/secrets
- Call real third-party services (this is a fully mocked domain)
- Skip a POC topic from the checklist without flagging it explicitly as descoped



## Success Criteria

### Checklist

- Every topic listed in `Google ADK Java POC.md` maps to at least one module in the Capability Map and has a corresponding Playbook entry
- `mvn compile exec:java -Dexec.mainClass=com.poc.adk.SupportAssistantApplication -Dexec.args="--adk.agents.source-dir=target"` starts the app and lists all `demo-*` agents in the Web UI
- Each Playbook scenario, run manually against the Web UI/REST API with the default `qwen2.5:7b`/Ollama provider, produces the documented behavior
- Switching `llm.provider` to `gemini` / `anthropic` / `openrouter` (with a valid key) works with no code change
- Langfuse shows traces for agent/tool/model calls; Qdrant holds `policy_chunks`; H2 holds seeded domain + memory data
- Layers 0–1 of the evaluation harness pass with no LLM (`ToolEvalTest`, `RetrievalEvalTest` including hybrid vs dense-only)
- Layers 2–3 pass against the default Ollama provider (`PromptEvalTest`, `EvaluationHarnessTest`) without asserting exact prose

### Architecture verification (resolved)

Bean-name collision, HITL `FunctionResponse` shape, and embedding dimension are resolved in `architecture.md` §2.2–2.4. Embedding dimension is asserted at startup by `EmbeddingClient`, not ad hoc `curl`.