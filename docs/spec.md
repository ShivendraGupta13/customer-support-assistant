# Spec: Customer Support Investigation Assistant (Google ADK Java POC)

Status: **APPROVED**


## Objective

Build a single Spring Boot / Maven application, running Google ADK's own `AdkWebServer` (from `google-adk-dev`) as the interaction surface, that teaches — through one concrete business scenario — every major capability in `Google ADK Java POC.md`: agent workflows (sequential/parallel/loop/routing/HITL), multi-agent composition, tool calling, all four memory types, RAG, guardrails, observability (OTel + Langfuse), and evaluation.

This is a **technology-learning POC**, not a production system. Success is measured by: for every topic in the POC checklist, there is a runnable demo you can drive from the ADK Web UI/REST API, a Playbook entry telling you exactly what to type and what to expect, and an Architecture diagram showing the runtime path that query takes.

- **User**: you (the developer), using the ADK Web UI/REST API directly — no custom frontend.
- **Business scenario**: e-commerce order investigation — a support agent (the AI) investigates order status, payment/refund disputes, shipping delays, and fraud flags for a fictional online store, backed entirely by synthetic/mocked data.



## Tech Stack


| Concern          | Choice                                                                                                                                                                                                                                     | Notes                                                                                                                                                                                                                                                                                                |
| ---------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------ | ---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| Language         | Java 25                                                                                                                                                                                                                                    | `google-adk` jars are compiled for Java 17 target; run fine as a dependency on a Java 25 JDK (forward-compatible bytecode).                                                                                                                                                                          |
| Framework        | Spring Boot **4.0.2** (transitive via `google-adk-dev` 1.9.0 — do not override unless Architecture spike validates)                                                                                                                        | Our `SupportAssistantApplication.main` is the process entrypoint; it starts ADK's `AdkWebServer` (Dev UI + REST). See Architecture Decision below.                                                                                                                                                   |
| Build            | Maven                                                                                                                                                                                                                                      | Single module, single `pom.xml`. `exec-maven-plugin` launches `com.northwind.support.SupportAssistantApplication`. Pin: `google-adk` / `google-adk-dev` **1.9.0**, `java.version` **25**. Run `mvn -q dependency:tree -Dincludes=org.springframework.boot` after first build to confirm effective Spring Boot version. |
| Agent framework  | `google-adk` + `google-adk-dev` (currently 1.9.x)                                                                                                                                                                                          | Core agents/tools/workflows + dev web server & REST API.                                                                                                                                                                                                                                             |
| Default LLM      | Ollama, `qwen2.5:7b`, via ADK's built-in `OpenAiCompatibleLlm`                                                                                                                                                                             | Ollama must already be running locally (`ollama serve`, model pulled) — out of scope for us to install.                                                                                                                                                                                              |
| Alternate LLMs   | Gemini (native ADK `Gemini` model class), Anthropic (native ADK `Claude` model class), OpenRouter (via `OpenAiCompatibleLlm`)                                                                                                              | Switchable via one config property, no code change.                                                                                                                                                                                                                                                  |
| Relational store | H2 (file-mode, persisted to disk, not in-memory-only)                                                                                                                                                                                      | Sufficient for a single-instance POC: mock domain data, long-term/episodic memory, guardrail & eval logs.                                                                                                                                                                                            |
| Vector store     | Qdrant (Docker) via `io.qdrant:qdrant-client` (Java)                                                                                                                                                                                       | Semantic memory + RAG document embeddings.                                                                                                                                                                                                                                                           |
| Observability    | OpenTelemetry SDK (Java) → OTLP → Langfuse (Docker, self-hosted)                                                                                                                                                                           | Traces/spans for agent runs, tool calls, model calls; Langfuse as the trace UI + LLM-specific analytics (cost, tokens).                                                                                                                                                                              |
| Embeddings       | Ollama `nomic-embed-text` via a small `EmbeddingClient` (HTTP to Ollama `/api/embeddings` or the OpenAI-compatible `/v1/embeddings` endpoint). **Not** routed through ADK `BaseLlm` — that abstraction is chat completion, not embeddings. | 768-dim dense vectors. Swap the client implementation later if a cloud key is set; do not block the default path on a cloud embedder.                                                                                                                                                                |




