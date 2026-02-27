#!/bin/bash
#
# Parsley Template Editor — Build & Install
#
# Builds all Parsley plugins and installs them into Eclipse.
#
# Usage:
#   ./install.sh /path/to/Eclipse.app
#
# After installation, restart Eclipse to pick up the changes.
#

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
REPO_DIR="$SCRIPT_DIR"
P2_REPO="$REPO_DIR/wolips.p2/target/repository"

# The bundle IDs that make up the Parsley feature
BUNDLE_IDS=(
    "ng.componenteditor"
    "parslips.tooling"
    "parslips.lsp"
)

# --- Locate Eclipse ---

if [[ -z "${1:-}" ]]; then
    echo "Usage: ./install.sh /path/to/Eclipse.app"
    exit 1
fi

ECLIPSE_APP="$1"

if [[ ! -d "$ECLIPSE_APP" ]]; then
    echo "❌ Not found: $ECLIPSE_APP"
    exit 1
fi

ECLIPSE_BIN="$ECLIPSE_APP/Contents/MacOS/eclipse"
if [[ ! -x "$ECLIPSE_BIN" ]]; then
    echo "❌ Eclipse binary not found at: $ECLIPSE_BIN"
    exit 1
fi

ECLIPSE_CONFIG="$ECLIPSE_APP/Contents/Eclipse/configuration"
BUNDLES_INFO="$ECLIPSE_CONFIG/org.eclipse.equinox.simpleconfigurator/bundles.info"

if [[ ! -f "$BUNDLES_INFO" ]]; then
    echo "❌ bundles.info not found at: $BUNDLES_INFO"
    exit 1
fi

echo "🔧 Eclipse: $ECLIPSE_APP"

# --- Build ---

echo ""
echo "📦 Building Parsley Template Editor..."
cd "$REPO_DIR"

./mvnw -B -q package

if [[ ! -d "$P2_REPO" ]]; then
    echo "❌ Build succeeded but p2 repository not found at: $P2_REPO"
    exit 1
fi

echo "   ✅ Build complete"

# --- Install / Update via p2 director ---

FEATURE_ID="ng.componenteditor.feature.feature.group"
P2_URI="file://$P2_REPO"

# Check if already installed
INSTALLED=$("$ECLIPSE_BIN" -nosplash \
    -application org.eclipse.equinox.p2.director \
    -listInstalledRoots 2>/dev/null | grep "^$FEATURE_ID/" || true)

echo ""
if [[ -n "$INSTALLED" ]]; then
    INSTALLED_VERSION="${INSTALLED#*/}"
    echo "♻️  Updating (installed: $INSTALLED_VERSION)..."

    # Uninstall old version, then install new
    "$ECLIPSE_BIN" -nosplash \
        -application org.eclipse.equinox.p2.director \
        -repository "$P2_URI" \
        -uninstallIU "$FEATURE_ID" \
        2>&1 | grep -v "^WARNING:" | grep -v "^$" | grep -v "DEBUG" || true

    "$ECLIPSE_BIN" -nosplash \
        -application org.eclipse.equinox.p2.director \
        -repository "$P2_URI" \
        -installIU "$FEATURE_ID" \
        2>&1 | grep -v "^WARNING:" | grep -v "^$" | grep -v "DEBUG" || true
else
    echo "🆕 Installing Parsley Template Editor for the first time..."

    "$ECLIPSE_BIN" -nosplash \
        -application org.eclipse.equinox.p2.director \
        -repository "$P2_URI" \
        -installIU "$FEATURE_ID" \
        2>&1 | grep -v "^WARNING:" | grep -v "^$" | grep -v "DEBUG" || true
fi

# --- Patch bundles.info ---
#
# Eclipse uses a shared p2 pool (~/.p2/) for profile metadata, but the OSGi runtime
# reads bundle locations from bundles.info inside Eclipse.app's configuration directory.
# The p2 director updates the profile but does NOT update bundles.info, so Eclipse
# keeps loading the old plugin version at startup.
#
# Fix: copy each built jar into the Eclipse bundle cache and update bundles.info to
# point to it.

# Find (or create) the p2 bundle cache directory inside this Eclipse installation
CACHE_DIR=$(find "$ECLIPSE_CONFIG/org.eclipse.osgi" -type d -name "plugins" 2>/dev/null \
    | while read -r dir; do
        # Find the cache dir that contains p2-installed bundles (not the shared pool)
        if [[ "$dir" == *"/data/"* ]]; then
            echo "$dir"
            break
        fi
    done)

if [[ -z "$CACHE_DIR" ]]; then
    # No existing p2 cache — use the Eclipse plugins directory instead
    CACHE_DIR="$ECLIPSE_APP/Contents/Eclipse/plugins"
fi

echo ""

for BUNDLE_ID in "${BUNDLE_IDS[@]}"; do
    # Find the built plugin jar
    BUILT_JAR=$(find "$P2_REPO/plugins" -name "${BUNDLE_ID}_*.jar" -type f | head -1)
    if [[ -z "$BUILT_JAR" ]]; then
        echo "⚠️  Built jar not found for $BUNDLE_ID — skipping"
        continue
    fi

    NEW_VERSION=$(basename "$BUILT_JAR" | sed "s/${BUNDLE_ID}_//; s/\.jar$//")

    # Copy the built jar into the cache
    JAR_NAME="${BUNDLE_ID}_${NEW_VERSION}.jar"
    INSTALLED_JAR="$CACHE_DIR/$JAR_NAME"
    cp "$BUILT_JAR" "$INSTALLED_JAR"

    # Read the current bundles.info entry for this bundle
    OLD_LINE=$(grep "^${BUNDLE_ID}," "$BUNDLES_INFO" || true)

    if [[ -n "$OLD_LINE" ]]; then
        # Extract start level and autostart from existing entry
        START_LEVEL=$(echo "$OLD_LINE" | cut -d',' -f4)
        AUTOSTART=$(echo "$OLD_LINE" | cut -d',' -f5)
    else
        # New installation — use sensible defaults
        START_LEVEL=4
        AUTOSTART=false
    fi

    NEW_LINE="${BUNDLE_ID},${NEW_VERSION},${INSTALLED_JAR},${START_LEVEL},${AUTOSTART}"

    if [[ -n "$OLD_LINE" ]]; then
        if [[ "$OLD_LINE" == "$NEW_LINE" ]]; then
            echo "   ✅ $BUNDLE_ID already up to date ($NEW_VERSION)"
        else
            # Replace the old entry with the new one
            TEMP_FILE=$(mktemp)
            sed "s|^${BUNDLE_ID},.*|${NEW_LINE}|" "$BUNDLES_INFO" > "$TEMP_FILE"
            mv "$TEMP_FILE" "$BUNDLES_INFO"
            echo "   ✅ $BUNDLE_ID updated → $NEW_VERSION"
        fi
    else
        # Append new entry
        echo "$NEW_LINE" >> "$BUNDLES_INFO"
        echo "   ✅ $BUNDLE_ID added → $NEW_VERSION"
    fi
done

# --- Done ---

echo ""
echo "✅ Parsley Template Editor installed"
echo ""
echo "🔄 Restart Eclipse to pick up the changes."
