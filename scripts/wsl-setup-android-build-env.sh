#!/usr/bin/env bash
# =============================================================================
# wsl-setup-android-build-env.sh
#
# One-shot installer for the Agrilliant Android build environment, intended to
# run inside a fresh WSL2 Ubuntu instance (or any Ubuntu 22.04 / 24.04 host).
#
# Idempotent: re-running on a partially-set-up box updates env vars but won't
# re-download artifacts that are already present.
#
# Installs (~10 GB total on disk):
#   - apt build deps (gcc, g++, libfreetype, libgtk, libstdc++-12, ...)
#   - SDKMAN  (Java toolchain manager)
#   - Liberica NIK 24.x (GraalVM-compatible JDK with native-image)
#   - Android cmdline-tools + platform-tools + platforms;android-34
#                                            + build-tools;34.0.0
#                                            + ndk;26.1.10909125
#   - Sets GRAALVM_HOME, JAVA_HOME, ANDROID_HOME, ANDROID_NDK in ~/.bashrc
#
# Run with:
#   bash wsl-setup-android-build-env.sh
#
# After it finishes, open a NEW shell so .bashrc takes effect, then verify:
#   echo "$GRAALVM_HOME" "$ANDROID_HOME" "$ANDROID_NDK"
#   java -version
#   native-image --version
#   sdkmanager --list_installed
# =============================================================================
set -euo pipefail

# ---------- Pinned versions (bump here when upgrading) ----------------------
NIK_VERSION="${NIK_VERSION:-24.1.r23-nik}"          # SDKMAN identifier
ANDROID_SDK_PLATFORM="${ANDROID_SDK_PLATFORM:-34}"
ANDROID_BUILD_TOOLS="${ANDROID_BUILD_TOOLS:-34.0.0}"
ANDROID_NDK_VERSION="${ANDROID_NDK_VERSION:-26.1.10909125}"
ANDROID_CLI_TOOLS_VERSION="${ANDROID_CLI_TOOLS_VERSION:-11076708}"

# ---------- 1. apt dependencies ---------------------------------------------
echo "==> [1/5] Installing apt dependencies (sudo required)…"
sudo apt-get update -y
sudo apt-get install -y --no-install-recommends \
    curl wget unzip zip ca-certificates git \
    gcc g++ make pkg-config \
    libstdc++-12-dev \
    libfreetype6-dev libpango1.0-dev \
    libx11-dev libxtst-dev libgtk-3-dev \
    libavcodec-dev libavformat-dev libasound2-dev \
    zlib1g-dev

# ---------- 2. SDKMAN + Liberica NIK ----------------------------------------
echo "==> [2/5] Installing SDKMAN…"
if [ ! -d "$HOME/.sdkman" ]; then
    curl -s "https://get.sdkman.io" | bash
fi
# shellcheck disable=SC1091
export SDKMAN_DIR="$HOME/.sdkman"
source "$SDKMAN_DIR/bin/sdkman-init.sh"

echo "==> [2b/5] Installing Liberica NIK $NIK_VERSION via SDKMAN…"
if ! sdk current java 2>/dev/null | grep -q "$NIK_VERSION"; then
    yes | sdk install java "$NIK_VERSION" || {
        echo "  NIK $NIK_VERSION not available via SDKMAN. Listing recent NIK builds:"
        sdk list java | grep -i nik | head -20
        echo "  Re-run with NIK_VERSION=<one of the above> bash $0"
        exit 1
    }
fi
sdk use java "$NIK_VERSION"
GRAALVM_HOME_PATH="$HOME/.sdkman/candidates/java/$NIK_VERSION"

# ---------- 3. Android cmdline-tools ----------------------------------------
echo "==> [3/5] Installing Android cmdline-tools…"
ANDROID_HOME_PATH="$HOME/android"
mkdir -p "$ANDROID_HOME_PATH/cmdline-tools"
cd "$ANDROID_HOME_PATH/cmdline-tools"
if [ ! -d latest/bin ]; then
    CLT_ZIP="commandlinetools-linux-${ANDROID_CLI_TOOLS_VERSION}_latest.zip"
    if [ ! -f "$CLT_ZIP" ]; then
        wget -q "https://dl.google.com/android/repository/$CLT_ZIP"
    fi
    unzip -q -o "$CLT_ZIP"
    rm -rf latest
    mv cmdline-tools latest
fi
export ANDROID_HOME="$ANDROID_HOME_PATH"
export ANDROID_SDK_ROOT="$ANDROID_HOME"
export PATH="$ANDROID_HOME/cmdline-tools/latest/bin:$ANDROID_HOME/platform-tools:$PATH"

# ---------- 4. SDK packages: platform-tools, platform, build-tools, NDK -----
echo "==> [4/5] Accepting licenses + installing SDK packages…"
yes | sdkmanager --licenses > /dev/null
sdkmanager --install \
    "platform-tools" \
    "platforms;android-${ANDROID_SDK_PLATFORM}" \
    "build-tools;${ANDROID_BUILD_TOOLS}" \
    "ndk;${ANDROID_NDK_VERSION}"

ANDROID_NDK_PATH="$ANDROID_HOME/ndk/${ANDROID_NDK_VERSION}"

# ---------- 5. Persist env vars to ~/.bashrc --------------------------------
echo "==> [5/5] Writing env vars to ~/.bashrc…"
MARKER_BEGIN="# >>> agrilliant android build env >>>"
MARKER_END="# <<< agrilliant android build env <<<"
# Remove any previous block, then append fresh one.
sed -i "/$MARKER_BEGIN/,/$MARKER_END/d" "$HOME/.bashrc"
cat >> "$HOME/.bashrc" <<EOF

$MARKER_BEGIN
export SDKMAN_DIR="\$HOME/.sdkman"
[ -s "\$SDKMAN_DIR/bin/sdkman-init.sh" ] && source "\$SDKMAN_DIR/bin/sdkman-init.sh"
export GRAALVM_HOME="$GRAALVM_HOME_PATH"
export JAVA_HOME="\$GRAALVM_HOME"
export ANDROID_HOME="$ANDROID_HOME_PATH"
export ANDROID_SDK_ROOT="\$ANDROID_HOME"
export ANDROID_NDK="$ANDROID_NDK_PATH"
export PATH="\$JAVA_HOME/bin:\$ANDROID_HOME/cmdline-tools/latest/bin:\$ANDROID_HOME/platform-tools:\$PATH"
$MARKER_END
EOF

# ---------- Summary ---------------------------------------------------------
cat <<EOF

============================================================================
 Setup complete.
============================================================================
 GRAALVM_HOME = $GRAALVM_HOME_PATH
 ANDROID_HOME = $ANDROID_HOME_PATH
 ANDROID_NDK  = $ANDROID_NDK_PATH

 Open a NEW shell (or run \`source ~/.bashrc\`) and then verify:
   echo \$GRAALVM_HOME \$ANDROID_HOME \$ANDROID_NDK
   java -version
   native-image --version
   sdkmanager --list_installed

 To run the first APK build:
   cd <your project dir>
   bash scripts/build-apk.sh
============================================================================
EOF
