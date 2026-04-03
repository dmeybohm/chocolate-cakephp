#!/bin/bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
VM_DIR="$SCRIPT_DIR/vm-data"
DISK_FILE="$VM_DIR/disk.qcow2"
SEED_ISO="$VM_DIR/seed.iso"
RAM="4096"
CPUS="4"
SSH_PORT="2222"

if [ ! -f "$DISK_FILE" ] || [ ! -f "$SEED_ISO" ]; then
    echo "Error: VM not set up yet. Run setup-vm.sh first."
    exit 1
fi

echo "Starting VM (SSH on port $SSH_PORT)..."
echo "Press Ctrl-A X to quit QEMU"
echo ""

qemu-system-x86_64 \
    -enable-kvm \
    -m "$RAM" \
    -smp "$CPUS" \
    -drive file="$DISK_FILE",format=qcow2 \
    -drive file="$SEED_ISO",format=raw \
    -net nic \
    -net user,hostfwd=tcp::"$SSH_PORT"-:22 \
    -nographic
