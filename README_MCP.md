MCP server (mcp-proxy-sidecar) — local run instructions

Location: `HackITAll_2026_v2/mcp.json`

This repo includes a small helper scripts to start/stop the MCP proxy sidecar used by JetBrains tools.

Files added
- `scripts/run-mcp.sh` — runs `npx -y mcp-proxy-sidecar` in the foreground (logs to stdout).
- `scripts/run-mcp-bg.sh` — runs the sidecar in background using `nohup`, writes logs to `mcp.log` and PID to `mcp.pid`.
- `scripts/stop-mcp.sh` — stops the background process (reads `mcp.pid` or falls back to `pkill`).

Usage examples

Run in foreground (interactive):

```bash
cd HackITAll_2026_v2
scripts/run-mcp.sh
```

Run in background (creates `mcp.log` and `mcp.pid`):

```bash
cd HackITAll_2026_v2
scripts/run-mcp-bg.sh
```

Stop the background process:

```bash
cd HackITAll_2026_v2
scripts/stop-mcp.sh
```

Notes and caveats
- Running these scripts will call `npx -y mcp-proxy-sidecar` which may download packages from the network if not cached. If you want to avoid network access, install `mcp-proxy-sidecar` into a local `node_modules` or use a cached environment.
- The environment variables match `mcp.json`: `WS_PORT=27045` and `LOG_ENABLED=true`. Adjust the scripts if you want different values.
- The scripts are deliberately minimal and POSIX/bash friendly. Make them executable if necessary: `chmod +x scripts/*.sh`.
