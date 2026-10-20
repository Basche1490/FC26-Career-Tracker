import http.server
import socketserver
import json
import time
import os
import socket
import re
import html
import ipaddress
import threading
import csv
import hashlib
import hmac
import secrets
from urllib.parse import urlsplit

LOCAL_PORT = 5050
BRIDGE_VERSION = "3.0.0"
BRIDGE_PROTOCOL = 3            # 3 = the phone must prove it knows the pairing code (docs/SECURITY.md)
MIN_SCRIPT_VERSION = "2.8.0"   # older capture scripts do not report their health or answer a re-send request
SCRIPT_ALIVE_SECONDS = 20      # the script writes its heartbeat every 5 seconds
PHONE_ALIVE_SECONDS = 30       # the app asks the bridge for news every 10 seconds
RESEND_MIN_SECONDS = 10        # at most one "re-send the last match" request per this many seconds
EVENTS_FILE = os.path.join(os.path.dirname(__file__), "telemetry_events.json")
MAX_BODY_BYTES = 5 * 1024 * 1024  # largest POST body the bridge will accept (5 MiB)

# ---------------------------------------------------------------------------
# Access control (protocol 3, written up in docs/SECURITY.md)
#
# * This PC's own browser (loopback address, host name localhost / 127.0.0.1, no proxy headers) may open the
#   dashboard and press its buttons. That is the only unsigned access to anything.
# * The phone may do exactly three things: GET /events, GET /status and POST /resend. Every one of them has to carry
#   a signature made with the pairing code (HMAC-SHA256 over the method, path, time, a one-time number and the body),
#   and the bridge signs its answer the same way so the phone knows the answer is from this bridge.
# * Everybody else gets the one-line GET /ping answer (is a bridge here? which version?) and nothing more.
# * Host / Origin checks keep web pages open in a browser from reaching the bridge.
# ---------------------------------------------------------------------------
PC_NAME = socket.gethostname().lower()

PAIR_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"      # no I, O, 0 or 1: nothing to mix up when typing
PAIR_LENGTH = 16                                          # 16 x 5 bits = 80 bits of randomness
KEY_LABEL = "fc26-bridge-v1:"
AUTH_WINDOW_SECONDS = 300                                 # how far the phone's clock may differ from this PC's
AUTH_MAX_FAILS = 10                                       # wrong signatures from one address ...
AUTH_FAIL_WINDOW_SECONDS = 60                             # ... within this time ...
AUTH_LOCKOUT_SECONDS = 60                                 # ... and that address is refused for this long
AUTH_MAX_NONCES = 20000
PHONE_ROUTES = {("GET", "/events"), ("GET", "/json"), ("GET", "/api/telemetry"),
                ("GET", "/status"), ("GET", "/api/status"), ("POST", "/resend")}
LOCAL_HOST_NAMES = ("localhost", "127.0.0.1", "::1")
PROXY_HEADERS = ("X-Forwarded-For", "X-Forwarded-Host", "X-Forwarded-Proto", "X-Real-IP", "Forwarded", "Via",
                 "CF-Connecting-IP", "True-Client-IP", "X-Client-IP")


def _strip_mapped(addr):
    addr = (addr or "").strip()
    if addr.lower().startswith("::ffff:"):
        return addr[7:]
    return addr


def _host_only(host_header):
    h = (host_header or "").strip()
    if h.startswith("["):                  # [::1]:5050
        end = h.find("]")
        return h[1:end] if end != -1 else h
    if h.count(":") == 1:                  # name:port or 1.2.3.4:port
        return h.split(":")[0]
    return h


def is_trusted_local(client_ip, host_header, header_get):
    """True only for a request that really comes from a program on this PC: the connection is from the loopback
    address, it is addressed to localhost / 127.0.0.1 / [::1], and no proxy or tunnel added forwarding headers.
    A request that arrived through a tunnel (ngrok and the like) always carries such headers, so it is not trusted."""
    try:
        if not ipaddress.ip_address(_strip_mapped(client_ip)).is_loopback:
            return False
    except ValueError:
        return False
    host = _host_only(host_header).lower()
    if host and host not in LOCAL_HOST_NAMES:
        return False
    return not any(header_get(name) for name in PROXY_HEADERS)


def host_header_ok(host_header):
    """Allow IP addresses, localhost and this PC's name. Other hostnames are refused
    so a malicious web page cannot reach the bridge through DNS rebinding."""
    host = _host_only(host_header).lower()
    if not host:
        return True                        # HTTP/1.0 client without a Host header - not a browser
    if host in ("localhost", PC_NAME, PC_NAME + ".local"):
        return True
    try:
        ipaddress.ip_address(host)
        return True
    except ValueError:
        return False


def origin_ok(origin, host_header, sec_fetch_site=None):
    """Writes may come from command-line tools (no Origin header) or from this
    bridge's own dashboard page. A page on any other site is refused."""
    if not origin:
        return (sec_fetch_site or "none").lower() in ("none", "same-origin")
    try:
        netloc = urlsplit(origin).netloc.lower()
    except ValueError:
        return False
    return bool(netloc) and netloc == (host_header or "").strip().lower()


# ---- the pairing code ------------------------------------------------------------------------------------------
def state_dir():
    """Where the bridge keeps the pairing code: %APPDATA%\\FC26Tracker (the launcher keeps its notes there too)."""
    override = os.environ.get("FC26_STATE_DIR")
    if override:
        return override
    base = os.environ.get("APPDATA") or os.path.join(os.path.expanduser("~"), ".config")
    return os.path.join(base, "FC26Tracker")


def pairing_file():
    return os.path.join(state_dir(), "bridge.key")


def normalize_code(text):
    return re.sub(r"[^A-Za-z0-9]", "", str(text or "")).upper()


def valid_code(text):
    code = normalize_code(text)
    return len(code) == PAIR_LENGTH and all(c in PAIR_ALPHABET for c in code)


def format_code(text):
    code = normalize_code(text)
    return "-".join(code[i:i + 4] for i in range(0, len(code), 4))


def generate_code():
    return "".join(secrets.choice(PAIR_ALPHABET) for _ in range(PAIR_LENGTH))


def derive_key(code):
    return hashlib.sha256((KEY_LABEL + normalize_code(code)).encode("ascii")).digest()


_pair_lock = threading.Lock()
_pair_cache = {"stamp": None, "code": "", "key": b""}


def _write_pairing_file(code):
    os.makedirs(state_dir(), exist_ok=True)
    path = pairing_file()
    tmp = path + ".tmp"
    fd = os.open(tmp, os.O_WRONLY | os.O_CREAT | os.O_TRUNC, 0o600)
    with os.fdopen(fd, "w", encoding="ascii") as f:
        f.write(format_code(code) + "\n")
    os.replace(tmp, path)


def _stamp(path):
    try:
        st = os.stat(path)
        return (st.st_mtime_ns, st.st_size)
    except OSError:
        return None


def pairing_code(create=True):
    """The normalised pairing code. The first call creates it. A change to the file (the launcher's "new code") is
    picked up on the next request, no restart needed. Returns "" when there is none and it cannot be created."""
    path = pairing_file()
    with _pair_lock:
        stamp = _stamp(path)
        if stamp is not None and stamp == _pair_cache["stamp"]:
            return _pair_cache["code"]
        code = ""
        if stamp is not None:
            try:
                with open(path, encoding="ascii", errors="ignore") as f:
                    code = normalize_code(f.read())
            except OSError:
                code = ""
        if not valid_code(code):
            if not create:
                return ""
            code = generate_code()
            try:
                _write_pairing_file(code)
            except OSError as e:
                print(f"[SECURITY] Could not save the pairing code to {path}: {e}")
                _pair_cache.update(stamp=None, code="", key=b"")
                return ""
            stamp = _stamp(path)
        _pair_cache.update(stamp=stamp, code=code, key=derive_key(code))
        return code


def pairing_key():
    code = pairing_code()
    return _pair_cache["key"] if code else b""


def rotate_pairing_code():
    """Makes a new pairing code. Every phone has to be given the new one."""
    code = generate_code()
    with _pair_lock:
        _write_pairing_file(code)
        _pair_cache.update(stamp=_stamp(pairing_file()), code=code, key=derive_key(code))
    return code


# ---- signing ---------------------------------------------------------------------------------------------------
def body_hash(body):
    return hashlib.sha256(body or b"").hexdigest()


def sign_request(key, method, target, timestamp, nonce, body=b""):
    text = "\n".join(["FC26-REQ-V1", method.upper(), target, str(timestamp), nonce, body_hash(body)])
    return hmac.new(key, text.encode("utf-8"), hashlib.sha256).hexdigest()


def sign_response(key, nonce, status, body):
    text = "\n".join(["FC26-RESP-V1", nonce, str(status), body_hash(body)])
    return hmac.new(key, text.encode("utf-8"), hashlib.sha256).hexdigest()


class AuthResult:
    def __init__(self, ok, status=200, code="", message="", nonce="", extra=None):
        self.ok, self.status, self.code, self.message, self.nonce = ok, status, code, message, nonce
        self.extra = extra or {}


class AuthGuard:
    """Checks the signature of a request from another machine and keeps track of one-time numbers and wrong guesses."""

    def __init__(self):
        self.lock = threading.Lock()
        self.nonces = {}        # one-time number -> when it may be forgotten
        self.fails = {}         # address -> times of recent wrong signatures
        self.locked = {}        # address -> refused until
        self.rejected = 0
        self.last_rejected = {"time": 0.0, "ip": "", "code": ""}

    def locked_for(self, ip, now):
        with self.lock:
            until = self.locked.get(ip, 0)
            if until > now:
                return int(until - now) + 1
            self.locked.pop(ip, None)
            return 0

    def _fail(self, ip, now, code):
        self.rejected += 1
        self.last_rejected = {"time": now, "ip": ip, "code": code}
        recent = [t for t in self.fails.get(ip, []) if now - t < AUTH_FAIL_WINDOW_SECONDS] + [now]
        self.fails[ip] = recent
        if len(recent) >= AUTH_MAX_FAILS:
            self.locked[ip] = now + AUTH_LOCKOUT_SECONDS
            self.fails.pop(ip, None)
        if len(self.fails) > 2000:
            self.fails.clear()

    def check(self, ip, method, target, body, header_get, key, now):
        """AuthResult for one request from another machine. Never raises."""
        wait = self.locked_for(ip, now)
        if wait:
            return AuthResult(False, 429, "locked", f"Too many wrong attempts. Try again in {wait} seconds.", extra={"retryAfter": wait})
        if not key:
            return AuthResult(False, 503, "not_paired", "The bridge has no pairing code yet.")
        stamp = (header_get("X-FC-Time") or "").strip()
        nonce = (header_get("X-FC-Nonce") or "").strip().lower()
        sig = (header_get("X-FC-Sig") or "").strip().lower()
        with self.lock:
            if not (stamp.isdigit() and len(stamp) <= 12 and re.fullmatch(r"[0-9a-f]{16,64}", nonce) and re.fullmatch(r"[0-9a-f]{64}", sig)):
                self._fail(ip, now, "unsigned")
                return AuthResult(False, 401, "unsigned", "This request is not signed. Enter the pairing code in the app.")
            expected = sign_request(key, method, target, stamp, nonce, body)
            if not hmac.compare_digest(expected, sig):
                self._fail(ip, now, "bad_signature")
                return AuthResult(False, 401, "bad_signature", "The pairing code was not accepted.")
            if abs(now - int(stamp)) > AUTH_WINDOW_SECONDS:
                return AuthResult(False, 401, "clock_skew", "The clocks of the phone and the PC differ by more than 5 minutes.",
                                  extra={"bridgeTime": int(now)})
            for n in [n for n, until in self.nonces.items() if until < now]:
                del self.nonces[n]
            if nonce in self.nonces:
                self._fail(ip, now, "replay")
                return AuthResult(False, 401, "replay", "This request was already used.")
            if len(self.nonces) >= AUTH_MAX_NONCES:
                return AuthResult(False, 503, "busy", "Too many requests right now.")
            self.nonces[nonce] = now + 2 * AUTH_WINDOW_SECONDS
        return AuthResult(True, nonce=nonce)


