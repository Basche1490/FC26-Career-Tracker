"""Runs the PowerShell bridge's Atlas importer on the same pretend save as test_match_log.py and checks that it
describes the match exactly like the Python bridge does. Skipped when PowerShell (pwsh) is not installed.

Run:  python3 tests/bridge/test_powershell_importer.py
"""
import json
import os
import shutil
import subprocess
import sys
import unittest

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
import test_match_log  # noqa: E402

BRIDGE_PS1 = os.path.join(HERE, "..", "..", "pc-bridge", "bridge.ps1")
RUNNER = os.path.join(HERE, "run_ps_importer.ps1")
PWSH = shutil.which("pwsh") or os.environ.get("PWSH")


@unittest.skipUnless(PWSH, "PowerShell (pwsh) is not installed")
class PowerShellImporterTest(test_match_log.ImporterTest):
    """Same data and same expectations as the Python importer test, but the payload comes from bridge.ps1."""

    def import_folder(self, export, snaps):
        env = dict(os.environ, DOTNET_SYSTEM_GLOBALIZATION_INVARIANT="1")
        proc = subprocess.run([PWSH, "-NoProfile", "-File", RUNNER, "-Bridge", os.path.abspath(BRIDGE_PS1),
                               "-Export", export, "-Snapshots", snaps],
                              capture_output=True, text=True, timeout=180, env=env)
        self.assertEqual(0, proc.returncode, proc.stdout + proc.stderr)
        lines = [l for l in proc.stdout.splitlines() if l.startswith("PAYLOAD:")]
        self.assertEqual(1, len(lines), proc.stdout + proc.stderr)
        payload = json.loads(lines[0][len("PAYLOAD:"):])
        self.sent = [payload]


if __name__ == "__main__":
    unittest.main()
