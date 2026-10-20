"""Tests for FC26_Launcher.bat, the one launcher for the PC side.

The launcher is a batch file with a PowerShell program inside it. launcher_tests.ps1 loads that program from the .bat
itself and runs its functions on pretend folder layouts (the bridge and Live Editor in different places, several Live
Editors, old scripts to move aside, a bridge that is already running, ...). Each check it prints becomes one result here.
The last test runs the whole launcher against the real bridge on this machine.

Skipped when PowerShell (pwsh) is not installed. Run:  python3 tests/launcher/test_launcher.py
"""
import http.server
import json
import os
import shutil
import socket
import subprocess
import sys
import tempfile
import threading
import time
import unittest

HERE = os.path.dirname(os.path.abspath(__file__))
REPO = os.path.abspath(os.path.join(HERE, "..", ".."))
LAUNCHER = os.path.join(REPO, "FC26_Launcher.bat")
RUNNER = os.path.join(HERE, "launcher_tests.ps1")
PWSH = shutil.which("pwsh") or os.environ.get("PWSH")


def free_port():
    s = socket.socket()
    s.bind(("127.0.0.1", 0))
    port = s.getsockname()[1]
    s.close()
    return port


class _Handler(http.server.BaseHTTPRequestHandler):
    kind = "bridge"

    def log_message(self, *a):
        pass

    def do_GET(self):
        if self.kind == "hang":
            time.sleep(8)
            return
        if self.kind == "bridge":
            body = json.dumps({"app": "fc26-bridge", "bridge": {"version": "2.0.0", "address": "http://192.168.1.5:5050"}})
            code, ctype = 200, "application/json"
        elif self.kind == "old":
            body, code, ctype = "<html><title>EA FC Telemetry Bridge</title>running</html>", 200, "text/html"
        elif self.kind == "html404":
            body, code, ctype = "<html>Not Found</html>", 404, "text/html"
        else:
            body, code, ctype = '{"hello":"world"}', 200, "application/json"
        data = body.encode()
        self.send_response(code)
        self.send_header("Content-Type", ctype)
        self.send_header("Content-Length", str(len(data)))
        self.end_headers()
        self.wfile.write(data)


def serve(kind):
    handler = type("H", (_Handler,), {"kind": kind})
    srv = http.server.ThreadingHTTPServer(("127.0.0.1", 0), handler)
    srv.daemon_threads = True
    threading.Thread(target=srv.serve_forever, daemon=True).start()
    return srv


def run_checks():
    servers = {k: serve(k) for k in ("bridge", "old", "other", "html404", "hang")}
    ports = {k: s.server_address[1] for k, s in servers.items()}
    ports["closed"] = free_port()
    try:
        env = dict(os.environ, DOTNET_SYSTEM_GLOBALIZATION_INVARIANT="1")
        proc = subprocess.run(
            [PWSH, "-NoProfile", "-NonInteractive", "-File", RUNNER, "-Launcher", LAUNCHER, "-RealRepo", REPO,
             "-StatusPorts", json.dumps(ports)],
            capture_output=True, text=True, timeout=300, env=env)
    finally:
        for s in servers.values():
            s.shutdown()
            s.server_close()
    results = {}
    for line in proc.stdout.splitlines():
        if line.startswith("RESULT|"):
            _, name, state, detail = (line.split("|", 3) + [""])[:4]
            results[name] = (state, detail)
    return proc, results


@unittest.skipUnless(PWSH, "PowerShell (pwsh) is not installed")
class LauncherFunctionTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.proc, cls.results = run_checks()

    def test_runner_finished(self):
        self.assertIn("DONE", self.proc.stdout, "the test program stopped early:\n" + self.proc.stdout[-1500:] + "\n" + self.proc.stderr[-1500:])

    def test_every_check_passes(self):
        self.assertGreater(len(self.results), 100, "too few checks ran: %d" % len(self.results))
        failed = ["%s  (%s)" % (k, v[1]) for k, v in self.results.items() if v[0] != "ok"]
        self.assertEqual(failed, [], "launcher checks failed:\n  " + "\n  ".join(failed))


