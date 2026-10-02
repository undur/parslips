# AGENTS.md

Two kinds of agent read this file. Find yours:

- **You're working on this repository** (the Parslips plugin itself): read
  [`CLAUDE.md`](CLAUDE.md). It covers the build, the architecture, conventions, and how
  releases work.
- **You're working on a WebObjects / ng-objects project in Eclipse with Parslips
  installed**, and want your disk edits to take effect: use
  **[parslips-skill](https://github.com/undur/parslips-skill)**. It explains how to drive
  the plugin's dev server (`localhost:9485`) and the running app's dev endpoints. That
  covers refreshing and rebuilding, validating templates, launching and restarting apps,
  and reading the console, the log and the Problems view. It's written for agents, and
  it's released alongside each plugin version.

This file used to carry its own copy of that guide. Two copies drift, so there is one
now: the skill. When the skill and a running dev server disagree, the server wins.
`curl -s http://localhost:9485/` returns its self-describing index of every endpoint.

## The two things to know before the skill loads

1. **After editing any project file, run `GET /refreshProject?project=NAME`.** Eclipse
   only notices edits made in its own editor; until you refresh, your change doesn't
   exist as far as the build or the running app is concerned. `ok` means it compiled
   cleanly. Any other answer means it didn't.
2. **Every refusal reads the same way.** `error` means your call is wrong (a parameter
   is missing or invalid). `reason` means the call was fine but nothing happened, and
   says why. `hint`, when present, is the call that fixes it.

## Setup (for the developer)

1. **Install the plugin.** In Eclipse, open *Help → Install New Software…*, add
   `https://undur.github.io/parslips/repository/`, select **Parsley Template Editor**, and
   restart. It coexists with WOLips; set `project.base=wo` in `build.properties` so
   Parslips handles WO projects.
2. **Install the skill** for your agent; its
   [README](https://github.com/undur/parslips-skill#readme) covers personal and
   per-project installs.
3. **Run apps from Eclipse in debug mode** so changes hot-swap. For structural changes
   (new methods and fields) to reload too, run on **JBR** with
   `-XX:+AllowEnhancedClassRedefinition` and the **HotswapAgent** java agent.
4. **Confirm** with `curl http://localhost:9485/`, which should return a JSON index. If
   the connection is refused, the plugin isn't loaded or the port differs (check Eclipse
   preferences).
