# ADK Concepts

---

## 1. Agent transfer flags

On `LlmAgent.builder()`:

```java
.disallowTransferToParent(true)
.disallowTransferToPeers(true)
```


| Flag                             | Effect                                               |
| -------------------------------- | ---------------------------------------------------- |
| `disallowTransferToParent(true)` | Agent cannot transfer control **up** to its parent   |
| `disallowTransferToPeers(true)`  | Agent cannot transfer **sideways** to sibling agents |


**Why use both?** Makes an agent **terminal** — it finishes the task instead of bouncing control around the tree (avoids coordinator ↔ specialist loops).

**In this repo:** Specialists (`billing`, `shipping`, `account`) set both flags. The coordinator does not.

**Side effect:** With `disallowTransferToParent(true)`, the next user turn goes back to the **root** agent (coordinator re-routes). The conversation does not "stick" on the specialist.

```
Coordinator (can transfer down)
├── billing   ← terminal (no parent/peer transfer)
├── shipping  ← terminal
└── account   ← terminal
```

---



## 2. Dynamic routing

Pattern: **coordinator → specialist**. No custom Java router — routing is prompt-driven + ADK's built-in `transfer_to_agent`.

### Agent tree (`demo-dynamic-routing`)


| Agent           | Role                        | Tools                             |
| --------------- | --------------------------- | --------------------------------- |
| **Coordinator** | Classify intent, route only | None                              |
| **billing**     | Refunds, payments           | `orderLookup`, `policyRetrieve`   |
| **shipping**    | Delays, tracking            | `orderLookup`, `shipmentTracking` |
| **account**     | Contact preferences         | `customerPreference`              |


Built in `DynamicRoutingAgent.create()` — coordinator has `.subAgents(billing, shipping, account)`.

### Runtime flow

```
User message
  → Coordinator LLM reads prompt + message
  → LLM calls transfer_to_agent(agent_name="billing")
  → ADK AutoFlow resolves target by name in agent tree
  → Specialist runs with its own prompt + tools
  → Specialist replies (cannot transfer again)
```



### What drives routing?

- **Prompt rules** in `demo-dynamic-routing-coordinator.v1.md` (refund → billing, late package → shipping, preference → account)
- **Not** hardcoded `if/else` in Java



### Example routes (Playbook §4)


| User says                              | Routes to  |
| -------------------------------------- | ---------- |
| "I want a refund for ORD-5001."        | `billing`  |
| "Why is my package late for ORD-5001?" | `shipping` |
| "Update my contact preference to SMS." | `account`  |




### Key ADK mechanism

When an agent has `subAgents()`, ADK exposes `transfer_to_agent` automatically. `AutoFlow` intercepts the call and hands off via `root_agent.findAgent(name)` — no `AgentTool` wrapper needed.

---



## 3. How user input reaches the agent

When you type in the ADK Dev UI, this project does **not** add custom chat code. ADK's web layer (`com.google.adk.web`, scanned by `SupportAssistantApplication`) handles it.

### Path

```
Dev UI (localhost:8000)
  → POST /run_sse  (or /run)
  → SpringAgentLoader.loadAgent(appName)
  → Runner.runAsync(userId, sessionId, newMessage)
  → Root agent invoked
  → beforeModelCallback (guardrails)
  → LLM (instruction + history + your text)
  → Events streamed back to UI
```



### Request shape (`AgentRunRequest`)

```json
{
  "appName": "demo-dynamic-routing",
  "userId": "user",
  "sessionId": "<session-id>",
  "newMessage": {
    "role": "user",
    "parts": [{ "text": "I want a refund for ORD-5001." }]
  }
}
```

- `appName` = agent dropdown value (must match `agent.name()`, e.g. `demo-dynamic-routing`)
- `userId` = ADK Dev UI default is `user` (must match the session you created or selected)
- `newMessage` = your typed query as `Content` with `role: user`



### Session holds context


| Stored in session    | Used for                                      |
| -------------------- | --------------------------------------------- |
| Conversation history | Short-term memory (prior turns)               |
| `session.state`      | Long-term context (e.g. `customer_id` for §8) |


Create session with initial state (REST):

```bash
curl -s -X POST "http://localhost:8000/apps/demo-memory-personalization/users/user/sessions" \
  -H "Content-Type: application/json" \
  -d '{"state":{"customer_id":"CUST-1001"}}'
```

Use `userId` `user` so curl-created sessions match Dev UI. If you curl and then click **New session** in Dev UI, you get a different session with no `customer_id` — open the curl-created session from the session list instead (see [Playbook §8 session identity](playbook.md#session-identity-playbook-8)).

Dev UI alternative (no curl): **More options → Update state** before the first message.

### What the LLM sees

1. Agent **instruction** (prompt file)
2. **Conversation history** from the session
3. Your **new message**
4. **Tool schemas** (if the agent has tools)



### Chat text vs session state


| Source                           | Goes to                                                  |
| -------------------------------- | -------------------------------------------------------- |
| What you type                    | LLM (via `newMessage`)                                   |
| `customer_id` in `session.state` | Tools (via `ToolContext.state()`) — not parsed from chat |


Example: `CustomerPreferenceTool` reads `toolContext.state().get("customer_id")`, not the user message.

### Guardrails (before LLM)

`InputGuardrailCallback` runs on every agent via `Guardrails.apply()`:

- Blocks prompt injection / jailbreak
- Masks inbound PII (e.g. card numbers)

If blocked, the LLM is skipped and a refusal is returned directly.

---



## Quick reference


| Concept         | One-liner                                                          |
| --------------- | ------------------------------------------------------------------ |
| Transfer flags  | Lock an agent in place; specialists can't hand off again           |
| Dynamic routing | Coordinator LLM picks specialist; ADK executes `transfer_to_agent` |
| User input      | Dev UI → `/run_sse` → `newMessage` → Runner → agent → LLM          |
| Agent selection | `appName` in request = Dev UI dropdown = Spring bean name          |
| Memory          | History in session; `customer_id` in `session.state` for tools     |




## See also

- [Playbook §4 — Dynamic Routing](playbook.md#4-dynamic-routing--coordinator-specialist--demo-dynamic-routing)
- [Architecture §6.4 — D4](architecture.md#64-dynamic-routing--d4)
- Source: `DynamicRoutingAgent.java`, `SpringAgentLoader.java`, `InputGuardrailCallback.java`