guard = AuthGuard()


def ping_info():
    """What anybody on the network may learn: that a bridge is here, which kind, and that it wants a pairing code."""
    return {"app": "fc26-bridge", "protocol": BRIDGE_PROTOCOL, "version": BRIDGE_VERSION, "name": PC_NAME,
            "auth": "hmac-sha256"}


def sanitize_penalty_text(text):
    if not text:
        return text
    s = str(text)
    s = re.sub(r'\s*\(Pens:\s*255-\d+\)', '', s, flags=re.IGNORECASE)
    s = re.sub(r'\s*\(Pens:\s*\d+-255\)', '', s, flags=re.IGNORECASE)
    s = re.sub(r'\s*\(Pens:\s*0-0\)', '', s, flags=re.IGNORECASE)
    s = re.sub(r'\b255\s*penalties\b', '0 penalties', s, flags=re.IGNORECASE)
    return s

def get_local_ip():
    try:
        s = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
        s.connect(("8.8.8.8", 80))
        ip = s.getsockname()[0]
        s.close()
        return ip
    except Exception:
        return "127.0.0.1"

# Load or init events
events_lock = threading.RLock()
events = []
if os.path.exists(EVENTS_FILE):
    try:
        with open(EVENTS_FILE, "r", encoding="utf-8") as f:
            raw_evs = json.load(f)
            for ev in raw_evs:
                if isinstance(ev, dict):
                    if "message" in ev and isinstance(ev["message"], str):
                        ev["message"] = sanitize_penalty_text(ev["message"])
                        if ev["message"].lstrip().startswith("{"):
                            try:
                                json.loads(ev["message"])
                            except ValueError:
                                continue      # an old malformed event: drop it so it cannot stall the phone app
                    events.append(ev)
    except Exception:
        events = []

def save_events():
    try:
        with events_lock:
            with open(EVENTS_FILE, "w", encoding="utf-8") as f:
                json.dump(events, f)
    except Exception as e:
        print(f"[bridge] could not save {EVENTS_FILE}: {e}")

_event_seq = 0

def add_event(title, msg, raw_payload=None):
    global _event_seq
    ts = int(time.time())
    with events_lock:
        _event_seq += 1
        seq = _event_seq
    ev = {
        "event": "message",
        "id": f"ev-{ts}-{seq}",
        "time": ts,
        "title": title,
        "message": raw_payload if raw_payload else msg
    }
    with events_lock:
        events.append(ev)
        if len(events) > 100:
            events.pop(0)
        save_events()
    print(f"[{time.strftime('%H:%M:%S')}] TELEMETRY SAVED: {title}")

def create_sample_match():
    today = time.strftime("%Y-%m-%d")
    return {
        "dataType": "match",
        "title": "Live Match Final",
        "message": "Full Time: vs 3 - 1 Arsenal (Premier League)",
        "opponentName": "Arsenal",
        "isHome": True,
        "goalsFor": 3,
        "goalsAgainst": 1,
        "resultBadge": "W",
        "date": today,
        "competition": "Premier League",
        "shotsFor": 12,
        "shotsAgainst": 6,
        "possession": 56,
        "passingAccuracy": 88,
        "expectedGoals": 2.8,
        "expectedGoalsAgainst": 0.9,
        "playerPerformances": [
            {"playerName": "Bukayo Saka", "goals": 2, "assists": 1, "rating": 8.9, "yellowCard": False, "redCard": False},
            {"playerName": "Declan Rice", "goals": 1, "assists": 0, "rating": 7.8, "yellowCard": True, "redCard": False},
            {"playerName": "Martin Odegaard", "goals": 0, "assists": 2, "rating": 8.4, "yellowCard": False, "redCard": False}
        ],
        "goalDetails": [
            {"playerName": "Bukayo Saka", "isOpponent": False},
            {"playerName": "Declan Rice", "isOpponent": False},
            {"playerName": "Bukayo Saka", "isOpponent": False},
            {"playerName": "Kai Havertz (Opponent)", "isOpponent": True}
        ]
    }

def process_payload_text(text):
    """Stores one payload (used for HTTP POSTs and for the payload files in the outbox folder).
    Returns (http_status, reply_dict_or_message): 200 = stored (or a duplicate that was ignored), 400 = refused."""
    text = (text or "").strip().lstrip("\ufeff")
    if not text:
        return 400, "Empty body."

    reply = {"status": "ok"}
    if text[0] in "{[":
        # JSON payload from the Live Editor scripts. A malformed one is refused with HTTP 400 instead of being
        # stored: a broken event that looks like JSON would stall the phone app for every event after it.
        data = None
        for strict in (True, False):          # strict=False tolerates raw control characters inside strings
            try:
                data = json.loads(text, strict=strict)
                break
            except ValueError:
                continue
        if not isinstance(data, dict):
            print(f"[{time.strftime('%H:%M:%S')}] REJECTED malformed JSON payload ({len(text)} bytes): {text[:120]!r}")
            return 400, "Payload is not a valid JSON object."
        title = str(data.get("title") or "Match Update")
        msg = sanitize_penalty_text(str(data.get("message") or ""))
        if data.get("dataType") == "match":
            if not add_match_event(title, msg, data):
                reply = {"status": "duplicate"}
        else:
            add_event(title, msg, json.dumps(data, ensure_ascii=False))
    else:
        # plain text message (not JSON): shown in the feed as-is
        add_event("Match Update", sanitize_penalty_text(text), None)
    return 200, reply


# ---------------------------------------------------------------------------
# Health: what the bridge page and the phone app show as "is everything working?"
#
# The capture script writes script_status.json (a heartbeat every few seconds) into Documents\FC26 Export. The bridge
# combines that with what it sees itself (folders, waiting files, when the phone last asked) into one list of checks.
# ---------------------------------------------------------------------------
STARTED_AT = time.time()
phone_seen = {"time": 0.0, "ip": ""}
resend_state = {"last": 0.0}
_write_test_cache = {"time": 0.0, "root": "", "ok": True}


def parse_version(text):
    m = re.match(r"^\s*v?(\d+)\.(\d+)(?:\.(\d+))?", str(text or ""))
    if not m:
        return None
    return (int(m.group(1)), int(m.group(2)), int(m.group(3) or 0))


def version_at_least(have, need):
    h, n = parse_version(have), parse_version(need)
    if h is None or n is None:
        return False
    return h >= n


def note_phone(client_ip, now=None):
    """The phone (or any other reader that is not this PC) just asked for news."""
    phone_seen["time"] = time.time() if now is None else now
    phone_seen["ip"] = _strip_mapped(client_ip)


def read_script_status(roots=None, now=None):
    """The freshest script_status.json found in the export folders, described for the status page."""
    now = time.time() if now is None else now
    best = None
    for root in (atlas_roots() if roots is None else roots):
        path = os.path.join(root, "script_status.json")
        try:
            with open(path, "rb") as f:
                data = json.loads(f.read().decode("utf-8-sig", errors="replace"))
            mtime = os.path.getmtime(path)
        except (OSError, ValueError):
            continue
        if not isinstance(data, dict):
            continue
        stamp = _i(data.get("time")) or int(mtime)
        if best is None or stamp > best[0]:
            best = (stamp, data, root)
    if best is None:
        return {"found": False}
    stamp, data, root = best
    age = max(0, int(now - stamp))
    version = str(data.get("version") or "")
    resend = data.get("resend") if isinstance(data.get("resend"), dict) else None
    return {
        "found": True,
        "folder": root,
        "version": version,
        "protocol": _i(data.get("protocol")),
        "time": stamp,
        "ageSeconds": age,
        "alive": age <= SCRIPT_ALIVE_SECONDS,
        "versionOk": version_at_least(version, MIN_SCRIPT_VERSION),
        "club": str(data.get("club") or ""),
        "careerDate": str(data.get("careerDate") or ""),
        "exporter": bool(data.get("exporter")),
        "scans": _i(data.get("scans")),
        "lastMatch": _i(data.get("lastMatch")),
        "lastTransfer": _i(data.get("lastTransfer")),
        "lastSquad": _i(data.get("lastSquad")),
        "pendingMatches": _i(data.get("pendingMatches")),
        "undelivered": _i(data.get("undelivered")),
        "resend": resend,
    }


def outbox_state(roots=None, now=None):
    """How many payload files are waiting in the outbox, how many were refused, and whether the bridge can write there."""
    now = time.time() if now is None else now
    pending = bad = 0
    oldest = 0
    writable = True
    folders = atlas_roots() if roots is None else roots
    for root in folders:
        outbox = os.path.join(root, "outbox")
        if not os.path.isdir(outbox):
            continue
        try:
            names = os.listdir(outbox)
        except OSError:
            continue
        for name in names:
            low = name.lower()
            if low.endswith(".json"):
                pending += 1
                try:
                    oldest = max(oldest, int(now - os.path.getmtime(os.path.join(outbox, name))))
                except OSError:
                    pass
            elif low.endswith(".bad"):
                bad += 1
        # A real write test, at most every 30 seconds. The file is not a .json, so the outbox watcher ignores it.
        if _write_test_cache["root"] != outbox or now - _write_test_cache["time"] > 30:
            ok = True
            probe = os.path.join(outbox, ".bridge_write_test")
            try:
                with open(probe, "w") as f:
                    f.write("ok")
                os.remove(probe)
            except OSError:
                ok = False
            _write_test_cache.update(time=now, root=outbox, ok=ok)
        writable = writable and _write_test_cache["ok"]
    return {"pending": pending, "bad": bad, "oldestSeconds": oldest, "writable": writable}


