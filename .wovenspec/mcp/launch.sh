#!/usr/bin/env bash
# Launch this project's wovenspec MCP server (the stdio proxy).
#
# Point your MCP client's server command at this script. It starts
# `wovenspec serve`, which auto-spawns the background daemon on
# first run and proxies tool calls over stdio. See .wovenspec/mcp/
# setup-mcp.md for how to register it with your specific client.
set -euo pipefail

# This script lives at <project>/.wovenspec/mcp/launch.sh; the
# project root (where wovenspec.toml lives) is two directories up.
script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
project_root="$(cd "$script_dir/../.." && pwd)"

# Which wovenspec to run: WOVENSPEC_BIN when that variable is set
# and non-empty, otherwise the path recorded below when this project
# was scaffolded. Those are the only two candidates. PATH is never
# consulted and there is no bare `wovenspec` fallback, because this
# project's daemon and this launcher must be the same build: the
# daemon identity handshake pins the framework version to a
# fingerprint of the executable file, so a second wovenspec
# installation launching this project retires the running daemon,
# and the two installations then evict each other in turn. A bare
# name is a different file on every machine and after every
# reinstall; a named file is not.
#
# The recorded path belongs to the machine that scaffolded this
# project. Working from a clone, or moved wovenspec? Set
# WOVENSPEC_BIN, or re-run the scaffolding command here.
recorded_binary='/Users/markvangulik/.cargo/bin/wovenspec'
wovenspec_binary="${WOVENSPEC_BIN:-$recorded_binary}"

if [ ! -f "$wovenspec_binary" ] || [ ! -x "$wovenspec_binary" ]; then
	cat >&2 <<EOF
wovenspec launcher: not an executable file:
  $wovenspec_binary

WOVENSPEC_BIN overrides the path recorded when this project was
scaffolded ($recorded_binary). Set WOVENSPEC_BIN to this machine's
wovenspec executable, or re-run the scaffolding command in the
project root to record it.
EOF
	exit 1
fi

cd "$project_root"
exec "$wovenspec_binary" serve "$@"
