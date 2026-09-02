# Bundled copies of ng-objects' element definitions — a temporary bridge

Everything in this folder is a **verbatim copy** of what ng-appserver ships in its own
`src/main/resources`: the tag registry (`parsley-tag-aliases.properties`) and one `.apiext`
per framework element (`ng/appserver/templating/elements/**`). ng-objects is the source of
truth; edit there, then re-sync here with `./sync.sh`.

## Why the copies exist

The editor reads these files from the project's classpath — but ng-appserver only started
shipping them in 0.1.2, and projects on the 0.1.1 release will be common for a while. Without
them an ng project had two bad options: no binding definitions at all, or borrowing the
WebObjects element of the same name (whose API is not necessarily ng's). So:

- `ParsleyTagAliasResolver` uses `parsley-tag-aliases.properties` from here when an **ng
  project's** classpath declares no aliases (`isNGProject()` gate — WO projects never see it).
- `ApiUtils.readBundledApiext` falls back to `/apiext/ng/<Type>.apiext` when no bundled WO
  definition and no element-own definition was found.

A real file on the classpath always wins: the element's own `.apiext` is resolved first
(`ApiUtils.locateElementApiext`), and the alias fallback only triggers when the classpath has
none.

## When to delete this folder

When an ng-appserver that ships its own registry and `.apiext` files is the floor for every
project you care about (or when ng grows a proper Parslips tag library), delete this folder
and the two fallbacks named above.
