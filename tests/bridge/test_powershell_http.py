"""Starts the real PowerShell bridge (pc-bridge/bridge.ps1) on a free port and talks to it over HTTP: status page and
self check, the phone's re-send request, the outbox folder watcher and the rules about who may write.
Skipped when PowerShell (pwsh) is not installed. PowerShell 7 on Linux has the same HttpListener, so the bridge's own
request loop runs here exactly as it does on Windows (the network-profile and adapter lookups just report nothing).

Run:  python3 tests/bridge/test_powershell_http.py
"""
import http.client
import json
import os
import shutil
import socket
import subprocess
import tempfile
import time
import unittest

HERE = os.path.dirname(os.path.abspath(__file__))
BRIDGE_PS1 = os.path.abspath(os.path.join(HERE, "..", "..", "pc-bridge", "bridge.ps1"))
PWSH = shutil.which("pwsh") or os.environ.get("PWSH")


def free_port():
    s = socket.socket()
    s.bind(("127.0.0.1", 0))
    port = s.getsockname()[1]
    s.close()
    return port


@unittest.skipUnless(PWSH, "PowerShell (pwsh) is not installed")
class PowerShellHttpTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.tmp = tempfile.TemporaryDirectory()
        cls.home = os.path.join(cls.tmp.name, "home")
        cls.root = os.path.join(cls.home, "Documents", "FC26 Export")
        cls.outbox = os.path.join(cls.root, "outbox")
        os.makedirs(cls.outbox)
        bridge_dir = os.path.join(cls.tmp.name, "bridge")          # its own copy: the bridge writes its event file next to itself
        os.makedirs(bridge_dir)
        shutil.copy(BRIDGE_PS1, bridge_dir)
        cls.port = free_port()
        env = dict(os.environ, USERPROFILE=cls.home, HOME=cls.home, DOTNET_SYSTEM_GLOBALIZATION_INVARIANT="1")
        cls.log = open(os.path.join(cls.tmp.name, "bridge.log"), "w")
        cls.proc = subprocess.Popen([PWSH, "-NoProfile", "-File", os.path.join(bridge_dir, "bridge.ps1"), "-Port", str(cls.port)],
                                    stdout=cls.log, stderr=subprocess.STDOUT, env=env)
        deadline = time.time() + 90
        while time.time() < deadline:
            try:
                socket.create_connection(("127.0.0.1", cls.port), timeout=1).close()
                break
            except OSError:
                if cls.proc.poll() is not None:
                    break
                time.sleep(0.5)
        else:
            cls.tearDownClass()
            raise RuntimeError("the PowerShell bridge did not start")

    @classmethod
    def tearDownClass(cls):
        cls.proc.kill()
        cls.proc.wait(timeout=20)
        cls.log.close()
        cls.tmp.cleanup()

    def setUp(self):
        for name in ("script_status.json", "resend_request.json"):
            try:
                os.remove(os.path.join(self.root, name))
            except OSError:
                pass
        self.request("POST", "/clear")

    def request(self, method, path, body=None, headers=None):
        conn = http.client.HTTPConnection("127.0.0.1", self.port, timeout=20)
        conn.request(method, path, body=body, headers=headers or {})
        resp = conn.getresponse()
        data = resp.read()
        conn.close()
        return resp.status, data

    def heartbeat(self, **over):
        data = {"app": "fc26-capture", "version": "2.8.0", "protocol": 2, "time": int(time.time()), "club": "Pumas",
                "exporter": True}
        data.update(over)
        with open(os.path.join(self.root, "script_status.json"), "w", encoding="utf-8") as f:
            json.dump(data, f)

    def status(self):
        code, body = self.request("GET", "/status")
        self.assertEqual(200, code)
        return json.loads(body)

    # ----- status and dashboard -----------------------------------------------------------------------------------
    def test_status_describes_the_bridge_and_the_script(self):
        st = self.status()
        self.assertEqual("fc26-bridge", st["app"])
        self.assertEqual("powershell", st["bridge"]["kind"])
        self.assertEqual(2, st["bridge"]["protocol"])
        self.assertFalse(st["script"]["found"])
        self.heartbeat()
        st = self.status()
        self.assertTrue(st["script"]["alive"])
        self.assertEqual("ok", [c for c in st["checks"] if c["id"] == "script"][0]["state"])
        code, _ = self.request("GET", "/api/status")
        self.assertEqual(200, code)

    def test_dashboard_has_the_self_check_and_the_resend_button(self):
        code, body = self.request("GET", "/")
        text = body.decode("utf-8")
        self.assertEqual(200, code)
        for needle in ("Self check", "Re-send last match", "refreshStatus", "v2.0.0"):
            self.assertIn(needle, text)
        self.assertNotIn("$BridgeVersion", text)

    # ----- re-send --------------------------------------------------------------------------------------------------
    def test_resend_is_refused_without_a_running_script_and_works_with_one(self):
        code, body = self.request("POST", "/resend")
        self.assertEqual(409, code)
        self.assertEqual("script_not_running", json.loads(body)["code"])
        self.heartbeat()
        time.sleep(0.2)
        code, body = self.request("POST", "/resend")
        self.assertEqual(200, code, body)
        reply = json.loads(body)
        self.assertEqual("requested", reply["status"])
        with open(os.path.join(self.root, "resend_request.json"), encoding="utf-8") as f:
            self.assertEqual(reply["id"], json.load(f)["id"])
        code, body = self.request("POST", "/resend")
        self.assertEqual(429, code)

    def test_resend_refuses_other_web_sites_and_odd_hosts(self):
        self.heartbeat()
        code, _ = self.request("POST", "/resend", headers={"Origin": "http://evil.example"})
        self.assertEqual(403, code)
        code, _ = self.request("POST", "/resend", headers={"Host": "evil.example"})
        self.assertEqual(403, code)
        self.assertFalse(os.path.exists(os.path.join(self.root, "resend_request.json")))

    # ----- payloads ----------------------------------------------------------------------------------------------
    def test_squad_payload_is_stored_and_shows_in_the_status(self):
        payload = {"dataType": "squad", "title": "Squad contracts", "message": "Contracts and loans of 1 players",
                   "careerDate": "2026-10-16", "players": [{"name": "Jemide", "pid": 7, "contractEnd": "2029-06-30"}]}
        code, body = self.request("POST", "/", body=json.dumps(payload), headers={"Content-Type": "application/json"})
        self.assertEqual(200, code, body)
        code, body = self.request("GET", "/events")
        events = json.loads(body)
        self.assertEqual(1, len(events))
        self.assertEqual("squad", json.loads(events[0]["message"])["dataType"])
        self.assertGreater(self.status()["events"]["lastSquad"], 0)

    def test_outbox_file_is_picked_up_by_the_watcher(self):
        path = os.path.join(self.outbox, "1_1.json")
        with open(path, "w", encoding="utf-8") as f:
            json.dump({"dataType": "transfer", "title": "Transfer Finalized", "message": "x signed", "playerName": "x",
                       "type": "Bought", "feeM": 1.0, "fromToClub": "y", "date": "2026-10-16"}, f)
        old = time.time() - 5
        os.utime(path, (old, old))
        deadline = time.time() + 15
        while time.time() < deadline and os.path.exists(path):
            time.sleep(0.5)
        self.assertFalse(os.path.exists(path))
        events = json.loads(self.request("GET", "/events")[1])
        self.assertEqual(["Transfer Finalized"], [e["title"] for e in events])
        self.assertTrue(os.path.exists(os.path.join(self.root, "bridge_watching.txt")))

    def test_get_does_not_trigger_the_test_buttons(self):
        for path in ("/clear", "/test-match"):
            self.assertEqual(302, self.request("GET", path)[0], path)
        self.assertEqual([], json.loads(self.request("GET", "/events")[1]))

    def test_this_pcs_own_browser_does_not_count_as_the_phone(self):
        self.request("GET", "/events")
        self.assertEqual(0, self.status()["phone"]["lastSeen"])


if __name__ == "__main__":
    unittest.main()
