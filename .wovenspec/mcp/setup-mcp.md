# Register this project's wovenspec MCP server

Paste this file into your AI coding assistant and ask it to set up
MCP access for this project. The instructions below are
client-agnostic on purpose — you (the agent) know your own client's
configuration format; apply it.

## What to register

This project ships a wovenspec MCP server. It speaks the Model
Context Protocol over **stdio** and is started by running
`wovenspec serve` from the project root. The framework auto-spawns
a background daemon on first run, so no separate start step is
needed.

Register it under the name **`wovenspec-avail`**. This name is unique
to this project (it carries the project's tool prefix), so it will
not collide with other wovenspec projects you may have registered.

## Canonical invocation

Either run the binary directly from the project root:

```
command: wovenspec
args:    ["serve"]
```

…or point your client at the cross-platform launch script, which
resolves the project root itself:

- macOS / Linux: `.wovenspec/mcp/launch.sh`
- Windows:       `.wovenspec/mcp/launch.ps1`

## Worked example (Claude Code `.mcp.json`)

For a Claude Code project, the registration is a `.mcp.json` at the
repo root:

```json
{
  "mcpServers": {
    "wovenspec-avail": {
      "type": "stdio",
      "command": "wovenspec",
      "args": ["serve"]
    }
  }
}
```

## Your task

1. Detect which MCP client I am using (Claude Code, Cursor, or
   another).
2. Write the equivalent server registration in *that* client's
   configuration format, using the name `wovenspec-avail` and the
   stdio invocation above.
3. Tell me what you changed and how to confirm the server is
   connected.
