#!/usr/bin/env bash
# BDJB Cyberpunk Autoloader build script
# Runs inside WSL/Linux. Called by BUILD.bat on Windows.
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
BDJ_SDK="${BDJ_SDK:-/opt/bdj-sdk}"
OUTPUT_DIR="$SCRIPT_DIR/output"
ELF_PATH="$SCRIPT_DIR/ps5_autoloader/sonic-loader.elf"
VER_FILE="$SCRIPT_DIR/ps5_autoloader/.sonic-loader-version"
XML_PATH="$SCRIPT_DIR/BDMV/META/DL/bdmt_eng.xml"

mkdir -p "$OUTPUT_DIR"

# Remove stale ISOs from previous project names
rm -f "$OUTPUT_DIR"/y2jb-autoloader.iso "$OUTPUT_DIR"/y2jb*.iso

log() { echo "[build] $*"; }
err() { echo "[build] ERROR: $*" >&2; exit 1; }

# ── 0. Auto-bump project version in Makefile ──────────────────────────────────
MAKEFILE="$SCRIPT_DIR/Makefile"
PROJ_VER_OLD=$(grep -E '^VERSION' "$MAKEFILE" | head -1 | sed 's/VERSION[[:space:]]*:=[[:space:]]*//' | tr -d ' \t\r\n')
MAJOR=$(echo "$PROJ_VER_OLD" | cut -d. -f1)
MINOR=$(echo "$PROJ_VER_OLD" | cut -d. -f2)
NEW_MINOR=$(( MINOR + 1 ))
PROJ_VER="${MAJOR}.${NEW_MINOR}"
sed -i "s|^VERSION\s*:=.*|VERSION    := ${PROJ_VER}|" "$MAKEFILE"
log "Project version: $PROJ_VER_OLD -> $PROJ_VER"

# ── 1. bdj-sdk setup (one-time) ───────────────────────────────────────────────
if [ ! -d "$BDJ_SDK/.git" ]; then
    log "Cloning bdj-sdk (one-time, ~100 MB)..."
    sudo git clone --depth 1 --recurse-submodules \
        https://github.com/john-tornblom/bdj-sdk "$BDJ_SDK"
    sudo chmod -R a+rX "$BDJ_SDK"
fi

if [ ! -x "$BDJ_SDK/host/bin/makefs" ]; then
    log "Building makefs..."
    (cd "$BDJ_SDK/host/src/makefs_termux" && \
     sudo env CFLAGS="-Wno-error=implicit-function-declaration -Wno-error=int-conversion" make && \
     sudo cp makefs "$BDJ_SDK/host/bin/makefs" && sudo chmod +x "$BDJ_SDK/host/bin/makefs")
fi

JAVA8_HOME="$BDJ_SDK/host/jdk8"
if [ ! -d "$JAVA8_HOME/bin" ]; then
    log "Setting up Java 8..."
    for pkg in openjdk-8-jdk openjdk-8-jdk-headless; do
        sudo apt-get install -y "$pkg" 2>/dev/null && break || true
    done
    J8=$(readlink -f "$(command -v javac 2>/dev/null || true)" 2>/dev/null | sed 's|/bin/javac||' || true)
    [ -z "$J8" ] && err "Java 8 not found"
    sudo ln -sfn "$J8" "$JAVA8_HOME"
fi

mkdir -p "$BDJ_SDK/target/lib" 2>/dev/null || sudo mkdir -p "$BDJ_SDK/target/lib"
if ls "$BDJ_SDK/host/lib/"*.jar >/dev/null 2>&1; then
    cp -n "$BDJ_SDK/host/lib/"*.jar "$BDJ_SDK/target/lib/" 2>/dev/null || \
        sudo cp -n "$BDJ_SDK/host/lib/"*.jar "$BDJ_SDK/target/lib/" 2>/dev/null || true
fi
if ls "$BDJ_SDK/host/lib/"*.zip >/dev/null 2>&1; then
    cp -n "$BDJ_SDK/host/lib/"*.zip "$BDJ_SDK/target/lib/" 2>/dev/null || \
        sudo cp -n "$BDJ_SDK/host/lib/"*.zip "$BDJ_SDK/target/lib/" 2>/dev/null || true
fi
if [ ! -f "$BDJ_SDK/target/lib/rt.jar" ]; then
    J8_REAL=$(readlink -f "$JAVA8_HOME" 2>/dev/null || echo "$JAVA8_HOME")
    RT=$(find -L "$J8_REAL" -name "rt.jar" 2>/dev/null | head -1 || true)
    [ -n "$RT" ] && (cp "$RT" "$BDJ_SDK/target/lib/rt.jar" 2>/dev/null || sudo cp "$RT" "$BDJ_SDK/target/lib/rt.jar") || err "rt.jar not found"
