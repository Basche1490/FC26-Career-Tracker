"""Career and squad reports in the bridge: only the newest of each kind waits for the phone, so they never push
matches or transfers out of the 100-event list.

Run:  python3 tests/bridge/test_career_reports.py
"""
import importlib.util
import json
import os
import tempfile
import unittest

HERE = os.path.dirname(os.path.abspath(__file__))
BRIDGE = os.path.join(HERE, "..", "..", "pc-bridge", "telemetry_bridge.py")


def load_bridge(tmp):
    spec = importlib.util.spec_from_file_location("telemetry_bridge_career_under_test", BRIDGE)
    mod = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(mod)
    mod.EVENTS_FILE = os.path.join(tmp, "telemetry_events.json")
    os.environ["FC26_STATE_DIR"] = os.path.join(tmp, "state")
    mod.events.clear()
    return mod


def kinds(bridge):
    out = []
    for e in bridge.events:
        try:
            out.append(json.loads(e["message"]).get("dataType"))
        except (ValueError, TypeError):
            out.append(None)
    return out


class CareerReportsTest(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.bridge = load_bridge(self.tmp.name)

    def tearDown(self):
        self.tmp.cleanup()

    def post(self, data):
        return self.bridge.process_payload_text(json.dumps(data))

    def test_a_new_career_report_replaces_the_old_one(self):
        self.post({"dataType": "career", "title": "Career", "careerDate": "2026-09-01"})
        self.post({"dataType": "transfer", "title": "Signed", "playerName": "A"})
        code, _ = self.post({"dataType": "career", "title": "Career", "careerDate": "2026-09-08"})
        self.assertEqual(code, 200)
        self.assertEqual(kinds(self.bridge), ["transfer", "career"])
        self.assertIn("2026-09-08", self.bridge.events[-1]["message"])

    def test_squad_and_career_are_kept_apart(self):
        self.post({"dataType": "squad", "title": "Squad"})
        self.post({"dataType": "career", "title": "Career"})
        self.post({"dataType": "squad", "title": "Squad"})
        self.assertEqual(kinds(self.bridge), ["career", "squad"])

    def test_many_reports_never_push_out_a_match(self):
        self.post({"dataType": "transfer", "title": "Signed", "playerName": "B"})
        for i in range(150):
            self.post({"dataType": "career", "title": "Career", "n": i})
        self.assertEqual(kinds(self.bridge), ["transfer", "career"])

    def test_transfers_are_never_replaced(self):
        self.post({"dataType": "transfer", "title": "One", "playerName": "C"})
        self.post({"dataType": "transfer", "title": "Two", "playerName": "D"})
        self.assertEqual(kinds(self.bridge), ["transfer", "transfer"])


if __name__ == "__main__":
    unittest.main()
