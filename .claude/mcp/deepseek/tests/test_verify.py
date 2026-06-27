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