def event_times():
    """When the newest match / transfer / squad report arrived (epoch seconds, 0 = never)."""
    latest = {"match": 0, "transfer": 0, "squad": 0}
    with events_lock:
        recent = list(events)
    for ev in recent:
        msg = ev.get("message")
        if not isinstance(msg, str) or not msg.startswith("{"):
            continue
        try:
            data = json.loads(msg)
        except ValueError:
            continue
        kind = data.get("dataType") if isinstance(data, dict) else None
        if kind in latest:
            latest[kind] = max(latest[kind], _i(ev.get("time")))
    return {"count": len(recent), "lastMatch": latest["match"], "lastTransfer": latest["transfer"],
            "lastSquad": latest["squad"]}


def _span(seconds):
    seconds = max(0, int(seconds))
    if seconds < 90:
        return f"{seconds}s"
    if seconds < 5400:
        return f"{seconds // 60} min"
    if seconds < 172800:
        return f"{seconds // 3600} h"
    return f"{seconds // 86400} days"


def _ago(seconds):
    return _span(seconds) + " ago"


def _check(check_id, label, state, detail, fix=""):
    return {"id": check_id, "label": label, "state": state, "detail": detail, "fix": fix}


def selfcheck(script, outbox, ev, roots, local_ip, now):
    """The list shown under 'Self check'. state: ok | warn | fail | info. 'fix' says what to do about a warn or fail."""
    checks = [_check("bridge", "Bridge", "ok",
                     f"Version {BRIDGE_VERSION} is running on {PC_NAME} (port {LOCAL_PORT}).")]

    if not roots:
        checks.append(_check("folder", "Export folder", "fail", "Documents\\FC26 Export was not found.",
                             "Run FC26_Auto_Capture.lua in Live Editor once: it creates the folder."))
    else:
        checks.append(_check("folder", "Export folder", "ok", roots[0]))

    if roots:
        if not outbox["writable"]:
            checks.append(_check("outbox", "Outbox", "fail", "The bridge cannot write to the outbox folder.",
                                 "Check that the folder is not read-only and that no program is locking it."))
        elif outbox["bad"]:
            checks.append(_check("outbox", "Outbox", "warn", f"{outbox['bad']} file(s) could not be read and were set aside (.bad).",
                                 "The script sent something the bridge could not read. Re-run the script; if it keeps happening, send the bridge_send_log.txt file."))
        elif outbox["pending"] and outbox["oldestSeconds"] > 30:
            checks.append(_check("outbox", "Outbox", "warn",
                                 f"{outbox['pending']} payload(s) have been waiting for {_span(outbox['oldestSeconds'])}.",
                                 "Keep this window open: the bridge delivers them. If it stays, restart the bridge."))
        else:
            checks.append(_check("outbox", "Outbox", "ok",
                                 "Empty." if not outbox["pending"] else f"{outbox['pending']} payload(s) being delivered."))

    if not script["found"]:
        checks.append(_check("script", "Capture script", "fail", "No heartbeat from the script yet.",
                             "In Live Editor open your career, then Lua Scripts > FC26_Auto_Capture.lua > Execute."))
    elif not script["alive"]:
        checks.append(_check("script", "Capture script", "fail", f"Last heard from {_ago(script['ageSeconds'])}.",
                             "Live Editor was closed or the script stopped. Execute FC26_Auto_Capture.lua again."))
    else:
        club = f" for {script['club']}" if script["club"] else ""
        checks.append(_check("script", "Capture script", "ok", f"Running{club}, version {script['version'] or '?'}."))

    if script["found"]:
        if not script["versionOk"]:
            checks.append(_check("version", "Script version", "warn",
                                 f"Script {script['version'] or '?'} is older than {MIN_SCRIPT_VERSION}, which this bridge expects.",
                                 "Run FC26_Launcher.bat to install the current script, then Execute it again in Live Editor."))
        else:
            checks.append(_check("version", "Script version", "ok", f"{script['version']} (needs {MIN_SCRIPT_VERSION} or newer)."))
        if script["alive"]:
            if script["exporter"]:
                checks.append(_check("exporter", "Match statistics", "ok", "The Project Atlas exporter is running."))
            else:
                checks.append(_check("exporter", "Match statistics", "warn",
                                     "The Project Atlas exporter is not running, so matches arrive with estimated statistics.",
                                     "Execute the Project Atlas exporter script in Live Editor too."))
            if script["undelivered"]:
                checks.append(_check("undelivered", "Delivery", "warn", f"{script['undelivered']} report(s) not delivered yet.",
                                     "Keep this window open; the bridge picks them up."))

    if ev["lastMatch"]:
        checks.append(_check("matches", "Last match", "info", f"Received {_ago(now - ev['lastMatch'])}."))
    else:
        checks.append(_check("matches", "Last match", "info", "No match received yet. Play or sim a match to see one arrive."))

    if phone_seen["time"] <= 0:
        checks.append(_check("phone", "Phone", "warn", "The phone has not connected yet.",
                             f"In the app open PC Connection (in the menu), tap Find my PC (or enter http://{local_ip}:{LOCAL_PORT}) and type the pairing code shown on the dashboard at http://localhost:{LOCAL_PORT}."))
    elif now - phone_seen["time"] > PHONE_ALIVE_SECONDS:
        checks.append(_check("phone", "Phone", "warn", f"The phone last asked {_ago(now - phone_seen['time'])} ({phone_seen['ip']}).",
                             "Open the app. Phone and PC need to be on the same Wi-Fi."))
    else:
        checks.append(_check("phone", "Phone", "ok", f"Connected ({phone_seen['ip']}), last asked {_ago(now - phone_seen['time'])}."))

    rejected = guard.last_rejected
    if rejected["time"] and now - rejected["time"] < 300:
        reasons = {"bad_signature": "a wrong pairing code", "unsigned": "no pairing code", "replay": "a repeated request",
                   "locked": "too many wrong attempts"}
        checks.append(_check("security", "Network access", "warn",
                             f"A device on your network ({rejected['ip']}) was refused {_ago(now - rejected['time'])}: "
                             f"{reasons.get(rejected['code'], 'not allowed')}.",
                             "If that was not your phone, press New pairing code on the dashboard (open http://localhost:"
                             f"{LOCAL_PORT} on this PC)."))
    else:
        checks.append(_check("security", "Network access", "ok", "Only a phone with the pairing code can read from the bridge."))

    if local_ip.startswith("127."):
        checks.append(_check("address", "Network address", "fail", "This PC has no network address.",
                             "Connect the PC to the same Wi-Fi or network as the phone."))
    else:
        checks.append(_check("address", "Network address", "ok", f"http://{local_ip}:{LOCAL_PORT}"))
    return checks


def build_status(now=None, roots=None):
    now = time.time() if now is None else now
    roots = atlas_roots() if roots is None else roots
    local_ip = get_local_ip()
    script = read_script_status(roots, now)
    outbox = outbox_state(roots, now)
    ev = event_times()
    checks = selfcheck(script, outbox, ev, roots, local_ip, now)
    states = [c["state"] for c in checks]
    summary = "fail" if "fail" in states else "warn" if "warn" in states else "ok"
    seen = phone_seen["time"]
    return {
        "app": "fc26-bridge",
        "now": int(now),
        "bridge": {"version": BRIDGE_VERSION, "protocol": BRIDGE_PROTOCOL, "minScript": MIN_SCRIPT_VERSION,
                   "kind": "python", "name": PC_NAME, "port": LOCAL_PORT, "address": f"http://{local_ip}:{LOCAL_PORT}",
                   "started": int(STARTED_AT)},
        "script": script,
        "outbox": outbox,
        "events": ev,
        "phone": {"lastSeen": int(seen), "ageSeconds": int(now - seen) if seen else -1, "ip": phone_seen["ip"]},
        "checks": checks,
        "summary": summary,
    }


def request_resend(roots=None, now=None):
    """Asks the capture script to send its latest match again (a file the script reads, because the script, not the
    bridge, knows the match). Returns (http_status, reply_dict)."""
    now = time.time() if now is None else now
    script = read_script_status(roots, now)
    if not script["found"] or not script["alive"]:
        return 409, {"status": "error", "code": "script_not_running",
                     "message": "The capture script is not running, so it cannot send anything. Execute FC26_Auto_Capture.lua in Live Editor first."}
    if not script["versionOk"]:
        return 409, {"status": "error", "code": "script_too_old",
                     "message": f"The capture script ({script['version'] or '?'}) is older than {MIN_SCRIPT_VERSION} and cannot do this. Install the current script with FC26_Launcher.bat."}
    if now - resend_state["last"] < RESEND_MIN_SECONDS:
        return 429, {"status": "error", "code": "too_soon", "message": "A re-send was just requested. Give it a few seconds."}
    request_id = f"r{int(now)}"
    target = os.path.join(script["folder"], "resend_request.json")
    try:
        tmp = target + ".tmp"
        with open(tmp, "w", encoding="utf-8") as f:
            json.dump({"id": request_id, "time": int(now)}, f)
        os.replace(tmp, target)
    except OSError as e:
        return 500, {"status": "error", "code": "write_failed", "message": f"Could not write the request: {e}"}
    resend_state["last"] = now
    print(f"[{time.strftime('%H:%M:%S')}] Re-send of the latest match requested [{request_id}]")
    return 200, {"status": "requested", "id": request_id}


