#!/usr/bin/env bash
# Stop the MCP proxy sidecar started by run-mcp-bg.sh
set -euo pipefail

cd "$(dirname "$0")/.."

if [ -f mcp.pid ]; then
  pid=$(cat mcp.pid)
  if kill "$pid" 2>/dev/null; then
    echo "Stopped pid $pid"
    rm -f mcp.pid
    exit 0
  else
    echo "Failed to kill pid $pid, falling back to pkill"
  fi
fi

if pkill -f mcp-proxy-sidecar; then
  echo "Killed mcp-proxy-sidecar processes"
else
  echo "No mcp-proxy-sidecar process found"
fi
