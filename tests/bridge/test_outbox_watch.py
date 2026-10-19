"""Checks for the PC bridge's outbox folder watcher (pc-bridge/telemetry_bridge.py).

The Live Editor script saves every payload as a small .json file in Documents\\FC26 Export\\outbox. The bridge reads
those files itself, so the script does not have to start a helper program (a command window) for each payload.

Run:  python3 tests/bridge/test_outbox_watch.py
"""
import importlib.util
import json
import os
import sys
import tempfile
import time
import unittest

HERE = os.path.dirname(os.path.abspath(__file__))
BRIDGE = os.path.join(HERE, "..", "..", "pc-bridge", "telemetry_bridge.py")


def load_bridge(tmp):
    spec = importlib.util.spec_from_file_location("telemetry_bridge_under_test", BRIDGE)
    mod = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(mod)
    mod.EVENTS_FILE = os.path.join(tmp, "telemetry_events.json")
    mod.events.clear()
    mod._outbox_handled.clear()
    return mod


class OutboxWatchTest(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.root = os.path.join(self.tmp.name, "FC26 Export")
        self.outbox = os.path.join(self.root, "outbox")
        os.makedirs(self.outbox)
        self.bridge = load_bridge(self.tmp.name)
        self._stdout = sys.stdout
        sys.stdout = open(os.devnull, "w")

    def tearDown(self):
        sys.stdout.close()
        sys.stdout = self._stdout
        self.tmp.cleanup()

    def put(self, name, text, age=5.0):
        path = os.path.join(self.outbox, name)
        with open(path, "wb") as f:
            f.write(text.encode("utf-8") if isinstance(text, str) else text)
        t = time.time() - age
        os.utime(path, (t, t))
        return path

    def read_root_file(self, name):
        with open(os.path.join(self.root, name), encoding="utf-8") as f:
            return f.read()

    def names(self):
        return sorted(os.listdir(self.outbox))

    def run_once(self):
        self.bridge.check_outbox_once(roots=[self.root], now=time.time())

    def transfer(self, name="José Perez"):
        return json.dumps({"dataType": "transfer", "title": "Transfer Finalized", "message": name + " signed",
                           "playerName": name, "type": "In (Permanent)", "feeM": 10.0, "fromToClub": "Pumas",
                           "date": "2026-10-16"}, ensure_ascii=False)

    MATCH = json.dumps({"dataType": "match", "title": "Live Match Final", "message": "Full Time", "opponentName": "Arsenal",
                        "goalsFor": 2, "goalsAgainst": 1, "fixtureKey": "fk1"})

    def test_payloads_are_stored_in_order_and_removed(self):
        self.put("100_1112.json", self.MATCH, age=4)
        self.put("100_1111.json", self.transfer(), age=6)
        self.run_once()
        self.assertEqual(self.names(), [])
        self.assertEqual([e["title"] for e in self.bridge.events], ["Transfer Finalized", "Live Match Final"])
        self.assertIn("José Perez", self.bridge.events[0]["message"])
        log = self.read_root_file("bridge_send_log.txt")
        self.assertIn("[100_1111] HTTP 200", log)
        self.assertIn("[100_1112] HTTP 200", log)

    def test_duplicate_match_is_dropped_but_its_file_is_removed(self):
        self.put("100_1.json", self.MATCH)
        self.put("100_2.json", self.MATCH)
        self.run_once()
        self.assertEqual(self.names(), [])
        self.assertEqual(len(self.bridge.events), 1)

    def test_fresh_file_waits_and_young_bad_file_is_kept(self):
        self.put("100_1.json", self.transfer(), age=0)
        self.put("100_2.json", '{"dataType":"transfer","title":', age=5)
        self.run_once()
        self.assertEqual(self.names(), ["100_1.json", "100_2.json"])
        self.assertEqual(len(self.bridge.events), 0)

    def test_old_bad_file_is_renamed(self):
        self.put("100_1.json", '{"dataType":"transfer","title":', age=200)
        self.run_once()
        self.assertEqual(self.names(), ["100_1.json.bad"])
        log = self.read_root_file("bridge_send_log.txt")
        self.assertIn("REFUSED", log)

    def test_utf8_bom_and_latin1_files_are_read(self):
        self.put("100_1.json", b"\xef\xbb\xbf" + self.transfer("Zlátan").encode("utf-8"))
        self.put("100_2.json", self.transfer("Muñoz").encode("latin-1"))
        self.run_once()
        self.assertEqual(self.names(), [])
        messages = " ".join(e["message"] for e in self.bridge.events)
        self.assertIn("Zlátan", messages)
        self.assertIn("Muñoz", messages)

    def test_a_file_that_cannot_be_deleted_is_not_stored_twice(self):
        path = self.put("100_1.json", self.transfer())
        real_remove = os.remove
        calls = {"n": 0}

        def failing_remove(p):
            calls["n"] += 1
            if p == path and calls["n"] == 1:
                raise PermissionError("locked")
            return real_remove(p)

        os.remove = failing_remove
        try:
            self.run_once()
            self.assertEqual(len(self.bridge.events), 1)
            self.run_once()
        finally:
            os.remove = real_remove
        self.assertEqual(len(self.bridge.events), 1)
        self.assertEqual(self.names(), [])

    def test_heartbeat_is_written_for_the_script(self):
        self.run_once()
        stamp = int(self.read_root_file("bridge_watching.txt"))
        self.assertLess(abs(stamp - time.time()), 5)

    def test_http_post_path_still_refuses_malformed_json(self):
        status, reply = self.bridge.process_payload_text('{"broken": ')
        self.assertEqual(status, 400)
        status, reply = self.bridge.process_payload_text("   ")
        self.assertEqual(status, 400)
        status, reply = self.bridge.process_payload_text(self.transfer())
        self.assertEqual((status, reply["status"]), (200, "ok"))


if __name__ == "__main__":
    unittest.main(verbosity=2)