## Business Scenario & Mock Domain

Fictional store "Northwind Retail". Synthetic data, owned entirely by us — field-level schema is an Architecture-phase concern, not a Spec concern. Two kinds of mock data:

- **Relational domain data** (seeded into H2): Customer, Order, Payment, Shipment, Ticket (past support interactions — the "episodic memory" substrate), Customer Preference (long-term memory substrate), Fraud Signal (feeds the fraud-check specialist).
- **Policy knowledge base** (markdown documents, not database rows): refund policy, shipping policy, fraud policy, loyalty terms — authored with `##` sections so header-aware chunking and citations are real. `loyalty-policy.md` also holds the planted hybrid-search clause (`NW-SHIP-EXC-04` / `NW-HP-1001`); see RAG Indexing. These files are what RAG chunks, embeds, and indexes into Qdrant — no relational table involved.

There is no real external system to call; everything above is synthetic and generated/authored by us.

## Capability Map 

This request bundles many independently testable capabilities. Below is the decomposition into build modules (Java packages within the single app), each traceable to POC topics. It is the backbone of the Playbook and Architecture.


| Module id                                                     | Responsibility                                                                                                                                                                       | POC topics covered                                                                                         | Depends on                                                                        |
| ------------------------------------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------ | ---------------------------------------------------------------------------------------------------------- | --------------------------------------------------------------------------------- |
| `domain-data`                                                 | JPA entities + H2 schema + synthetic seed data                                                                                                                                       | — (foundation)                                                                                             | —                                                                                 |
| `model-routing`                                               | `llm.provider` config + `ModelFactory` producing the right ADK `BaseLlm` (Ollama/Gemini/Anthropic/OpenRouter)                                                                        | Model configuration                                                                                        | —                                                                                 |
| `observability`                                               | OTel SDK setup, OTLP exporter to Langfuse, span/event capture around agent runs, tool calls, model calls                                                                             | Observability (traces, agent events, tool execution, token usage, latency, errors, cost, logs, metrics)    | `model-routing`                                                                   |
| `shared-tools`                                                | Java function tools wrapping domain data: order lookup, payment history, shipment tracking, fraud signal check                                                                       | Tool Calling (Java Functions, Database Tool, Custom Tools)                                                 | `domain-data`                                                                     |
| `memory-services`                                             | Short-term (session state helpers), Long-term (H2-backed customer preferences), Episodic (H2-backed past tickets + similarity lookup), Semantic (Qdrant-backed facts/business rules) | Memory (all 4 types) + Memory comparison matrix                                                            | `domain-data`                                                                     |
| `guardrails`                                                  | Before/after model & tool callbacks: input/output guardrails, PII masking, prompt-injection/jailbreak heuristics, basic content moderation                                           | Guardrails (all sub-topics)                                                                                | `model-routing`                                                                   |
| `rag-index`                                                   | Markdown-header chunking + dense embeddings + lexical (full-text) index + RRF hybrid retrieval + citation formatting                                                                 | RAG (retrieval, embeddings, vector store, hybrid search, context injection, citation)                      | `domain-data`, `memory-services` (shares Qdrant)                                  |
| `demo-single-agent`                                           | Single `LlmAgent` + tool calling + short-term memory — the "hello world" baseline                                                                                                    | Single Agent, Prompt management, Context window                                                            | `shared-tools`, `memory-services`, `guardrails`, `observability`, `model-routing` |
| `demo-sequential-investigation`                               | `SequentialAgent`: gather order+payment+shipment → check policy (RAG) → draft resolution                                                                                             | Sequential workflow, Agent composition via `outputKey` chaining, Retry & error recovery on tool failure    | `demo-single-agent`'s shared infra                                                |
| `demo-parallel-investigation`                                 | `ParallelAgent` fan-out (payment / shipment / fraud checks) → aggregator                                                                                                             | Parallel workflow, fan-out/fan-in composition                                                              | same infra                                                                        |
| `demo-dynamic-routing`                                        | Coordinator `LlmAgent` delegating to specialist sub-agents (billing / shipping / account) based on query classification                                                              | Dynamic routing, Coordinator/Specialist pattern, Agent delegation, Conditional branching, Nested workflows | same infra                                                                        |
| `demo-hitl-approval`                                          | `LlmAgent` + `ToolConfirmation` on refund above threshold (Web UI dialog for live demo)                                                                                              | Human-in-the-loop, Retry & error recovery (approve/reject paths)                                           | same infra                                                                        |
| `demo-loop-refinement`                                        | `LoopAgent`: draft customer-facing reply → critique → refine until policy-compliant or `max_iterations`                                                                              | (Loop workflow, iterative self-correction — implied by "LoopAgent" in your ask)                            | same infra                                                                        |
| `demo-rag-policy`                                             | Agent answering policy questions using `rag-index`, with citations                                                                                                                   | RAG end-to-end, Semantic memory                                                                            | `rag-index`                                                                       |
| `demo-memory-personalization`                                 | Agent recalling long-term preferences + episodic past-ticket history across sessions                                                                                                 | Long-term memory, Episodic memory, cross-session recall                                                    | `memory-services`                                                                 |
| `evaluation-harness`                                          | Layered JUnit harness: tool eval, retrieval eval (incl. hybrid vs dense-only), prompt eval (frozen context), agent eval (full loop)                                                  | Evaluation (golden datasets, prompt evaluation, agent evaluation, tool evaluation)                         | all `demo-*` modules, `rag-index`, `shared-tools`                                 |
| *(future, explicitly deferred)* `coordinator-of-coordinators` | Top-level agent routing across all `demo-`* agents as sub-agents, for a single unified entry point                                                                                   | —                                                                                                          | all `demo-*` modules                                                              |


