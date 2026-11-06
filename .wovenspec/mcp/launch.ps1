# Launch this project's wovenspec MCP server (the stdio proxy).
#
# Point your MCP client's server command at this script. It starts
# `wovenspec serve`, which auto-spawns the background daemon on
# first run and proxies tool calls over stdio. See wovenspec\mcp\
# setup-mcp.md for how to register it with your specific client.
$ErrorActionPreference = 'Stop'

# This script lives at <project>\wovenspec\mcp\launch.ps1; the
# project root (where wovenspec.toml lives) is two directories up.
$projectRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path

# Which wovenspec to run: $env:WOVENSPEC_BIN when that variable is
# set and non-empty, otherwise the path recorded below when this
# project was scaffolded. Those are the only two candidates. PATH is
# never consulted and there is no bare `wovenspec` fallback, because
# this project's daemon and this launcher must be the same build:
# the daemon identity handshake pins the framework version to a
# fingerprint of the executable file, so a second wovenspec
# installation launching this project retires the running daemon,
# and the two installations then evict each other in turn. A bare
# name is a different file on every machine and after every
# reinstall; a named file is not.
#
# The recorded path belongs to the machine that scaffolded this
# project. Working from a clone, or moved wovenspec? Set
# WOVENSPEC_BIN, or re-run the scaffolding command here.
$recordedBinary = '/Users/markvangulik/.cargo/bin/wovenspec'
$wovenspecBinary = $env:WOVENSPEC_BIN
if (-not $wovenspecBinary) {
	$wovenspecBinary = $recordedBinary
}

# Windows has no executable permission bit, so being a file rather
# than a directory or a missing path is the whole check available.
if (-not (Test-Path -LiteralPath $wovenspecBinary -PathType Leaf)) {
	[Console]::Error.WriteLine(
		"wovenspec launcher: not an executable file:`n" +
		"  $wovenspecBinary`n`n" +
		"WOVENSPEC_BIN overrides the path recorded when this`n" +
		"project was scaffolded ($recordedBinary). Set`n" +
		"WOVENSPEC_BIN to this machine's wovenspec executable, or`n" +
		"re-run the scaffolding command in the project root to`n" +
		"record it.")
	exit 1
}

Set-Location $projectRoot
& $wovenspecBinary serve @args