class LauncherFileTests(unittest.TestCase):
    """What can be checked without PowerShell: the file must be fit for double-clicking on Windows."""

    def setUp(self):
        with open(LAUNCHER, "rb") as f:
            self.raw = f.read()
        self.text = self.raw.decode("ascii")

    def test_ascii_and_crlf(self):
        self.assertNotIn(b"\n", self.raw.replace(b"\r\n", b""), "every line must end with CRLF")

    def test_batch_part_hides_nothing_and_stops_before_powershell(self):
        head = self.text.split("##PS-START")[0]
        self.assertIn("exit /b", head)
        # Windows PowerShell by its full path (a powershell.exe next to the launcher must not run instead), and no
        # policy override: text given with -Command is not a script file, so the policy does not apply to it anyway.
        self.assertIn(r'set "FC26_PS=%SystemRoot%\System32\WindowsPowerShell\v1.0\powershell.exe"', head)
        self.assertIn('"%FC26_PS%" -NoProfile -Command', head)
        self.assertNotIn("\npowershell ", head.lower().replace("\r", ""))
        self.assertNotIn("-ExecutionPolicy Bypass", head)
        self.assertIn("pause", head, "a failure must keep the window open so the message can be read")

    def test_no_window_flash_for_the_bridge(self):
        body = self.text.split("##PS-START", 1)[1]
        self.assertIn("-WindowStyle Hidden", body)
        self.assertIn("Press Enter to close", body, "the window must wait at the end instead of closing at once")
        self.assertNotIn("Set-NetConnectionProfile", body, "the launcher must not change the network profile")

    def test_windows_programs_are_started_by_full_path(self):
        body = self.text.split("##PS-START", 1)[1]
        self.assertNotIn("-FilePath 'powershell.exe'", body)
        self.assertNotIn("& netsh ", body)
        self.assertNotIn('"netsh ', body)
        self.assertNotIn("sddl=D:(A;;GX;;;WD)", body, "the port permission must not be for Everyone")
        self.assertNotIn("profile=any remoteip", body.replace("profile=$profiles", ""), "the firewall rule must not be for every network by default")

    def test_nothing_starts_with_windows(self):
        low = self.text.lower()
        for word in ("schtasks", "currentversion\\run", "shell:startup", "\\startup\\"):
            self.assertNotIn(word, low)


