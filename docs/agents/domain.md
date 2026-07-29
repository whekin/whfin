# Domain Docs

How the engineering skills should consume this repo's domain documentation when exploring the codebase.

## Before exploring, read these

- **`CONTEXT.md`** at the repo root, or
- **`CONTEXT-MAP.md`** if it exists — read every context relevant to the task.
- **`docs/adr/`** — read ADRs touching the area being changed.

If these files don't exist, proceed silently. The domain-modeling workflow creates them lazily when terms or decisions are actually resolved.

## File structure

WHFIN uses the single-context layout:

```text
/
├── CONTEXT.md
├── docs/adr/
└── app/, core-ui/, widget/
```

## Use the glossary's vocabulary

When output names a domain concept—in an issue title, proposal, hypothesis, or test—use the term defined in `CONTEXT.md`. Avoid synonyms that the glossary explicitly rejects.

If a required concept is missing, reconsider whether the term belongs to the project or note the gap for domain modeling.

## Flag ADR conflicts

If output contradicts an existing ADR, surface it explicitly rather than silently overriding:

> Contradicts ADR-0007 — but worth reopening because…
