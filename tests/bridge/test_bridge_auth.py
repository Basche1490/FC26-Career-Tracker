"""Checks for the pairing code and the signed requests of the Python bridge (pc-bridge/telemetry_bridge.py).
The rules are written up in docs/SECURITY.md. A request from another machine is imitated by addressing the loopback
connection to a LAN address (Host header): the bridge decides "is this the PC itself?" from the connection and the
Host header together, so such a request is treated like one from the phone.

Run:  python3 tests/bridge/test_bridge_auth.py
"""
import http.client
import importlib.util
import json
import os
import socketserver
import stat
import sys
import tempfile
import threading
import time
import unittest

HERE = os.path.dirname(os.path.abspath(__file__))
BRIDGE = os.path.join(HERE, "..", "..", "pc-bridge", "telemetry_bridge.py")
VECTORS = os.path.join(HERE, "..", "vectors", "auth_vectors.json")
KOTLIN_TEST = os.path.join(HERE, "..", "..", "app", "src", "test", "java", "com", "fctracker", "career", "telemetry", "BridgeAuthTest.kt")


def load_bridge(tmp):
    spec = importlib.util.spec_from_file_location("telemetry_bridge_auth_under_test", BRIDGE)
    mod = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(mod)
    mod.EVENTS_FILE = os.path.join(tmp, "telemetry_events.json")
    os.environ["FC26_STATE_DIR"] = os.path.join(tmp, "state")
    mod.events.clear()
    return mod


class VectorTest(unittest.TestCase):
    """The same numbers are checked by the PowerShell bridge (through HTTP) and by the app's Kotlin test."""

    @classmethod
    def setUpClass(cls):
        cls.tmp = tempfile.TemporaryDirectory()
        cls.bridge = load_bridge(cls.tmp.name)
        with open(VECTORS, encoding="utf-8") as f:
            cls.v = json.load(f)

    @classmethod
    def tearDownClass(cls):
        cls.tmp.cleanup()

    def test_key_and_signatures_match_the_shared_vectors(self):
        b, v = self.bridge, self.v
        self.assertEqual(v["normalizedCode"], b.normalize_code(v["code"]))
        self.assertEqual(v["normalizedCode"], b.normalize_code(v["code"].lower().replace("-", " ")))
        key = b.derive_key(v["code"])
        self.assertEqual(v["keyHex"], key.hex())
        for r in v["requests"]:
            self.assertEqual(r["sig"], b.sign_request(key, r["method"], r["target"], r["time"], r["nonce"], r["body"].encode()))
        for r in v["responses"]:
            self.assertEqual(r["sig"], b.sign_response(key, r["nonce"], r["status"], r["body"].encode()))

    def test_every_vector_is_also_checked_by_the_kotlin_test(self):
        if not os.path.exists(KOTLIN_TEST):
            self.skipTest("the Kotlin test is not there yet")
        text = open(KOTLIN_TEST, encoding="utf-8").read()
        for needle in [self.v["keyHex"]] + [r["sig"] for r in self.v["requests"]] + [r["sig"] for r in self.v["responses"]]:
            self.assertIn(needle, text)

    def test_signature_depends_on_every_part(self):
        b = self.bridge
        key = b.derive_key(self.v["code"])
        base = b.sign_request(key, "GET", "/events", 1, "a" * 32, b"")
        self.assertNotEqual(base, b.sign_request(key, "POST", "/events", 1, "a" * 32, b""))
        self.assertNotEqual(base, b.sign_request(key, "GET", "/status", 1, "a" * 32, b""))
        self.assertNotEqual(base, b.sign_request(key, "GET", "/events", 2, "a" * 32, b""))
        self.assertNotEqual(base, b.sign_request(key, "GET", "/events", 1, "b" * 32, b""))
        self.assertNotEqual(base, b.sign_request(key, "GET", "/events", 1, "a" * 32, b"x"))
        self.assertNotEqual(base, b.sign_request(b.derive_key("ZZZZ-ZZZZ-ZZZZ-ZZZZ"), "GET", "/events", 1, "a" * 32, b""))


