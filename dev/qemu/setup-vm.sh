#!/bin/bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
VM_DIR="$SCRIPT_DIR/vm-data"
IMAGE_URL="https://cloud-images.ubuntu.com/noble/current/noble-server-cloudimg-amd64.img"
IMAGE_FILE="$VM_DIR/ubuntu-24.04-cloud.img"
DISK_FILE="$VM_DIR/disk.qcow2"
SEED_ISO="$VM_DIR/seed.iso"
DISK_SIZE="20G"
RAM="2048"
CPUS="2"
SSH_PORT="2222"

mkdir -p "$VM_DIR"

# Download cloud image if not present
if [ ! -f "$IMAGE_FILE" ]; then
    echo "Downloading Ubuntu 24.04 cloud image..."
    curl -L -o "$IMAGE_FILE" "$IMAGE_URL"
else
    echo "Cloud image already downloaded."
fi

# Create disk from cloud image if not present
if [ ! -f "$DISK_FILE" ]; then
    echo "Creating VM disk ($DISK_SIZE)..."
    cp "$IMAGE_FILE" "$DISK_FILE"
    qemu-img resize "$DISK_FILE" "$DISK_SIZE"
else
    echo "VM disk already exists."
fi

# Find SSH public key
SSH_KEY=""
for key in ~/.ssh/id_ed25519.pub ~/.ssh/id_rsa.pub ~/.ssh/id_ecdsa.pub; do
    if [ -f "$key" ]; then
        SSH_KEY="$(cat "$key")"
        echo "Using SSH key: $key"
        break
    fi
done

if [ -z "$SSH_KEY" ]; then
    echo "Error: No SSH public key found in ~/.ssh/"
    exit 1
fi

# Create cloud-init user-data
cat > "$VM_DIR/user-data" <<USERDATA
#cloud-config
users:
  - name: dev
    sudo: ALL=(ALL) NOPASSWD:ALL
    shell: /bin/bash
    ssh_authorized_keys:
      - $SSH_KEY

package_update: true
packages:
  - openjdk-17-jdk
  - git
  - openssh-server

runcmd:
  - systemctl enable ssh
  - systemctl start ssh
USERDATA

# Create cloud-init meta-data
cat > "$VM_DIR/meta-data" <<METADATA
instance-id: chocolate-cakephp-test
local-hostname: cakephp-test
METADATA

# Create seed ISO for cloud-init
echo "Creating cloud-init seed ISO..."
genisoimage -output "$SEED_ISO" \
    -volid cidata \
    -joliet -rock \
    "$VM_DIR/user-data" "$VM_DIR/meta-data" 2>/dev/null

echo ""
echo "=== VM Setup Complete ==="
echo ""
echo "To start the VM, run:"
echo "  $SCRIPT_DIR/start-vm.sh"
echo ""
echo "To SSH into the VM:"
echo "  ssh -p $SSH_PORT dev@localhost"
echo ""
echo "Note: On first boot, cloud-init will install packages (JDK 17, git)."
echo "This may take a few minutes. Check progress with: ssh -p $SSH_PORT dev@localhost 'cloud-init status'"
