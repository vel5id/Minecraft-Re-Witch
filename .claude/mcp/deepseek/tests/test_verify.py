import verify


def test_pass(tmp_path):
    r = verify.run(["bash", "-c", "exit 0"], cwd=str(tmp_path))
    assert r["ran"] is True
    assert r["passed"] is True
    assert r["timed_out"] is False


def test_fail_captures_error_excerpt(tmp_path):
    r = verify.run(["bash", "-c", "echo 'error: boom happened'; exit 1"], cwd=str(tmp_path))
    assert r["passed"] is False
    assert "boom happened" in r["excerpt"]


def test_timeout(tmp_path):
    r = verify.run(["bash", "-c", "sleep 5"], cwd=str(tmp_path), timeout=1)
    assert r["timed_out"] is True
    assert r["passed"] is False


def test_string_command_is_shlex_split(tmp_path):
    r = verify.run("bash -c 'exit 0'", cwd=str(tmp_path))
    assert r["passed"] is True


def test_env_is_merged(tmp_path):
    r = verify.run(["bash", "-c", '[ "$MYVAR" = hi ]'], cwd=str(tmp_path), env={"MYVAR": "hi"})
    assert r["passed"] is True


def test_unrunnable_command_is_error_not_exception(tmp_path):
    r = verify.run(["this_binary_does_not_exist_42"], cwd=str(tmp_path))
    assert r["ran"] is False
    assert r["passed"] is False
    assert "could not run" in r["excerpt"]


def test_non_executable_shell_script_runs_via_interpreter(tmp_path):
    # A fresh `git worktree` checks out gradlew/mvnw as mode 644 (non-executable). The no-shell
    # exec of `./gradlew` then dies with EACCES; the gate must transparently run it via its
    # shebang interpreter rather than report a (false) gate error.
    script = tmp_path / "gradlew"
    script.write_text("#!/usr/bin/env sh\nexit 0\n")
    script.chmod(0o644)
    r = verify.run(["./gradlew", "build"], cwd=str(tmp_path))
    assert r["ran"] is True
    assert r["passed"] is True
    assert r["timed_out"] is False


def test_non_executable_script_failure_is_red_not_gate_error(tmp_path):
    # Running via the interpreter must still surface a genuine non-zero exit as a real failure.
    script = tmp_path / "run.sh"
    script.write_text("#!/bin/sh\necho 'error: assertion nope'; exit 1\n")
    script.chmod(0o644)
    r = verify.run(["./run.sh"], cwd=str(tmp_path))
    assert r["ran"] is True
    assert r["passed"] is False
    assert "nope" in r["excerpt"]


def test_non_executable_script_with_no_shebang_defaults_to_sh(tmp_path):
    script = tmp_path / "noshebang.sh"
    script.write_text("exit 0\n")  # no shebang -> default /bin/sh
    script.chmod(0o644)
    r = verify.run(["./noshebang.sh"], cwd=str(tmp_path))
    assert r["ran"] is True
    assert r["passed"] is True
