# Proposals Index

Design proposals, investigation findings, and idea pads for Parsley
(`ng.componenteditor`). Each `proposal-*.md` at the repo root is a standalone
document; this index tracks what they are and their **current** state so the
collection stays trustworthy as work ships.

## Status legend

| Marker | Meaning |
|---|---|
| ✅ EXECUTED | Shipped. Kept as the design/decision record; no open work. |
| 🔶 PARTIALLY EXECUTED | Some parts shipped, some still open (see the doc's status line for the split). |
| ⏸ SUPERSEDED / PAUSED | Overtaken by another doc, or paused; may carry residual open work. |
| 🟢 OPEN | Live proposal — not yet executed; the decision/design is the point. |
| 💡 IDEAS | Exploratory idea pad, not a concrete plan. |

## The proposals

| Document | What it is | Status | Open work |
|---|---|---|---|
| [`proposal-design-cleanup.md`](proposal-design-cleanup.md) | The `editor.*` package consolidation + typed-accessor cleanup that preceded (and replaced) the package restructure | ✅ EXECUTED | none (root rename tracked below) |
| [`proposal-severity-policy.md`](proposal-severity-policy.md) | `SeverityPolicy` layer centralizing validation-severity interpretation; fixes the keypath Missing-Key bug | ✅ EXECUTED | none |
| [`proposal-package-restructure.md`](proposal-package-restructure.md) | Original package-restructuring plan | ⏸ SUPERSEDED / PARTIAL | **Tier 2: root namespace rename** `org.objectstyle.wolips.*` → `parsley.*` (deferred, not done) |
| [`proposal-wiring-audit.md`](proposal-wiring-audit.md) | Deep audit: accidentally-disabled / half-wired / dead code vs. `wolips-original` | 🔶 PARTIAL | **2.1–2.4** validation staleness (→ revalidate), **2.6** Parsley menu (feature), **3.1** dead builder/nature cluster, **4.1** ContentDescriberWO (verify first) |
| [`proposal-revalidate.md`](proposal-revalidate.md) | Project-wide revalidation to kill stale markers (the lost build-time validation sweep) | 🟢 OPEN | the whole proposal — the substantive next feature; absorbs wiring-audit 2.1–2.4 |
| [`proposal-live-channel.md`](proposal-live-channel.md) | Ideas for a live IDE ↔ running-app channel | 💡 IDEAS | exploratory; no commitment |
| [`proposal-tag-library-format.md`](proposal-tag-library-format.md) | Prior-art survey (CEM, JSP TLD, JSF, web-types, Stencil, Vue, VS Code custom-data) + design input for the Parsley/ng tag-library descriptor — the single source of truth for a project's tags, read by runtime + tooling | 🟢 OPEN | the format design itself; ultimately lands in Parsley/ng with this editor as one consumer |
| [`tag-library-formats/`](tag-library-formats/overview.md) | **Field-by-field deep-dive** on 10 component-descriptor formats (the 7 above + Angular, Blazor/Razor, Svelte 5), observed as industry best-practice for a *greenfield* ng/WO format — one doc per format + a cross-format `overview.md` with a field universe and prioritized recommendations | 🟢 OPEN | feeds the format design; companion to the summary proposal above |
| [`proposal-apilib-format.md`](proposal-apilib-format.md) + [`apilib-format/example.apilib`](apilib-format/example.apilib) | **The strawman.** A concrete `.apilib`/`.apiext` v2 design presented as ONE self-documenting example file (real WO elements, vocabulary explained inline, no DTD), synthesizing the deep-dive + all 11 [`apiext-format`](https://github.com/undur/apiext-format/issues) issues. Reviewed by subtraction. | 🟢 OPEN | format design; lands in `undur/apiext-format` when settled |

## What's actually open right now

In rough priority order:

1. **Project-wide revalidation** (`proposal-revalidate.md`) — the real user-facing
   next step; the wiring audit's 2.1–2.4 are facets of the same root cause and fold
   into it.
2. **Dead-cluster removal** (`proposal-wiring-audit.md` §3.1) — lower-stakes cleanup;
   careful to keep the live `HTMLProjectParams`.
3. **Root namespace rename** (`proposal-package-restructure.md` Tier 2) — large,
   mechanical, deferred until there's appetite.
4. **Parsley Navigate menu** (`proposal-wiring-audit.md` §2.6) — feature, not cleanup;
   needs `org.eclipse.ui.menus` work + a product call.
5. **Tag-library format** (`proposal-tag-library-format.md`) — forward-looking design
   input, not an editor task. The format belongs in Parsley/ng; this is the coordinating
   record until then. Concrete near-term adds it identifies (deprecation-with-reason,
   declared library identity + version, explicit precedence) can land incrementally.

## Convention for new proposals

- File name: `proposal-<short-kebab-name>.md` in this `proposals/` folder.
- First lines: a `# Title` then a `**Status:**` line using the legend above.
- When a proposal ships, **update its status line in place** (don't delete the doc —
  it's the design record) and reflect it here.
- Detailed change history still lives in [`CHANGES.md`](../CHANGES.md); proposals are the
  *why/decision* record, `CHANGES.md` is the *what-shipped* record.
