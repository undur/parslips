#!/bin/sh
# Re-copies ng-appserver's tag registry and element .apiext files into this folder.
# ng-objects is the source of truth; never edit the copies here.
set -e
NG="${1:-$HOME/git/ng-objects}/ng-appserver/src/main/resources"
HERE="$(cd "$(dirname "$0")" && pwd)"
rm -f "$HERE"/*.apiext "$HERE"/parsley-tag-aliases.properties
cp "$NG"/parsley-tag-aliases.properties "$HERE"/
cp "$NG"/ng/appserver/templating/elements/*.apiext "$NG"/ng/appserver/templating/elements/ajax/*.apiext "$HERE"/
echo "synced $(ls "$HERE"/*.apiext | wc -l | tr -d ' ') .apiext files + tag registry from $NG"
