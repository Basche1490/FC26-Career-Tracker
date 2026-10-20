"""Checks for the PC bridge's health report (pc-bridge/telemetry_bridge.py): /status, the self-check list, the script
version check and the phone's "Re-send last match" request.

Run:  python3 tests/bridge/test_bridge_status.py
"""
import http.client
import importlib.util
import json
import os
import socketserver
import sys
import tempfile
import threading
import time
import unittest

HERE = os.path.dirname(os.path.abspath(__file__))
BRIDGE = os.path.join(HERE, "..", "..", "pc-bridge", "telemetry_bridge.py")


def load_bridge(tmp):
    spec = importlib.util.spec_from_file_location("telemetry_bridge_status_under_test", BRIDGE)
    mod = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(mod)
    mod.EVENTS_FILE = os.path.join(tmp, "telemetry_events.json")
    mod.events.clear()
    mod._outbox_handled.clear()
    return mod


class StatusTest(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.root = os.path.join(self.tmp.name, "FC26 Export")
        os.makedirs(os.path.join(self.root, "outbox"))
        self.bridge = load_bridge(self.tmp.name)
        self.now = 1_800_000_000
        self._stdout = sys.stdout
        sys.stdout = open(os.devnull, "w")

    def tearDown(self):
        sys.stdout.close()
        sys.stdout = self._stdout
        self.tmp.cleanup()

    def heartbeat(self, age=2, **over):
        data = {"app": "fc26-capture", "version": "2.8.0", "protocol": 2, "time": self.now - age, "started": self.now - 900,
                "exporter": True, "club": "Pumas", "careerDate": "2026-10-16", "scans": 12, "lastMatch": self.now - 3600,
                "lastTransfer": 0, "lastSquad": self.now - 500, "pendingMatches": 0, "undelivered": 0}
        data.update(over)
        with open(os.path.join(self.root, "script_status.json"), "w", encoding="utf-8") as f:
            json.dump(data, f)

    def status(self):
        return self.bridge.build_status(now=self.now, roots=[self.root])

    def check(self, status, check_id):
        found = [c for c in status["checks"] if c["id"] == check_id]
        self.assertEqual(1, len(found), f"check {check_id} in {[c['id'] for c in status['checks']]}")
        return found[0]

    # ----- versions -----------------------------------------------------------------------------------------------
    def test_version_compare(self):
        b = self.bridge
        self.assertTrue(b.version_at_least("2.8.0", "2.8.0"))
        self.assertTrue(b.version_at_least("2.10.1", "2.8.0"))
        self.assertTrue(b.version_at_least("v3.0", "2.8.0"))
        self.assertFalse(b.version_at_least("2.7.9", "2.8.0"))
        self.assertFalse(b.version_at_least("", "2.8.0"))
        self.assertFalse(b.version_at_least("beta", "2.8.0"))

    # ----- script heartbeat ---------------------------------------------------------------------------------------
    def test_no_script_heartbeat_is_a_failure_with_a_fix(self):
        st = self.status()
        self.assertFalse(st["script"]["found"])
        c = self.check(st, "script")
        self.assertEqual("fail", c["state"])
        self.assertIn("Execute", c["fix"])
        self.assertEqual("fail", st["summary"])

    def test_fresh_heartbeat_is_alive(self):
        self.heartbeat(age=3)
        st = self.status()
        self.assertTrue(st["script"]["alive"])
        self.assertTrue(st["script"]["versionOk"])
        self.assertEqual("Pumas", st["script"]["club"])
        self.assertEqual("2026-10-16", st["script"]["careerDate"])
        self.assertEqual("ok", self.check(st, "script")["state"])
        self.assertEqual("ok", self.check(st, "version")["state"])
        self.assertEqual("ok", self.check(st, "exporter")["state"])

    def test_stale_heartbeat_means_the_script_stopped(self):
        self.heartbeat(age=95)
        st = self.status()
        self.assertFalse(st["script"]["alive"])
        c = self.check(st, "script")
        self.assertEqual("fail", c["state"])
        self.assertIn("min ago", c["detail"])

    def test_old_script_version_is_flagged_with_the_launcher_as_the_fix(self):
        self.heartbeat(version="2.7.0")
        st = self.status()
        self.assertFalse(st["script"]["versionOk"])
        c = self.check(st, "version")
        self.assertEqual("warn", c["state"])
        self.assertIn("FC26_Launcher", c["fix"])

    def test_missing_exporter_explains_estimated_stats(self):
        self.heartbeat(exporter=False)
        c = self.check(self.status(), "exporter")
        self.assertEqual("warn", c["state"])
        self.assertIn("estimated", c["detail"])

    def test_the_freshest_of_two_export_folders_wins(self):
        other = os.path.join(self.tmp.name, "OneDrive", "FC26 Export")
        os.makedirs(other)
        with open(os.path.join(other, "script_status.json"), "w") as f:
            json.dump({"version": "2.5.0", "time": self.now - 300}, f)
        self.heartbeat(age=1)
        info = self.bridge.read_script_status(roots=[other, self.root], now=self.now)
        self.assertEqual("2.8.0", info["version"])
        self.assertEqual(self.root, info["folder"])

    def test_a_damaged_heartbeat_file_is_ignored(self):
        with open(os.path.join(self.root, "script_status.json"), "w") as f:
            f.write('{"version": "2.8.0", "tim')
        self.assertFalse(self.status()["script"]["found"])

    # ----- outbox / folder ------------------------------------------------------------------------------------------
    def test_no_export_folder_is_a_failure(self):
        st = self.bridge.build_status(now=self.now, roots=[])
        self.assertEqual("fail", self.check(st, "folder")["state"])

    def test_outbox_counts_and_stuck_files_warn(self):
        outbox = os.path.join(self.root, "outbox")
        path = os.path.join(outbox, "1_1.json")
        with open(path, "w") as f:
            f.write("{}")
        os.utime(path, (self.now - 120, self.now - 120))
        with open(os.path.join(outbox, "1_2.json.bad"), "w") as f:
            f.write("x")
        st = self.bridge.build_status(now=self.now, roots=[self.root])
        self.assertEqual(1, st["outbox"]["pending"])
        self.assertEqual(1, st["outbox"]["bad"])
        self.assertTrue(st["outbox"]["writable"])
        self.assertEqual("warn", self.check(st, "outbox")["state"])

    def test_write_test_file_is_not_left_behind(self):
        self.status()
        self.assertEqual([], [n for n in os.listdir(os.path.join(self.root, "outbox")) if n.startswith(".")])

    # ----- events and phone -----------------------------------------------------------------------------------------
    def test_last_event_times_by_kind(self):
        b = self.bridge
        b.events.extend([
            {"id": "a", "time": self.now - 500, "title": "t", "message": json.dumps({"dataType": "match"})},
            {"id": "b", "time": self.now - 200, "title": "t", "message": json.dumps({"dataType": "transfer"})},
            {"id": "c", "time": self.now - 100, "title": "t", "message": json.dumps({"dataType": "squad"})},
            {"id": "d", "time": self.now - 50, "title": "t", "message": "plain text"},
        ])
        ev = b.event_times()
        self.assertEqual((self.now - 500, self.now - 200, self.now - 100, 4),
                         (ev["lastMatch"], ev["lastTransfer"], ev["lastSquad"], ev["count"]))

    def test_phone_check_before_during_and_after_contact(self):
        b = self.bridge
        b.phone_seen.update(time=0.0, ip="")
        self.assertEqual("warn", self.check(self.status(), "phone")["state"])
        b.note_phone("::ffff:192.168.2.40", now=self.now - 4)
        c = self.check(self.status(), "phone")
        self.assertEqual("ok", c["state"])
        self.assertIn("192.168.2.40", c["detail"])
        b.note_phone("192.168.2.40", now=self.now - 300)
        self.assertEqual("warn", self.check(self.status(), "phone")["state"])

    def test_status_is_plain_json_with_the_fields_the_app_reads(self):
        self.heartbeat()
        st = json.loads(json.dumps(self.status()))
        self.assertEqual("fc26-bridge", st["app"])
        self.assertEqual(self.bridge.BRIDGE_PROTOCOL, st["bridge"]["protocol"])
        self.assertEqual(self.bridge.MIN_SCRIPT_VERSION, st["bridge"]["minScript"])
        for key in ("version", "address", "name"):
            self.assertTrue(st["bridge"][key])
        for c in st["checks"]:
            self.assertIn(c["state"], ("ok", "warn", "fail", "info"))
            self.assertTrue(c["label"] and c["detail"])

    # ----- re-send ------------------------------------------------------------------------------------------------
    def test_resend_needs_a_running_current_script(self):
        code, reply = self.bridge.request_resend(roots=[self.root], now=self.now)
        self.assertEqual((409, "script_not_running"), (code, reply["code"]))
        self.heartbeat(age=100)
        code, reply = self.bridge.request_resend(roots=[self.root], now=self.now)
        self.assertEqual((409, "script_not_running"), (code, reply["code"]))
        self.heartbeat(version="2.6.1")
        code, reply = self.bridge.request_resend(roots=[self.root], now=self.now)
        self.assertEqual((409, "script_too_old"), (code, reply["code"]))
        self.assertFalse(os.path.exists(os.path.join(self.root, "resend_request.json")))

    def test_resend_writes_the_request_once_and_is_rate_limited(self):
        self.heartbeat()
        self.bridge.resend_state["last"] = 0.0
        code, reply = self.bridge.request_resend(roots=[self.root], now=self.now)
        self.assertEqual((200, "requested"), (code, reply["status"]))
        with open(os.path.join(self.root, "resend_request.json"), encoding="utf-8") as f:
            written = json.load(f)
        self.assertEqual(reply["id"], written["id"])
        code, reply2 = self.bridge.request_resend(roots=[self.root], now=self.now + 3)
        self.assertEqual((429, "too_soon"), (code, reply2["code"]))
        code, reply3 = self.bridge.request_resend(roots=[self.root], now=self.now + 11)
        self.assertEqual(200, code)
        self.assertNotEqual(reply["id"], reply3["id"])
        self.assertEqual([], [n for n in os.listdir(self.root) if n.endswith(".tmp")])

    def test_resend_result_from_the_script_reaches_the_status(self):
        self.heartbeat(resend={"id": "r5", "time": self.now - 1, "result": "sent", "detail": "Pumas 2-1 Arsenal"})
        self.assertEqual("r5", self.status()["script"]["resend"]["id"])


class HttpTest(unittest.TestCase):
    """The same bridge answering real HTTP requests on a free port."""

    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.root = os.path.join(self.tmp.name, "FC26 Export")
        os.makedirs(os.path.join(self.root, "outbox"))
        self.bridge = load_bridge(self.tmp.name)
        self.bridge.atlas_roots = lambda: [self.root]
        self.bridge.resend_state["last"] = 0.0
        self.bridge.phone_seen.update(time=0.0, ip="")

        class Server(socketserver.ThreadingTCPServer):
            allow_reuse_address = True
            daemon_threads = True

        self.httpd = Server(("127.0.0.1", 0), self.bridge.TelemetryHandler)
        self.port = self.httpd.server_address[1]
        threading.Thread(target=self.httpd.serve_forever, daemon=True).start()
        with open(os.path.join(self.root, "script_status.json"), "w") as f:
            json.dump({"version": "2.8.0", "protocol": 2, "time": int(time.time()), "club": "Pumas"}, f)

    def tearDown(self):
        self.httpd.shutdown()
        self.httpd.server_close()
        self.tmp.cleanup()

    def request(self, method, path, headers=None):
        conn = http.client.HTTPConnection("127.0.0.1", self.port, timeout=10)
        conn.request(method, path, headers=headers or {})
        resp = conn.getresponse()
        body = resp.read()
        conn.close()
        return resp.status, body

    def test_status_endpoint(self):
        code, body = self.request("GET", "/status")
        self.assertEqual(200, code)
        st = json.loads(body)
        self.assertEqual("fc26-bridge", st["app"])
        self.assertTrue(st["script"]["alive"])
        code, body = self.request("GET", "/api/status")
        self.assertEqual(200, code)
        self.assertEqual("fc26-bridge", json.loads(body)["app"])

    def test_dashboard_page_shows_the_self_check(self):
        code, body = self.request("GET", "/")
        text = body.decode("utf-8")
        self.assertEqual(200, code)
        self.assertIn("Self check", text)
        self.assertIn("Re-send last match", text)
        self.assertIn("v" + self.bridge.BRIDGE_VERSION, text)
        self.assertNotIn("__LOCAL_IP__", text)
        self.assertNotIn("__EVENTS__", text)

    def test_resend_over_http_writes_the_request(self):
        code, body = self.request("POST", "/resend")
        self.assertEqual(200, code, body)
        self.assertTrue(os.path.exists(os.path.join(self.root, "resend_request.json")))

    def test_resend_is_allowed_from_the_network_but_other_writes_are_not(self):
        self.bridge.is_local_client = lambda client, server: False      # pretend the request comes from the phone
        code, _ = self.request("POST", "/resend")
        self.assertEqual(200, code)
        for path in ("/clear", "/test-match", "/test-transfer"):
            self.assertEqual(403, self.request("POST", path)[0], path)
        self.assertEqual(403, self.request("POST", "/")[0])

    def test_resend_refuses_other_web_sites_and_odd_hosts(self):
        code, _ = self.request("POST", "/resend", {"Origin": "http://evil.example"})
        self.assertEqual(403, code)
        code, _ = self.request("POST", "/resend", {"Host": "evil.example"})
        self.assertEqual(403, code)
        self.assertFalse(os.path.exists(os.path.join(self.root, "resend_request.json")))

    def test_phone_contact_is_noted_only_for_other_machines(self):
        self.request("GET", "/events")
        self.assertEqual(0.0, self.bridge.phone_seen["time"])            # this PC's own browser does not count
        self.bridge.is_local_client = lambda client, server: False
        self.request("GET", "/events")
        self.assertGreater(self.bridge.phone_seen["time"], 0)
        self.bridge.phone_seen.update(time=0.0, ip="")
        self.request("GET", "/status")
        self.assertGreater(self.bridge.phone_seen["time"], 0)

    def test_get_does_not_trigger_the_test_buttons(self):
        for path in ("/clear", "/test-match"):
            self.assertEqual(302, self.request("GET", path)[0])


if __name__ == "__main__":
    unittest.main()