DASHBOARD_HTML = """<!DOCTYPE html>
<html>
<head>
    <title>EA FC Telemetry Bridge</title>
    <meta name="viewport" content="width=device-width, initial-scale=1">
    <style>
        body { font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif; background: #0f172a; color: #f8fafc; padding: 24px; margin: 0; }
        .card { background: #1e293b; border-radius: 14px; padding: 24px; max-width: 680px; margin: 0 auto 16px; }
        h1 { color: #38bdf8; font-size: 22px; margin-top: 0; }
        h3 { margin: 0 0 12px; }
        .info { background: #0f172a; padding: 14px; border-radius: 8px; font-family: monospace; margin: 14px 0; border: 1px solid #334155; }
        button, a.btn { background: #10b981; color: white; border: none; padding: 10px 16px; border-radius: 8px; cursor: pointer; font-weight: bold; text-decoration: none; display: inline-block; margin: 0 8px 8px 0; }
        button.blue { background: #0284c7; }
        button.red { background: #ef4444; }
        button:disabled { opacity: .5; cursor: default; }
        .row { display: flex; gap: 12px; padding: 10px 0; border-top: 1px solid #334155; }
        .row:first-child { border-top: 0; }
        .dot { flex: 0 0 12px; height: 12px; border-radius: 6px; margin-top: 5px; background: #64748b; }
        .dot.ok { background: #10b981; } .dot.warn { background: #f59e0b; } .dot.fail { background: #ef4444; }
        .label { font-weight: bold; font-size: 14px; }
        .detail { font-size: 13px; color: #cbd5e1; }
        .fix { font-size: 13px; color: #fcd34d; margin-top: 2px; }
        #summary { font-size: 15px; font-weight: bold; margin-bottom: 8px; }
        #resendNote { font-size: 13px; color: #cbd5e1; margin-left: 4px; }
    </style>
</head>
<body>
    <div class="card">
        <h1>EA Sports FC Telemetry Bridge <span style="font-size:13px;color:#94a3b8;">v__VERSION__ (Python)</span></h1>
        <div class="info">
            <strong>IP address for the Android app:</strong><br>
            <span style="color:#10b981;font-size:16px;font-weight:bold;">http://__LOCAL_IP__:__PORT__</span>
        </div>
    </div>
    <div class="card">
        <h3>Pairing code</h3>
        <p style="font-size:13px;color:#cbd5e1;margin-top:0;">Type this code into the app (menu, PC Connection). Only a phone that has it can read anything from this bridge. Do not share it, and do not post a screenshot of this page.</p>
        <div id="pairCode" style="font-family:monospace;font-size:26px;letter-spacing:2px;color:#facc15;">__PAIRING__</div>
        <div style="margin-top:12px;">
            <button id="newCodeBtn" class="red" type="button">New pairing code</button>
            <span id="newCodeNote" style="font-size:13px;color:#cbd5e1;margin-left:6px;"></span>
        </div>
    </div>
    <div class="card">
        <h3>Self check</h3>
        <div id="summary">Checking...</div>
        <div id="checks"></div>
        <div style="margin-top:14px;">
            <button id="resendBtn" class="blue" type="button">Re-send last match</button><button id="recheckBtn" type="button">Check again</button>
            <span id="resendNote"></span>
        </div>
    </div>
    <div class="card">
        <div style="margin: 0 0 16px;">
            <button onclick="fetch('/test-match', {method:'POST'}).then(()=>location.reload())">Send Test Match (3 - 1 vs Arsenal)</button>
            <button class="blue" onclick="fetch('/test-transfer', {method:'POST'}).then(()=>location.reload())">Send Test Transfer (&euro;120M)</button>
            <button class="red" onclick="fetch('/clear', {method:'POST'}).then(()=>location.reload())">Clear</button>
        </div>
        <p style="font-size:12px;color:#94a3b8;"><a href="/events" target="_blank" style="color:#38bdf8;">[View JSON]</a> <a href="/status" target="_blank" style="color:#38bdf8;">[Status JSON]</a></p>
        <h3>Live Events (__COUNT__ queued)</h3>
        <pre style="background:#0f172a;padding:12px;border-radius:8px;max-height:260px;overflow:auto;font-size:12px;color:#cbd5e1;">__EVENTS__</pre>
    </div>
<script>
document.getElementById('newCodeBtn').onclick = function () {
    if (!confirm('Make a new pairing code? The phone will stop working until you type the new code into the app.')) return;
    fetch('/pairing/new', { method: 'POST' }).then(function (r) { return r.json(); }).then(function (d) {
        if (d && d.code) { document.getElementById('pairCode').textContent = d.code; document.getElementById('newCodeNote').textContent = 'Done. Enter the new code in the app.'; }
        else { document.getElementById('newCodeNote').textContent = (d && d.message) || 'Could not make a new code.'; }
    }).catch(function () { document.getElementById('newCodeNote').textContent = 'The bridge did not answer.'; });
};
function row(c) {
    var r = document.createElement('div'); r.className = 'row';
    var d = document.createElement('div'); d.className = 'dot ' + (c.state || '');
    var t = document.createElement('div');
    var l = document.createElement('div'); l.className = 'label'; l.textContent = c.label;
    var x = document.createElement('div'); x.className = 'detail'; x.textContent = c.detail;
    t.appendChild(l); t.appendChild(x);
    if (c.fix) { var f = document.createElement('div'); f.className = 'fix'; f.textContent = c.fix; t.appendChild(f); }
    r.appendChild(d); r.appendChild(t);
    return r;
}
function refresh() {
    return fetch('/status').then(function (r) { return r.json(); }).then(function (s) {
        var box = document.getElementById('checks');
        box.textContent = '';
        s.checks.forEach(function (c) { box.appendChild(row(c)); });
        var sum = document.getElementById('summary');
        sum.textContent = s.summary === 'ok' ? 'Everything is working.' : s.summary === 'warn' ? 'Working, with something to look at.' : 'Something needs fixing.';
        sum.style.color = s.summary === 'ok' ? '#10b981' : s.summary === 'warn' ? '#f59e0b' : '#ef4444';
        return s;
    }).catch(function () {
        document.getElementById('summary').textContent = 'The bridge did not answer.';
    });
}
document.getElementById('recheckBtn').onclick = refresh;
document.getElementById('resendBtn').onclick = function () {
    var btn = this, note = document.getElementById('resendNote');
    btn.disabled = true; note.textContent = 'Asking the script...';
    fetch('/resend', {method: 'POST'}).then(function (r) { return r.json(); }).then(function (reply) {
        if (reply.status !== 'requested') { note.textContent = reply.message || 'Could not ask.'; btn.disabled = false; return; }
        var tries = 0;
        (function poll() {
            refresh().then(function (s) {
                var rs = s && s.script && s.script.resend;
                if (rs && rs.id === reply.id) {
                    note.textContent = rs.result === 'sent' ? 'Sent: ' + rs.detail : rs.result === 'nothing' ? rs.detail : 'The script could not do it: ' + rs.detail;
                    btn.disabled = false;
                } else if (++tries > 12) {
                    note.textContent = 'No answer from the script. Is Live Editor still open?'; btn.disabled = false;
                } else { setTimeout(poll, 1500); }
            });
        })();
    }).catch(function () { note.textContent = 'The bridge did not answer.'; btn.disabled = false; });
};
refresh();
setInterval(refresh, 5000);
</script>
</body>
</html>"""


class TelemetryHandler(http.server.BaseHTTPRequestHandler):
    timeout = 15  # drop clients that stall in the middle of a request
    server_version = "FC26Bridge"
    sys_version = ""
    _sign_with = None      # (key, one-time number) once the phone's request has been checked: the answer is signed too
    _last_logged = {}

    def version_string(self):
        return self.server_version

    def end_headers(self):
        # Deliberately no CORS headers: the phone app is not a browser and the
        # dashboard page is served from this same origin.
        self.send_header('X-Content-Type-Options', 'nosniff')
        self.send_header('X-Frame-Options', 'DENY')
        self.send_header('Cache-Control', 'no-store')
        super().end_headers()

    def _send(self, code, body, content_type="application/json", extra=None):
        self.send_response(code)
        self.send_header('Content-type', content_type)
        self.send_header('Content-Length', str(len(body)))
        for name, value in (extra or {}).items():
            self.send_header(name, str(value))
        if self._sign_with:
            key, nonce = self._sign_with
            self.send_header('X-FC-Sig', sign_response(key, nonce, code, body))
        self.end_headers()
        self.wfile.write(body)

    def _json(self, code, obj):
        self._send(code, json.dumps(obj).encode())

    def _reject(self, code, message, extra=None, headers=None):
        reply = {"status": "error", "message": message}
        reply.update(extra or {})
        self._send(code, json.dumps(reply).encode(), extra=headers)

    def _host_allowed(self):
        if host_header_ok(self.headers.get('Host')):
            return True
        self._reject(403, "Unexpected Host header.")
        return False

    def _is_local(self):
        return is_trusted_local(self.client_address[0], self.headers.get('Host'), self.headers.get)

    def _write_allowed(self):
        """Gate for anything that changes state on this PC (dashboard buttons, the Live Editor script). Only a program on
        this PC may do these, and a web page in a browser may not. Sends the error reply itself when refusing."""
        if not self._host_allowed():
            return False
        if not self._is_local():
            self._reject(403, "Only the PC that runs the bridge may do this. Open http://localhost:%d on that PC." % LOCAL_PORT,
                         {"code": "local_only"})
            return False
        if not origin_ok(self.headers.get('Origin'), self.headers.get('Host'), self.headers.get('Sec-Fetch-Site')):
            self._reject(403, "Cross-site request blocked.")
            return False
        return True

    def _read_body(self, limit):
        """The request body, or None after sending the error reply."""
        try:
            length = int(self.headers.get('Content-Length', 0))
        except ValueError:
            self._reject(400, "Bad Content-Length.")
            return None
        if length < 0:
            self._reject(400, "Bad Content-Length.")
            return None
        if length > limit:
            self._reject(413, "Payload too large.")
            return None
        return self.rfile.read(length)

    def _log_refusal(self, ip, result):
        now = time.time()
        key = (ip, result.code)
        if now - TelemetryHandler._last_logged.get(key, 0) > 60:
            TelemetryHandler._last_logged[key] = now
            print(f"[{time.strftime('%H:%M:%S')}] Refused a request from {ip}: {result.message}")

    def _phone_allowed(self, method, body=b""):
        """The gate for the phone's three requests (and for any request from another machine). Local, unsigned requests
        are the dashboard's. Everything from another machine must be signed. Returns True when the request may go on;
        otherwise the refusal has been sent."""
        self._sign_with = None
        ip = _strip_mapped(self.client_address[0])
        if self._is_local() and not self.headers.get('X-FC-Sig'):
            return True
        route = (method, self.path.lower())
        if not self._is_local():
            wait = guard.locked_for(ip, time.time())
            if wait:
                self._reject(429, f"Too many wrong attempts. Try again in {wait} seconds.", {"code": "locked"}, {"Retry-After": wait})
                return False
            if route not in PHONE_ROUTES:
                self._reject(403, "Only the PC that runs the bridge may do this. Open http://localhost:%d on that PC." % LOCAL_PORT,
                             {"code": "local_only"})
                return False
        result = guard.check(ip, method, self.path, body, self.headers.get, pairing_key(), time.time())
        if not result.ok:
            self._log_refusal(ip, result)
            self._reject(result.status, result.message, dict(result.extra, code=result.code))
            return False
        self._sign_with = (pairing_key(), result.nonce)
        if not self._is_local():
            note_phone(ip)
        return True

    def do_OPTIONS(self):
        # No CORS preflight support - browsers on other sites must not be able to write.
        self._reject(405, "Method not allowed.")

    def do_POST(self):
        path = self.path.lower()
        self._sign_with = None
        if path == "/resend":
            # The phone's "Re-send last match" button (signed), or the dashboard's own button. It only asks the capture
            # script to send its latest match again (rate limited, and the bridge drops a duplicate).
            if not self._host_allowed():
                return
            if not origin_ok(self.headers.get('Origin'), self.headers.get('Host'), self.headers.get('Sec-Fetch-Site')):
                self._reject(403, "Cross-site request blocked.")
                return
            body = self._read_body(1024)
            if body is None or not self._phone_allowed("POST", body):
                return
            code, reply = request_resend()
            self._json(code, reply)
            return
        if not self._write_allowed():
            return
        if path == "/pairing/new":
            try:
                code = rotate_pairing_code()
            except OSError as e:
                self._reject(500, f"Could not save the new pairing code: {e}")
                return
            print(f"[{time.strftime('%H:%M:%S')}] A new pairing code was made. Enter it in the app.")
            self._json(200, {"status": "ok", "code": format_code(code)})
            return

        if path == "/test-match":
            sample = create_sample_match()
            add_event("Live Match Final", "Full Time: vs 3 - 1 Arsenal (Premier League)", json.dumps(sample))
            self._json(200, {"status": "ok", "message": "Test match sent successfully!"})
            return

        if path == "/test-transfer":
            sample = {
                "dataType": "transfer",
                "title": "Transfer Finalized",
                "message": "Erling Haaland signed from Manchester City for \u20ac120.0M",
                "playerName": "Erling Haaland",
                "type": "Bought",
                "feeM": 120.0,
                "fromToClub": "Manchester City",
                "date": time.strftime("%Y-%m-%d")
            }
            add_event("Transfer Finalized", sample["message"], json.dumps(sample))
            self._json(200, {"status": "ok", "message": "Test transfer sent successfully!"})
            return

        if path == "/clear":
            with events_lock:
                events.clear()
                save_events()
            self._json(200, {"status": "cleared"})
            return

        body = self._read_body(MAX_BODY_BYTES)
        if body is None:
            return
        status, reply = process_payload_text(body.decode('utf-8', errors='replace'))
        if status != 200:
            self._reject(status, reply)
            return
        self._json(status, reply)

    def do_GET(self):
        self._sign_with = None
        if not self._host_allowed():
            return
        path = self.path.lower()
        if path == "/ping":
            # The only thing anybody on the network may ask: is a bridge here, and which one?
            self._json(200, ping_info())
            return
        if not self._is_local() or self.headers.get('X-FC-Sig'):
            if not self._phone_allowed("GET"):
                return
        if path in ("/test-match", "/test-transfer", "/clear"):
            # These used to fire on a plain GET, which means a link or <img> tag on any
            # web page could trigger them. They are POST-only now (use the dashboard buttons).
            self.send_response(302)
            self.send_header('Location', '/')
            self.end_headers()
            return

        if path in ["/status", "/api/status"]:
            self._json(200, build_status())
            return

        if path in ["/events", "/json", "/api/telemetry"]:
            with events_lock:
                body = json.dumps(events).encode()
            self._send(200, body)
            return

        page = (DASHBOARD_HTML
                .replace("__LOCAL_IP__", html.escape(get_local_ip()))
                .replace("__PORT__", str(LOCAL_PORT))
                .replace("__VERSION__", html.escape(BRIDGE_VERSION))
                .replace("__COUNT__", str(len(events)))
                .replace("__PAIRING__", html.escape(format_code(pairing_code())))
                .replace("__EVENTS__", html.escape(json.dumps(events, indent=2))))
        self._send(200, page.encode('utf-8'), 'text/html; charset=utf-8',
                   {'Content-Security-Policy': "default-src 'none'; script-src 'unsafe-inline'; style-src 'unsafe-inline'; connect-src 'self'; frame-ancestors 'none'; base-uri 'none'; form-action 'none'"})

    def log_message(self, format, *args):
        pass


