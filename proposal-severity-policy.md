# Proposal: Centralize validation-severity interpretation (SeverityPolicy)

**Status:** Proposal — discussion document. Not executed.
**Origin:** The keypath "no key in component" severity bug (a validation error that
ignored the user's Missing-Key preference) turned out to be a *symptom*: the logic
that interprets a severity preference is hand-copied at every validation site, and
one copy drifted. This proposes the missing layer that makes the bug class
impossible, and folds the bug fix into it.

## What the investigation found (the honest picture)

It is **not** a pervasive mess. **14 of 15** severity-interpretation sites are
correct — they all apply the same two-step pattern:

```java
String severity = BindingValidationPreferences.severity(SOME_SEVERITY_KEY);
if (!PreferenceConstants.IGNORE.equals(severity)) {                 // gate on Ignore
    problems.add(new WodXProblem(..., PreferenceConstants.WARNING.equals(severity))); // warning-vs-error flag
}
```

The problem is **duplication, not pervasiveness**: that interpretation is copy-pasted
~15 times (mostly in `AbstractWodBinding` and `AbstractWodElement`, plus
`HtmlCacheEntry`/`WodCacheEntry`). Copy-paste is *why* one copy drifted into a bug
and two others are suspect:

- **1 confirmed bug** — the invalid-keypath branch in `AbstractWodBinding`
  (`validKeyPath != null`, ~lines 291-296): it *computes* the warning flag from the
  severity, then **hardcodes `false`** to the `WodBindingValueProblem` constructor,
  and the branch has **no IGNORE gate**. Result: "no key in component" errors are
  always ERROR and can't be ignored — exactly the reported symptom.
- **2 sites with OR-logic** (OGNL wrapping ~line 414; WOD-errors-mirrored-into-HTML,
  `WodCacheEntry` ~line 57): `innerProblem.isWarning() || WARNING.equals(severity)`.
  **Decision taken: this is intentional** — a wrapped/mirrored problem is a warning
  if either its source or its own severity says so. Preserve the behavior; just
  centralize it. (No behavioral change to these two.)

### How severity → marker works today (for reference)

`WodProblem` (and all subclasses) carry a single **`boolean warning`** field. The
severity *string* → boolean is resolved **at the call site**; `IGNORE` is never a
problem state — it's a gate that prevents problem creation. Marker severity is set
in `WodModelUtils.createMarker` (`isWarning()` → `SEVERITY_WARNING` else
`SEVERITY_ERROR`); HTML problems map the string directly in
`HtmlProblem.createMarker`. The `WodProblem` constructor contract is already
uniform (`boolean warning` everywhere), which makes centralizing clean.

## Out of scope (deliberately)

- **`WELL_FORMED_TEMPLATE_KEY`** — it's a YES/NO/DEFAULT *parser* toggle, not a
  severity. Correctly unrelated.
- **WOO-encoding has no per-type severity** — that's a missing *feature*, not a bug.
  Not part of "centralize what exists."
- **The VALIDATE_* toggles** — the double-gating (toggle, then per-problem severity)
  is intentional. Leave it.
- Any change to the other 13 sites' *behavior*. This is refactor + the one fix only.

## Proposed design — `SeverityPolicy`

A small, focused class (in `org.objectstyle.wolips.bindings.preferences`, beside
`BindingValidationPreferences`) that owns *all* severity interpretation. It reads
through the existing typed accessor, so it's purely the "interpret" layer atop the
"read" layer we already built.

```java
public final class SeverityPolicy {
    private SeverityPolicy() {}

    /** True if the configured severity for this key is IGNORE (suppress the problem). */
    public static boolean isIgnored(String severityKey) {
        return PreferenceConstants.IGNORE.equals(BindingValidationPreferences.severity(severityKey));
    }

    /** WARNING → true, ERROR → false. (Caller must have ruled out IGNORE.) */
    public static boolean isWarning(String severityKey) {
        return PreferenceConstants.WARNING.equals(BindingValidationPreferences.severity(severityKey));
    }

    /**
     * The OR-combine used by wrapped/mirrored problems (OGNL, WOD-in-HTML):
     * warning if the inner problem is already a warning OR this severity says warn.
     * Preserves existing behavior at those two sites.
     */
    public static boolean isWarningOr(String severityKey, boolean innerIsWarning) {
        return innerIsWarning || isWarning(severityKey);
    }
}
```

Note `isWarning` deliberately mirrors today's exact semantics: it returns the result
of `WARNING.equals(...)`, so a non-ERROR/non-WARNING value behaves precisely as the
current `WARNING.equals` checks do (no new exceptions, no behavior change). The
agent's sketch threw on unrecognized values — we **won't** do that; it'd change
behavior. Faithful first, opinionated never (this round).

### Migration

Route all 15 sites through `SeverityPolicy`:

- **13 correct sites:** mechanical swap —
  `!PreferenceConstants.IGNORE.equals(sev)` → `!SeverityPolicy.isIgnored(KEY)`, and
  `PreferenceConstants.WARNING.equals(sev)` → `SeverityPolicy.isWarning(KEY)`.
  Behavior identical; the local `String severity = ...severity(KEY)` reads largely
  disappear.
- **2 OR-logic sites:** use `SeverityPolicy.isWarningOr(KEY, inner.isWarning())` —
  behavior identical, intent now named.
- **The buggy keypath branch:** gains the IGNORE gate it's missing and passes
  `SeverityPolicy.isWarning(KEY)` instead of hardcoded `false`. **This is the bug
  fix** — and it stops being a special case, because it now uses the same layer as
  everything else. (Care needed: trace which severity key actually governs this
  branch — the `warning` it *computed* drew from missingComponent / missingCollection
  depending on the keypath kind, so the IGNORE gate must use the matching key.)

### Why this is the right "whole"

It's the natural completion of the Area-1 accessor work (we did read → value; this
adds read → *interpret*). It fixes the reported bug as a side effect of removing its
cause. It converts two ambiguous sites into named, intentional behavior. And it makes
the 16th copy impossible: a new validation calls `SeverityPolicy`, can't forget the
IGNORE gate, can't hardcode the flag.

## Testing

`SeverityPolicy` is pure and trivially unit-testable (IGNORE/WARNING/ERROR/odd
values → expected booleans) — one focused test instead of trusting 15 scattered
copies. Then in Eclipse: a bad keypath binding with the governing Missing-Key
preference at Error → red, Warning → yellow, Ignore → no marker (the three-way test
that exposed the bug). Plus a spot-check that the OGNL and WOD-in-HTML markers are
unchanged.

## Suggested execution

1. Add `SeverityPolicy` + a unit test. (Commit 1 — pure addition, no behavior change.)
2. Migrate the 13 correct sites + 2 OR sites. (Commit 2 — pure refactor, behavior
   identical, `clean verify` to be sure no incremental-cache illusion.)
3. Fix the keypath branch via the policy (IGNORE gate + correct warning flag).
   (Commit 3 — the behavioral fix; CHANGES.md entry; the three-way Eclipse test.)

Splitting the *fix* (commit 3) from the *refactor* (commit 2) keeps the one
behavior change isolated and reviewable — and bisectable if anything regresses.

## Open question

- Confirm the keypath branch's governing severity key. The old code's computed
  `warning` switched on keypath kind (WOComponent → missingComponent; NSKeyValueCoding
  → missingCollection). The fix's IGNORE gate + warning flag must use the *same*
  per-kind key, or we'd change which preference controls it. Needs careful reading of
  the `bindingValueKeyPath.isXxx()` branches before writing the fix.