class CodeTest(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.bridge = load_bridge(self.tmp.name)
        self._stdout = sys.stdout
        sys.stdout = open(os.devnull, "w")

    def tearDown(self):
        sys.stdout.close()
        sys.stdout = self._stdout
        self.tmp.cleanup()

    def test_generated_codes_are_long_random_and_easy_to_type(self):
        b = self.bridge
        codes = {b.generate_code() for _ in range(200)}
        self.assertEqual(200, len(codes))
        for c in codes:
            self.assertEqual(16, len(c))
            self.assertTrue(all(ch in b.PAIR_ALPHABET for ch in c))
            self.assertTrue(b.valid_code(c))
        self.assertTrue(set("IO01").isdisjoint(b.PAIR_ALPHABET))

    def test_what_counts_as_a_valid_code(self):
        b = self.bridge
        self.assertTrue(b.valid_code("abcd-efgh-jklm-npqr"))
        self.assertTrue(b.valid_code(" ABCD EFGH JKLM NPQR "))
        self.assertEqual("ABCD-EFGH-JKLM-NPQR", b.format_code("abcdefghjklmnpqr"))
        for bad in ("", "ABCD", "ABCD-EFGH-JKLM-NPQ", "ABCD-EFGH-JKLM-NPQRS", "ABCD-EFGH-JKLM-NPQ0", "ABCD-EFGH-JKLM-NPQI"):
            self.assertFalse(b.valid_code(bad), bad)

    def test_the_code_is_made_once_kept_and_stored_privately(self):
        b = self.bridge
        first = b.pairing_code()
        self.assertTrue(b.valid_code(first))
        self.assertEqual(first, b.pairing_code())
        self.assertEqual(b.derive_key(first), b.pairing_key())
        path = b.pairing_file()
        self.assertTrue(os.path.exists(path))
        if os.name != "nt":
            self.assertEqual(0o600, stat.S_IMODE(os.stat(path).st_mode))
        with open(path) as f:
            self.assertEqual(b.format_code(first), f.read().strip())

    def test_a_new_code_written_by_the_launcher_is_picked_up_without_a_restart(self):
        b = self.bridge
        first = b.pairing_code()
        time.sleep(0.01)
        with open(b.pairing_file(), "w") as f:
            f.write("ZZZZ-YYYY-XXXX-WWWW\n")
        self.assertEqual("ZZZZYYYYXXXXWWWW", b.pairing_code())
        self.assertNotEqual(first, b.pairing_code())

    def test_a_damaged_code_file_is_replaced_not_trusted(self):
        b = self.bridge
        os.makedirs(os.path.dirname(b.pairing_file()), exist_ok=True)
        with open(b.pairing_file(), "w") as f:
            f.write("1234\n")
        self.assertTrue(b.valid_code(b.pairing_code()))

    def test_rotating_makes_a_different_code_and_old_signatures_stop_working(self):
        b = self.bridge
        old_key = b.pairing_key()
        new = b.rotate_pairing_code()
        self.assertTrue(b.valid_code(new))
        self.assertNotEqual(old_key, b.pairing_key())
        self.assertEqual(new, b.pairing_code())


class GuardTest(unittest.TestCase):
    """The checks on their own, with the clock under the test's control."""

    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.bridge = load_bridge(self.tmp.name)
        self.key = self.bridge.derive_key("ABCD-EFGH-JKLM-NPQR")
        self.guard = self.bridge.AuthGuard()
        self.now = 1_800_000_000

    def tearDown(self):
        self.tmp.cleanup()

    def headers(self, method="GET", target="/events", body=b"", when=None, nonce=None, key=None):
        when = self.now if when is None else when
        nonce = nonce or os.urandom(16).hex()
        sig = self.bridge.sign_request(key or self.key, method, target, when, nonce, body)
        return {"X-FC-Time": str(when), "X-FC-Nonce": nonce, "X-FC-Sig": sig}

    def check(self, headers, ip="192.168.2.40", method="GET", target="/events", body=b"", now=None):
        return self.guard.check(ip, method, target, body, headers.get, self.key, self.now if now is None else now)

    def test_a_good_request_passes_once_only(self):
        h = self.headers()
        self.assertTrue(self.check(h).ok)
        again = self.check(h)
        self.assertFalse(again.ok)
        self.assertEqual("replay", again.code)

    def test_missing_or_garbled_headers_are_refused(self):
        self.assertEqual("unsigned", self.check({}).code)
        h = self.headers()
        h["X-FC-Sig"] = "nothex"
        self.assertEqual("unsigned", self.check(h).code)
        h = self.headers()
        h["X-FC-Nonce"] = "short"
        self.assertEqual("unsigned", self.check(h).code)
        h = self.headers()
        h["X-FC-Time"] = "-5"
        self.assertEqual("unsigned", self.check(h).code)

    def test_the_wrong_code_is_refused(self):
        h = self.headers(key=self.bridge.derive_key("ZZZZ-ZZZZ-ZZZZ-ZZZZ"))
        r = self.check(h)
        self.assertFalse(r.ok)
        self.assertEqual((401, "bad_signature"), (r.status, r.code))

    def test_a_changed_request_is_refused(self):
        h = self.headers(target="/events")
        self.assertEqual("bad_signature", self.check(h, target="/status").code)
        h = self.headers(method="POST", target="/resend", body=b"")
        self.assertEqual("bad_signature", self.check(h, method="POST", target="/resend", body=b"x").code)

    def test_the_clock_window(self):
        r = self.check(self.headers(when=self.now - 299))
        self.assertTrue(r.ok)
        r = self.check(self.headers(when=self.now - 301))
        self.assertEqual((401, "clock_skew"), (r.status, r.code))
        self.assertEqual(self.now, r.extra["bridgeTime"])
        self.assertEqual("clock_skew", self.check(self.headers(when=self.now + 301)).code)
        self.assertTrue(self.check(self.headers(when=self.now + 299)).ok)

    def test_a_request_replayed_after_the_window_is_refused_by_its_age_and_the_cache_forgets_it(self):
        h = self.headers()
        self.assertTrue(self.check(h).ok)
        self.assertEqual("clock_skew", self.check(h, now=self.now + 400).code)
        self.assertTrue(self.check(self.headers(when=self.now + 700), now=self.now + 700).ok)
        self.assertLess(len(self.guard.nonces), 3)

    def test_ten_wrong_guesses_lock_the_address_out_even_for_a_right_one(self):
        bad = self.bridge.derive_key("ZZZZ-ZZZZ-ZZZZ-ZZZZ")
        for i in range(self.bridge.AUTH_MAX_FAILS):
            self.assertEqual("bad_signature", self.check(self.headers(key=bad), now=self.now + i).code)
        locked = self.check(self.headers(), now=self.now + 11)
        self.assertEqual((429, "locked"), (locked.status, locked.code))
        self.assertGreater(locked.extra["retryAfter"], 0)
        other = self.check(self.headers(), ip="192.168.2.77", now=self.now + 11)
        self.assertTrue(other.ok)                                             # another device is not punished
        later = self.check(self.headers(when=self.now + 120), now=self.now + 120)
        self.assertTrue(later.ok)

    def test_slow_guessing_is_not_a_lockout(self):
        bad = self.bridge.derive_key("ZZZZ-ZZZZ-ZZZZ-ZZZZ")
        for i in range(30):
            self.assertEqual("bad_signature", self.check(self.headers(key=bad), now=self.now + i * 20).code)

    def test_no_key_means_nothing_passes(self):
        r = self.guard.check("192.168.2.40", "GET", "/events", b"", self.headers().get, b"", self.now)
        self.assertEqual((503, "not_paired"), (r.status, r.code))


class Http(unittest.TestCase):
    """The real HTTP server of the bridge."""

    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.root = os.path.join(self.tmp.name, "FC26 Export")
        os.makedirs(os.path.join(self.root, "outbox"))
        self.bridge = load_bridge(self.tmp.name)
        self.bridge.atlas_roots = lambda: [self.root]
        self.bridge.resend_state["last"] = 0.0
        self.bridge.phone_seen.update(time=0.0, ip="")
        self.bridge.guard = self.bridge.AuthGuard()
        self._stdout = sys.stdout
        sys.stdout = open(os.devnull, "w")

        class Server(socketserver.ThreadingTCPServer):
            allow_reuse_address = True
            daemon_threads = True

        self.httpd = Server(("127.0.0.1", 0), self.bridge.TelemetryHandler)
        self.port = self.httpd.server_address[1]
        threading.Thread(target=self.httpd.serve_forever, daemon=True).start()
        self.lan = {"Host": "192.168.2.24:%d" % self.port}
        self.key = self.bridge.pairing_key()
        self.bridge.add_event("Live Match Final", "Full Time", json.dumps({"dataType": "match"}))
        with open(os.path.join(self.root, "script_status.json"), "w") as f:
            json.dump({"version": "2.8.0", "protocol": 2, "time": int(time.time()), "club": "Pumas"}, f)

    def tearDown(self):
        self.httpd.shutdown()
        self.httpd.server_close()
        sys.stdout.close()
        sys.stdout = self._stdout
        self.tmp.cleanup()

    def request(self, method, path, headers=None, body=None):
        conn = http.client.HTTPConnection("127.0.0.1", self.port, timeout=10)
        conn.request(method, path, body=body, headers=headers or {})
        resp = conn.getresponse()
        data = resp.read()
        out = (resp.status, data, {k.lower(): v for k, v in resp.getheaders()})
        conn.close()
        return out

    def signed(self, method, path, body=b"", key=None, host=None, **kw):
        h = dict(host or self.lan)
        h.update(self.sign_headers(method, path, body, key, **kw))
        return h

    def sign_headers(self, method, path, body=b"", key=None, when=None, nonce=None):
        when = int(time.time()) if when is None else when
        nonce = nonce or os.urandom(16).hex()
        return {"X-FC-Time": str(when), "X-FC-Nonce": nonce,
                "X-FC-Sig": self.bridge.sign_request(key or self.key, method, path, when, nonce, body)}

    # ---- what a stranger on the network can and cannot do -------------------------------------------------------
    def test_a_stranger_gets_the_ping_and_nothing_else(self):
        code, body, _ = self.request("GET", "/ping", self.lan)
        self.assertEqual(200, code)
        info = json.loads(body)
        self.assertEqual("fc26-bridge", info["app"])
        self.assertEqual(self.bridge.BRIDGE_PROTOCOL, info["protocol"])
        self.assertNotIn("events", info)
        self.assertNotIn(self.bridge.pairing_code(), body.decode())
        for path in ("/events", "/json", "/api/telemetry", "/status", "/api/status"):
            code, body, _ = self.request("GET", path, self.lan)
            self.assertEqual(401, code, path)
            self.assertNotIn(b"Full Time", body)
        for path in ("/", "/index.html", "/anything", "/test-match", "/clear"):
            self.assertEqual(403, self.request("GET", path, self.lan)[0], path)

    def test_a_stranger_cannot_change_anything_even_with_the_code(self):
        for path in ("/clear", "/test-match", "/test-transfer", "/pairing/new", "/", "/events"):
            code, _, _ = self.request("POST", path, self.signed("POST", path), b"")
            self.assertEqual(403, code, path)
        self.assertEqual(1, len(self.bridge.events))
        code, _, _ = self.request("POST", "/", self.lan, json.dumps({"dataType": "match"}))
        self.assertEqual(403, code)
        self.assertEqual(1, len(self.bridge.events))

    def test_the_dashboard_with_the_pairing_code_is_for_this_pc_only(self):
        code, body, _ = self.request("GET", "/")
        self.assertEqual(200, code)
        self.assertIn(self.bridge.format_code(self.bridge.pairing_code()), body.decode())
        self.assertNotIn("__PAIRING__", body.decode())
        for host in ("localhost:%d" % self.port, "127.0.0.1:%d" % self.port, "[::1]:%d" % self.port):
            self.assertEqual(200, self.request("GET", "/", {"Host": host})[0], host)
        code, body, _ = self.request("GET", "/", self.lan)
        self.assertEqual(403, code)
        self.assertNotIn(self.bridge.pairing_code(), body.decode())

    def test_a_request_that_came_through_a_tunnel_is_not_this_pc(self):
        for header in ("X-Forwarded-For", "X-Real-IP", "Forwarded", "Via", "CF-Connecting-IP"):
            h = {header: "203.0.113.9"}
            self.assertEqual(403, self.request("GET", "/", h)[0], header)
            self.assertEqual(403, self.request("POST", "/clear", h)[0], header)
            self.assertEqual(401, self.request("GET", "/events", h)[0], header)
        self.assertEqual(1, len(self.bridge.events))

    def test_other_web_sites_cannot_use_the_dashboard_buttons(self):
        old = self.bridge.pairing_code()
        for path in ("/pairing/new", "/clear", "/test-match"):
            self.assertEqual(403, self.request("POST", path, {"Origin": "http://evil.example"})[0], path)
        self.assertEqual(403, self.request("POST", "/pairing/new", {"Host": "evil.example"})[0])
        self.assertEqual(old, self.bridge.pairing_code())

    def test_the_server_does_not_name_its_python_version(self):
        _, _, headers = self.request("GET", "/ping", self.lan)
        self.assertNotIn("python", headers.get("server", "").lower())

    # ---- the phone ----------------------------------------------------------------------------------------------
    def test_a_signed_request_is_answered_and_the_answer_is_signed(self):
        nonce = os.urandom(16).hex()
        h = self.signed("GET", "/events", nonce=nonce)
        code, body, headers = self.request("GET", "/events", h)
        self.assertEqual(200, code)
        self.assertEqual(1, len(json.loads(body)))
        self.assertEqual(self.bridge.sign_response(self.key, nonce, 200, body), headers["x-fc-sig"])
        self.assertGreater(self.bridge.phone_seen["time"], 0)

    def test_status_is_signed_too_and_answers_are_not_cached(self):
        nonce = os.urandom(16).hex()
        code, body, headers = self.request("GET", "/status", self.signed("GET", "/status", nonce=nonce))
        self.assertEqual(200, code)
        self.assertEqual("fc26-bridge", json.loads(body)["app"])
        self.assertEqual(self.bridge.sign_response(self.key, nonce, 200, body), headers["x-fc-sig"])
        self.assertEqual("no-store", headers["cache-control"])
        security = [c for c in json.loads(body)["checks"] if c["id"] == "security"]
        self.assertEqual("ok", security[0]["state"])

    def test_the_wrong_code_a_replay_and_a_wrong_clock_are_refused_with_a_reason(self):
        wrong = self.bridge.derive_key("ZZZZ-ZZZZ-ZZZZ-ZZZZ")
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
        self.assertAlmostEqual(time.time(), reply["bridgeTime"], delta=5)

    def test_a_signature_for_one_path_does_not_work_for_another(self):
        h = self.signed("GET", "/events")
        self.assertEqual(401, self.request("GET", "/status", h)[0])

    def test_resend_needs_the_signature_and_the_body_must_match(self):
        self.assertEqual(401, self.request("POST", "/resend", self.lan, b"")[0])
        self.assertFalse(os.path.exists(os.path.join(self.root, "resend_request.json")))
        h = self.signed("POST", "/resend", b"")
        code, body, headers = self.request("POST", "/resend", h, b"tampered")
        self.assertEqual(401, code)
        h = self.signed("POST", "/resend", b"")
        h["Content-Length"] = "0"
        code, body, headers = self.request("POST", "/resend", h, b"")
        self.assertEqual(200, code, body)
        self.assertEqual("requested", json.loads(body)["status"])
        self.assertIn("x-fc-sig", headers)
        self.assertTrue(os.path.exists(os.path.join(self.root, "resend_request.json")))

    def test_resend_from_the_pcs_own_dashboard_still_needs_no_signature(self):
        self.assertEqual(200, self.request("POST", "/resend", {}, b"")[0])

    def test_repeated_wrong_guesses_lock_that_address_out(self):
        wrong = self.bridge.derive_key("ZZZZ-ZZZZ-ZZZZ-ZZZZ")
        for _ in range(self.bridge.AUTH_MAX_FAILS):
            self.assertEqual(401, self.request("GET", "/events", self.signed("GET", "/events", key=wrong))[0])
        code, body, headers = self.request("GET", "/events", self.signed("GET", "/events"))
        self.assertEqual(429, code)
        self.assertIn("retry-after", headers)
        self.assertEqual(200, self.request("GET", "/ping", self.lan)[0])
        security = self.bridge.build_status(roots=[self.root])["checks"]
        self.assertEqual("warn", [c for c in security if c["id"] == "security"][0]["state"])

    # ---- changing the code --------------------------------------------------------------------------------------
    def test_a_new_code_cuts_off_the_old_one(self):
        old_key = self.key
        code, body, _ = self.request("POST", "/pairing/new", {})
        self.assertEqual(200, code)
        new_code = json.loads(body)["code"]
        self.assertTrue(self.bridge.valid_code(new_code))
        self.assertEqual(new_code, self.bridge.format_code(self.bridge.pairing_code()))
        self.assertEqual(401, self.request("GET", "/events", self.signed("GET", "/events", key=old_key))[0])
        self.assertEqual(200, self.request("GET", "/events", self.signed("GET", "/events", key=self.bridge.derive_key(new_code)))[0])


if __name__ == "__main__":
    unittest.main()