import traceback

# ---------------------------------------------------------------------------
# Number helpers: CSV exports contain decimals ("86.4", "90.0") and empty cells. int("86.4") raises,
# which used to abort the whole import silently.
# ---------------------------------------------------------------------------
def _f(v, default=0.0):
    try:
        x = float(str(v).strip())
        if x != x or x in (float("inf"), float("-inf")):
            return default
        return x
    except (TypeError, ValueError):
        return default

def _i(v, default=0):
    return int(round(_f(v, default)))

def _pct(v):
    x = _f(v, 0.0)
    return x * 100.0 if 0 < x <= 1 else x

# ---------------------------------------------------------------------------
# Match de-duplication. The Lua script and the hot-folder importer can both report the same match;
# the first one to arrive wins and the other is dropped.
# ---------------------------------------------------------------------------
DEDUPE_WINDOW_SECONDS = 600

def _norm_team(name):
    return re.sub(r"[^a-z0-9]", "", str(name or "").lower())

def is_duplicate_match(data):
    opp = _norm_team(data.get("opponentName"))
    gf, ga = _i(data.get("goalsFor")), _i(data.get("goalsAgainst"))
    key = str(data.get("fixtureKey") or "")
    now = int(time.time())
    with events_lock:
        recent = list(events)
    for ev in reversed(recent):                      # newest first
        if now - _i(ev.get("time")) > DEDUPE_WINDOW_SECONDS:
            break
        msg = ev.get("message")
        if not isinstance(msg, str) or not msg.startswith("{"):
            continue
        try:
            other = json.loads(msg)
        except ValueError:
            continue
        if not isinstance(other, dict) or other.get("dataType") != "match":
            continue
        if key and other.get("fixtureKey") == key:
            return True
        o_opp = _norm_team(other.get("opponentName"))
        same_opponent = (o_opp == opp) or bool(opp and o_opp and (opp in o_opp or o_opp in opp))
        if same_opponent and _i(other.get("goalsFor")) == gf and _i(other.get("goalsAgainst")) == ga:
            return True
    return False

def add_match_event(title, msg, payload):
    """Adds a match unless the same match was already reported moments ago. Returns True if added."""
    if is_duplicate_match(payload):
        print(f"  -> (duplicate match ignored: {msg})")
        return False
    add_event(title, msg, json.dumps(payload, ensure_ascii=False))
    return True

# ---------------------------------------------------------------------------
# Project Atlas hot-folder importer
# ---------------------------------------------------------------------------
PROCESSED_FILE = os.path.join(os.path.dirname(os.path.abspath(__file__)), "processed_atlas_folders.json")
STALE_EXPORT_SECONDS = 600       # exports older than this at first start are never replayed
MAX_ATLAS_ATTEMPTS = 3
processed_atlas_folders = set()
atlas_attempts = {}
atlas_first_seen = {}
atlas_deferred_noted = set()

def _load_processed():
    try:
        with open(PROCESSED_FILE, "r", encoding="utf-8") as f:
            processed_atlas_folders.update(json.load(f))
        return True
    except Exception:
        return False

def _save_processed():
    try:
        with open(PROCESSED_FILE, "w", encoding="utf-8") as f:
            json.dump(sorted(processed_atlas_folders)[-500:], f)
    except Exception:
        pass

# ---------------------------------------------------------------------------
# Match log helpers (the same rules as the Live Editor script, so both senders describe a match the same way)
# ---------------------------------------------------------------------------
ATLAS_DEFER_SECONDS = 45   # give the Live Editor script this long to report the match itself first (it knows more)

def pair_substitutions(history):
    """history: rows {"id", "minutes", "pos", "sub"} from the game's record of the club's last match.
    A substitute's minutes plus the minutes of the player he replaced add up to the length of the match, and he takes
    over the same position. Returns (pairs, marks, length): pairs = [{"minute", "out": row, "incoming": row}],
    marks = {player id: {"in_minute"/"out_minute"}}."""
    starters = [h for h in history if h["minutes"] > 0 and not h["sub"]]
    subs = [h for h in history if h["minutes"] > 0 and h["sub"]]
    counts = {}
    for s in starters:
        counts[s["minutes"]] = counts.get(s["minutes"], 0) + 1
    full, most = 0, 0
    for minutes, n in counts.items():          # players who stayed on all game share one number: the most common
        if n > most or (n == most and minutes > full):
            full, most = minutes, n
    outgoing = [s for s in starters if s["minutes"] < full]

    best_score, best_list, best_length = -1, [], full
    if subs and len(subs) <= 8 and outgoing:
        for length in range(full, full + 13):
            found = {"score": 0, "list": []}
            used, current = set(), []

            def search(i, score):
                if i >= len(subs):
                    if score > found["score"]:
                        found["score"], found["list"] = score, list(current)
                    return
                s = subs[i]
                for oi, o in enumerate(outgoing):
                    if oi in used:
                        continue
                    diff = abs(s["minutes"] + o["minutes"] - length)
                    if diff <= 1:
                        same = s["pos"] is None or o["pos"] is None or s["pos"] == o["pos"]
                        used.add(oi)
                        current.append((s, o))
                        search(i + 1, score + 100 - diff - (0 if same else 2))
                        current.pop()
                        used.discard(oi)
                search(i + 1, score)           # this substitute replaced nobody that can be identified

            search(0, 0)
            if found["score"] > best_score:
                best_score, best_list, best_length = found["score"], found["list"], length

    marks, pairs = {}, []
    for s, o in best_list:
        pairs.append({"minute": o["minutes"], "out": o, "incoming": s})
        marks.setdefault(s["id"], {})["in_minute"] = o["minutes"]
    for o in outgoing:                          # also a sending-off or an injury
        marks.setdefault(o["id"], {})["out_minute"] = o["minutes"]
    for s in subs:
        if "in_minute" not in marks.get(s["id"], {}) and best_length > 0:
            marks.setdefault(s["id"], {})["in_minute"] = max(1, best_length - s["minutes"])
    pairs.sort(key=lambda p: (p["minute"], p["out"]["id"]))
    return pairs, marks, best_length

def lineup_matches_export(history, exported_ids):
    """Is the game's last-match record about the same players as the export? exported_ids: set of str ids or None."""
    played = [h for h in history if h["minutes"] > 0]
    if len(played) < 11:
        return False
    if not exported_ids:
        return True
    hit = sum(1 for h in played if str(h["id"]) in exported_ids)
    return hit / len(played) >= 0.7 and hit / len(exported_ids) >= 0.7

# Position ids of the game's last-match record (EA's usual numbering) -> the short names the app sorts the line-up by.
# Bench / reserve ids (28, 29) are not listed: no position is sent for them.
POSITION_LABELS = {
    0: "GK", 1: "SW", 2: "RWB", 3: "RB", 4: "RCB", 5: "CB", 6: "LCB", 7: "LB", 8: "LWB", 9: "RDM", 10: "CDM", 11: "LDM",
    12: "RM", 13: "RCM", 14: "CM", 15: "LCM", 16: "LM", 17: "RAM", 18: "CAM", 19: "LAM", 20: "RF", 21: "CF", 22: "LF",
    23: "RW", 24: "RS", 25: "ST", 26: "LS", 27: "LW",
}

