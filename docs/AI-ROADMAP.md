# AI Roadmap

This document describes optional AI capabilities that may be added **after the core backend is stable**.

AI should extend the product where probabilistic reasoning is useful. It should not replace deterministic domain logic, scheduling rules, validation, state transitions, or persistence rules.

## 1. Natural Language → Structured Task

Allow the user to write something like:

> "Remind me next Sunday evening to wash the floor. It should take about 30 minutes and it's medium priority."

The model should return a structured `TaskDraft`, for example:

```text
title
description
importance
estimatedDurationMinutes
deadline
scheduledFor
recurrence
```

The LLM must not write directly to the database.

Expected flow:

```text
Natural language
↓
LLM structured output
↓
Schema validation
↓
Domain validation
↓
User confirmation if needed
↓
TaskService
↓
Database
```

Why this fits:
- Natural language is genuinely useful for task creation.
- The existing Task domain already provides a clear structured target.
- It demonstrates structured outputs, validation, guardrails, and separation between AI and backend business logic.

---

## 2. Tool-Calling Assistant

After the core APIs are stable, an AI assistant may use backend tools such as:

```text
getTodayAgenda()
getActionableTasks()
getRecommendations(availableMinutes)
getTask(id)
scheduleTask(id, date)
```

Example request:

> "I have 45 minutes before I leave. What should I do?"

The model can retrieve actionable tasks and recommendations, then explain the best options.

For mutating actions such as scheduling or rescheduling, the assistant should not silently change data.

Why this fits:
- The backend already exposes meaningful business capabilities.
- The agent reasons over tools instead of reimplementing domain logic.
- It demonstrates tool calling while keeping the backend as the source of truth.

---

## 3. Human-in-the-Loop + Guardrails

Any AI-generated action that changes task state should be validated by the backend.

For higher-impact actions, prefer confirmation before execution.

Example:

```text
AI proposal:
"Move Backend Project to tomorrow at 18:00"

↓
Backend validates command

↓
User confirms

↓
TaskService.schedule(...)
```

Useful guardrails:

- strict structured schemas
- enum validation
- date/time validation
- domain state validation
- no direct database access from the LLM
- explicit confirmation for selected mutating actions

Why this fits:
- The project has real state transitions and side effects.
- It demonstrates safe AI integration rather than treating model output as trusted application state.

---

## 4. Evals

Once AI parsing or tool calling exists, maintain a small evaluation dataset based on realistic user requests.

Examples:

```text
"Finish homework by Thursday"
→ deadline extracted correctly
```

```text
"I have 20 minutes"
→ recommendation tool called with availableMinutes=20
```

```text
"Move this completed task to tomorrow"
→ assistant should not bypass the reopen/schedule domain flow
```

Evaluate areas such as:

- structured field extraction
- tool selection
- argument correctness
- invalid-action rejection
- hallucinated fields/actions
- confirmation behavior

Why this fits:
- AI behavior is probabilistic and should be measured.
- It demonstrates engineering discipline beyond simply calling an LLM API.

---

## 5. AI Observability / Tracing

When the AI flow becomes non-trivial, add tracing around:

```text
User request
↓
Model call
↓
Structured output / tool calls
↓
Validation
↓
Domain command
↓
Result
```

Useful signals may include:

- latency
- model errors
- tool calls selected
- validation failures
- retries
- token usage/cost
- final outcome

Why this fits:
- Tool-calling systems become difficult to debug without visibility into the execution path.
- Add this only once there is enough AI behavior to justify it.

---

# Optional / Not Currently Needed

## MCP

Consider MCP only if there is a real use case for exposing the task manager to external AI clients or agents.

Possible future tools:

```text
list_tasks
get_agenda
get_recommendations
schedule_task
```

Do not add MCP only to list it as a technology.

## RAG

Not currently justified.

The project does not yet contain a meaningful document/knowledge corpus that requires retrieval.

Only reconsider RAG if the product later includes notes, project documents, personal knowledge, or other unstructured information that the assistant needs to search.

## Skills / Reusable Workflows

Consider reusable AI workflows only after multiple real workflows emerge, for example:

```text
Plan my next 2 hours
Weekly task cleanup
Prepare tomorrow's agenda
```

Do not build a skills framework before repeated workflows actually exist.

---

# Recommended Order

Do not implement AI until the core V1 backend is working and its APIs/domain rules are stable.

Suggested progression:

```text
Core V1 backend
↓
Natural-language structured Task input
↓
Guardrails + human confirmation
↓
Tool-calling assistant
↓
Evals
↓
Tracing / observability
```

MCP, reusable skills, and RAG should only be reconsidered when a concrete product requirement justifies them.

## Guiding Principle

> Use AI for ambiguity, language understanding, and orchestration.
> Keep deterministic business rules in the backend.

The AI layer may suggest and orchestrate actions, but the backend remains the source of truth.