@unittest.skipUnless(PWSH, "PowerShell (pwsh) is not installed")
class LauncherEndToEndTest(unittest.TestCase):
    """Runs the whole launcher against the real Python bridge: finds the files, installs the scripts, starts the bridge
    hidden, reports the address, and a second run keeps the same bridge."""

    def setUp(self):
        s = socket.socket()
        s.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)  # a port in TIME_WAIT from an earlier run is still free
        try:
            s.bind(("127.0.0.1", 5050))
        except OSError:
            self.skipTest("port 5050 is busy on this machine")
        finally:
            s.close()
        self.tmp = tempfile.mkdtemp(prefix="fc26-e2e-")
        self.proj = os.path.join(self.tmp, "Downloads", "FC26-Career-Tracker-main")
        os.makedirs(self.proj)
        for name in ("pc-bridge", "live-editor-scripts"):
            shutil.copytree(os.path.join(REPO, name), os.path.join(self.proj, name),
                            ignore=shutil.ignore_patterns("__pycache__", "legacy", "tools"))
        self.launcher = os.path.join(self.proj, "FC26_Launcher.bat")
        shutil.copy(LAUNCHER, self.launcher)
        self.le = os.path.join(self.tmp, "Documents", "FC 26 Live Editor")
        os.makedirs(os.path.join(self.le, "lua", "imports"))
        os.makedirs(os.path.join(self.le, "lua", "scripts"))
        self.old_script = os.path.join(self.le, "lua", "scripts", "telemetry_sync.lua")
        with open(self.old_script, "w") as f:
            f.write("-- old")
        self.env = dict(os.environ, HOME=self.tmp, USERPROFILE=self.tmp, FC26_STATE_DIR=os.path.join(self.tmp, "state"),
                        FC26_LAUNCHER=self.launcher, FC26_ARGS="nopause", DOTNET_SYSTEM_GLOBALIZATION_INVARIANT="1",
                        APPDATA=os.path.join(self.tmp, "AppData"))
        for k in ("OneDrive", "OneDriveConsumer", "OneDriveCommercial"):
            self.env.pop(k, None)

    def tearDown(self):
        subprocess.run(["pkill", "-f", os.path.join(self.proj, "pc-bridge")], capture_output=True)
        time.sleep(0.5)
        shutil.rmtree(self.tmp, ignore_errors=True)

    def launch(self, args="nopause"):
        env = dict(self.env, FC26_ARGS=args)
        code = ("$t=[IO.File]::ReadAllText($env:FC26_LAUNCHER); $i=$t.IndexOf('#'+'#PS-START'); "
                "& ([scriptblock]::Create($t.Substring($i)))")
        return subprocess.run([PWSH, "-NoProfile", "-NonInteractive", "-Command", code], capture_output=True, text=True,
                              timeout=180, env=env, stdin=subprocess.DEVNULL)

    def test_full_run(self):
        first = self.launch()
        out = first.stdout
        self.assertEqual(first.returncode, 0, out + first.stderr)
        self.assertIn("Bridge files:", out)
        self.assertIn("Installed FC26_Auto_Capture.lua", out)
        self.assertIn("Moved old scripts aside", out)
        self.assertIn("The bridge is running in the background", out)
        self.assertIn(":5050", out)
        scripts = os.path.join(self.le, "lua", "scripts")
        self.assertTrue(os.path.exists(os.path.join(scripts, "FC26_Auto_Capture.lua")))
        self.assertTrue(os.path.exists(os.path.join(scripts, "FC26_AutoCapture_v1.1.1.lua")))
        self.assertFalse(os.path.exists(self.old_script))
        self.assertTrue(os.path.exists(self.old_script + ".old"))
        # the bridge really answers (this PC's own dashboard needs no code)
        import urllib.request
        with urllib.request.urlopen("http://127.0.0.1:5050/status", timeout=5) as r:
            status = json.load(r)
        self.assertEqual(status["app"], "fc26-bridge")
        # the launcher made the pairing code, showed it, and the bridge it started uses exactly that code
        with open(os.path.join(self.tmp, "state", "bridge.key"), encoding="ascii") as f:
            code = f.read().strip()
        self.assertIn(code, out)
        self.assertEqual(200, self.phone_request(code))
        self.assertEqual(401, self.phone_request(None))
        self.assertEqual(401, self.phone_request("ZZZZ-ZZZZ-ZZZZ-ZZZZ"))
        # second run: nothing to do, same bridge
        second = self.launch()
        self.assertEqual(second.returncode, 0, second.stdout + second.stderr)
        self.assertIn("The bridge is already running", second.stdout)
        self.assertIn("Already up to date", second.stdout)
        self.assertNotIn("Moved old scripts aside", second.stdout)
        # status option
        st = self.launch("status")
        self.assertIn("Bridge 3.", st.stdout)
        self.assertIn(code, st.stdout)
        # a new code: the running bridge takes it at once, the old one stops working
        new = self.launch("newcode nopause")
        self.assertEqual(new.returncode, 0, new.stdout + new.stderr)
        with open(os.path.join(self.tmp, "state", "bridge.key"), encoding="ascii") as f:
            new_code = f.read().strip()
        self.assertNotEqual(code, new_code)
        self.assertIn(new_code, new.stdout)
        self.assertEqual(401, self.phone_request(code))
        self.assertEqual(200, self.phone_request(new_code))

    def phone_request(self, code):
        """GET /events the way the phone does it, from 'another machine' (a LAN address in the Host header)."""
        import hashlib
        import hmac
        import http.client
        headers = {"Host": "192.168.1.5:5050"}
        if code:
            key = hashlib.sha256(("fc26-bridge-v1:" + code.replace("-", "")).encode()).digest()
            when, nonce = str(int(time.time())), os.urandom(16).hex()
            text = "\n".join(["FC26-REQ-V1", "GET", "/events", when, nonce, hashlib.sha256(b"").hexdigest()])
            headers.update({"X-FC-Time": when, "X-FC-Nonce": nonce,
                            "X-FC-Sig": hmac.new(key, text.encode(), hashlib.sha256).hexdigest()})
        conn = http.client.HTTPConnection("127.0.0.1", 5050, timeout=10)
        conn.request("GET", "/events", headers=headers)
        status = conn.getresponse().status
        conn.close()
        return status


if __name__ == "__main__":
    unittest.main(verbosity=2)
