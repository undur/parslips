# Parsley — Driving the Editor from an Agent or External Tool

Parsley exposes a few HTTP hooks so an external tool — a script, or an AI agent
editing files outside Eclipse — can close the edit→see-it-run loop without a human
relaying anything by hand.

Written for whoever's driving them. If you're an agent in a WebObjects/ng-objects
project whose developer runs Eclipse with this plugin, this is your guide; humans,
see **Setup** at the end.

## Why

Edit a file **on disk** and Eclipse doesn't notice — it only tracks edits made
through its own editor. So the change sits there: no recompile, no revalidation, and
a running app keeps using stale `.class` files. Two HTTP surfaces fix that:

1. **Dev server** (this plugin, inside Eclipse) — refresh/rebuild, validate a
   template, open a component.
2. **Log endpoint** (the app's runtime) — read the running app's console.

Together: edit → refresh → app reloads → read the log; and validate a template on
disk before you ever render it.

## Runtimes

The hooks work the same for both runtimes; two things differ:

- **Project type** — `project.base=ng` or `project.base=wo` in `build.properties`
  (selects component types, validation root, template format; absent → probed).
- **Log endpoint** — lives in the app runtime, not this plugin. On WebObjects/Wonder
  it ships in **wonder-slim's ERExtensions**; other runtimes may lack it (the dev
  server and `/validate` still work).

## Dev server

`http://localhost:9485` — **loopback only**, no auth (loopback *is* the trust
boundary). Plain `GET`; `/validate` returns JSON, the rest return `ok`. Port is
configurable in Eclipse prefs.

| Endpoint | Params | Does |
|---|---|---|
| `/refreshProject` | `project?`, `build?`, `clean?` | Refresh project(s) from disk + incremental build. Use after editing. |
| `/validate` | `component`, `project?` | Validate a component's template, return problems as JSON. |
| `/refresh` | `path` | Refresh one resource path. |
| `/openComponent` | `app?`, `component`, `lineNumber?`, `offset?`, `length?` | Open a component, reveal a position. |
| `/openJavaFile` | `className`, `lineNumber`, `app?` | Open a Java file at a line. |

### `/refreshProject` — make Eclipse pick up disk edits

```bash
curl -s 'http://localhost:9485/refreshProject?project=MyApp'   # omit project → all open projects
```

- `build` defaults true (`build=false` to skip).
- `clean` defaults false — **leave it.** Incremental builds yield the per-class delta
  hot-swap needs (same as an in-editor save); `clean=true` forces CLEAN+FULL, which
  *breaks* hot reload. Use it only to recover a corrupted build.

The call blocks until the build settles, so on return the new `.class` files exist.

**Hot-swap reach:** a debug JVM swaps **method bodies**. Shape changes (new/removed
methods or fields, signature changes, new classes) and constructor changes need an
**app restart** — more swaps with JBR + HotswapAgent (see Setup), but new classes
and constructors still restart. If a refresh changes nothing, suspect a shape change
and restart rather than assuming failure.

### `/validate` — catch template errors without rendering

Templates are long strings with weak type/definition discovery; mistakes often hide
until render time. This runs Parsley's validator on a named component and hands back
the problems.

```bash
curl -s 'http://localhost:9485/validate?component=ASISearchPage&project=MyApp'
```

```json
{ "component":"ASISearchPage", "found":true,
  "files":["/MyApp/.../ASISearchPage.html"],
  "problems":[ {"severity":"error","line":17,"charStart":420,"charEnd":448,
                "message":"…","file":"/MyApp/.../ASISearchPage.html"} ] }
```

- Refreshes from disk first — reflects your edits without a prior `/refreshProject`.
- Empty `problems` → clean. `"found":false` → name didn't resolve.
- `severity` error/warning/info; `line` 1-based; `charStart`/`charEnd` pair with
  `/openComponent`'s `offset`/`length`. Re-validating clears prior problems. Works
  for `.wo` bundles and standalone `.html`.
- **`/refreshProject` does not validate templates** (validation is editor-driven) —
  call `/validate` explicitly after a template edit.

## Log endpoint

From the app runtime (wonder-slim ERExtensions on WebObjects/Wonder). **Dev mode
only** — 404 in production.

```
http://localhost:<PORT>/cgi-bin/WebObjects/<App>.woa/log
```

```bash
curl -s '.../log?tail=50'
curl -s '.../log?contains=MYDEBUG&tail=30'   # contains is case-sensitive
```

Replaces "paste the console." Diagnose: add a unique marker
(`log.info("MYDEBUG …")`), refresh/restart, exercise the app, read back
`?contains=MYDEBUG`, then remove the marker.

Buffer: last ~2000 lines, captured after logging init (so early *boot* output isn't
there); one entry per event, stack traces included.

## A loop

```bash
curl -s '.../9485/validate?component=SomeComponent&project=MyApp'   # check the template
curl -s '.../9485/refreshProject?project=MyApp'                     # rebuild Java (incremental)
curl -s '.../MyApp.woa/log?contains=MYDEBUG&tail=40'               # read the result
```

No change after a refresh + method-body edit → re-run the refresh; shape/constructor
edit → restart.

## Template conventions

- **ng-objects** — usually **standalone**: one `.html` with inline bindings
  (`<wo:WOString value="$name" />`).
- **WebObjects/Wonder** — usually **bundle**: a `.wo` folder of `.html` + `.wod`
  (+ optional `.woo`), HTML using `<webobject name="X">` against named `.wod` entries.

## Setup (for the developer)

1. **Install the plugin** — Eclipse → *Help → Install New Software…*, add
   `https://undur.github.io/parslips/repository/`, pick **Parsley Template Editor**,
   restart. (Coexists with WOLips; set `project.base=wo` so Parsley wins on WO
   projects.)
2. **Run the app from Eclipse in debug mode** (enables hot-code-replace).
3. **Recommended: enhanced reload** — run on **JBR** with
   `-XX:+AllowEnhancedClassRedefinition` + the **HotswapAgent** java agent; a
   `HOTSWAP AGENT: … reload` line confirms swaps. Without it, only method-body swaps.
4. **Log endpoint** — automatic in dev mode from the app runtime. Share the app's
   port and `.woa` name with the agent.
5. **Confirm:** `curl http://localhost:9485/refreshProject` should respond. Refused →
   plugin not loaded or wrong port (check Eclipse prefs).