def apply_lineup(player_perfs, history, goals_against=0):
    """Adds what the game's own record knows to the exported player lines (dicts, changed in place): players the export
    left out (a player on loan, say) and every substitution. Returns the list of substitutions for the payload."""
    pairs, marks, _length = pair_substitutions(history)
    by_id = {str(p["playerId"]): p for p in player_perfs}
    for h in history:
        if h["minutes"] <= 0:
            continue
        key = str(h["id"])
        perf = by_id.get(key)
        if perf is None:
            perf = {"playerId": key, "playerName": h.get("name") or f"Player {key}", "rating": 0.0, "goals": 0, "assists": 0,
                    "passes": 0, "passAccuracy": 0, "shots": 0, "shotAccuracy": 0, "saves": 0, "penaltySaves": 0,
                    "tackles": 0, "tacklePercentage": 0, "interceptions": 0, "cleanSheet": goals_against == 0,
                    "yellowCard": False, "redCard": False, "isMotm": False}
            player_perfs.append(perf)
            by_id[key] = perf
        perf["_pos"] = h.get("pos")
        m = marks.get(h["id"])
        if m and "in_minute" in m:
            perf["subInMinute"] = m["in_minute"]
        if m and "out_minute" in m:
            perf["subOutMinute"] = m["out_minute"]
    starters = [p for p in player_perfs if "subInMinute" not in p]
    bench = sorted((p for p in player_perfs if "subInMinute" in p), key=lambda p: (p["subInMinute"], str(p["playerId"])))
    player_perfs[:] = starters + bench
    subs = []
    for p in pairs:
        out_perf, in_perf = by_id.get(str(p["out"]["id"])), by_id.get(str(p["incoming"]["id"]))
        if out_perf and in_perf:
            subs.append({"minute": p["minute"], "playerOutName": out_perf["playerName"], "playerInName": in_perf["playerName"],
                         "playerOutId": str(p["out"]["id"]), "playerInId": str(p["incoming"]["id"])})
    return subs

def estimate_keeper_saves(player_perfs, shots_on_target_against, goals_against):
    """Only when the export has no saves at all: shots on target that were not goals were saved."""
    if sum(p.get("saves", 0) for p in player_perfs) > 0:
        return False
    saved = shots_on_target_against - goals_against
    if saved <= 0:
        return False
    keepers = [p for p in player_perfs if p.get("_pos") == 0]
    if not keepers:
        return False
    keepers[0]["saves"] = saved
    return True

def build_league_table(fixtures, comp_obj_id, max_date_raw=None):
    """fixtures: dicts with competition_object_id, home_team_id, away_team_id, date_raw, completed, home_score, away_score.
    Returns (rows sorted by points, goal difference, goals scored; number of that day's matches still unplayed)."""
    teams, pending = {}, 0

    def team(tid):
        return teams.setdefault(tid, {"team_id": tid, "played": 0, "won": 0, "drawn": 0, "lost": 0, "gf": 0, "ga": 0, "pts": 0})

    for f in fixtures:
        if f["competition_object_id"] != comp_obj_id or f["home_team_id"] <= 0 or f["away_team_id"] <= 0:
            continue
        h, a = team(f["home_team_id"]), team(f["away_team_id"])
        in_time = max_date_raw is None or f["date_raw"] <= max_date_raw
        if f["completed"] and in_time:
            hs, a_s = f["home_score"], f["away_score"]
            if 0 <= hs < 60 and 0 <= a_s < 60:
                h["played"] += 1; a["played"] += 1
                h["gf"] += hs; h["ga"] += a_s; a["gf"] += a_s; a["ga"] += hs
                if hs > a_s:
                    h["won"] += 1; h["pts"] += 3; a["lost"] += 1
                elif hs < a_s:
                    a["won"] += 1; a["pts"] += 3; h["lost"] += 1
                else:
                    h["drawn"] += 1; a["drawn"] += 1; h["pts"] += 1; a["pts"] += 1
        elif not f["completed"] and max_date_raw is not None and f["date_raw"] == max_date_raw:
            pending += 1
    rows = sorted(teams.values(), key=lambda t: (-t["pts"], -(t["gf"] - t["ga"]), -t["gf"], t["team_id"]))
    return rows, pending

def _read_csv_rows(path):
    try:
        with open(path, "r", encoding="utf-8-sig", newline="") as f:
            return list(csv.DictReader(f))
    except Exception:
        return []