fi

# ── 2. Sonic-loader auto-update ───────────────────────────────────────────────
SONIC_UPDATED=0
SONIC_OLD_VER="not present"
SONIC_NEW_VER="unknown"

# Pre-seed from local cache so offline builds still show the correct version
if [ -f "$VER_FILE" ]; then
    SONIC_OLD_VER=$(cat "$VER_FILE" | tr -d '\r\n')
    SONIC_NEW_VER="$SONIC_OLD_VER"
elif [ -f "$ELF_PATH" ]; then
    SONIC_OLD_VER="existing (version unknown)"
fi

log "Checking Sonic Loader update..."
API_RESP=$(curl -sf \
    "https://git.earthonion.com/api/v1/repos/soniciso/sonicloader/releases?limit=1" \
    2>/dev/null || true)

if [ -n "$API_RESP" ]; then
    LATEST_VER=$(echo "$API_RESP" | \
        grep -o '"tag_name":"[^"]*"' | head -1 | \
        sed 's/"tag_name":"//;s/"//')
    DOWNLOAD_URL=$(echo "$API_RESP" | \
        grep -o '"browser_download_url":"[^"]*sonic-loader\.elf[^"]*"' | head -1 | \
        sed 's/"browser_download_url":"//;s/"$//')

    if [ -n "$LATEST_VER" ] && [ -n "$DOWNLOAD_URL" ]; then
        if [ "$SONIC_OLD_VER" != "$LATEST_VER" ]; then
            log "Updating sonic-loader: $SONIC_OLD_VER -> $LATEST_VER"
            curl -sfL "$DOWNLOAD_URL" -o "$ELF_PATH"
            echo "$LATEST_VER" > "$VER_FILE"
            SONIC_UPDATED=1
            log "sonic-loader updated to $LATEST_VER"
        else
            log "sonic-loader already at $LATEST_VER"
        fi
        SONIC_NEW_VER="$LATEST_VER"
    else
        log "Warning: could not parse Gitea API response - keeping local $SONIC_NEW_VER"
    fi
else
    log "Warning: could not reach git.earthonion.com - keeping local $SONIC_NEW_VER"
fi

# ── 3. Disc title + theme badge — always use the real version string ───────────
# SONIC_NEW_VER is now always the real version (online latest or offline cached).
# This keeps the disc title, theme badge (Version.SONIC_VERSION), and
# .sonic-loader-version file all in sync.
if [ -n "$SONIC_NEW_VER" ] && [ "$SONIC_NEW_VER" != "unknown" ]; then
    DISC_TITLE="Sonic Loader $SONIC_NEW_VER"
else
    DISC_TITLE="Sonic Loader"
fi

sed -i "s|<di:name>Sonic Loader[^<]*</di:name>|<di:name>$DISC_TITLE</di:name>|g" "$XML_PATH"
sed -i "s|<di:titleName titleNumber=\"1\">Sonic Loader[^<]*</di:titleName>|<di:titleName titleNumber=\"1\">$DISC_TITLE</di:titleName>|g" "$XML_PATH"
log "Disc title: $DISC_TITLE"

# ── 4. Build ISO ──────────────────────────────────────────────────────────────
log "Building ISO..."
cd "$SCRIPT_DIR"
export BDJSDK_HOME="$BDJ_SDK"
export JAVA8_HOME="$BDJ_SDK/host/jdk8"

make clean
make SONIC_VERSION="$SONIC_NEW_VER" all

ISO="cyberbdjb-autoloader.iso"
[ -f "$ISO" ] || err "No $ISO file produced"

cp "$ISO" "$OUTPUT_DIR/"
log "ISO ready: $OUTPUT_DIR/$ISO"

# ── 5. Write summary for BUILD.bat to read ───────────────────────────────────
cat > "$OUTPUT_DIR/.build_summary" << SUMMARY
ISO=$ISO
SONIC_UPDATED=$SONIC_UPDATED
SONIC_OLD_VER=$SONIC_OLD_VER
SONIC_NEW_VER=$SONIC_NEW_VER
DISC_TITLE=$DISC_TITLE
PROJ_VER=$PROJ_VER
PROJ_VER_OLD=$PROJ_VER_OLD
SUMMARY

log ""
log "Build complete."
