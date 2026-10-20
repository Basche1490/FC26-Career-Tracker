"""The pairing code and signed requests of the PowerShell bridge (pc-bridge/bridge.ps1), checked two ways:
  1. its signing functions give the same numbers as the shared test vectors (and so as the Python bridge and the app);
  2. the real bridge, started on a free port, refuses everything a stranger on the network may try, answers the phone's
     signed requests with signed answers, and cuts off a wrong code. A request from another machine is imitated by
     addressing the loopback connection to a LAN address (Host header), exactly as in test_bridge_auth.py.
Skipped when PowerShell (pwsh) is not installed.

Run:  python3 tests/bridge/test_powershell_auth.py
"""
import http.client
import importlib.util
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
BRIDGE_PY = os.path.abspath(os.path.join(HERE, "..", "..", "pc-bridge", "telemetry_bridge.py"))
RUNNER = os.path.join(HERE, "run_ps_auth.ps1")
VECTORS = os.path.abspath(os.path.join(HERE, "..", "vectors", "auth_vectors.json"))
PWSH = shutil.which("pwsh") or os.environ.get("PWSH")


def load_python_bridge():
    """The Python bridge is used here only as the independent implementation that signs and checks."""
    spec = importlib.util.spec_from_file_location("telemetry_bridge_for_ps_auth_test", BRIDGE_PY)
    mod = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(mod)
    return mod


def free_port():
    s = socket.socket()
    s.bind(("127.0.0.1", 0))
    port = s.getsockname()[1]
    s.close()
    return port


@unittest.skipUnless(PWSH, "PowerShell (pwsh) is not installed")
class PowerShellVectorTest(unittest.TestCase):
    def test_the_powershell_functions_match_the_shared_vectors(self):
        py = load_python_bridge()
        with open(VECTORS, encoding="utf-8") as f:
            v = json.load(f)
        out = subprocess.run([PWSH, "-NoProfile", "-File", RUNNER, "-Bridge", BRIDGE_PS1, "-Vectors", VECTORS],
                             capture_output=True, text=True, timeout=120,
                             env=dict(os.environ, DOTNET_SYSTEM_GLOBALIZATION_INVARIANT="1"))
        line = [l for l in out.stdout.splitlines() if l.startswith("RESULT:")]
        self.assertTrue(line, out.stdout + out.stderr)
        r = json.loads(line[0][7:])
        self.assertEqual(v["normalizedCode"], r["normalized"])
        self.assertEqual(v["normalizedCode"], r["normalizedLoose"])
        self.assertEqual(v["keyHex"], r["keyHex"])
        self.assertEqual([x["sig"] for x in v["requests"]], r["requests"])
        self.assertEqual([x["sig"] for x in v["responses"]], r["responses"])
        self.assertEqual("ABCD-EFGH-JKLM-NPQR", r["formatted"])
        self.assertEqual([True, True], r["valid"])
        self.assertEqual([False] * 5, r["invalid"])
        self.assertEqual([True, False, False], r["sameText"])
        self.assertEqual(60, len(set(r["generated"])))
        for code in r["generated"]:
            self.assertTrue(py.valid_code(code), code)


