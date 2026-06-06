#!/bin/bash
# AI Storage Cleaner — PRoot Environment Setup Script
# This runs inside Android's shell to set up the PRoot + Ubuntu environment

set -e

APP_DIR="$1"
PROOT_DIR="$APP_DIR/proot"
ROOTFS_DIR="$APP_DIR/ubuntu"
PROOT_BIN="$PROOT_DIR/proot"

echo "[1/5] Creating directories..."
mkdir -p "$PROOT_DIR"
mkdir -p "$ROOTFS_DIR"

# Detect architecture
ARCH=$(uname -m)
echo "Architecture: $ARCH"

# Download PRoot if not exists
if [ ! -x "$PROOT_BIN" ]; then
    echo "[2/5] Downloading PRoot..."

    if [ "$ARCH" = "aarch64" ]; then
        PROOT_URL="https://github.com/proot-me/proot-me/releases/download/v5.4.0/proot-v5.4.0-aarch64-static"
    elif [ "$ARCH" = "armv7l" ] || [ "$ARCH" = "armv8l" ]; then
        PROOT_URL="https://github.com/proot-me/proot-me/releases/download/v5.4.0/proot-v5.4.0-arm-static"
    else
        PROOT_URL="https://github.com/proot-me/proot-me/releases/download/v5.4.0/proot-v5.4.0-x86_64-static"
    fi

    curl -L "$PROOT_URL" -o "$PROOT_BIN"
    chmod +x "$PROOT_BIN"
else
    echo "[2/5] PRoot already exists, skipping..."
fi

# Download Ubuntu rootfs if not exists
if [ ! -f "$ROOTFS_DIR/bin/bash" ]; then
    echo "[3/5] Downloading Ubuntu rootfs (this takes a few minutes)..."

    if [ "$ARCH" = "aarch64" ]; then
        ROOTFS_URL="https://cdimage.ubuntu.com/ubuntu-base/releases/24.04/release/ubuntu-base-24.04-base-arm64.tar.gz"
    elif [ "$ARCH" = "armv7l" ] || [ "$ARCH" = "armv8l" ]; then
        ROOTFS_URL="https://cdimage.ubuntu.com/ubuntu-base/releases/24.04/release/ubuntu-base-24.04-base-armhf.tar.gz"
    else
        ROOTFS_URL="https://cdimage.ubuntu.com/ubuntu-base/releases/24.04/release/ubuntu-base-24.04-base-amd64.tar.gz"
    fi

    TARBALL="/tmp/rootfs.tar.gz"
    curl -L "$ROOTFS_URL" -o "$TARBALL"

    echo "[4/5] Extracting rootfs..."
    tar -xzf "$TARBALL" -C "$ROOTFS_DIR"
    rm -f "$TARBALL"
else
    echo "[3/5] Rootfs already exists, skipping..."
    echo "[4/5] Skipping extraction..."
fi

# Configure rootfs
echo "[5/5] Configuring environment..."

# DNS
cat > "$ROOTFS_DIR/etc/resolv.conf" << 'DNS'
nameserver 8.8.8.8
nameserver 8.8.4.4
nameserver 1.1.1.1
DNS

# Hostname
echo "aicleaner" > "$ROOTFS_DIR/etc/hostname"

# Link sdcard
ln -sf /sdcard "$ROOTFS_DIR/sdcard" 2>/dev/null || true

# Create basic .bashrc
cat > "$ROOTFS_DIR/root/.bashrc" << 'BASHRC'
export PATH="/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin"
export HOME=/root
export TERM=xterm-256color
alias ls='ls --color=auto'
alias ll='ls -la'
BASHRC

# Test PRoot
echo ""
echo "Testing PRoot..."
TEST=$($PROOT_BIN --rootfs="$ROOTFS_DIR" --link2symlink /bin/echo "PROOT_OK" 2>&1)
if echo "$TEST" | grep -q "PROOT_OK"; then
    echo ""
    echo "========================================="
    echo "  ✅ Setup complete! Environment ready."
    echo "========================================="
    exit 0
else
    echo ""
    echo "========================================="
    echo "  ❌ PRoot test failed: $TEST"
    echo "========================================="
    exit 1
fi
