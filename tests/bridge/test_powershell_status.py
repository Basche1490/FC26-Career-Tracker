"""Runs the PowerShell bridge's health report (status, self check, re-send request) on the same pretend export folders as
the Python bridge and checks that both describe them identically. Skipped when PowerShell (pwsh) is not installed.

Run:  python3 tests/bridge/test_powershell_status.py
"""
import json
import os
import shutil
import subprocess
import sys
import tempfile
import unittest

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
import test_bridge_status  # noqa: E402

BRIDGE_PS1 = os.path.join(HERE, "..", "..", "pc-bridge", "bridge.ps1")
RUNNER = os.path.join(HERE, "run_ps_status.ps1")
PWSH = shutil.which("pwsh") or os.environ.get("PWSH")
NOW = 1_800_000_000
LOCAL_IP = "192.168.2.24"


@unittest.skipUnless(PWSH, "PowerShell (pwsh) is not installed")
class PowerShellStatusTest(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.root = os.path.join(self.tmp.name, "FC26 Export")
        self.outbox = os.path.join(self.root, "outbox")
        os.makedirs(self.outbox)
        self.bridge = test_bridge_status.load_bridge(self.tmp.name)
        self.bridge.get_local_ip = lambda: LOCAL_IP
        self._stdout = sys.stdout
        sys.stdout = open(os.devnull, "w")

    def tearDown(self):
        sys.stdout.close()
        sys.stdout = self._stdout
        self.tmp.cleanup()

    # ----- helpers --------------------------------------------------------------------------------------------------
    def heartbeat(self, age=2, **over):
        data = {"app": "fc26-capture", "version": "2.8.0", "protocol": 2, "time": NOW - age, "started": NOW - 900,
                "exporter": True, "club": "Pumas", "careerDate": "2026-10-16", "scans": 12, "lastMatch": NOW - 3600,
                "lastTransfer": 0, "lastSquad": NOW - 500, "pendingMatches": 0, "undelivered": 0}
        data.update(over)
        with open(os.path.join(self.root, "script_status.json"), "w", encoding="utf-8") as f:
            json.dump(data, f)

    def outbox_file(self, name, age):
        path = os.path.join(self.outbox, name)
        with open(path, "w") as f:
            f.write("{}")
        os.utime(path, (NOW - age, NOW - age))

    def run_ps(self, **scenario):
        base = {"pcName": self.bridge.PC_NAME, "port": self.bridge.LOCAL_PORT, "localIp": LOCAL_IP, "now": NOW,
                "roots": [self.root], "phone": {"time": 0, "ip": ""}, "events": []}
        base.update(scenario)
        path = os.path.join(self.tmp.name, "scenario.json")
        with open(path, "w", encoding="utf-8") as f:
            json.dump(base, f)
        env = dict(os.environ, DOTNET_SYSTEM_GLOBALIZATION_INVARIANT="1")
        proc = subprocess.run([PWSH, "-NoProfile", "-File", RUNNER, "-Bridge", os.path.abspath(BRIDGE_PS1), "-Scenario", path],
                              capture_output=True, text=True, timeout=180, env=env)
        self.assertEqual(0, proc.returncode, proc.stdout + proc.stderr)
        lines = [l for l in proc.stdout.splitlines() if l.startswith("RESULT:")]
        self.assertEqual(1, len(lines), proc.stdout + proc.stderr)
        return json.loads(lines[0][len("RESULT:"):])

    def python_status(self, roots=None, phone=None, events=None):
        b = self.bridge
        b.events.clear()
        b.events.extend(events or [])
        b.phone_seen.update(time=float((phone or {}).get("time", 0)), ip=(phone or {}).get("ip", ""))
        b._write_test_cache.update(time=0.0, root="", ok=True)
        return json.loads(json.dumps(b.build_status(now=NOW, roots=self.roots_arg(roots))))

    def roots_arg(self, roots):
        return [self.root] if roots is None else roots

    def assert_same(self, roots=None, phone=None, events=None, lock_held=False):
        scenario = {"phone": phone or {"time": 0, "ip": ""}, "events": events or [], "lockHeld": lock_held}
        if roots is not None:
            scenario["roots"] = roots
        ps = self.run_ps(**scenario)
        self.bridge.script_lock_held = lambda path: lock_held
        py = self.python_status(roots, phone, events)
        for key in ("app", "now", "script", "outbox", "events", "phone", "checks", "summary"):
            self.assertEqual(py[key], ps[key], key)
        for key in ("version", "protocol", "minScript", "name", "port", "address"):
            self.assertEqual(py["bridge"][key], ps["bridge"][key], "bridge." + key)
        self.assertEqual("powershell", ps["bridge"]["kind"])
        return ps

    # ----- the same scenarios as the Python tests -----------------------------------------------------------------
    def test_no_export_folder(self):
        ps = self.assert_same(roots=[])
        self.assertEqual("fail", ps["summary"])

    def test_no_heartbeat(self):
        ps = self.assert_same()
        self.assertFalse(ps["script"]["found"])

    def test_fresh_heartbeat(self):
        self.heartbeat(age=3)
        ps = self.assert_same()
        self.assertTrue(ps["script"]["alive"])
        self.assertTrue(ps["script"]["versionOk"])

    def test_stale_heartbeat(self):
        self.heartbeat(age=95)
        ps = self.assert_same()
        self.assertFalse(ps["script"]["alive"])

    def test_quiet_script_that_keeps_its_file_open(self):
        self.heartbeat(age=600)
        ps = self.assert_same(lock_held=True)
        self.assertTrue(ps["script"]["alive"])
        self.assertTrue(ps["script"]["quiet"])

    def test_old_script_and_missing_exporter(self):
        self.heartbeat(version="2.7.0", exporter=False, undelivered=2)
        ps = self.assert_same()
        self.assertFalse(ps["script"]["versionOk"])

    def test_current_script_without_exporter_and_with_a_resend_answer(self):
        self.heartbeat(exporter=False, undelivered=3,
                       resend={"id": "r5", "time": NOW - 1, "result": "sent", "detail": "Pumas 2-1 Arsenal"})
        ps = self.assert_same()
        self.assertEqual("r5", ps["script"]["resend"]["id"])

    def test_outbox_with_a_stuck_file_and_a_bad_file(self):
        self.heartbeat()
        self.outbox_file("1_1.json", 120)
        self.outbox_file("1_2.json.bad", 5)
        ps = self.assert_same()
        self.assertEqual(1, ps["outbox"]["pending"])
        self.assertEqual(1, ps["outbox"]["bad"])

    def test_events_and_phone(self):
        self.heartbeat()
        events = [
            {"event": "message", "id": "a", "time": NOW - 500, "title": "t", "message": json.dumps({"dataType": "match"})},
            {"event": "message", "id": "b", "time": NOW - 200, "title": "t", "message": json.dumps({"dataType": "transfer"})},
            {"event": "message", "id": "c", "time": NOW - 100, "title": "t", "message": json.dumps({"dataType": "squad"})},
            {"event": "message", "id": "d", "time": NOW - 50, "title": "t", "message": "plain text"},
        ]
        for phone in ({"time": 0, "ip": ""}, {"time": NOW - 4, "ip": "192.168.2.40"}, {"time": NOW - 300, "ip": "192.168.2.40"}):
            ps = self.assert_same(phone=phone, events=events)
        self.assertEqual(NOW - 500, ps["events"]["lastMatch"])

    def test_the_freshest_of_two_folders_wins(self):
        other = os.path.join(self.tmp.name, "OneDrive", "FC26 Export")
        os.makedirs(other)
        with open(os.path.join(other, "script_status.json"), "w") as f:
            json.dump({"version": "2.5.0", "time": NOW - 300}, f)
        self.heartbeat(age=1)
        ps = self.assert_same(roots=[other, self.root])
        self.assertEqual("2.8.0", ps["script"]["version"])

    def test_damaged_heartbeat_is_ignored(self):
        with open(os.path.join(self.root, "script_status.json"), "w") as f:
            f.write('{"version": "2.8.0", "tim')
        ps = self.assert_same()
        self.assertFalse(ps["script"]["found"])

    def test_heartbeat_with_a_byte_order_mark(self):
        with open(os.path.join(self.root, "script_status.json"), "w", encoding="utf-8-sig") as f:
            json.dump({"version": "2.8.0", "time": NOW - 2, "club": "Atlético Pumas"}, f, ensure_ascii=False)
        ps = self.assert_same()
        self.assertEqual("Atlético Pumas", ps["script"]["club"])

    # ----- re-send ------------------------------------------------------------------------------------------------
    def test_resend_refusals_and_request_file(self):
        # no script
        res = self.run_ps(action="resend", nows=[NOW])
        self.assertEqual([(409, "script_not_running")], [(r["code"], r["reply"]["code"]) for r in res])
        # too old
        self.heartbeat(version="2.6.1")
        res = self.run_ps(action="resend", nows=[NOW])
        self.assertEqual([(409, "script_too_old")], [(r["code"], r["reply"]["code"]) for r in res])
        self.assertFalse(os.path.exists(os.path.join(self.root, "resend_request.json")))
        # accepted, then rate limited, then accepted again
        self.heartbeat()
        res = self.run_ps(action="resend", nows=[NOW, NOW + 3, NOW + 11])
        self.assertEqual([200, 429, 200], [r["code"] for r in res])
        self.assertEqual("requested", res[0]["reply"]["status"])
        self.assertNotEqual(res[0]["reply"]["id"], res[2]["reply"]["id"])
        with open(os.path.join(self.root, "resend_request.json"), "rb") as f:
            raw = f.read()
        self.assertFalse(raw.startswith(b"\xef\xbb\xbf"), "the Lua script reads this file: no byte order mark")
        written = json.loads(raw)
        self.assertEqual(res[2]["reply"]["id"], written["id"])
        self.assertEqual([], [n for n in os.listdir(self.root) if n.endswith(".tmp")])


if __name__ == "__main__":
    unittest.main()