@unittest.skipUnless(PWSH, "PowerShell (pwsh) is not installed")
class PowerShellAuthHttpTest(unittest.TestCase):
    def setUp(self):
        # A bridge of its own for every test: wrong guesses and the lockout are remembered by the bridge.
        self.py = load_python_bridge()
        self.tmp = tempfile.TemporaryDirectory()
        self.home = os.path.join(self.tmp.name, "home")
        self.state = os.path.join(self.tmp.name, "state")
        self.root = os.path.join(self.home, "Documents", "FC26 Export")
        os.makedirs(os.path.join(self.root, "outbox"))
        bridge_dir = os.path.join(self.tmp.name, "bridge")
        os.makedirs(bridge_dir)
        shutil.copy(BRIDGE_PS1, bridge_dir)
        self.port = free_port()
        env = dict(os.environ, USERPROFILE=self.home, HOME=self.home, FC26_STATE_DIR=self.state,
                   DOTNET_SYSTEM_GLOBALIZATION_INVARIANT="1")
        self.log = open(os.path.join(self.tmp.name, "bridge.log"), "w")
        self.proc = subprocess.Popen([PWSH, "-NoProfile", "-File", os.path.join(bridge_dir, "bridge.ps1"), "-Port", str(self.port)],
                                     stdout=self.log, stderr=subprocess.STDOUT, env=env)
        deadline = time.time() + 90
        while time.time() < deadline:
            try:
                socket.create_connection(("127.0.0.1", self.port), timeout=1).close()
                break
            except OSError:
                if self.proc.poll() is not None:
                    break
                time.sleep(0.3)
        else:
            self.tearDown()
            raise RuntimeError("the PowerShell bridge did not start")
        self.lan = {"Host": "192.168.2.24:%d" % self.port}

    def tearDown(self):
        self.proc.kill()
        self.proc.wait(timeout=20)
        self.log.close()
        self.tmp.cleanup()

    # ---- helpers ------------------------------------------------------------------------------------------------
    def code_text(self):
        with open(os.path.join(self.state, "bridge.key"), encoding="ascii") as f:
            return f.read().strip()

    def write_code(self, text):
        with open(os.path.join(self.state, "bridge.key"), "w", encoding="ascii") as f:
            f.write(text + "\n")

    def key(self):
        return self.py.derive_key(self.code_text())

    def request(self, method, path, headers=None, body=None):
        conn = http.client.HTTPConnection("127.0.0.1", self.port, timeout=20)
        conn.request(method, path, body=body, headers=headers or {})
        resp = conn.getresponse()
        data = resp.read()
        out = (resp.status, data, {k.lower(): v for k, v in resp.getheaders()})
        conn.close()
        return out

    def signed(self, method, path, body=b"", key=None, when=None, nonce=None, host=None):
        when = int(time.time()) if when is None else when
        nonce = nonce or os.urandom(16).hex()
        h = dict(host or self.lan)
        h.update({"X-FC-Time": str(when), "X-FC-Nonce": nonce,
                  "X-FC-Sig": self.py.sign_request(key or self.key(), method, path, when, nonce, body)})
        return h

    def heartbeat(self):
        with open(os.path.join(self.root, "script_status.json"), "w", encoding="utf-8") as f:
            json.dump({"app": "fc26-capture", "version": "2.8.0", "protocol": 2, "time": int(time.time()), "club": "Pumas"}, f)

    # ---- tests --------------------------------------------------------------------------------------------------
    def test_the_bridge_made_a_valid_pairing_code_on_its_first_start(self):
        self.assertTrue(self.py.valid_code(self.code_text()))
        self.assertEqual(self.py.format_code(self.code_text()), self.code_text())

    def test_a_stranger_gets_the_ping_and_nothing_else(self):
        code, body, _ = self.request("GET", "/ping", self.lan)
        self.assertEqual(200, code)
        info = json.loads(body)
        self.assertEqual(("fc26-bridge", 3), (info["app"], info["protocol"]))
        for path in ("/events", "/json", "/api/telemetry", "/status", "/api/status"):
            code, body, _ = self.request("GET", path, self.lan)
            self.assertEqual(401, code, path)
            self.assertEqual("unsigned", json.loads(body)["code"])
        for path in ("/", "/anything", "/test-match", "/clear"):
            self.assertEqual(403, self.request("GET", path, self.lan)[0], path)

    def test_a_stranger_cannot_change_anything_even_with_the_code(self):
        self.request("POST", "/test-match")
        before = len(json.loads(self.request("GET", "/events")[1]))
        for path in ("/clear", "/test-match", "/test-transfer", "/pairing/new", "/", "/events"):
            self.assertEqual(403, self.request("POST", path, self.signed("POST", path), b"")[0], path)
        self.assertEqual(403, self.request("DELETE", "/events", self.signed("DELETE", "/events"))[0])
        code, _, _ = self.request("POST", "/", self.lan, json.dumps({"dataType": "match"}))
        self.assertEqual(403, code)
        self.assertEqual(before, len(json.loads(self.request("GET", "/events")[1])))

    def test_the_dashboard_with_the_pairing_code_is_for_this_pc_only(self):
        code, body, _ = self.request("GET", "/")
        self.assertEqual(200, code)
        self.assertIn(self.code_text(), body.decode("utf-8"))
        for host in ("localhost:%d" % self.port, "127.0.0.1:%d" % self.port):
            self.assertEqual(200, self.request("GET", "/", {"Host": host})[0], host)
        code, body, _ = self.request("GET", "/", self.lan)
        self.assertEqual(403, code)
        self.assertNotIn(self.code_text(), body.decode("utf-8"))

    def test_a_request_that_came_through_a_tunnel_is_not_this_pc(self):
        for header in ("X-Forwarded-For", "X-Real-IP", "Forwarded", "Via", "CF-Connecting-IP"):
            h = {header: "203.0.113.9"}
            self.assertEqual(403, self.request("GET", "/", h)[0], header)
            self.assertEqual(403, self.request("POST", "/clear", h)[0], header)
            self.assertEqual(401, self.request("GET", "/events", h)[0], header)

    def test_other_web_sites_cannot_use_the_dashboard_buttons(self):
        old = self.code_text()
        for path in ("/pairing/new", "/clear", "/test-match"):
            self.assertEqual(403, self.request("POST", path, {"Origin": "http://evil.example"})[0], path)
        self.assertEqual(403, self.request("POST", "/pairing/new", {"Host": "evil.example"})[0])
        self.assertEqual(old, self.code_text())

    def test_a_signed_request_is_answered_and_the_answer_is_signed(self):
        self.request("POST", "/test-match")
        nonce = os.urandom(16).hex()
        code, body, headers = self.request("GET", "/events", self.signed("GET", "/events", nonce=nonce))
        self.assertEqual(200, code)
        self.assertEqual(1, len(json.loads(body)))
        self.assertEqual(self.py.sign_response(self.key(), nonce, 200, body), headers["x-fc-sig"])
        nonce = os.urandom(16).hex()
        code, body, headers = self.request("GET", "/status", self.signed("GET", "/status", nonce=nonce))
        self.assertEqual(200, code)
        self.assertEqual(self.py.sign_response(self.key(), nonce, 200, body), headers["x-fc-sig"])
        status = json.loads(body)
        self.assertEqual("fc26-bridge", status["app"])
        self.assertEqual(3, status["bridge"]["protocol"])
        self.assertEqual("ok", [c for c in status["checks"] if c["id"] == "security"][0]["state"])
        self.assertGreater(status["phone"]["lastSeen"], 0)

    def test_the_wrong_code_a_replay_and_a_wrong_clock_are_refused_with_a_reason(self):
        wrong = self.py.derive_key("ZZZZ-ZZZZ-ZZZZ-ZZZZ")
        code, body, headers = self.request("GET", "/events", self.signed("GET", "/events", key=wrong))
        self.assertEqual((401, "bad_signature"), (code, json.loads(body)["code"]))
        self.assertNotIn("x-fc-sig", headers)
        h = self.signed("GET", "/events")
        self.assertEqual(200, self.request("GET", "/events", h)[0])
        code, body, _ = self.request("GET", "/events", h)
        self.assertEqual((401, "replay"), (code, json.loads(body)["code"]))
        code, body, _ = self.request("GET", "/events", self.signed("GET", "/events", when=int(time.time()) - 3600))
        reply = json.loads(body)
        self.assertEqual((401, "clock_skew"), (code, reply["code"]))
        self.assertAlmostEqual(time.time(), reply["bridgeTime"], delta=10)

    def test_a_signature_for_one_path_does_not_work_for_another(self):
        self.assertEqual(401, self.request("GET", "/status", self.signed("GET", "/events"))[0])

    def test_resend_needs_the_signature_and_the_body_must_match(self):
        self.heartbeat()
        time.sleep(0.2)
        self.assertEqual(401, self.request("POST", "/resend", self.lan, b"")[0])
        self.assertFalse(os.path.exists(os.path.join(self.root, "resend_request.json")))
        self.assertEqual(401, self.request("POST", "/resend", self.signed("POST", "/resend", b""), b"tampered")[0])
        nonce = os.urandom(16).hex()
        code, body, headers = self.request("POST", "/resend", self.signed("POST", "/resend", b"", nonce=nonce), b"")
        self.assertEqual(200, code, body)
        self.assertEqual("requested", json.loads(body)["status"])
        self.assertEqual(self.py.sign_response(self.key(), nonce, 200, body), headers["x-fc-sig"])
        self.assertTrue(os.path.exists(os.path.join(self.root, "resend_request.json")))

    def test_a_new_code_written_to_the_file_is_picked_up_and_cuts_off_the_old_one(self):
        old_key = self.key()
        self.write_code("ZZZZ-YYYY-XXXX-WWWW")
        self.assertEqual(401, self.request("GET", "/events", self.signed("GET", "/events", key=old_key))[0])
        new_key = self.py.derive_key("ZZZZ-YYYY-XXXX-WWWW")
        self.assertEqual(200, self.request("GET", "/events", self.signed("GET", "/events", key=new_key))[0])

    def test_the_dashboards_new_code_button_makes_a_valid_different_code(self):
        old = self.code_text()
        code, body, _ = self.request("POST", "/pairing/new", {})
        self.assertEqual(200, code, body)
        new = json.loads(body)["code"]
        self.assertTrue(self.py.valid_code(new))
        self.assertNotEqual(old, new)
        self.assertEqual(new, self.code_text())
        self.assertEqual(200, self.request("GET", "/events", self.signed("GET", "/events"))[0])

    def test_repeated_wrong_guesses_lock_that_address_out_and_are_shown_in_the_check(self):
        wrong = self.py.derive_key("ZZZZ-ZZZZ-ZZZZ-ZZZZ")
        for _ in range(10):
            self.assertEqual(401, self.request("GET", "/events", self.signed("GET", "/events", key=wrong))[0])
        code, body, headers = self.request("GET", "/events", self.signed("GET", "/events"))
        self.assertEqual(429, code)
        self.assertEqual("locked", json.loads(body)["code"])
        self.assertIn("retry-after", headers)
        self.assertEqual(200, self.request("GET", "/ping", self.lan)[0])                  # the ping stays open
        status = json.loads(self.request("GET", "/status")[1])                            # this PC's own dashboard
        security = [c for c in status["checks"] if c["id"] == "security"][0]
        self.assertEqual("warn", security["state"])


if __name__ == "__main__":
    unittest.main()