def snapshot_lineup(snapshot_dir, team_id):
    """The club's last-match record from a snapshot (last_match_players.csv)."""
    rows = []
    for r in _read_csv_rows(os.path.join(snapshot_dir, "last_match_players.csv")):
        if _i(r.get("team_id")) != team_id:
            continue
        pid = _i(r.get("player_id"))
        if pid <= 0:
            continue
        pos = r.get("actual_position_id")
        rows.append({"id": pid, "minutes": _i(r.get("minutes_played")), "name": r.get("display_name") or "",
                     "pos": _i(pos) if str(pos or "").strip() != "" else None,
                     "sub": (_i(r.get("player_fact_raw")) // 8) % 2 == 1})
    return rows

def snapshot_league_position(snapshot_dir, team_id, comp_obj_id, date_raw):
    """Position in the league on the day of the match, from the snapshot's season fixtures; None when unknown."""
    if not comp_obj_id:
        return None
    fixtures = []
    for r in _read_csv_rows(os.path.join(snapshot_dir, "fixtures.csv")):
        if _i(r.get("competition_object_id")) != comp_obj_id:
            continue
        fixtures.append({"competition_object_id": comp_obj_id, "home_team_id": _i(r.get("home_team_id")),
                         "away_team_id": _i(r.get("away_team_id")), "date_raw": _i(r.get("date_raw")),
                         "completed": str(r.get("is_completed", "")).strip() in ("1", "true", "True"),
                         "home_score": _i(r.get("home_score"), -1), "away_score": _i(r.get("away_score"), -1)})
    rows, _pending = build_league_table(fixtures, comp_obj_id, date_raw or None)
    for i, t in enumerate(rows, start=1):
        if t["team_id"] == team_id and len(rows) >= 2:
            return i
    return None

def _is_league_name(name):
    """A regular league (not a cup, European competition or play-off). 'Championship' is a league: it must not be
    taken for the Champions League."""
    c = str(name or "").lower().replace("championship", "league")
    if any(k in c for k in ("cup", "trophy", "shield", "fa", "uefa", "champions", "conference", "europa", "play-off", "playoff")):
        return False
    return True

def _team_row(rows, idx):
    """The team file's row for team_index idx (found by its column, not by its position in the file)."""
    for r in rows:
        if str(r.get("team_index", "")).strip() != "" and _i(r.get("team_index"), -1) == idx:
            return r
    return rows[idx] if len(rows) > idx else (rows[0] if rows else {})

def import_atlas_folder(folder, folder_id, snapshots_ready):
    part_csv = os.path.join(folder, "16_match_participants_wide.csv")
    teams_csv = os.path.join(folder, "18_match_teams_wide.csv")
    print(f"\n[PROJECT ATLAS] 🎯 New MatchStats export detected: {folder_id}")

    opp_name = "Opponent"
    managed_club_name = "My Club"
    managed_team_id = 0
    current_league_name = "League"
    comp_name = "League Match"
    is_home = True
    managed_score = 0
    opponent_score = 0
    result_badge = "W"
    match_date = time.strftime("%Y-%m-%d")
    league_pos = None
    completed_matches = []
    managed_squad_player_ids = set()
    latest = {}
    latest_snap = None
    league_comp_id = 0
    match_date_raw = 0

    if os.path.exists(snapshots_ready):
        s_folders = [os.path.join(snapshots_ready, d) for d in os.listdir(snapshots_ready) if os.path.isdir(os.path.join(snapshots_ready, d))]
        s_folders.sort(key=os.path.getmtime, reverse=True)
        if s_folders:
            latest_snap = s_folders[0]
            s_matches_csv = os.path.join(latest_snap, "managed_team_matches.csv")
            s_leagues_csv = os.path.join(latest_snap, "team_leagues.csv")
            s_meta_csv = os.path.join(latest_snap, "career_metadata.csv")
            s_rosters_csv = os.path.join(latest_snap, "rosters.csv")

            if os.path.exists(s_meta_csv):
                with open(s_meta_csv, "r", encoding="utf-8-sig") as mf:
                    for row in csv.DictReader(mf):
                        if row.get("key") == "managed_team_id" and row.get("value"):
                            try: managed_team_id = int(row["value"])
                            except: pass
                        if row.get("key") == "managed_team_name" and row.get("value"):
                            managed_club_name = row["value"]

            if os.path.exists(s_matches_csv):
                with open(s_matches_csv, "r", encoding="utf-8-sig") as mf:
                    m_rows = list(csv.DictReader(mf))
                    completed_matches = [r for r in m_rows if str(r.get("is_completed", "")).strip() in ("1", 1)]
                    if completed_matches:
                        latest = completed_matches[-1]
                        managed_club_name = latest.get("managed_team_name") or managed_club_name
                        opp_name = latest.get("opponent_team_name") or opp_name
                        is_home = (latest.get("side", "").upper() == "HOME" or latest.get("managed_team_side", "").upper() == "HOME")
                        managed_score = _i(latest.get("managed_score"))
                        opponent_score = _i(latest.get("opponent_score"))
                        result_badge = latest.get("result") or latest.get("managed_team_result") or ("W" if managed_score > opponent_score else ("D" if managed_score == opponent_score else "L"))
                        match_date = latest.get("date") or match_date
                        match_date_raw = _i(latest.get("date_raw"))
                        if latest.get("managed_team_id"):
                            try: managed_team_id = int(latest["managed_team_id"])
                            except: pass

            # Pick Primary League by max matches_played in team_leagues.csv
            if os.path.exists(s_leagues_csv):
                with open(s_leagues_csv, "r", encoding="utf-8-sig") as lf:
                    best_pld = -1
                    for tl in csv.DictReader(lf):
                        tid = _i(tl.get("team_id"))
                        tname = tl.get("team_name") or ""
                        if tid == managed_team_id or (managed_club_name and managed_club_name.lower() in tname.lower()):
                            # only the league's name is taken from here: the stored table position is refreshed
                            # now and then (it can stay at the pre-season value), so it is not used
                            pld = _i(tl.get("matches_played"))
                            if pld > best_pld:
                                best_pld = pld
                                if tl.get("league_name"): current_league_name = tl["league_name"]

            # Extract Managed Squad player IDs
            if os.path.exists(s_rosters_csv):
                with open(s_rosters_csv, "r", encoding="utf-8-sig") as rf:
                    for r in csv.DictReader(rf):
                        rtid = _i(r.get("team_id"))
                        if rtid == managed_team_id and r.get("player_id"):
                            try: managed_squad_player_ids.add(int(r["player_id"]))
                            except: pass

    # League position from the season's results (the competition of this match if it is a league match, else the
    # competition the club has played most league matches in)
    if latest_snap:
        if latest and _is_league_name(latest.get("competition_name")) and _i(latest.get("competition_object_id")) > 0:
            league_comp_id = _i(latest.get("competition_object_id"))
        else:
            seen = {}
            for m in completed_matches:
                cid = _i(m.get("competition_object_id"))
                if cid > 0 and _is_league_name(m.get("competition_name")):
                    seen[cid] = seen.get(cid, 0) + 1
            if seen:
                league_comp_id = max(seen, key=seen.get)
        try:
            league_pos = snapshot_league_position(latest_snap, managed_team_id, league_comp_id, match_date_raw)
        except Exception:
            league_pos = None

    # Determine Playoff vs Regular League vs Cup
    raw_comp = latest.get("competition_name") or current_league_name
    is_playoff = any(k in raw_comp.lower() for k in ["play-off", "playoff", "promotion"])
    is_regular_league = (not is_playoff) and any(k in raw_comp.lower() for k in [current_league_name.lower(), "league", "championship", "division", "premier"]) and not any(k in raw_comp.lower() for k in ["cup", "fa", "carabao", "trophy"])

    if is_playoff:
        comp_name = current_league_name
        playoff_matches = [m for m in completed_matches if any(k in (m.get("competition_name") or "").lower() for k in ["play-off", "playoff", "promotion"])]
        p_count = max(1, len(playoff_matches))
        if p_count >= 3: stage_val = "Play-off Final"
        elif p_count == 2: stage_val = "Play-off Semi-Final (Leg 2)"
        else: stage_val = "Play-off Semi-Final (Leg 1)"
    elif is_regular_league:
        comp_name = current_league_name
        league_matches = [m for m in completed_matches if any(k in (m.get("competition_name") or "").lower() for k in [current_league_name.lower(), "league", "championship", "division", "premier"]) and not any(k in (m.get("competition_name") or "").lower() for k in ["play-off", "playoff", "cup", "fa", "carabao", "trophy"])]
        stage_val = f"Matchday {max(1, len(league_matches))}"
    else:
        comp_name = raw_comp
        cup_matches = [m for m in completed_matches if m.get("competition_name") == raw_comp]
        c_count = max(1, len(cup_matches))
        if "fa cup" in raw_comp.lower() or "emirates" in raw_comp.lower():
            if c_count >= 6: stage_val = "Final"
            elif c_count == 5: stage_val = "Semi-Final"
            elif c_count == 4: stage_val = "Quarter-Final"
            elif c_count == 3: stage_val = "Round 5"
            elif c_count == 2: stage_val = "Round 4"
            else: stage_val = "Round 3"
        elif "carabao" in raw_comp.lower() or "efl cup" in raw_comp.lower():
            if c_count >= 6: stage_val = "Final"
            elif c_count == 5: stage_val = "Semi-Final"
            elif c_count == 4: stage_val = "Quarter-Final"
            else: stage_val = f"Round {c_count}"
        else:
            stage_val = f"Round {c_count}"

    # Parse participants
    participants = []
    with open(part_csv, "r", encoding="utf-8-sig") as pf:
        participants = list(csv.DictReader(pf))

    history = snapshot_lineup(latest_snap, managed_team_id) if latest_snap else []
    history_names = {h["id"]: h["name"] for h in history if h.get("name")}
    managed_squad_player_ids.update(h["id"] for h in history if h["minutes"] > 0)   # a loaned-in player counts too

    count0 = 0
    count1 = 0
    max_minutes = 0
    for p in participants:
        pid = _i(p.get("player_id"))
        p_team = _i(p.get("team_index"))
        mins = _i(p.get("minutes_played"))
        if mins > max_minutes: max_minutes = mins
        if pid in managed_squad_player_ids:
            if p_team == 0: count0 += 1
            else: count1 += 1

    if count0 > count1:
        managed_team_idx = 0
        opp_team_idx = 1
    elif count1 > count0:
        managed_team_idx = 1
        opp_team_idx = 0
    else:
        managed_team_idx = 0 if is_home else 1
        opp_team_idx = 1 if managed_team_idx == 0 else 0

    # Parse team stats from 18_match_teams_wide.csv
    team_rows = []
    with open(teams_csv, "r", encoding="utf-8-sig") as tf:
        team_rows = list(csv.DictReader(tf))

    m_team = _team_row(team_rows, managed_team_idx)
    o_team = _team_row(team_rows, opp_team_idx)

    # Real Extra Time & Penalties extraction
    is_playoff_leg1 = is_playoff and ("leg 1" in stage_val.lower())
    if is_regular_league or is_playoff_leg1:
        is_extra_time = False
        home_pen = 0
        away_pen = 0
    else:
        m_shootout = _i(m_team.get("shootout_goals"))
        o_shootout = _i(o_team.get("shootout_goals"))
        if m_shootout >= 255 or m_shootout < 0: m_shootout = 0
        if o_shootout >= 255 or o_shootout < 0: o_shootout = 0
        is_extra_time = (max_minutes >= 115) or (m_shootout > 0 or o_shootout > 0)
        home_pen = m_shootout if is_home else o_shootout
        away_pen = o_shootout if is_home else m_shootout

    if home_pen >= 255 or home_pen < 0: home_pen = 0
    if away_pen >= 255 or away_pen < 0: away_pen = 0

    # Man of the match straight from the game's own flag (either team can win it), else the best rating
    def _name_of(p):
        return p.get("player_name") or history_names.get(_i(p.get("player_id"))) or "Player"
    motm_row = None
    for p in participants:
        if str(p.get("man_of_the_match") or "").strip().lower() in ("true", "1"):
            motm_row = p
            break
    if motm_row is None:
        highest_rating = -1.0
        for p in participants:
            r = _f(p.get("game_rating"), 0.0)
            if r > highest_rating:
                highest_rating, motm_row = r, p
    top_player = _name_of(motm_row) if motm_row is not None else ""
    motm_is_opponent = motm_row is not None and _i(motm_row.get("team_index")) != managed_team_idx

    shots_for = _i(m_team.get("shots") or m_team.get("shots_on_target"))
    shots_against = _i(o_team.get("shots") or o_team.get("shots_on_target"))
    xg_f = _f(m_team.get("npk_expected_goals"))
    xg_a = _f(o_team.get("npk_expected_goals"))
    passes_f = _i(m_team.get("passes"))
    passes_a = _i(o_team.get("passes"))
    pass_acc = _i(_pct(m_team.get("pass_accuracy_pct_derived")))
    poss = _i(_pct(m_team.get("possession") or m_team.get("possession_pct")))
    poss_estimated = False
    if poss <= 0:
        # The exporter has no possession figure: the share of all passes is the closest thing it records.
        poss_estimated = True
        if (passes_f + passes_a) > 0:
            poss = max(25, min(75, int(passes_f / (passes_f + passes_a) * 100 + 0.5)))
        else:
            poss = 50

    # Parse real player performances from 16_match_participants_wide.csv
    player_perfs = []
    goal_details = []

    goal_players = []
    opponent_goal_players = []   # who scored for the other side (the export lists every player of both teams)
    for p in participants:
        p_team = _i(p.get("team_index"))
        p_minutes = _f(p.get("minutes_played"), -1.0)
        played = not (max_minutes > 0 and 0 <= p_minutes <= 0)   # export lists the whole matchday squad
        if p_team != managed_team_idx:
            # "" when the export does not name him: the goal then stays "<club> Goal", never a made-up "Player"
            opp_scorer = p.get("player_name") or history_names.get(_i(p.get("player_id"))) or ""
            for _g in range(_i(p.get("goals_derived") or p.get("npk_goals"))):
                opponent_goal_players.append(opp_scorer)
        if p_team == managed_team_idx and played:
            p_name = _name_of(p)
            p_id = p.get("player_id", "")
            p_goals = _i(p.get("goals_derived") or p.get("npk_goals"))
            p_assists = _i(p.get("assists"))
            p_rating = _f(p.get("game_rating"), 6.5)
            p_passes = _i(p.get("passes"))
            p_pacc = _i(_pct(p.get("pass_accuracy_pct_derived")))
            p_shots = _i(p.get("shots") or p_goals)
            p_sacc = min(100, int((_i(p.get("shots_on_target")) / max(1, p_shots)) * 100)) if p_shots > 0 else 0
            p_tackles = _i(p.get("tackles"))
            if _f(p.get("stand_tackle_success_pct_derived"), -1) >= 0:
                p_tpct = _i(_pct(p.get("stand_tackle_success_pct_derived")))
            else:
                # blank for a player with no stand tackles (slide tackles only): use all tackles won / attempted
                if p.get("stand_tackles") is not None or p.get("slide_tackles") is not None:
                    t_att = _f(p.get("stand_tackles")) + _f(p.get("slide_tackles"))
                    t_won = _f(p.get("stand_tackles_won")) + _f(p.get("slide_tackles_won"))
                else:
                    t_att = float(p_tackles)
                    t_won = _f(p.get("tackles_completed"), p_tackles)
                p_tpct = min(100, int(t_won / t_att * 100)) if t_att > 0 else 0
            is_motm = p is motm_row

            player_perfs.append({
                "playerId": str(p_id),
                "playerName": p_name,
                "rating": round(p_rating, 1),
                "goals": p_goals,
                "assists": p_assists,
                "passes": p_passes,
                "passAccuracy": p_pacc,
                "shots": p_shots,
                "shotAccuracy": p_sacc,
                "saves": _i(p.get("saves")),
                "penaltySaves": _i(p.get("penalty_saves")),
                "tackles": p_tackles,
                "tacklePercentage": p_tpct,
                "interceptions": _i(p.get("interceptions")),
                "cleanSheet": (opponent_score == 0),
                "yellowCard": _i(p.get("yellow_card_events")) > 0,
                "redCard": _i(p.get("red_dismissals")) > 0,
                "isMotm": is_motm
            })

            for _g in range(p_goals):
                goal_players.append(p_name)

    # The game's own record of the last match adds who replaced whom and any player the export left out (a loan
    # signing, say). It is only used when it is about the same players as the export.
    substitutions = []
    if history:
        if lineup_matches_export(history, {str(pp["playerId"]) for pp in player_perfs}):
            substitutions = apply_lineup(player_perfs, history, opponent_score)
        else:
            print("  (the game's last-match record is about different players: substitutions are not sent)")
    if estimate_keeper_saves(player_perfs, _i(o_team.get("shots_on_target")), opponent_score):
        print("  (the export has no saves: the goalkeeper's saves are worked out from shots on target)")
    for pp in player_perfs:
        label = POSITION_LABELS.get(_i(pp["_pos"])) if pp.get("_pos") is not None else None
        if label:
            pp["position"] = label
        pp.pop("_pos", None)

    # The export has no goal minutes: spread them evenly over the match and flag them as estimated.
    for k, scorer in enumerate(goal_players, start=1):
        goal_details.append({
            "playerName": scorer,
            "minute": max(1, min(90, int(90 * k / (len(goal_players) + 1)))),
            "isOpponent": False,
            "minuteEstimated": True
        })
    for og in range(1, opponent_score + 1):
        # the export names the other side's scorers; a goal it cannot name (an own goal, say) stays "<club> Goal"
        goal_details.append({
            "playerName": (opponent_goal_players[og - 1] if og <= len(opponent_goal_players) else "") or f"{opp_name} Goal",
            "minute": max(1, min(90, int(90 * og / (opponent_score + 1)))),
            "isOpponent": True,
            "minuteEstimated": True
        })

    loc_symbol = "vs" if is_home else "@"
    et_str = f" (Pens: {home_pen}-{away_pen})" if (home_pen > 0 or away_pen > 0) else (" (A.E.T.)" if is_extra_time else "")
    msg = f"Full Time: {managed_club_name} {loc_symbol} {managed_score} - {opponent_score} {opp_name} ({comp_name} - {stage_val}){et_str}"
    match_payload = {
        "dataType": "match",
        "title": "Live Match Final",
        "message": msg,
        "opponentName": opp_name,
        "isHome": is_home,
        "goalsFor": managed_score,
        "goalsAgainst": opponent_score,
        "resultBadge": result_badge,
        "date": match_date,
        "competition": comp_name,
        "stage": stage_val,
        "isExtraTime": is_extra_time,
        "homePenalties": home_pen,
        "awayPenalties": away_pen,
        "shotsFor": shots_for,
        "shotsAgainst": shots_against,
        "possession": poss,
        "possessionEstimated": poss_estimated,
        "passingAccuracy": pass_acc,
        "expectedGoals": round(xg_f, 2),
        "expectedGoalsAgainst": round(xg_a, 2),
        "motmName": top_player,
        "motmIsOpponent": motm_is_opponent,
        "leaguePosition": league_pos if (league_pos and league_pos > 0) else None,
        "detailSource": "atlas",
        "playerPerformances": player_perfs,
        "substitutions": substitutions,
        "goalDetails": goal_details
    }

    add_match_event("Live Match Final", msg, match_payload)
    print(f"  -> ⚽ Imported Match: {msg}")
    print(f"     xG: {round(xg_f, 2)} - {round(xg_a, 2)} | MOTM: {top_player} | Lineup: {len(player_perfs)} players")

def atlas_roots():
    """Folders the Lua scripts / Atlas exporters write to. They use %USERPROFILE%\\Documents\\FC26 Export, but Windows can
    redirect Documents to OneDrive, so that location is watched as well."""
    home = os.path.expanduser("~")
    cands = [os.path.join(home, "Documents", "FC26 Export")]
    for od in (os.environ.get("OneDrive"), os.environ.get("OneDriveConsumer"), os.path.join(home, "OneDrive")):
        if od:
            cands.append(os.path.join(od, "Documents", "FC26 Export"))
    roots = []
    for c in cands:
        if c not in roots and os.path.isdir(c):
            roots.append(c)
    return roots

def watch_atlas_exports():
    have_state = _load_processed()
    first_scan = True
    while True:
        try:
            for docs in atlas_roots():
                match_stats_ready = os.path.join(docs, "MatchStats", "ready")
                snapshots_ready = os.path.join(docs, "Snapshots", "ready")
                if not os.path.exists(match_stats_ready):
                    continue

                folders = [os.path.join(match_stats_ready, d) for d in os.listdir(match_stats_ready)
                           if os.path.isdir(os.path.join(match_stats_ready, d))]
                folders.sort(key=os.path.getmtime, reverse=True)

                if first_scan and not have_state:
                    # First start: exports from earlier matches must not be replayed into the app.
                    cutoff = time.time() - STALE_EXPORT_SECONDS
                    for f in folders:
                        if os.path.getmtime(f) < cutoff:
                            processed_atlas_folders.add(os.path.basename(f))
                    _save_processed()

                for folder in folders:
                    folder_id = os.path.basename(folder)
                    if folder_id in processed_atlas_folders:
                        continue

                    ready_file = os.path.join(folder, "MATCH_READY")
                    alt_ready = os.path.join(folder, "READY_FOR_MANUAL_ZIP_AND_UPLOAD.txt")
                    part_csv = os.path.join(folder, "16_match_participants_wide.csv")
                    teams_csv = os.path.join(folder, "18_match_teams_wide.csv")

                    if (os.path.exists(ready_file) or os.path.exists(alt_ready)) and os.path.exists(part_csv) and os.path.exists(teams_csv):
                        first_seen = atlas_first_seen.setdefault(folder_id, time.time())
                        if time.time() - first_seen < ATLAS_DEFER_SECONDS:
                            if folder_id not in atlas_deferred_noted:
                                atlas_deferred_noted.add(folder_id)
                                print(f"[PROJECT ATLAS] New export {folder_id}: waiting up to {ATLAS_DEFER_SECONDS} s for the "
                                      "Live Editor script to report the match itself (it adds more detail).")
                            continue
                        try:
                            import_atlas_folder(folder, folder_id, snapshots_ready)
                            processed_atlas_folders.add(folder_id)
                            _save_processed()
                        except Exception:
                            n = atlas_attempts.get(folder_id, 0) + 1
                            atlas_attempts[folder_id] = n
                            print(f"[PROJECT ATLAS] ⚠️ Could not import {folder_id} (attempt {n}/{MAX_ATLAS_ATTEMPTS}):")
                            traceback.print_exc()
                            if n >= MAX_ATLAS_ATTEMPTS:
                                print(f"[PROJECT ATLAS] Giving up on {folder_id}; the Lua script still sends the match directly.")
                                processed_atlas_folders.add(folder_id)
                                _save_processed()
            first_scan = False
        except Exception:
            print("[PROJECT ATLAS] watcher error:")
            traceback.print_exc()
        time.sleep(2)

OUTBOX_SETTLE_SECONDS = 1
OUTBOX_BAD_AFTER_SECONDS = 90

def _write_outbox_log(root, ident, text):
    try:
        with open(os.path.join(root, "bridge_send_log.txt"), "a", encoding="utf-8") as f:
            f.write(f"{time.strftime('%Y-%m-%d %H:%M:%S')} [{ident}] {text}\n")
    except Exception:
        pass

def check_outbox_once(roots=None, now=None, beat=True):
    """The Live Editor script saves every transfer and match as a small .json file in Documents\\FC26 Export\\outbox.
    Reading those files here means the script does not have to start a helper program for each one (every helper
    program flashes a command window on top of the game). bridge_watching.txt tells the script that this bridge is
    reading the folder, so it only starts its own sender if a file is still waiting after a while."""
    now = time.time() if now is None else now
    for root in (atlas_roots() if roots is None else roots):
        if beat:
            try:
                with open(os.path.join(root, "bridge_watching.txt"), "w") as f:
                    f.write(str(int(now)))
            except Exception:
                pass
        outbox = os.path.join(root, "outbox")
        if not os.path.isdir(outbox):
            continue
        entries = []
        for name in os.listdir(outbox):
            if name.lower().endswith(".json"):
                path = os.path.join(outbox, name)
                try:
                    entries.append((os.path.getmtime(path), name, path))
                except OSError:
                    pass
        for mtime, name, path in sorted(entries):
            if path in _outbox_handled:
                try:
                    os.remove(path)                      # stored earlier but could not be deleted then
                except OSError:
                    pass
                continue
            age = now - mtime
            if age < OUTBOX_SETTLE_SECONDS:
                continue                                  # the script may still be writing it
            try:
                with open(path, "rb") as f:
                    raw = f.read()
            except OSError:
                continue                                  # locked: try again on the next pass
            try:
                text = raw.decode("utf-8")
            except UnicodeDecodeError:
                text = raw.decode("latin-1")
            ident = name[:-5]
            status, reply = process_payload_text(text)
            if status == 200:
                _outbox_handled.add(path)
                try:
                    os.remove(path)
                    _outbox_handled.discard(path)
                except OSError:
                    pass
                _write_outbox_log(root, ident, "HTTP 200 (read from the outbox folder by the bridge)")
            elif age > OUTBOX_BAD_AFTER_SECONDS:
                try:
                    os.replace(path, path + ".bad")       # unreadable after a minute and a half: not just half-written
                except OSError:
                    pass
                _write_outbox_log(root, ident, f"REFUSED ({reply}) - renamed to .bad")

_outbox_handled = set()

def watch_outbox():
    last_beat = 0.0
    while True:
        try:
            now = time.time()
            beat = now - last_beat >= 5
            check_outbox_once(now=now, beat=beat)
            if beat:
                last_beat = now
        except Exception:
            print("[OUTBOX] watcher error:")
            traceback.print_exc()
        time.sleep(1)

class _QuietStream:
    """stdout / stderr that never raise. Started without a window, the bridge's output can lose its reader (on Linux and
    macOS when the starting program exits); a failed print must never break a request or stop a watcher thread."""

    def __init__(self, stream):
        self._stream = stream

    def write(self, text):
        try:
            return self._stream.write(text)
        except (OSError, ValueError):
            return len(text)

    def flush(self):
        try:
            self._stream.flush()
        except (OSError, ValueError):
            pass

    def __getattr__(self, name):
        return getattr(self._stream, name)


if __name__ == "__main__":
    import sys
    sys.stdout = _QuietStream(sys.stdout)
    sys.stderr = _QuietStream(sys.stderr)
    local_ip = get_local_ip()
    port = LOCAL_PORT
    print("===================================================")
    print(f" EA SPORTS FC TELEMETRY BRIDGE {BRIDGE_VERSION} (Python)")
    code = pairing_code()
    print(f" ⭐ ENTER IN YOUR ANDROID APP:")
    print(f"    http://{local_ip}:{port}")
    if not code:
        print(" ⚠️ No pairing code could be made, so no phone can connect. Check that the folder above can be written to.")
    elif os.environ.get("FC26_HIDDEN"):
        print(" Pairing code: shown by FC26_Launcher.bat and on the dashboard (never written to this log).")
    else:
        print(f"    Pairing code: {format_code(code)}")
    print(f" Web Dashboard: http://localhost:{port}")
    print(" Only a phone that knows the pairing code can read from the bridge (and only events, status and 'send the last match again').")
    print(" Everything else works from this PC only.")
    print(" Self check: open the web dashboard above.")
    print(" Keep this window open while playing!")
    print("===================================================")

    class ReusableServer(socketserver.ThreadingTCPServer):
        allow_reuse_address = True
        daemon_threads = True
        
    try:
        threading.Thread(target=watch_atlas_exports, daemon=True).start()
        threading.Thread(target=watch_outbox, daemon=True).start()
        print("🟢 Outbox folder watcher active (the Live Editor script saves payloads there)")
        print("🟢 Project Atlas hot-folder auto-importer active (Documents\\FC26 Export)")
        with ReusableServer(("", port), TelemetryHandler) as httpd:
            print(f"🟢 Python Bridge actively listening on Port {port}")
            httpd.serve_forever()
    except KeyboardInterrupt:
        print("\nShutting down bridge...")
    except Exception as e:
        print(f"⚠️ Port {port} listener error: {e}")
