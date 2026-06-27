"""Gate-outcome -> caller status mapping.

The trust bug this guards: a gate that could not EXECUTE (`ran=False`) must be its own
`gate_error` state — never `verify_failed` (which means the gate ran and the code failed) and
never `verified`. Conflating them lets a harness/environment problem masquerade as a code verdict,
or lets the agent's untrustworthy self-report stand in for a real gate.
"""
import server


def test_no_gate_is_unverified():
    assert server._derive_status(False, False, None) == "unverified"


def test_ran_and_passed_is_verified():
    assert server._derive_status(True, True, True) == "verified"


def test_ran_and_failed_is_verify_failed():
    assert server._derive_status(True, True, False) == "verify_failed"


def test_could_not_run_is_gate_error_not_verify_failed():
    assert server._derive_status(True, False, False) == "gate_error"
    assert server._derive_status(True, False, None) == "gate_error"
