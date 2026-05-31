# Live IDE ↔ App Channel — Ideas

Brainstorm notes on what to build on top of the Parsley dev server now that we
have a bidirectional socket between Eclipse and a running WO/ng-objects app.

Status: **ideas only**, not a plan. Captured for review. Nothing here is
committed to.

## The mental-model shift

The dev server today is one-directional in spirit: **app → IDE** ("open this
file at this line"). But it's a socket — inherently bidirectional. The richest
ideas come from treating the IDE and the running app as **two halves of one
development surface**, each able to see and drive the other.

Two directions:

- **App → IDE** — the running app tells the IDE about itself (what's happening,
  what broke, what it knows).
- **IDE → App** — the IDE reaches into the live app (inspect state, trigger
  actions, change things).

The dream environment is where the boundary between "editing" and "running"
gets thin.

---

## App → IDE ideas

### Live request inspector in Eclipse
The app streams each request/response cycle to a Parsley view: URL, the
component tree that rendered, timing, the session. You see your app's actual
behaviour *in the IDE* as you click around in the browser. Like a WO-aware
"Network tab" that understands components and bindings, not just HTTP.

### Live component render tree
When a page renders, the app reports which components were instantiated and how
they nested. Click a node → jump to that component's source. Debug "why did
this render this way" structurally, instead of reading generated HTML.

### Binding values at render time  ⭐
The standout. When a page renders, for each binding the app evaluated
(`$selectedObject.name` → `"Hugi"`), it reports the *actual resolved value*.
In the template editor, hover a binding and see what it evaluated to on the
last render. The template stops being static text and becomes annotated with
live data. No other WO tool has ever done this well.

### Exceptions push themselves to the IDE
Instead of you noticing an exception in the browser and clicking a link, the
app *pushes* the exception to Eclipse the moment it happens — opens the file,
highlights the line, shows the message inline. The browser link becomes a
fallback, not the primary path. Errors come to you.

### Runtime validation feedback loop
The app reports KVC failures, unbound-binding warnings, and missing-key
exceptions back to the editor as it actually hits them at runtime — so the
editor's static validation gets corrected/augmented by ground truth.
"You flagged this as an error but it resolved fine at runtime," or vice versa.
The two validation worlds (editor guesses, runtime knows) reconcile.

### Hot-reload status
The app tells the IDE when a class/template got hot-swapped (DCEVM), so the
editor can show "this is live in the running app" vs. "you've edited but it
hasn't reloaded." Removes the "did my change take?" uncertainty.

---

## IDE → App ideas

### Inspect live objects
From the IDE, ask the running app: "show me the current session," "show me the
EC's registered objects," "evaluate this keypath against the current page's
component." A live object browser fed by the running app — inspecting *real
state*, not a debugger snapshot.

### Trigger actions / navigation
From the IDE, tell the app "render ComponentX with these bindings" or "navigate
to this page" — and the browser (or a preview pane) updates. Foundation of a
live preview that's actually *live*, not a static mockup.

### Push template edits without redeploy  ⭐
You edit a `.html` template in Eclipse; the IDE pushes the new template text to
the running app over the socket; the app re-parses and re-renders that
component — no rebuild, no restart, not even DCEVM (templates aren't compiled).
Instant template iteration. Very achievable, since templates are parsed at
runtime. A genuine "whoa" feature.

### Live binding experimentation
Change a binding in the editor, push it, see the running page update. The
Bindings Inspector becomes a live control panel for the actual running
component.

### Evaluate keypaths in context
Type `selectedObject.invoice.total` in an Eclipse field; the app evaluates it
against the current page's component and returns the value (and the type
chain). Instant "what would this binding produce" without adding it to a
template and reloading.

---

## The load-bearing idea: re-attaching runtime context to source

Everything above is, underneath, one project: **tear down the opacity wall
between the template you wrote and the thing that rendered.** WO development has
always had this wall. You write `$foo.bar.baz` and you don't know what it
resolves to, or where in the render it lives, until you run it and squint at a
browser. The information you need exists *at runtime* and is then thrown away
before it ever reaches the tooling.

The two areas of deepest pain — *linking the rendered template back to source*
and *understanding the flow of data between elements* — are the same problem
seen from two angles. Both need the runtime to report **structured,
source-aware facts** to the IDE, and the IDE to resolve those facts back to
editor locations.

### The missing half: stack frames need their element context

A Java stack trace is a **call stack** — "method called method called method."
But a WO render is happening in *two* stacks at once:

1. **The Java call stack** — `appendToResponse` → `appendChildrenToResponse` →
   `appendToResponse` → … the methods.
2. **The component / element context stack** — `Main` is rendering, inside its
   `WORepetition` on iteration 3, inside the `InvoiceRow` component, inside
   *its* `WOConditional`, evaluating the binding `$invoice.total`.

The Java stack shows you (1), and for component exceptions it's nearly useless:
a dozen near-identical `appendToResponse` frames of framework plumbing, with no
indication of *which component* or *which binding* each frame was working on.
The information you actually need to diagnose is (2) — and it's **completely
invisible** in a normal trace. You end up reverse-engineering "which of these
identical frames corresponds to the part of my page that broke."

> In practice, the WOContext stack is **often more useful than the Java stack**
> when debugging component exceptions. Both together is best — but the context
> stack carries the *meaning*, while the Java stack only carries the
> *mechanism*. A stack frame without its element context is half a fact.

The dream version of exception-push is therefore not "a stack trace with
clickable lines." It's a **dual-stack diagnosis view** — the Java reality on one
side, the WO reality on the other:

```
java.lang.NullPointerException
  at InvoiceRow.total()                  │ Component: InvoiceRow
                                          │ Element:   wo:str  "$invoice.total"
                                          │ In:        InvoiceList.wo, repetition iter 3
                                          │ where:     invoice = <Invoice pk=4711>
                                          │            invoice.total = null
  at WORepetition.appendToResponse()      │ Element:   wo:repetition "$invoices"
                                          │            list size 12, index 3
  at InvoiceList.appendToResponse()       │ Component: InvoiceList
  …framework frames folded…
```

The right-hand side is the one that actually says what went wrong: "it blew up
rendering the `total` binding of the 4th invoice row, and that invoice's total
is null." You're not reading a call stack — you're reading *what your app was
doing, in your app's own terms*.

Why this is both powerful and achievable:

- **The runtime already has all of it.** `WOContext` carries the element ID and
  the component stack during rendering. `WOContext.elementID()` literally *is*
  a path through the component tree. At throw time the framework knows exactly
  where in the render it was. We're not computing anything new — we're capturing
  context that already exists and currently gets discarded.
- **It maps to source through things we already understand.** Parsley already
  parses templates and knows where every element and binding sits in the
  source. Given the runtime's element path, we resolve it to the exact offset
  in the exact template file. The dual stack is dual-navigable: Java frame →
  `.java`, element context → `.html` template.
- **It generalizes past exceptions.** The same captured context powers the
  annotated-template idea — "binding `$invoice.total` evaluated to `null`" is
  the *same observation* whether it threw or not. An exception is just a
  render-context observation that happened to fail.

### Observations, not commands

This points at the right design philosophy for the whole channel. The dev
server today speaks **imperatives**: "open this file at this line." The dream
environment should speak **observations**: "here is a render event / an
exception event / a binding-evaluation event" — facts the IDE can do many
things with.

A render event and an exception event become the *same kind of message* with a
different outcome field. The runtime emits structured truths
(`component X, binding Y, file Z, line N, value V`); Eclipse — or a future LSP
client, a logging view, whatever — each consumes them how it likes.

This is the same tension we settled for the `.papi` element spec: *declarative
facts the consumer interprets* beat *imperative commands baked for one
consumer*. The live channel wants the same shape.

**If there is a unifying first step beneath all of these features, it is:
define the structured event vocabulary the app speaks to the IDE, and the
resolution layer that maps those events back to source.** Get that right and
the individual dream features — annotated templates, dual-stack exceptions,
live preview — become almost easy. They are all *views onto the same
context-reporting channel*.

---

## The combinations that feel dream-like

The individual ideas are nice; the *combinations* are where it becomes a dream
environment.

### 1. The annotated template
Binding-values-at-render-time (app→IDE) + the template editor. You look at
`<wo:str value="$selectedObject.name" />` and the editor shows `→ "Hugi"`
ghosted next to it, from the last real render. Every binding annotated with
live truth. Combine with push-template-edits (IDE→app): edit binding, see it
re-evaluate live, all without leaving the editor.

### 2. Errors that come to you, with context
Exception-pushes-itself (app→IDE) + live-object-inspection (IDE→app). The
exception opens the file at the line *and* brings the relevant live state — the
session, the component's bindings at failure time, the object that was nil. You
land on the bug with everything you need already laid out, instead of starting
a debugging expedition.

### 3. True live preview
Push-template-edits + trigger-render + a preview pane. Edit template → push →
app renders → preview updates. The Tier-2 live preview from the roadmap, but
driven by this channel instead of pointing a browser at localhost. Tighter,
IDE-native, and it works for a single component in isolation, not just whole
pages.

---

## What to reach for first

Ranked by (impact × feasibility × uniqueness):

1. **Binding values at render time → annotated templates.** Highest "no one
   else has this" factor. Technically very doable: the runtime already
   evaluates these bindings; it just needs to report them. The editor already
   knows where the bindings are. The wiring is the new part. This single
   feature would make Parsley feel magical and unmistakably *alive*. It attacks
   the deepest, oldest pain in WO development: **bindings are opaque** — you
   write `$foo.bar.baz` and don't know what it resolves to until you run it and
   squint at the page. An editor that shows the live resolved value inline, as
   you edit, is the thing people tell their friends about.

2. **Dual-stack exception diagnosis (push + element context).** Two layers:
   (a) the app *pushes* the exception to the IDE the moment it happens, instead
   of you noticing it in the browser and clicking; (b) each frame carries its
   WOContext — the component, element, and binding it was working on. The
   second layer is the real prize: the WOContext stack is often *more* useful
   than the Java stack for component exceptions, and it has never been
   available to tooling. The runtime already holds the context at throw time;
   we just capture it and hand it over. Build the context-capture once and it
   also feeds idea #1 (annotated templates).

3. **Push template edits → live re-render.** The "whoa" feature. Templates
   being runtime-parsed makes this far easier than hot-swapping code. The
   edit-see loop for templates collapses to near-zero.

---

## One caution

Every feature here makes the IDE depend on a *running app in a known state*.
They're wonderful when the app is up and reachable; they must degrade
gracefully (and visibly) when it isn't.

The design challenge across all of them is the same: make **"the app is
connected and live"** a first-class, visible state, and make every feature fall
back cleanly to static behaviour when it's not.

This loops straight back to the dev-server status-indicator / toggle question:
that status surface isn't just for the server — it's the foundation the whole
live-environment story sits on. The toggle button is step one of something much
bigger.