**Build order**: `domain-data`, `model-routing`, `observability` → `shared-tools`, `memory-services`, `guardrails` → `rag-index` → `demo-single-agent` → `demo-sequential-investigation` → `demo-parallel-investigation` → `demo-dynamic-routing` → `demo-hitl-approval` → `demo-loop-refinement` → `demo-rag-policy` → `demo-memory-personalization` → `evaluation-harness`.

Each `demo-*` module registers its own `public static final BaseAgent ROOT_AGENT`, so all demo agents appear as separate selectable entries in the ADK Web UI's agent dropdown — each Playbook scenario tells you exactly which one to pick.

## Architecture Decision: our `Application` class starts `AdkWebServer`

**Decided:** the process entrypoint is our class `com.northwind.support.SupportAssistantApplication` with a normal `public static void main`. That `main` constructs the demo agents and starts ADK's Dev UI + REST via `AdkWebServer.start(...)` (Google's documented programmatic path; [Java getting-started codelab](https://codelabs.developers.google.com/adk-java-getting-started)). Run:

`mvn compile exec:java -Dexec.mainClass=com.northwind.support.SupportAssistantApplication`

We do **not** build a second custom `@RestController` layer. Interaction stays ADK Web UI / ADK REST. `AdkWebServer` is still marked dev-only by Google; that is acceptable for this learning POC.

**What we will not do:** `SpringApplication.run(SupportAssistantApplication.class)` as a *second* `@SpringBootApplication` that replaces `AdkWebServer`. `AdkWebServer` already *is* the Spring Boot app that owns Tomcat, `/dev-ui`, and `/run`. A competing `SpringApplication.run(OurApp.class)` would boot our context and skip that UI unless we re-implement it. Our `main` is the Java entrypoint; `AdkWebServer` remains the Spring Boot application class internally (`start()` does `new SpringApplication(AdkWebServer.class)`).

**Agent construction (decided — see Open Question #1):** each `demo-*` still exposes `public static final BaseAgent ROOT_AGENT` (tests + a stable place to build the graph). `main` passes those instances into `AdkWebServer.start(...)`, which registers `AgentStaticLoader` — not `CompiledAgentLoader` scanning `target/classes`. Those fields are not Spring beans. `AdkWebServer`'s component scan is still `com.google.adk.web`, so it will not pick up `com.northwind.support`. Do **not** constructor-inject into agent classes. Use a composition-root registry for tools/services; use Spring for JPA/Qdrant/OTel via AutoConfiguration (or an Architecture-spike `scanBasePackages` wrapper). `AdkWebServer.start()` currently calls `app.run(new String[0])` and drops CLI args — Architecture may wrap that so `--server.port` still works.

## Model Routing Config (shape, to be finalized in Architecture)

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

Switching provider = change `llm.provider` (and possibly the model string) in `application.yml` or via an environment variable override — no code change, no rebuild logic beyond a `ModelFactory` that switches on the enum.

## Persistence Plan

- **H2**: file-mode (`jdbc:h2:file:./data/support-assistant`), Spring Data JPA. Holds: relational domain data (customers/orders/payments/shipments/tickets), long-term memory (customer preferences), episodic memory (ticket history), guardrail audit log, evaluation run results. No policy content lives here.
- **Qdrant**: one collection for policy-document embeddings (RAG, sourced from the plain-text policy files, not H2), one collection for semantic "facts/business rules," reused for episodic similarity search if useful. Exact collection schema finalized in Architecture.



## RAG Indexing (chunking + hybrid search)

These are spec-level contracts so Architecture does not invent a different retrieval story. Implementation details (Qdrant collection names, vector sizes, RRF `k`) stay in Architecture.

### Chunking strategy: Markdown-header split, then size cap, with overlap

Policy files are authored as Markdown with `##` / `###` sections. Indexer behavior:

1. Split first on headings (`##` then `###`) so each chunk maps to a citable section, not an arbitrary token window.
2. If a section is still larger than **~400 tokens (~1,500 characters)**, split on paragraphs, then sentences, until it fits.
3. Apply **~50 token (~200 character) overlap** on those size-splits only, so a sentence that sits on a boundary is not lost.
4. Persist payload metadata on every chunk: `doc_id`, `source_path`, `section_heading`, `chunk_index`. Citations are formatted from this metadata, not guessed by the model.

**Why this and not the alternatives:**


| Rejected                             | Why not, for this POC                                                                                                      |
| ------------------------------------ | -------------------------------------------------------------------------------------------------------------------------- |
| One chunk per file                   | Too coarse for citations, and Playbook RAG step 2 (two policies in one answer) becomes "stuff both files into the prompt." |
| Fixed-size windows with no structure | Splits mid-section; citations degrade to "`refund-policy.md` chunk 3."                                                     |
| Semantic / embedding-based chunking  | Needs a second model and tuning, for four short files we author ourselves. Not earned complexity.                          |


We will write the policy files with several `##` sections on purpose, including the hybrid-search trap section below. Chunking is only demonstrable if the source docs actually have sections.

### Hybrid search: dense + lexical, fused with Reciprocal Rank Fusion (RRF)

"Hybrid" here means **keyword retrieval + vector retrieval, then fusion** — not "the agent read two documents." Qdrant's documented pattern is a dense prefetch plus a lexical/sparse prefetch, merged with RRF ([Hybrid queries](https://qdrant.tech/documentation/search/hybrid-queries/)).

**Default implementation (simplicity first):** dense cosine search on `nomic-embed-text` vectors **plus** a Qdrant payload full-text index on `chunk_text`, fuse the two ranked lists with RRF in our Java retrieval code. Architecture may upgrade the lexical side to a named sparse BM25 vector if the Java client + our Qdrant image make that the shorter path; the Playbook contract does not change.

**Why hybrid is in the POC at all:** dense search misses rare exact tokens (policy exception codes, SKUs). Lexical search misses paraphrases ("how long can I send this back" vs "refund request window"). Support-policy Q&A needs both.

**Required fixture** (authored into `loyalty-policy.md`, *not* `shipping-policy.md`): a GOLD-member courtesy clause that is the **only** place the tokens `NW-SHIP-EXC-04` and `NW-HP-1001` appear. `shipping-policy.md` will discuss weather delays in natural language **without** those codes, so it is a dense-search distractor. Query: *"Does exception code NW-SHIP-EXC-04 apply to SKU NW-HP-1001?"*

- Dense-only top-1 is expected to prefer the shipping-policy weather section (semantic match, wrong doc).
- Hybrid top-1 must be the loyalty courtesy-clause chunk (lexical hit on the codes).
- This is asserted by a **retrieval unit test with no LLM**. The Playbook RAG scenario is the agent-level wrapping of the same fixture.

If a first implementation's dense-only already ranks the loyalty chunk #1, the fixture is too weak — add distractor weather-refund language to `shipping-policy.md` until the retrieval test fails on dense-only and passes on hybrid. Do not "fix" it by editing the agent prompt.

## Cross-Cutting Concerns (apply to every `demo-*` agent, not separate demos)

- **Guardrails**: input guardrail (prompt-injection/jailbreak heuristics, PII detection on inbound text) and output guardrail (PII masking, lightweight heuristic checks — uncited factual claims, policy-keyword mismatches — plus audit log). These are **not** a second-LLM safety judge; hallucination mitigation for RAG is proven by retrieval + Playbook §7.4 (manual: empty retrieval → "not covered" response), not by a 7B model grading another 7B's output.
- **Observability**: every agent run, tool call, and model call emits an OTel span with token usage/latency/cost attributes where available, exported via OTLP to Langfuse. No agent is exempt.
- **Prompts**: versioned files under `src/main/resources/prompts/`, not string literals edited while staring at the Web UI. Changing a prompt is a code change that must pass the prompt-eval layer before it is considered done.
- **Evaluation**: the layered JUnit harness below — run via `mvn test`, independent of the running `AdkWebServer` process. The Web UI is manual confirmation, not the inner loop.



## Testing Strategy

The failure mode to avoid: something looks wrong in the UI → tweak the system prompt → try again → cannot tell whether retrieval, tools, routing, or the prompt broke.

**Localize first, then change the layer that actually failed.** Most tests must not call an LLM.


| Layer              | What it proves                                                                                                      | LLM?                               | Command (indicative; finalized in `plan.md`) | Pass criteria                                                                                                                                                     |
| ------------------ | ------------------------------------------------------------------------------------------------------------------- | ---------------------------------- | -------------------------------------------- | ----------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| 0. Tool eval       | Domain tools + guardrail helpers return the right records / masks                                                   | No                                 | `mvn test -Dtest=ToolEvalTest`               | Exact values from seed data (order status, fraud score, PII masked).                                                                                              |
| 1. Retrieval eval  | Chunking, citation metadata, dense vs hybrid ranking                                                                | No                                 | `mvn test -Dtest=RetrievalEvalTest`          | Hybrid query ranks the `NW-SHIP-EXC-04` chunk #1; dense-only does not; multi-policy query returns chunks from both named files.                                   |
| 2. Prompt eval     | Given **frozen** context (canned tool JSON or policy chunks), does the **wording** of the reply match requirements? | Yes (`@Tag("llm")`, temperature 0) | `mvn test -Dtest=PromptEvalTest`             | `must_contain` / `must_not_contain` / citation regex. **Does not invoke real tools or retrieval.**                                                                |
| 3. Agent eval      | Full `Runner` loop: did the agent **choose** the right tools/routes/workflow steps, and produce acceptable output?  | Yes (`@Tag("llm")`)                | `mvn test -Dtest=EvaluationHarnessTest`      | **Event-stream assertions** (tool name + args, transfer target, confirmation pause, sub-agent order) + `must_contain` seed facts. One case per Playbook scenario. |
| 4. Manual Playbook | UX, Langfuse span shape, provider switch                                                                            | Yes, human                         | Playbook steps in the Web UI                 | Trace shape matches the Architecture diagram cited by that scenario.                                                                                              |


**Prompt eval fixtures** live next to the prompt files (same version suffix, e.g. `demo-rag-policy.v1.md` + `demo-rag-policy.v1.eval.json`). Each case supplies: system prompt file id, canned user message, canned context (chunks or tool results), `must_contain`, `must_not_contain`, optional citation pattern. The model is called **once per case** with that frozen input — retrieval and tools are not in the path. If prompt eval passes and agent eval fails, the prompt is not the bug.

### What each layer tests (and what it does not)


| Question                                                                           | Answer                                                                                                                                                                |
| ---------------------------------------------------------------------------------- | --------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| Does the tool **work** when called directly?                                       | **Layer 0** — call `orderLookup("ORD-5001")`, assert `DELAYED`. No prompt, no agent.                                                                                  |
| Does the agent **decide to call** the tool and pass the right args?                | **Layer 3** — run `InMemoryRunner`, assert events contain `tool_call` for `order_lookup` with `order_id=ORD-5001`.                                                    |
| Does the prompt produce good text **if the tool already returned the right JSON**? | **Layer 2** — inject fake tool JSON `{"status":"DELAYED"}`, ask model to answer; assert reply mentions DELAYED. **This does not prove the tool was called.**          |
| Does sequential / parallel / routing **topology** work?                            | **Layer 3** — assert **event order and shape** (see Workflow testing below). Optional **Layer 3b** with `TestLlm` for fully deterministic graph tests without Ollama. |




### Workflow testing (Layer 3)

Workflows are tested by running the real agent graph through `InMemoryRunner` (or `Runner` + Ollama for `@Tag("llm")`) and asserting on the **event stream**, not on exact final prose:


| Playbook scenario  | Workflow assertion (examples)                                                                                                                                                                                                                                       |
| ------------------ | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| §1 Single agent    | Events include `tool_call` → `order_lookup`; final text contains `DELAYED`.                                                                                                                                                                                         |
| §2 Sequential      | Sub-agent / stage events appear in order: gather → policy (RAG tool or retrieval span) → draft; later stage input references prior `outputKey` in session state.                                                                                                    |
| §3 Parallel        | Payment, shipment, fraud tool calls appear in the same turn without strict A→B→C ordering; aggregator event follows all three.                                                                                                                                      |
| §4 Dynamic routing | Event shows `transfer` (or equivalent) to **billing** specialist for refund query, **shipping** for delay query.                                                                                                                                                    |
| §5 HITL            | Event shows `adk_request_confirmation` (or pending confirmation) before refund; after programmatic `confirmed: true`, refund tool runs; after `confirmed: false`, refund tool never runs and H2 unchanged. **Live demo:** same flow via Web UI dialog, not Postman. |
| §6 Loop            | ≥2 draft/critique iterations in events, then exit; forced-fail case hits `max_iterations`.                                                                                                                                                                          |
| §7 RAG             | Retrieval tool or RAG span returns chunks from expected `source_path`; hybrid case cites loyalty doc for code query.                                                                                                                                                |
| §8 Memory          | Memory-load tool or DB read occurs without those facts in the user message; reply cites `TCK-3001` / email preference.                                                                                                                                              |


For flaky routing tests, use `TestLlm` to return a fixed `transfer_to_agent` response so Layer 3 can verify graph wiring without depending on qwen2.5:7b mood.

**When a Playbook step fails in the UI, do not edit the prompt first.** Check Langfuse: missing tool span → Layer 0/3; wrong chunk in the retrieved-context span → Layer 1; right chunks/tools but wrong wording/citation → Layer 2. Only then change the prompt file and re-run Layer 2, then Layer 3.

**What we will not do:** LLM-as-judge as the merge gate (noisy on a local 7B, and it hides which layer failed); exact prose snapshots; using the ADK Web "Eval" tab as the harness ([adk-java#300](https://github.com/google/adk-java/issues/300) — eval REST endpoints are unimplemented in Java).

- **Unit tests**: Layers 0–1, plus `ModelFactory` and memory services — H2 in-memory profile, no Ollama required.
- **Integration tests**: Layer 3 graph checks (e.g. `SequentialAgent` `outputKey` chaining) against local Ollama, `@Tag("llm")`.
- **Manual verification**: the Playbook.



## Project Structure (indicative — finalized in Architecture)

```
pom.xml
src/main/java/com/northwind/support/
  SupportAssistantApplication.java  → process `main`; calls `AdkWebServer.start(demo agents...)`
  config/       → model-routing, observability, qdrant, data-seed config
  domain/       → JPA entities, repositories, seed loader
  tools/        → shared Java function tools
  memory/       → short/long-term/episodic/semantic memory services
  guardrails/   → callbacks + PII/prompt-injection utilities
  rag/          → chunking, embedding, retrieval (dense + lexical + RRF), citation formatting
  agents/
    singleagent/  sequential/  parallel/  routing/  hitl/  loop/  rag/  personalization/
src/main/resources/policies/   → markdown policy documents (RAG source, not H2)
src/main/resources/prompts/    → versioned agent instructions (`{agent}.v1.md`)
src/test/resources/eval/       → golden datasets + prompt-eval fixtures
src/test/java/...
docs/           → spec.md, playbook.md, architecture.md, plan.md (this series)
docker/         → docker-compose for Qdrant + Langfuse
data/           → H2 file-mode database (gitignored)
```



## Boundaries

- **Always**: keep every ADK/API fact verified against current `google-adk`/`google-adk-dev` docs or source before writing it into Architecture/Plan (APIs are young and shift between versions); keep the mock domain data synthetic (no real customer data); keep guardrails/observability applied uniformly, never skipped for a demo "to save time."
- **Ask first**: adding any new external dependency not already named in this spec; changing the H2→Postgres decision; changing the embedding-model choice; changing the chunking/hybrid contract above; deviating from the agreed module build order; "fixing" a failing demo by editing a prompt without a failing Layer 2 fixture.
- **Never**: commit API keys/secrets; call real third-party services (this is a fully mocked domain); skip a POC topic from the checklist without flagging it explicitly as descoped.



## Success Criteria

- Every topic listed in `Google ADK Java POC.md` maps to at least one module in the Capability Map above and has a corresponding Playbook entry.
- `mvn compile exec:java -Dexec.mainClass=com.northwind.support.SupportAssistantApplication` starts the app and lists all `demo-*` agents in the Web UI.
- Each Playbook scenario, run manually against the Web UI/REST API with the default `qwen2.5:7b`/Ollama provider, produces the documented behavior.
- Switching `llm.provider` to `gemini`/`anthropic`/`openrouter` (with a valid key) works with no code change.
- Langfuse shows traces for agent/tool/model calls; Qdrant holds the RAG/semantic collections; H2 holds seeded domain + memory data.
- Layers 0–1 of the evaluation harness pass with no LLM (`ToolEvalTest`, `RetrievalEvalTest` including hybrid vs dense-only).
- Layers 2–3 pass against the default Ollama provider (`PromptEvalTest`, `EvaluationHarnessTest`) without asserting exact prose.



## Open Questions / Risks

**Status: decided** (approved with spec). Architecture spikes only what remains technically unknown (how our package is scanned after `AdkWebServer.start()`, HITL `FunctionResponse` JSON, embedding dim check).

### 1. Spring DI inside `AdkWebServer`-loaded agent classes

**Decided: our `SupportAssistantApplication.main` + composition root. Spring for infrastructure beans — not for constructing** `ROOT_AGENT`**.**

`AdkWebServer` *is* a Spring Boot app (`@SpringBootApplication`). Our `main` is the process entrypoint and calls `AdkWebServer.start(agents...)`, which boots `SpringApplication(AdkWebServer.class)` and registers `AgentStaticLoader`. A context exists. That does **not** mean our `@Service` classes are scanned (`AdkWebServer` lives in `com.google.adk.web`, so default component scan will not pick up `com.northwind.support`), and it does **not** mean agents can use constructor injection: they are built in `main` / as `ROOT_AGENT` fields, not as Spring beans.

Fighting that with `ApplicationContextAware` static holders is how you get "works on my machine, fails on first request" ordering bugs.

**Plan:** a small `AppServices` registry populated once at startup (Spring Boot AutoConfiguration in our package is the likely way to still get JPA `DataSource`, Qdrant client, OTel while `AdkWebServer` owns the context). Agent classes build `ROOT_AGENT` by reading that registry (lazy, so class-load order cannot outrun bean creation). Architecture spike verifies AutoConfiguration is picked up under `start()` on 1.9.x; if not, wrap `SpringApplication` ourselves with `scanBasePackages` / extra sources — still one process, still no second REST layer.

### 2. Embedding model (fully local)

**Decided:** `nomic-embed-text` **via Ollama HTTP, behind** `EmbeddingClient`**. Do not send embed requests through ADK** `BaseLlm`**.**

ADK's Java model types (`Gemini`, `Claude`, `OpenAiCompatibleLlm`) are chat/completions models. Embeddings are a different HTTP shape. Calling Ollama directly (or its OpenAI-compatible embeddings route) is the boring path and keeps the default stack fully local/free. Architecture confirms with one `curl` that the pulled model returns 768-dim vectors; the interface stays swappable if a cloud embedder is added later.

### 3. ADK Java evaluation API vs custom JUnit harness

**Decided: keep a custom JUnit harness. Do not depend on Java** `AgentEvaluator` **or the Web UI Eval tab.**

Python ADK has `AgentEvaluator` and `adk eval` ([docs](https://google.github.io/adk-docs/evaluate/)). Java's matching Web UI/REST eval endpoints are **unimplemented** as of [adk-java#300](https://github.com/google/adk-java/issues/300) (still open). The Eval tab in `AdkWebServer` cannot save or run eval sets today.

**How Java developers actually test agents today** (this is what our harness will do — same patterns ADK itself uses):


| Pattern                                     | What it is                                                                                    | Used for                                                    |
| ------------------------------------------- | --------------------------------------------------------------------------------------------- | ----------------------------------------------------------- |
| `InMemoryRunner` + `InMemorySessionService` | Run an agent graph in-process, no web server                                                  | All automated agent/workflow tests                          |
| `TestLlm` (ADK test utility)                | Fake LLM with scripted responses                                                              | Deterministic routing / tool-choice tests without Ollama    |
| Real `Runner` + local Ollama                | Full integration                                                                              | `@Tag("llm")` agent eval against real model                 |
| Event-stream assertions                     | Inspect `runner.runAsync(...)` events for `tool_call`, `transfer`, `adk_request_confirmation` | Tool trajectory, workflow order, HITL pause, routing target |
| Direct tool unit tests                      | Call Java tool methods / repos                                                                | Layer 0 — no agent, no LLM                                  |


Nobody serious waits for the Java Eval UI. They write JUnit tests that spin up `InMemoryRunner`, feed a query, and assert on the **event list** (which tools fired, in what order, with what args) plus optional `must_contain` on the final text. Python teams use `AgentEvaluator`; Java teams use `Runner` + assertions until parity ships.

Our golden JSON mirrors Python eval-set shape (`query`, `expected_tool_use`, `must_contain`) so a future Java `AgentEvaluator` is a runner swap. Revisit only if that API ships and covers Layers 0–3.

### 4. Human approval (HITL) — demo UX vs automated tests

**Two different ADK mechanisms — do not conflate them:**


| Mechanism                                                                                              | Demo UX                                                                                                                                                                                                                                                                             | Automated test                                                                                                                                   |
| ------------------------------------------------------------------------------------------------------ | ----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------ |
| `ToolConfirmation` (`FunctionTool` with `requireConfirmation`, or `toolContext.requestConfirmation()`) | **Web UI shows an approval dialog** ([ADK confirmation docs](https://adk.dev/tools-custom/confirmation/), [Java 1.0 HITL blog](https://developers.googleblog.com/announcing-adk-for-java-100-building-the-future-of-ai-agents-in-java/)). User clicks Approve/Reject in the Dev UI. | `InMemoryRunner`: programmatically send the `adk_request_confirmation` `FunctionResponse` with `confirmed: true/false` — no Postman, no browser. |
| `LongRunningFunctionTool`                                                                              | Pauses until the **client** posts a `functionResponse` on `/run` or `/run_sse`. General **workflow Resume** from Web UI/CLI is [not supported](https://google.github.io/adk-docs/runtime/resume/).                                                                                  | Same REST shape, or Runner API in tests.                                                                                                         |


**Decided for this POC:** implement HITL with `ToolConfirmation` **on the refund tool**, not `LongRunningFunctionTool`. That gives a clean live-demo path (type query → dialog appears → click Approve) and a clean test path (`InMemoryRunner` + synthetic confirmation response). Postman is **not** part of the Playbook demo flow.

`LongRunningFunctionTool` remains a documented alternative in Architecture if we need to show async external jobs; it is not the primary HITL demo unless the 1.9.x spike proves `ToolConfirmation` cannot express "refund above threshold."

**What open question #4 is supposed to do:** define how a *paused* agent run continues after human input — which event to send back, and whether the UI or only REST can send it. For our refund demo, the answer is: **UI dialog via** `ToolConfirmation` for humans; **Runner event injection** for JUnit. Architecture spike only documents the exact `FunctionResponse` JSON for the test harness.

### 5. `coordinator-of-coordinators` as a unified entry point

**Decided: stay deferred. Do not build it in this POC.**

Each `demo-*` agent is a teaching unit: the Playbook names which dropdown entry to pick, and eval asserts on that graph. A top-level router would hide which pattern ran, couple every demo's prompt to every other, and make Layer 3 failures ambiguous. Revisit only after all demos and the harness pass.