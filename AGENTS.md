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
- **Log endpoint URL** — the endpoint lives in the app runtime, not this plugin, and
  the URL form differs: on WebObjects/Wonder (wonder-slim's ERExtensions) it's
  `…/<App>.woa/log`; on ng-objects it's `…/ng/dev/log`. Same parameters on both.

## Dev server

`http://localhost:9485` — **loopback only**, no auth (loopback *is* the trust
boundary). Plain `GET`; most endpoints return JSON (fire-and-forget ones return `ok`). Port is
configurable in Eclipse prefs.

| Endpoint | Params | Does |
|---|---|---|
| `/` | | Self-describing JSON index of every endpoint (also answers unknown paths). Trust it over this table. |
| `/status` | `app?` | Per launch config: running/mode/uptime, project open state, compile errors, registered port/pid/runtime + reachability. |
| `/refreshProject` | `project?`, `build?`, `clean?` | Refresh project(s) from disk + incremental build; returns a build-error report when dirty. Use after editing. |
| `/validate` | `component`, `project?` | Validate a component's template, return problems as JSON. |
| `/revalidate` | `project?` | Revalidate EVERY template in a project (or the whole workspace) — the bulk cure for stale markers. Slow; generous timeout. |
| `/purgeMarkers` | `project?` | Bring out your dead: delete orphaned exact-stock-type PROBLEM markers on js/css/html/xml (legacy-validator leftovers). Typed markers untouched. |
| `/elementApi` | `element`, `project?`, `raw?` | An element's resolved binding API as JSON (types, pull/push, constraints with messages); aliases and shortcuts resolve. `raw=true` = the .apiext XML. |
| `/problems` | `project?`, `severity?`, `limit?` | Problem markers as JSON; `count` is the true total, entries carry a `source` (parsley/java/stock/other). |
| `/console` | `app`/`config`, `tail?` | A launch's console output — kept after the process dies (the startup post-mortem). |
| `/launch` | `config?`/`app?`, `mode?`, `open?`, `ignoreErrors?`, `allowMultiple?`, `waitForPort?`, `timeout?` | List launch configs, or start one — with preflight refusals and wait-until-ready. |
| `/stop` | `app`, `force?` | Stop a running app (terminate, or `force=true` to hard-kill). |
| `/restart` | `app`, `refresh?` + `/launch` params | stop → refresh+rebuild → launch → wait, in one call. |
| `/openProject` | `project`\|`all`, `related?` | Open a closed project with its workspace dependencies (pom-resolved). |
| `/breakpoints` | `skipAll?` | List workspace breakpoints; toggle Skip All. |
| `/refresh` | `path` | Refresh one resource path. |
| `/apps` | `name?` | Discover running apps: port, `runtime` (`ng`/`wo` — pick the right app-endpoint URL form), and source-available dependencies. |
| `/openComponent` | `app?`, `component`, `lineNumber?`, `offset?`, `length?` | Open a component, reveal a position. |
| `/openJavaFile` | `className`, `lineNumber`, `app?` | Open a Java file at a line. |
| `/registerApp` | `name`, `port`, `pid?`, `runtime?` | (App-side, automatic) An app announces its port and framework at startup. You won't call this. |

### `/launch` and `/stop` — start and stop apps

Start an app without the developer doing it by hand:

```bash
curl -s 'http://localhost:9485/launch'                       # list configs: {"configs":[{"name":…,"project":…}]}
curl -s 'http://localhost:9485/launch?config=MyApp%20-%20Local'   # launch by exact config name
curl -s 'http://localhost:9485/launch?app=MyApp'           # launch by project name
curl -s 'http://localhost:9485/launch?app=MyApp&mode=run'  # run mode (default is debug)
```

Mode defaults to **debug** — the dev loop (hot-code-replace / HotswapAgent) needs a
debug JVM, so launch in debug unless you have a reason not to.

Only **Java application** launch configurations are considered (and listed) — Maven
builds, JUnit runs etc. in the workspace's config pool are ignored, even on an exact
name match, since "launching the app" means running its main class.

**Choosing the config matters.** A project often has several configs for different
environments (e.g. `MyApp - Local`, `MyApp - Production`). Resolution: an exact
config name wins; otherwise the query is treated as a project name, and when a project
has several configs it prefers one whose name contains `local`/`dev`. If it still can't
choose safely it launches **nothing** and returns the candidates — so you never
accidentally fire Production. If you get `{"launched":false,"candidates":[…]}`, pick an
exact name from that list.

Stop a running app:

```bash
curl -s 'http://localhost:9485/stop?app=MyApp'             # clean terminate (Eclipse, or graceful kill)
curl -s 'http://localhost:9485/stop?app=MyApp&force=true'  # kill -9 — for a wedged JVM (e.g. DCEVM choked on a big reload)
```

Default is a clean terminate via Eclipse (or a graceful `kill` of the registered pid if
Eclipse doesn't own the launch). `force=true` hard-kills the registered pid — reach for
it when a hot reload wedged the JVM and a clean stop won't take.

### `/apps` — discover an app's port and which dependencies you can read

Apps register their port with the dev server at startup, so you can look up where one
is running by name instead of being told the port or guessing:

```bash
curl -s 'http://localhost:9485/apps'                 # list of running apps
curl -s 'http://localhost:9485/apps?name=MyApp'      # {"found":true,"app":{…}} or {"found":false,…}
```

Each app entry looks like:

```json
{
  "name":"MyApp","port":1200,"running":true,"lastSeen":1780611151951,"pid":"24067",
  "dependencies":[
    {"name":"ERExtensions","path":"/Users/you/git/wonder-slim/ERExtensions",
     "sourceFolders":["/Users/you/git/wonder-slim/ERExtensions/src/main/java", …]},
    …
  ]
}
```

Two things you get from this:

- **Port** — build the log URL yourself: `…:<port>/cgi-bin/WebObjects/<name>.woa/log`.
  `running` is a live reachability check done when you call `/apps` (a TCP probe of the
  port), so the list only shows apps that are actually up — dead entries are dropped, since
  apps don't deregister on shutdown. `lastSeen` (epoch-millis of the last startup
  announcement) is just a recency hint alongside it.
- **`dependencies`** — the app's dependencies whose **source is open in the workspace**:
  their project name, on-disk path, and source folders. These are the libraries you can
  actually read and edit (e.g. to understand a framework's behavior, or fix a bug across
  the boundary). Jar-only dependencies are deliberately omitted — if it's not here, you
  don't have its source in this workspace. Computed live, so opening/closing a project in
  Eclipse changes the list without restarting the app.

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

From the app runtime. **Dev mode only.** The URL form depends on the runtime:

```
http://localhost:<PORT>/cgi-bin/WebObjects/<App>.woa/log    # WebObjects/Wonder (wonder-slim ERExtensions)
http://localhost:<PORT>/ng/dev/log                          # ng-objects
```

Get `<PORT>` and `<App>` from `/apps` (above) rather than guessing — e.g.
`curl -s 'http://localhost:9485/apps'` returns each app's name and port. (Probe
`…/ng/dev/type` if you need to tell the runtimes apart: it returns `ng` on ng-objects
and 404s on WO.)

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
