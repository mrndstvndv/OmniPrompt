# Termux RUN_COMMAND Intent via `am` Command

Run commands in Termux from other apps or ADB using Android's `am startservice` command.

## Prerequisites

1. **Permission**: The calling app must have `com.termux.permission.RUN_COMMAND` granted
2. **Allow external apps**: Set `allow-external-apps=true` in `~/.termux/termux.properties`
3. **Draw Over Apps** (Android 10+): Grant Termux "Draw Over Apps" permission for foreground commands

## Basic Syntax

```bash
am startservice --user 0 -n com.termux/com.termux.app.RunCommandService \
-a com.termux.RUN_COMMAND \
--es com.termux.RUN_COMMAND_PATH '<path-to-executable>' \
--esa com.termux.RUN_COMMAND_ARGUMENTS '<arg1>,<arg2>' \
--es com.termux.RUN_COMMAND_WORKDIR '<working-directory>' \
--ez com.termux.RUN_COMMAND_BACKGROUND 'false' \
--es com.termux.RUN_COMMAND_SESSION_ACTION '0'
```

## Command Extras

| Extra | Type | Description |
|-------|------|-------------|
| `com.termux.RUN_COMMAND_PATH` | `--es` | Path to executable (mandatory) |
| `com.termux.RUN_COMMAND_ARGUMENTS` | `--esa` | Comma-separated arguments |
| `com.termux.RUN_COMMAND_WORKDIR` | `--es` | Working directory |
| `com.termux.RUN_COMMAND_BACKGROUND` | `--ez` | Run in background (`true`/`false`) |
| `com.termux.RUN_COMMAND_SESSION_ACTION` | `--es` | Session behavior (see below) |

## Session Action Values

| Value | Behavior |
|-------|----------|
| `0` | Switch to new session and open Termux activity |
| `1` | Switch to new session but don't open activity |
| `2` | Keep current session and open activity |
| `3` | Keep current session but don't open activity |

## Path Shortcuts

- `~/` expands to `/data/data/com.termux/files/home`
- `$PREFIX/` expands to `/data/data/com.termux/files/usr`

## Examples

### Simple echo command

```bash
am startservice --user 0 -n com.termux/com.termux.app.RunCommandService \
-a com.termux.RUN_COMMAND \
--es com.termux.RUN_COMMAND_PATH '/data/data/com.termux/files/usr/bin/echo' \
--esa com.termux.RUN_COMMAND_ARGUMENTS 'hi' \
--ez com.termux.RUN_COMMAND_BACKGROUND 'false' \
--es com.termux.RUN_COMMAND_SESSION_ACTION '0'
```

### Run a script from home directory

```bash
am startservice --user 0 -n com.termux/com.termux.app.RunCommandService \
-a com.termux.RUN_COMMAND \
--es com.termux.RUN_COMMAND_PATH '~/myscript.sh' \
--ez com.termux.RUN_COMMAND_BACKGROUND 'false' \
--es com.termux.RUN_COMMAND_SESSION_ACTION '0'
```

### Run script with sudo and arguments

```bash
am startservice --user 0 -n com.termux/com.termux.app.RunCommandService \
-a com.termux.RUN_COMMAND \
--es com.termux.RUN_COMMAND_PATH '/data/data/com.termux/files/usr/bin/sudo' \
--esa com.termux.RUN_COMMAND_ARGUMENTS './start.sh,steven' \
--es com.termux.RUN_COMMAND_WORKDIR '/data/data/com.termux/files/home' \
--ez com.termux.RUN_COMMAND_BACKGROUND 'false' \
--es com.termux.RUN_COMMAND_SESSION_ACTION '0'
```

### Run command and keep shell open

```bash
am startservice --user 0 -n com.termux/com.termux.app.RunCommandService \
-a com.termux.RUN_COMMAND \
--es com.termux.RUN_COMMAND_PATH '/data/data/com.termux/files/usr/bin/bash' \
--esa com.termux.RUN_COMMAND_ARGUMENTS '-c,echo hi; exec bash' \
--ez com.termux.RUN_COMMAND_BACKGROUND 'false' \
--es com.termux.RUN_COMMAND_SESSION_ACTION '0'
```

## References

- [Termux RUN_COMMAND Intent Wiki](https://github.com/termux/termux-app/wiki/RUN_COMMAND-Intent)
- [termux-tasker README](https://github.com/termux/termux-tasker)

