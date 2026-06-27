"""Server-run authoritative verification for a delegated task.

This is the gate the delegation economy depends on: the calling session trusts a green
verdict here instead of re-reading the implementation. It runs a LOCAL build/test command
(no data leaves the machine) with a hard timeout, and extracts a compact error excerpt so
the failure can be fed back to the agent (or read cheaply by the caller) without shipping the
whole build log.
"""
import os
import shlex
import subprocess

_ERR_KEYS = (
    "error:", "ERROR", "FAILED", "Exception", "Caused by", "BUILD FAILED",
    "cannot find symbol", "AssertionError", "Expected", "but was",
)


def _excerpt(output: str, *, max_err_lines: int = 40, max_chars: int = 4000) -> str:
    """Pull the diagnostic lines (errors/failures) plus a fixed tail — the real cause lives mid-log."""
    lines = output.splitlines()
    errs = [l for l in lines if any(k in l for k in _ERR_KEYS)][:max_err_lines]
    tail = lines[-20:]
    text = "\n".join(errs + ["--- tail ---"] + tail) if errs else "\n".join(tail)
    return text[-max_chars:]


def _interpreter_for(path: str) -> list | None:
    """Argv prefix to run a NON-executable script: its shebang interpreter, else `/bin/sh`.

    Returns None when `path` is not an existing regular file — so a genuine missing-binary or
    non-script EACCES is still reported as a gate error rather than silently masked.
    """
    if not os.path.isfile(path):
        return None
    try:
        with open(path, "rb") as fh:
            first = fh.readline(256)
    except OSError:
        return None
    if first.startswith(b"#!"):
        parts = first[2:].decode("utf-8", "replace").split()
        if parts:
            return parts  # e.g. ["/usr/bin/env", "sh"] or ["/bin/bash"]
    return ["/bin/sh"]


def run(command, cwd: str, env: dict | None = None, timeout: int = 300) -> dict:
    """Run `command` (list of args, or a string that is shlex-split) in `cwd`.

    No shell (`shell=False`) — the command is an arg list, never an interpolated string, so a
    task-derived value cannot inject `;`/`|`/`$(...)`. `env` is MERGED over the current
    environment (e.g. JAVA_HOME for Gradle builds). Returns:
        {ran: bool, passed: bool, timed_out: bool, excerpt: str}

    If the entry point is a non-executable script (a fresh `git worktree` checks out
    `gradlew`/`mvnw` as mode 644), the EACCES is recovered by re-running it through its shebang
    interpreter — still shell-FREE: the interpreter program is explicit and the original args are
    passed verbatim as argv, never re-parsed by a shell.
    """
    if isinstance(command, str):
        command = shlex.split(command)
    full_env = dict(os.environ)
    if env:
        full_env.update(env)

    def _attempt(argv) -> dict:
        p = subprocess.run(argv, cwd=cwd, env=full_env, capture_output=True, text=True,
                           timeout=timeout, start_new_session=True)
        return {"ran": True, "passed": p.returncode == 0, "timed_out": False,
                "excerpt": _excerpt(p.stdout + "\n" + p.stderr)}

    def _timed_out(e) -> dict:
        out = (e.stdout or "") + "\n" + (e.stderr or "")
        return {"ran": True, "passed": False, "timed_out": True,
                "excerpt": _excerpt(out + "\n[VERIFY TIMED OUT]")}

    try:
        return _attempt(command)
    except subprocess.TimeoutExpired as e:
        return _timed_out(e)
    except PermissionError:
        # PermissionError must be handled before the generic OSError below (it is a subclass).
        argv0 = command[0] if command else ""
        resolved = argv0 if os.path.isabs(argv0) else os.path.join(cwd, argv0)
        interp = _interpreter_for(resolved)
        if interp is None:
            return {"ran": False, "passed": False, "timed_out": False,
                    "excerpt": f"could not run verify command (permission denied, not a runnable script): {argv0}"}
        try:
            return _attempt([*interp, resolved, *command[1:]])
        except subprocess.TimeoutExpired as e:
            return _timed_out(e)
        except (OSError, ValueError) as e:
            return {"ran": False, "passed": False, "timed_out": False,
                    "excerpt": f"could not run verify command via interpreter {interp}: {e}"}
    except (OSError, ValueError) as e:
        return {"ran": False, "passed": False, "timed_out": False,
                "excerpt": f"could not run verify command: {e}"}
