"""Checks for the match log the PC bridge builds from an Atlas export (pc-bridge/telemetry_bridge.py).

The Live Editor script describes a finished match in detail (who replaced whom, saves, the real league position).
When the bridge reports a match itself it must describe it the same way. These tests run the bridge's importer on
a pretend save: a snapshot folder plus an Atlas export with an opposing man of the match and a loaned-in player the
export left out.

Run:  python3 tests/bridge/test_match_log.py
"""
import csv
import importlib.util
import os
import sys
import tempfile
import time
import unittest

HERE = os.path.dirname(os.path.abspath(__file__))
BRIDGE = os.path.join(HERE, "..", "..", "pc-bridge", "telemetry_bridge.py")

MANAGED = 115486
OPP = 106


def load_bridge(tmp):
    spec = importlib.util.spec_from_file_location("telemetry_bridge_match_log", BRIDGE)
    mod = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(mod)
    mod.EVENTS_FILE = os.path.join(tmp, "telemetry_events.json")
    mod.PROCESSED_FILE = os.path.join(tmp, "processed_atlas_folders.json")
    mod.events.clear()
    return mod


def write_csv(path, rows, columns=None):
    columns = columns or sorted({k for r in rows for k in r})
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8-sig", newline="") as f:
        w = csv.DictWriter(f, fieldnames=columns, restval="")
        w.writeheader()
        for r in rows:
            w.writerow(r)


def torquay_record():
    """Torquay's last match as the save snapshot recorded it (three substitutes, 92 minutes long)."""
    def h(i, m, pos, sub=False):
        return {"id": i, "minutes": m, "pos": pos, "sub": sub}
    return [h(462221, 0, 3), h(461360, 47, 11, True), h(460045, 47, 18, True), h(78859, 35, 23, True),
            h(259627, 90, 27), h(460106, 90, 25), h(460055, 57, 23), h(275436, 45, 18), h(460023, 45, 11),
            h(461436, 90, 9), h(461438, 90, 7), h(73616, 90, 6), h(462884, 90, 4), h(460038, 90, 3), h(460129, 90, 0)]


class PairingTest(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.bridge = load_bridge(self.tmp.name)

    def tearDown(self):
        self.tmp.cleanup()

    def test_real_match_pairs_every_substitute_with_the_player_he_replaced(self):
        pairs, marks, length = self.bridge.pair_substitutions(torquay_record())
        got = [(p["minute"], p["out"]["id"], p["incoming"]["id"]) for p in pairs]
        self.assertEqual([(45, 275436, 460045), (45, 460023, 461360), (57, 460055, 78859)], got)
        self.assertEqual(92, length)
        self.assertEqual(57, marks[78859]["in_minute"])
        self.assertEqual(57, marks[460055]["out_minute"])
        self.assertNotIn(259627, marks)

    def test_sending_off_is_not_a_substitution(self):
        rows = [{"id": i, "minutes": 60 if i == 5 else 90, "pos": i, "sub": False} for i in range(1, 12)]
        rows.append({"id": 12, "minutes": 20, "pos": 99, "sub": True})
        pairs, marks, length = self.bridge.pair_substitutions(rows)
        self.assertEqual([], pairs)
        self.assertEqual(60, marks[5]["out_minute"])
        self.assertEqual(length - 20, marks[12]["in_minute"])

    def test_record_of_another_match_is_refused(self):
        ids = {str(h["id"]) for h in torquay_record() if h["minutes"] > 0}
        self.assertTrue(self.bridge.lineup_matches_export(torquay_record(), ids))
        self.assertFalse(self.bridge.lineup_matches_export(torquay_record(), {str(i) for i in range(1, 12)}))
        self.assertFalse(self.bridge.lineup_matches_export(torquay_record()[:5], None))

    def test_league_table(self):
        def fx(date, h, a, hs, a_s, done):
            return {"competition_object_id": 779, "home_team_id": h, "away_team_id": a, "date_raw": date,
                    "completed": done, "home_score": hs, "away_score": a_s}
        M, A, B, C, D, E = MANAGED, 50, 106, 88, 1795, 1806
        fixtures = [fx(20280805, M, A, 3, 0, True), fx(20280805, B, C, 1, 1, True), fx(20280805, D, E, 2, 1, True),
                    fx(20280812, M, B, 1, 3, True), fx(20280812, A, C, 2, 2, True), fx(20280812, D, E, 0, 0, True),
                    {**fx(20280809, M, D, 5, 0, True), "competition_object_id": 1856}]
        rows, pending = self.bridge.build_league_table(fixtures, 779, 20280812)
        self.assertEqual([B, D, M, C, E, A], [r["team_id"] for r in rows])
        self.assertEqual(0, pending)
        rows, _ = self.bridge.build_league_table(fixtures, 779, 20280805)
        self.assertEqual(M, rows[0]["team_id"])
        fixtures[5]["completed"] = False
        _rows, pending = self.bridge.build_league_table(fixtures, 779, 20280812)
        self.assertEqual(1, pending)

    def test_championship_is_a_league(self):
        self.assertTrue(self.bridge._is_league_name("EFL Championship"))
        self.assertFalse(self.bridge._is_league_name("UEFA Champions League"))
        self.assertFalse(self.bridge._is_league_name("FA Cup"))


class ImporterTest(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.root = os.path.join(self.tmp.name, "FC26 Export")
        self.bridge = load_bridge(self.tmp.name)
        self.sent = []
        self.bridge.add_match_event = lambda title, msg, payload: self.sent.append(payload) or True
        self._stdout = sys.stdout
        sys.stdout = open(os.devnull, "w")

    def tearDown(self):
        sys.stdout.close()
        sys.stdout = self._stdout
        self.tmp.cleanup()

    def build(self, stale_record=False):
        snap = os.path.join(self.root, "Snapshots", "ready", "snapshot_20281012_120000")
        write_csv(os.path.join(snap, "career_metadata.csv"), [
            {"key": "managed_team_id", "value": str(MANAGED)}, {"key": "managed_team_name", "value": "Torquay AFC"}])
        match_cols = ["managed_team_id", "managed_team_name", "competition_object_id", "competition_name", "date_raw", "date",
                      "side", "opponent_team_id", "opponent_team_name", "is_completed", "managed_score", "opponent_score", "result"]
        write_csv(os.path.join(snap, "managed_team_matches.csv"), [
            {"managed_team_id": MANAGED, "managed_team_name": "Torquay AFC", "competition_object_id": 779,
             "competition_name": "EFL Championship", "date_raw": 20280805, "date": "2028-08-05", "side": "HOME",
             "opponent_team_id": 50, "opponent_team_name": "Parma", "is_completed": 1, "managed_score": 3, "opponent_score": 0, "result": "W"},
            {"managed_team_id": MANAGED, "managed_team_name": "Torquay AFC", "competition_object_id": 779,
             "competition_name": "EFL Championship", "date_raw": 20280812, "date": "2028-08-12", "side": "HOME",
             "opponent_team_id": OPP, "opponent_team_name": "Sunderland", "is_completed": 1, "managed_score": 1, "opponent_score": 3, "result": "L"},
        ], match_cols)
        # the stored position is the stale pre-season value 2
        write_csv(os.path.join(snap, "team_leagues.csv"), [
            {"team_id": MANAGED, "team_name": "Torquay AFC", "league_name": "EFL Championship", "matches_played": 2, "current_table_position": 2}])
        squad_ids = [460129, 462884, 463320, 463808, 73049, 460053, 460045, 460055, 464205, 461483, 462221, 460047, 470001]
        write_csv(os.path.join(snap, "rosters.csv"), [{"team_id": MANAGED, "player_id": i} for i in squad_ids])
        # the game's record of the last match: includes the loaned-in player 461607 (not in the squad list)
        def lm(pid, name, minutes, pos, fact):
            return {"player_id": pid, "display_name": name, "team_id": MANAGED, "is_managed_team": 1, "minutes_played": minutes,
                    "actual_position_id": pos, "player_fact_raw": fact}
        rec = [lm(460129, "Marc Palomino", 90, 0, 0), lm(462884, "Oliver Ebner", 90, 3, 0), lm(463320, "Leo Guerra", 90, 4, 0),
               lm(461607, "Fabian Bravo", 90, 6, 0), lm(463808, "Denilson Fernandes", 90, 7, 0), lm(73049, "Pedro Lima", 90, 9, 0),
               lm(460053, "Ignacio Zapata", 45, 11, 0), lm(460045, "Jordan Anderson", 57, 18, 0), lm(460055, "Kento Takeuchi", 90, 23, 0),
               lm(464205, "Elivelton Jesus", 90, 27, 0), lm(461483, "Enrique Iglesias", 90, 25, 0),
               lm(462221, "Bruno Henrique Gonzaga", 47, 11, 10), lm(460047, "Harry Dalton", 35, 18, 10), lm(470001, "Nico Striker", 0, 25, 6)]
        if stale_record:
            for i, r in enumerate(rec):
                r["player_id"] = 880000 + i
        rec.append({"player_id": 999001, "display_name": "Someone Else", "team_id": 50, "is_managed_team": 0, "minutes_played": 90,
                    "actual_position_id": 0, "player_fact_raw": 0})
        write_csv(os.path.join(snap, "last_match_players.csv"), rec)
        fx_rows = []

        def fx(date, h, a, hs, a_s, done, comp=779):
            fx_rows.append({"competition_object_id": comp, "home_team_id": h, "away_team_id": a, "date_raw": date,
                            "is_completed": 1 if done else 0, "home_score": hs, "away_score": a_s})
        M, A, B, C, D, E = MANAGED, 50, 106, 88, 1795, 1806
        fx(20280805, M, A, 3, 0, True); fx(20280805, B, C, 1, 1, True); fx(20280805, D, E, 2, 1, True)
        fx(20280812, M, B, 1, 3, True); fx(20280812, A, C, 2, 2, True); fx(20280812, D, E, 0, 0, True)
        fx(20280819, A, M, 0, 0, False)
        write_csv(os.path.join(snap, "fixtures.csv"), fx_rows)

        export = os.path.join(self.root, "MatchStats", "ready", "FC26_MatchStats_pmc_20281012_120500_abc")
        cols = ["team_index", "player_id", "player_name", "minutes_played", "game_rating", "man_of_the_match", "passes",
                "pass_accuracy_pct_derived", "shots", "shots_on_target", "tackles", "interceptions", "goals_derived", "assists",
                "saves", "penalty_saves"]

        def p(team, pid, name, minutes, **extra):
            r = {"team_index": team, "player_id": pid, "player_name": name, "minutes_played": minutes, "game_rating": "6.5",
                 "man_of_the_match": "0", "passes": "20", "pass_accuracy_pct_derived": "80", "shots": "0", "shots_on_target": "0",
                 "tackles": "1", "interceptions": "0", "goals_derived": "0", "assists": "0"}
            r.update(extra)
            return r
        write_csv(os.path.join(export, "16_match_participants_wide.csv"), [
            p(0, 900001, "Opp Keeper", 90), p(0, 900002, "Opp Striker", 90, game_rating="9.1", man_of_the_match="1", goals_derived="3"),
            p(1, 460129, "Marc Palomino", 90, saves="4", penalty_saves="1", game_rating="6.2"),
            p(1, 462884, "Oliver Ebner", 90), p(1, 463320, "Leo Guerra", 90), p(1, 463808, "Denilson Fernandes", 90),
            p(1, 73049, "Pedro Lima", 90), p(1, 460053, "Ignacio Zapata", 45), p(1, 460045, "Jordan Anderson", 57),
            p(1, 460055, "Kento Takeuchi", 90), p(1, 464205, "Elivelton Jesus", 90),
            p(1, 461483, "Enrique Iglesias", 90, goals_derived="1", game_rating="7.4", shots="3", shots_on_target="2"),
            p(1, 462221, "Bruno Henrique Gonzaga", 47), p(1, 460047, "Harry Dalton", 35), p(1, 470001, "Nico Striker", 0),
        ], cols)
        # our club is team_index 1 and its row comes first in the team file
        write_csv(os.path.join(export, "18_match_teams_wide.csv"), [
            {"team_index": 1, "passes": 300, "shots": 7, "shots_on_target": 3, "npk_expected_goals": "0.8",
             "pass_accuracy_pct_derived": "77.5", "shootout_goals": 255},
            {"team_index": 0, "passes": 500, "shots": 14, "shots_on_target": 7, "npk_expected_goals": "2.6",
             "pass_accuracy_pct_derived": "88", "shootout_goals": 255}])
        return export, os.path.join(self.root, "Snapshots", "ready")

    def import_folder(self, export, snaps):
        """Runs the importer on one export folder (the PowerShell test overrides this)."""
        self.bridge.import_atlas_folder(export, os.path.basename(export), snaps)

    def run_import(self, **kw):
        export, snaps = self.build(**kw)
        self.import_folder(export, snaps)
        self.assertEqual(1, len(self.sent))
        return self.sent[0]

    def test_full_match_log(self):
        m = self.run_import()
        self.assertEqual("Sunderland", m["opponentName"])
        self.assertEqual(3, m["leaguePosition"])                      # from the results, not the stored 2
        self.assertEqual(38, m["possession"])                         # 300 of 800 passes, from our own row
        self.assertTrue(m["possessionEstimated"])
        self.assertEqual((7, 14), (m["shotsFor"], m["shotsAgainst"]))
        self.assertEqual("Opp Striker", m["motmName"])
        self.assertTrue(m["motmIsOpponent"])
        self.assertFalse(any(p["isMotm"] for p in m["playerPerformances"]))
        by_name = {p["playerName"]: p for p in m["playerPerformances"]}
        self.assertIn("Fabian Bravo", by_name)                        # the loaned-in player the export left out
        self.assertEqual(4, by_name["Marc Palomino"]["saves"])
        self.assertEqual(1, by_name["Marc Palomino"]["penaltySaves"])
        self.assertNotIn("Nico Striker", by_name)
        self.assertEqual(13, len(m["playerPerformances"]))
        self.assertEqual(45, by_name["Ignacio Zapata"]["subOutMinute"])
        self.assertEqual(45, by_name["Bruno Henrique Gonzaga"]["subInMinute"])
        subs = [(s["minute"], s["playerOutName"], s["playerInName"]) for s in m["substitutions"]]
        self.assertEqual([(45, "Ignacio Zapata", "Bruno Henrique Gonzaga"), (57, "Jordan Anderson", "Harry Dalton")], subs)
        self.assertTrue(all("_pos" not in p for p in m["playerPerformances"]))
        # the position each player played, so the app can line the team up from striker to goalkeeper
        self.assertEqual("GK", by_name["Marc Palomino"]["position"])
        self.assertEqual("ST", by_name["Enrique Iglesias"]["position"])
        self.assertEqual("LCB", by_name["Fabian Bravo"]["position"])
        # the three goals against are credited to the scorer the export names
        theirs = [g["playerName"] for g in m["goalDetails"] if g["isOpponent"]]
        self.assertEqual(["Opp Striker"] * 3, theirs)
        self.assertEqual(["Enrique Iglesias"], [g["playerName"] for g in m["goalDetails"] if not g["isOpponent"]])

    def test_record_of_another_match_adds_nothing_made_up(self):
        m = self.run_import(stale_record=True)
        self.assertEqual([], m["substitutions"])
        self.assertEqual(12, len(m["playerPerformances"]))

    def test_a_player_the_export_gives_no_name_is_named_from_the_snapshot(self):
        # regens and academy graduates often come without a name in the match export; the app then showed "Unknown player"
        export, snaps = self.build()
        path = os.path.join(export, "16_match_participants_wide.csv")
        with open(path, encoding="utf-8-sig", newline="") as f:
            rows = list(csv.DictReader(f))
            cols = list(rows[0].keys())
        for r in rows:
            if r["player_name"] == "Kento Takeuchi":
                r["player_name"] = ""
        write_csv(path, rows, cols)
        snap = os.path.join(snaps, "snapshot_20281012_120000")
        rec_path = os.path.join(snap, "last_match_players.csv")
        with open(rec_path, encoding="utf-8-sig", newline="") as f:
            rec = list(csv.DictReader(f))
            rec_cols = list(rec[0].keys())
        write_csv(rec_path, [r for r in rec if r["player_id"] != "460055"], rec_cols)
        write_csv(os.path.join(snap, "players.csv"), [{"player_id": 460055, "display_name": "Kento Takeuchi"},
                                                     {"player_id": 1, "display_name": "Somebody"}])
        self.import_folder(export, snaps)
        names = [p["playerName"] for p in self.sent[0]["playerPerformances"]]
        self.assertIn("Kento Takeuchi", names)
        self.assertNotIn("Player", names)

    def test_own_man_of_the_match_is_not_an_opponent(self):
        export, snaps = self.build()
        path = os.path.join(export, "16_match_participants_wide.csv")
        with open(path, encoding="utf-8-sig", newline="") as f:
            rows = list(csv.DictReader(f))
            cols = list(rows[0].keys())
        for r in rows:
            r["man_of_the_match"] = "1" if r["player_name"] == "Enrique Iglesias" else "0"
        write_csv(path, rows, cols)
        self.import_folder(export, snaps)
        m = self.sent[0]
        self.assertEqual("Enrique Iglesias", m["motmName"])
        self.assertFalse(m["motmIsOpponent"])
        self.assertEqual(["Enrique Iglesias"], [p["playerName"] for p in m["playerPerformances"] if p["isMotm"]])


class DeferralTest(unittest.TestCase):
    """The bridge waits a little before reporting an export itself, so the Live Editor script's own report wins."""

    class Stop(Exception):
        pass

    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.root = os.path.join(self.tmp.name, "FC26 Export")
        self.bridge = load_bridge(self.tmp.name)
        export = os.path.join(self.root, "MatchStats", "ready", "FC26_MatchStats_pmc_20281012_120500_abc")
        os.makedirs(export)
        for name in ("MATCH_READY", "16_match_participants_wide.csv", "18_match_teams_wide.csv"):
            with open(os.path.join(export, name), "w") as f:
                f.write("x")
        os.makedirs(os.path.join(self.root, "Snapshots", "ready"))
        self.imported = []
        self.bridge.atlas_roots = lambda: [self.root]
        self.bridge.import_atlas_folder = lambda folder, fid, snaps: self.imported.append(fid)
        self.bridge.processed_atlas_folders.clear()
        self.bridge.atlas_first_seen.clear()
        self.bridge.atlas_deferred_noted.clear()
        self._stdout = sys.stdout
        sys.stdout = open(os.devnull, "w")

    def tearDown(self):
        sys.stdout.close()
        sys.stdout = self._stdout
        self.tmp.cleanup()

    def one_pass(self):
        real_sleep = self.bridge.time.sleep

        def stop(_seconds):
            raise self.Stop()
        self.bridge.time.sleep = stop
        try:
            self.bridge.watch_atlas_exports()
        except self.Stop:
            pass
        finally:
            self.bridge.time.sleep = real_sleep

    def test_export_is_held_back_at_first_and_imported_later(self):
        self.one_pass()
        self.assertEqual([], self.imported)                           # still waiting for the script's own report
        fid = os.listdir(os.path.join(self.root, "MatchStats", "ready"))[0]
        self.bridge.atlas_first_seen[fid] = time.time() - self.bridge.ATLAS_DEFER_SECONDS - 1
        self.one_pass()
        self.assertEqual([fid], self.imported)                        # nothing arrived: the bridge reports it itself


if __name__ == "__main__":
    unittest.main(verbosity=2)
