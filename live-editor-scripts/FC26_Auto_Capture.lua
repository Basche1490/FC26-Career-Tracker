-- FC26 AutoCapture v2.12.0 (Precision Live Telemetry & Atlas Tracker)
-- Complete support for EFL Championship, Premier League, FA Cup, Carabao Cup, Play-offs.
-- Gathers REAL in-game data: Real League Position, Real Lineups, Real Goal Scorers, Real MOTM.
-- Strict League Rules: League matches NEVER have extra time or penalty shootouts.
-- Accurate Matchday XX calculation for all league matches; dedicated tournament rounds for cups.
-- Safe session lifecycle: No phantom beaming on startup; immediate beam on actual match completion.
-- v2.6: complete match log (substitutions, loan players, saves, opposing man of the match, league position from
-- the real results, positions, the other side's scorers); no flashing command windows.
-- v2.7: no command windows at start-up or after a match (all folders in one command, spare folders for the exporter),
-- other side's scorers named from the players' goal tallies, loan-outs noticed while the game runs.
-- v2.8: reports its own health to the bridge and the app (script_status.json), answers a "re-send the last match"
-- request from the app, and sends the real contract and loan end dates of the squad.
-- v2.9: sends a "career" report: league tables (with every club's form), fixtures still to play, the league's top
-- scorers, shirt numbers, squad roles, wages, ratings, injuries and suspensions, the team sheet, the club's honours and
-- stadium, the transfer budget, the European club ranking and, in June and July, last season's winners.
-- v2.10: never opens a command window while you play: payloads wait in the outbox for the bridge (which reads the
-- folder itself) instead of being sent by a program, the Atlas export is found through MatchStats\last_export.txt
-- instead of a folder listing, and the stock of spare folders lasts well over 100 matches. FC26_AutoCapture_SendNow()
-- sends the outbox by hand. The career report also carries the trophies the club wins, every player's attributes and
-- form, the squad's league statistics and the club's prestige.

local MEMORY = require "imports/core/memory"
require "imports/other/helpers"
require "imports/services/enums"
require "imports/career_mode/helpers"

assert(IsInCM(), "FC26 AutoCapture must be executed while a career is loaded")

local VERSION = "2.12.0"
local SCRIPT_INSTANCE = "FC26_AUTOCAP_" .. tostring(os.time()) .. "_" .. tostring(math.random(10000, 99999))
_G["FC26_AUTOCAPTURE_CURRENT_INSTANCE"] = SCRIPT_INSTANCE

if not _G["FC26_BEAMED_FIXTURE_KEYS"] then
    _G["FC26_BEAMED_FIXTURE_KEYS"] = {}
end

-- Keys of transfers already sent are loaded from beamed_transfers.txt (so a restart does not repeat them)
_G["FC26_BEAMED_TRANSFERS"] = {}

local CHECK_INTERVAL_SECONDS = 2

local state = nil -- Forward declaration for all helper functions

local function trim(value)
    local text = tostring(value or "")
    text = text:gsub("^\239\187\191", "")
    text = text:gsub("^%s+", "")
    text = text:gsub("%s+$", "")
    return text -- a single value: gsub's second return (the count) must never leak into table.insert()/string.format()
end

-- Escape a Lua string so it can sit inside a JSON "..." value (names with quotes, backslashes, accents...).
local function jstr(value)
    local s = tostring(value == nil and "" or value)
    s = s:gsub('[%c"\\]', function(c)
        if c == '"' then return '\\"' end
        if c == "\\" then return "\\\\" end
        if c == "\n" then return "\\n" end
        if c == "\r" then return "\\r" end
        if c == "\t" then return "\\t" end
        return string.format("\\u%04x", c:byte())
    end)
    return s
end

-- Lua 5.3 raises an error for string.format("%d", 86.4). Every integer field goes through here.
local function to_int(value, default)
    local n = tonumber(value)
    if n == nil or n ~= n or n == math.huge or n == -math.huge then return default or 0 end
    if n >= 0 then return math.floor(n + 0.5) end
    return -math.floor(-n + 0.5)
end

local function to_num(value, default)
    local n = tonumber(value)
    if n == nil or n ~= n or n == math.huge or n == -math.huge then return default or 0 end
    return n
end

local function file_exists(path)
    local f = io.open(path, "rb")
    if f then f:close() return true end
    return false
end

local user_profile = os.getenv("USERPROFILE") or ""
local function win_path(p)
    local converted = tostring(p or ""):gsub("/", string.char(92))
    return converted -- one value only (gsub's match count must not leak into string.format arguments)
end

local COMMUNITY_ROOT = (user_profile ~= "" and user_profile or ".") .. "/Documents/FC26 Export"
local EXPORT_ROOT = COMMUNITY_ROOT .. "/Snapshots"
local MATCH_STATS_ROOT = COMMUNITY_ROOT .. "/MatchStats"
local OUTBOX_DIR = COMMUNITY_ROOT .. "/outbox"

local get_current_career_date -- defined further down; the match code above it needs it

-- What the script tells the bridge (and through it the app) about itself: see script_status.json further down.
local status = { started = os.time(), last_match = 0, last_transfer = 0, last_squad = 0, scans = 0, written = 0,
                 squad_dirty = true, resend = nil }

-- BEGIN quiet_exec (this exact block is also run by tests/lua/test_transfer_logic.lua)
-- Every program a script starts through os.execute / io.popen opens a console window on top of the game for a moment.
-- This block keeps that to the minimum: folders that already exist are never "created" again, folder contents are read
-- through Live Editor's own functions when it has them, and the few programs that still have to run are started
-- hidden when Live Editor allows it (shellExecute with SW_HIDE). It also covers the Project Atlas exporter, which makes
-- folders with os.execute: its calls are answered here without starting anything when the folder is already there, and
-- a new folder is made by renaming one of a stock of spare folders (created together, in a single program start).
local QUIET = { hidden = nil, dir_api = false, file_api = false, started = 0, windows = 0, notes = {} }
QUIET.real_execute = rawget(_G, "FC26_REAL_OS_EXECUTE") or os.execute
rawset(_G, "FC26_REAL_OS_EXECUTE", QUIET.real_execute)
QUIET.counter = 0

function QUIET.comspec()
    local c = os.getenv("ComSpec")
    if c == nil or c == "" then c = "cmd.exe" end
    return c
end

-- true when the folder exists (decided by the file system alone, no program is started)
function QUIET.dir_exists(path)
    path = tostring(path or ""):gsub("[/\\]+$", "")
    if path == "" then return false end
    local ok, _, code = os.rename(path, path) -- renaming a folder to itself changes nothing; only a missing folder fails with "no such file"
    if ok or code == 13 or code == 17 then return true end
    local probe = path .. "/.fc26_probe"
    local f = io.open(probe, "wb")
    if f then
        f:close()
        pcall(os.remove, probe)
        return true
    end
    return false
end

function QUIET.sleep_ms(ms)
    local fn = rawget(_G, "sleep")
    if type(fn) == "function" and pcall(fn, ms) then return end
    local t = os.clock()
    while os.clock() - t < ms / 1000 do end
end

function QUIET.wait_until(fn, max_ms)
    local waited = 0
    while true do
        if fn() then return true end
        if waited >= max_ms then return false end
        QUIET.sleep_ms(25)
        waited = waited + 25
    end
end

-- Starts a program with its window hidden. Returns false when Live Editor cannot do that (the caller then decides
-- whether a visible window is acceptable).
function QUIET.hidden_run(program, params)
    if QUIET.hidden == false then return false end
    local fn = rawget(_G, "shellExecute")
    if type(fn) ~= "function" then return false end
    local ok = pcall(fn, program, params or "", "", 0)
    if ok then QUIET.started = QUIET.started + 1 end
    return ok
end

-- Runs "cmd /c <inner>" hidden and waits until it has finished (a "done" marker is written at the end). When Live Editor
-- cannot start a program hidden, nothing is run unless allow_visible is true (a visible start flashes a console window).
function QUIET.run_and_wait(inner, max_ms, allow_visible)
    QUIET.counter = QUIET.counter + 1
    local temp_dir = os.getenv("TEMP") or os.getenv("TMP") or "."
    local done = temp_dir .. "/fc26_done_" .. tostring(os.time()) .. "_" .. QUIET.counter .. ".txt"
    local full = inner .. ' & echo x> "' .. win_path(done) .. '"'
    if QUIET.hidden ~= false and QUIET.hidden_run(QUIET.comspec(), '/c ' .. full) then
        if QUIET.wait_until(function() return file_exists(done) end, max_ms or 4000) then
            pcall(os.remove, done)
            return true
        end
    end
    if not allow_visible then return false end
    QUIET.windows = QUIET.windows + 1
    pcall(QUIET.real_execute, 'cmd /c ' .. full) -- visible, but it finishes before it returns
    local ok = file_exists(done)
    if ok then pcall(os.remove, done) end
    return ok
end

-- Spare folders: empty folders made ahead of time in ONE program start. A folder that is needed later (the Atlas
-- exporter makes a new one for every export) is simply renamed into place, which starts no program and shows no window.
QUIET.pool_root = nil -- set by the script: the folder that holds the spare folders (nil = no spare folders)
QUIET.pool_size = 240 -- about two are used per match (snapshot + match stats), so this lasts for well over 100 matches
QUIET.pool_low = 140  -- topped up at script start when fewer than this are left (a long session never runs out)
QUIET.moved = 0       -- folders that were created by moving a spare into place
QUIET.batches = 0     -- program starts used to create several folders at once

function QUIET.parent_of(path)
    path = tostring(path or ""):gsub("[/\\]+$", "")
    return path:match("^(.*)[/\\][^/\\]+$")
end

function QUIET.spare_path(index)
    return string.format("%s/s%03d", QUIET.pool_root, index)
end

-- The spare folders that exist right now (list of paths) and the paths of the ones that are missing.
function QUIET.scan_pool()
    local ready, missing = {}, {}
    if not QUIET.pool_root then return ready, missing end
    for i = 1, QUIET.pool_size do
        local spare = QUIET.spare_path(i)
        if QUIET.dir_exists(spare) then ready[#ready + 1] = spare else missing[#missing + 1] = spare end
    end
    return ready, missing
end

-- Moves a spare folder to [path] (its parent folder must exist). false when there is none or it did not work.
function QUIET.take_spare(path)
    if not QUIET.pool_root then return false end
    if QUIET.spares == nil then QUIET.spares = QUIET.scan_pool() end
    local misses = 0
    while #QUIET.spares > 0 and misses < 3 do
        local spare = QUIET.spares[#QUIET.spares]
        table.remove(QUIET.spares)
        local ok = os.rename(spare, path)
        if ok then
            QUIET.moved = QUIET.moved + 1
            return true
        end
        if QUIET.dir_exists(spare) then
            -- the spare is fine, so it is the target that cannot be used: keep the spare for the next folder
            QUIET.spares[#QUIET.spares + 1] = spare
            return false
        end
        misses = misses + 1 -- the spare itself is gone: try the next one
    end
    QUIET.spares = nil -- the list was out of date (folders removed by hand): look again next time
    return false
end

-- Creates several folders with ONE program start: hidden when Live Editor allows it, otherwise one visible window
-- for all of them. Spare folders are named relative to their own folder ("cd /d" there, then "mkdir s001 s002 ..."),
-- which keeps even a full stock inside one short command. Returns true when every folder exists afterwards.
function QUIET.batch_mkdir(paths)
    local others, spares = {}, {}
    local pool = QUIET.pool_root and (QUIET.pool_root:gsub("[/\\]+$", "")) or nil
    for _, p in ipairs(paths) do
        if not QUIET.dir_exists(p) then
            local parent, name = tostring(p):match("^(.*)[/\\]([^/\\]+)$")
            if pool and parent == pool and name:match("^s%d+$") then spares[#spares + 1] = { path = p, name = name }
            else others[#others + 1] = p end
        end
    end
    -- the commands: plain "mkdir" lists of at most ~6000 characters, the spare folders appended to the first one
    local commands, checks = {}, {}
    local first = 1
    repeat
        local parts, len, last, list = {}, 0, first, {}
        while last <= #others and (len < 6000 or last == first) do
            local quoted = '"' .. win_path(others[last]) .. '"'
            parts[#parts + 1] = quoted
            list[#list + 1] = others[last]
            len = len + #quoted + 1
            last = last + 1
        end
        if #commands == 0 and #spares > 0 then
            local names = {}
            for _, sp in ipairs(spares) do
                names[#names + 1] = sp.name
                list[#list + 1] = sp.path
            end
            parts[#parts + 1] = '"' .. win_path(pool) .. '"'
            commands[#commands + 1] = "mkdir " .. table.concat(parts, " ") .. ' 2>nul & cd /d "' .. win_path(pool)
                .. '" && mkdir ' .. table.concat(names, " ")
            checks[#commands] = list
        elseif #parts > 0 then
            commands[#commands + 1] = "mkdir " .. table.concat(parts, " ")
            checks[#commands] = list
        end
        first = last
    until first > #others
    local all_ok = true
    for i, command in ipairs(commands) do
        local list = checks[i]
        local function all_there()
            for _, p in ipairs(list) do
                if not QUIET.dir_exists(p) then return false end
            end
            return true
        end
        QUIET.batches = QUIET.batches + 1
        local done = false
        if QUIET.hidden ~= false and QUIET.hidden_run(QUIET.comspec(), "/c " .. command) then
            done = QUIET.wait_until(all_there, 4000)
        end
        if not done then
            QUIET.windows = QUIET.windows + 1
            pcall(QUIET.real_execute, command)
            done = all_there()
        end
        all_ok = all_ok and done
    end
    return all_ok
end

-- First run on a PC: every folder the scripts need plus the spare folders, in ONE program start (nothing is started
-- when they all exist).
function QUIET.prepare(folders)
    local wanted = {}
    for _, p in ipairs(folders) do
        if not QUIET.dir_exists(p) then wanted[#wanted + 1] = p end
    end
    if QUIET.pool_root then
        local ready, missing = QUIET.scan_pool()
        if #ready < QUIET.pool_low then
            for _, p in ipairs(missing) do wanted[#wanted + 1] = p end
        end
    end
    QUIET.spares = nil
    if #wanted == 0 then return true end
    return QUIET.batch_mkdir(wanted)
end

-- Creates a folder (and its parents). Nothing is started when it already exists, and no window opens while a spare
-- folder is left to move into place.
function QUIET.make_dir(path, depth)
    if QUIET.dir_exists(path) then return true end
    depth = depth or 0
    local batch = { path }
    if QUIET.pool_root then
        local parent = QUIET.parent_of(path)
        local parent_ok = parent == nil or not parent:find("[/\\]") -- a drive root is always there
            or QUIET.dir_exists(parent) or (depth < 8 and QUIET.make_dir(parent, depth + 1))
        if parent_ok and QUIET.take_spare(path) then return true end
        -- none left: make this folder and new spare folders together, in one go
        local _, missing = QUIET.scan_pool()
        for _, p in ipairs(missing) do batch[#batch + 1] = p end
        QUIET.spares = nil
    end
    QUIET.batch_mkdir(batch)
    return QUIET.dir_exists(path)
end

function QUIET.base_name(entry)
    local name = tostring(entry or ""):gsub("[/\\]+$", ""):match("([^/\\]+)$")
    return name
end

-- Reads a listing through a Live Editor function (silent). nil = not available / not trusted.
function QUIET.api_list(fn_name, trusted, path, extra)
    local fn = rawget(_G, fn_name)
    if not trusted or type(fn) ~= "function" then return nil end
    local ok, res = pcall(fn, win_path(path), extra)
    if not ok or type(res) ~= "table" then return nil end
    local names = {}
    for _, entry in ipairs(res) do
        local name = QUIET.base_name(entry)
        if name and name ~= "." and name ~= ".." then names[#names + 1] = name end
    end
    return names
end

-- True when a folder can be listed without a console window (Live Editor's own function, or a hidden program start).
function QUIET.can_list(api_ok)
    return api_ok or QUIET.hidden == true
end

-- Names via "dir", only ever started hidden: when that is not possible the list is empty (and the log says so once).
function QUIET.shell_list(dir_command)
    if QUIET.hidden ~= true then
        if not QUIET.listing_note then
            QUIET.listing_note = true
            QUIET.notes[#QUIET.notes + 1] = "folder listings are skipped: Live Editor cannot list a folder without a console window"
        end
        return {}
    end
    QUIET.counter = QUIET.counter + 1
    local temp_dir = os.getenv("TEMP") or os.getenv("TMP") or "."
    local out = temp_dir .. "/fc26_list_" .. tostring(os.time()) .. "_" .. QUIET.counter .. ".txt"
    QUIET.run_and_wait(string.format('%s > "%s" 2>nul', dir_command, win_path(out)), 4000)
    local names = {}
    local tf = io.open(out, "r")
    if tf then
        for line in tf:lines() do
            local name = trim(line)
            if name ~= "" then names[#names + 1] = name end
        end
        tf:close()
        pcall(os.remove, out)
    end
    return names
end

-- Sub-folder names of a folder; newest_first only matters for the "dir" fallback (the API gives no dates, so names
-- are sorted descending, which is newest first for the date-stamped export folders).
function QUIET.list_dirs(path, newest_first)
    if not QUIET.dir_exists(path) then return {} end -- nothing to list: no need to ask Windows
    local names = QUIET.api_list("getDirectoryList", QUIET.dir_api, path)
    if names then
        if newest_first then table.sort(names, function(a, b) return a > b end) end
        return names
    end
    return QUIET.shell_list(string.format('dir /b /ad%s "%s"', newest_first and " /o:-d" or "", win_path(path)))
end

-- File names (with the given extension, e.g. ".json") in a folder.
function QUIET.list_files(path, extension)
    if not QUIET.dir_exists(path) then return {} end
    local names = QUIET.api_list("getFileList", QUIET.file_api, path, "*" .. extension)
    if not names then
        names = QUIET.shell_list(string.format('dir /b "%s"', win_path(path .. "/*" .. extension)))
    end
    local out = {}
    for _, name in ipairs(names) do
        if name:lower():sub(-#extension) == extension then out[#out + 1] = name end
    end
    return out
end

-- Starts a .bat file: hidden when Live Editor allows it, otherwise the old minimized window.
function QUIET.run_bat(bat_path)
    local win = win_path(bat_path)
    if QUIET.hidden ~= false and QUIET.hidden_run(QUIET.comspec(), '/c "' .. win .. '"') then return true, true end
    QUIET.windows = QUIET.windows + 1
    pcall(QUIET.real_execute, 'start /min "" cmd /c "' .. win .. '"')
    return true, false
end

-- One-time check of what this Live Editor can do. Costs nothing when it cannot do anything special.
function QUIET.probe(folder)
    QUIET.make_dir(folder)
    local marker = folder .. "/quiet_probe.txt"
    local f = io.open(marker, "wb")
    if f then f:write("probe") f:close() end

    if QUIET.hidden == nil and type(rawget(_G, "shellExecute")) == "function" then
        local test = folder .. "/quiet_hidden_test.txt"
        pcall(os.remove, test)
        if QUIET.hidden_run(QUIET.comspec(), '/c echo ok> "' .. win_path(test) .. '"') then
            QUIET.hidden = QUIET.wait_until(function() return file_exists(test) end, 2500)
        else
            QUIET.hidden = false
        end
        pcall(os.remove, test)
    elseif QUIET.hidden == nil then
        QUIET.hidden = false
    end

    local dirs = QUIET.api_list("getDirectoryList", true, folder)
    if dirs then
        for _, name in ipairs(dirs) do
            if name:lower() == "outbox" then QUIET.dir_api = true end
        end
    end
    local files = QUIET.api_list("getFileList", true, folder, "*.txt")
    if files then
        for _, name in ipairs(files) do
            if name:lower() == "quiet_probe.txt" then QUIET.file_api = true end
        end
    end
    pcall(os.remove, marker)
end

function QUIET.describe()
    if QUIET.pool_root and QUIET.spares == nil then QUIET.spares = QUIET.scan_pool() end
    local function listing(api)
        if api then return "Live Editor function" end
        return QUIET.hidden and "hidden dir command" or "skipped (would open a window)"
    end
    return string.format("hidden program start: %s | folder listing: %s / %s | spare folders: %d ready, %d used | visible windows opened so far: %d",
        QUIET.hidden and "yes" or "no (Live Editor has no working shellExecute)",
        listing(QUIET.dir_api), listing(QUIET.file_api),
        #(QUIET.spares or {}), QUIET.moved, QUIET.windows)
end

-- The Atlas exporter (FC26_AutoCapture_v1.1.1.lua) makes its folders through this too when it finds it here.
rawset(_G, "FC26_QUIET", QUIET)

-- Answer the Atlas exporter's "create this folder" commands here (nothing is started when the folder exists).
-- Everything else goes to the real os.execute unchanged.
os.execute = function(command, ...)
    if type(command) == "string" then
        local path = command:match('^if not exist "([^"]+)" mkdir "[^"]+"$')
            or command:match('^cmd%.exe /C if not exist "([^"]+)" mkdir "[^"]+"$')
        if path then
            if QUIET.make_dir(path) then return true, "exit", 0 end
            return nil, "exit", 1
        end
    end
    return QUIET.real_execute(command, ...)
end
-- END quiet_exec

-- Folders are only created when they are really missing (the first run on a PC); see the quiet_exec block above.
-- The folders this script needs are made in ONE go, together with a stock of spare folders: the folders the Atlas
-- exporter makes later (a new one for every export, plus its own "incoming" / "ready" folders on the first run) are
-- moved into place from that stock, so no command window opens for them. (The exporter's own folders are NOT made
-- here: the script reads "MatchStats/ready exists" as "an export has been made on this PC before".)
pcall(function()
    QUIET.pool_root = COMMUNITY_ROOT .. "/spare_folders"
    QUIET.prepare({ COMMUNITY_ROOT, OUTBOX_DIR, EXPORT_ROOT, MATCH_STATS_ROOT })
    QUIET.probe(COMMUNITY_ROOT)
end)

local function log_monitor(msg)
    local s = "[FC26 AutoCapture] " .. tostring(msg)
    pcall(print, s)
    pcall(function()
        local logPath = COMMUNITY_ROOT .. "/auto_capture_monitor.log"
        local f = io.open(logPath, "a")
        if f then
            f:write(os.date("%Y-%m-%d %H:%M:%S") .. " " .. s .. "\n")
            f:close()
        end
    end)
end

log_monitor("Quiet mode - " .. QUIET.describe())

local function is_positive_id(value)
    local number = tonumber(value)
    return number ~= nil and number > 0 and number < 4294967295
end

local function safe_team_name(team_id)
    local tid = tonumber(team_id) or 0
    if not is_positive_id(tid) then return "" end
    local ok, name = pcall(function() return GetTeamName(tid) end)
    if ok and name and tostring(name) ~= "" then return tostring(name) end
    if tid == 115486 then return "Torquay United" end
    return "Team " .. tostring(tid)
end

local function safe_player_name(player_id)
    local pid = tonumber(player_id) or 0
    if not is_positive_id(pid) then return "Player" end
    local ok, name = pcall(function() return GetPlayerName(pid) end)
    if ok and name and tostring(name) ~= "" then return tostring(name) end
    return "Player " .. tostring(pid)
end

local function safe_position_name(pos_id)
    local pid = tonumber(pos_id) or 0
    local ok, name = pcall(function() return GetPlayerPrimaryPositionName(pid) end)
    if ok and name and tostring(name) ~= "" and tostring(name) ~= "INVALID" then
        return tostring(name)
    end
    return "CM"
end

local function safe_competition_name(comp_obj_id)
    local number = tonumber(comp_obj_id) or 0
    if number <= 0 then return "EFL Championship" end
    local ok, name = pcall(function() return GetCompetitionNameByObjID(number) end)
    if ok and name and tostring(name) ~= "" then return tostring(name) end
    return "EFL Championship"
end

-- Strictly determines whether a competition is a regular league match
local function is_league_competition(comp_name)
    -- "Championship" is a league; it must not trip the "champions" (Champions League) test below
    local c = tostring(comp_name or ""):lower():gsub("championship", "league")
    if c:find("cup") or c:find("trophy") or c:find("shield") or c:find("fa") or c:find("uefa") or c:find("champions") or c:find("conference") or c:find("europa") then
        return false
    end
    if c:find("play%-?off") then
        return false
    end
    if c:find("championship") or c:find("league") or c:find("premier") or c:find("division") or c:find("serie") or c:find("laliga") or c:find("bundesliga") or c:find("ligue") or c:find("eredivisie") or c:find("mls") or c:find("pro league") then
        return true
    end
    return true
end

-- Dynamic managed team ID retrieval
local function get_managed_team_id()
    local result = 0
    pcall(function()
        if type(GetUserTeamID) == "function" then
            local ok, uid = pcall(GetUserTeamID)
            if ok and tonumber(uid) and tonumber(uid) > 0 then
                result = tonumber(uid)
                return
            end
        end
        local users = LE.db:GetTable("career_users")
        if users then
            local record = users:GetFirstRecord()
            while record and record > 0 do
                local team_id = tonumber(users:GetRecordFieldValue(record, "clubteamid")) or tonumber(users:GetRecordFieldValue(record, "teamid")) or 0
                if team_id > 0 and team_id < 4294967295 then
                    result = team_id
                    break
                end
                record = users:GetNextValidRecord()
            end
        end
    end)
    if result == 0 then
        pcall(function()
            if type(GetDBTableRows) == "function" then
                local rows = GetDBTableRows("career_users")
                if type(rows) == "table" and #rows > 0 then
                    local tid = tonumber(rows[1].clubteamid and rows[1].clubteamid.value or rows[1].clubteamid) or 0
                    if tid > 0 then result = tid end
                end
            end
        end)
    end
    return (result > 0) and result or 115486
end

local function safe_league_name_by_id(league_id)
    local lid = tonumber(league_id) or 0
    if lid <= 0 then return "" end
    local name = ""
    pcall(function()
        if type(GetLeagueName) == "function" then
            name = tostring(GetLeagueName(lid) or "")
        end
    end)
    if name ~= "" and name ~= "INVALID" then return name end
    pcall(function()
        if LE and LE.db then
            local tbl = LE.db:GetTable("leagues")
            if tbl then
                local r = tbl:GetFirstRecord()
                while r and r > 0 do
                    if tonumber(tbl:GetRecordFieldValue(r, "leagueid")) == lid then
                        name = tostring(tbl:GetRecordFieldValue(r, "leaguename") or "")
                        break
                    end
                    r = tbl:GetNextValidRecord()
                end
            end
        end
    end)
    return name
end

-- Real league position from leagueteamlinks (selects domestic league by highest matches played)
local function get_real_league_position(managed_team_id)
    local best_pos = 0
    local max_pld = -1
    local best_league_id = 0

    pcall(function()
        if type(GetDBTableRows) == "function" then
            local rows = GetDBTableRows("leagueteamlinks")
            if type(rows) == "table" then
                for _, r in ipairs(rows) do
                    local tid = tonumber(r.teamid and r.teamid.value or r.teamid) or 0
                    if tid == managed_team_id then
                        local pld = tonumber(r.nummatchesplayed and r.nummatchesplayed.value or r.nummatchesplayed) or 0
                        local pos = tonumber(r.currenttableposition and r.currenttableposition.value or r.currenttableposition) or 0
                        local lid = tonumber(r.leagueid and r.leagueid.value or r.leagueid) or 0
                        if pld > max_pld and pos > 0 then
                            max_pld = pld
                            best_pos = pos
                            best_league_id = lid
                        elseif max_pld == -1 and pos > 0 then
                            best_pos = pos
                            best_league_id = lid
                        end
                    end
                end
            end
        end
    end)
    if best_pos == 0 then
        pcall(function()
            if LE and LE.db then
                local tbl = LE.db:GetTable("leagueteamlinks")
                if tbl then
                    local r = tbl:GetFirstRecord()
                    while r and r > 0 do
                        local tid = tonumber(tbl:GetRecordFieldValue(r, "teamid")) or 0
                        if tid == managed_team_id then
                            local pld = tonumber(tbl:GetRecordFieldValue(r, "nummatchesplayed")) or 0
                            local pos = tonumber(tbl:GetRecordFieldValue(r, "currenttableposition")) or 0
                            local lid = tonumber(tbl:GetRecordFieldValue(r, "leagueid")) or 0
                            if pld > max_pld and pos > 0 then
                                max_pld = pld
                                best_pos = pos
                                best_league_id = lid
                            elseif max_pld == -1 and pos > 0 then
                                best_pos = pos
                                best_league_id = lid
                            end
                        end
                        r = tbl:GetNextValidRecord()
                    end
                end
            end
        end)
    end

    if best_league_id > 0 and _G["FC26_AUTO_CAPTURE_STATE"] then
        local lname = safe_league_name_by_id(best_league_id)
        if lname ~= "" then
            _G["FC26_AUTO_CAPTURE_STATE"].primary_league_name = lname
        end
    end

    return best_pos
end

local function days_since_1970_to_civil(z)
    z = math.floor(tonumber(z) or 0)
    z = z + 719468
    local era
    if z >= 0 then
        era = z // 146097
    else
        era = (z - 146096) // 146097
    end
    local doe = z - era * 146097
    local yoe = (doe - doe // 1460 + doe // 36524 - doe // 146096) // 365
    local year = yoe + era * 400
    local doy = doe - (365 * yoe + yoe // 4 - yoe // 100)
    local mp = (5 * doy + 2) // 153
    local day = doy - (153 * mp + 2) // 5 + 1
    local month = mp + (mp < 10 and 3 or -9)
    year = year + (month <= 2 and 1 or 0)
    return year, month, day
end

local function ea_day_to_iso(raw_value)
    local raw = tonumber(raw_value)
    if raw == nil or raw <= 0 then return "" end
    if raw >= 19000101 and raw <= 25001231 then
        local year = raw // 10000
        local month = (raw // 100) % 100
        local day = raw % 100
        if month >= 1 and month <= 12 and day >= 1 and day <= 31 then
            return string.format("%04d-%02d-%02d", year, month, day)
        end
    end
    local year, month, day = days_since_1970_to_civil(raw)
    if year and year >= 1900 and year <= 2500 and month >= 1 and month <= 12 and day >= 1 and day <= 31 then
        return string.format("%04d-%02d-%02d", year, month, day)
    end
    return ""
end

local function fixture_time_to_text(time_raw)
    local number = tonumber(time_raw) or 0
    local hour = number // 60
    local minute = number % 60
    return string.format("%02d:%02d", hour, minute)
end

local function get_fce_data_manager()
    local ok, res = pcall(function()
        if type(ENUM_djb2IFCEInterface_CLSS) ~= "nil" and type(GetPlugin) == "function" then
            local interface = GetPlugin(ENUM_djb2IFCEInterface_CLSS)
            if interface and interface ~= 0 then
                local ptr = MEMORY:ReadMultilevelPointer(interface, {0x18, 0x10, 0x08, 0x00})
                if ptr and ptr ~= 0 then return ptr end
            end
        end
        local base = MEMORY:GetModuleBase("FC26.exe")
        if not base or base == 0 then return 0 end
        local p1 = MEMORY:ReadPointer(base + 0xCFDBE08)
        if not p1 or p1 == 0 then return 0 end
        local p2 = MEMORY:ReadPointer(p1 + 0x60)
        if not p2 or p2 == 0 then return 0 end
        return MEMORY:ReadPointer(p2 + 0x48)
    end)
    return (ok and res) and res or 0
end

local function read_valid_standings()
    local result = {}
    local manager = get_fce_data_manager()
    if not manager or manager == 0 then return result end
    pcall(function()
        local list = MEMORY:ReadPointer(manager + 0x88)
        if not list or list == 0 then return end
        local item_size = 0x18
        local begin_ptr = MEMORY:ReadPointer(list + 0x28)
        local max_items = MEMORY:ReadInt(list + 0x1C) - 1
        for index = 0, max_items do
            local current = begin_ptr + (item_size * index)
            local is_used = MEMORY:ReadBool(current + 0x16)
            local team_id = MEMORY:ReadInt(current + 0x04)
            if is_used and team_id > 0 then
                result[index] = {
                    id = MEMORY:ReadShort(current + 0x00),
                    competition_object_id = MEMORY:ReadShort(current + 0x02),
                    team_id = team_id,
                    team_index = MEMORY:ReadChar(current + 0x08)
                }
            end
        end
    end)
    return result
end

local function read_valid_fixtures()
    local result = {}
    local manager = get_fce_data_manager()
    if not manager or manager == 0 then return result end
    pcall(function()
        local list = MEMORY:ReadPointer(manager + 0x60)
        if not list or list == 0 then return end
        local item_size = 0x18
        local begin_ptr = MEMORY:ReadPointer(list + 0x28)
        local max_items = MEMORY:ReadInt(list + 0x1C) - 1
        for index = 0, max_items do
            local current = begin_ptr + (item_size * index)
            local is_used = MEMORY:ReadBool(current + 0x14)
            if is_used then
                local raw_hp = MEMORY:ReadChar(current + 0x10)
                local raw_ap = MEMORY:ReadChar(current + 0x12)
                local hp = (raw_hp >= 0 and raw_hp < 255) and raw_hp or 0
                local ap = (raw_ap >= 0 and raw_ap < 255) and raw_ap or 0

                result[#result + 1] = {
                    date_raw = MEMORY:ReadInt(current + 0x00),
                    time_raw = MEMORY:ReadShort(current + 0x04),
                    fixture_id = MEMORY:ReadShort(current + 0x06),
                    competition_object_id = MEMORY:ReadShort(current + 0x08),
                    home_standing_index = MEMORY:ReadShort(current + 0x0A),
                    away_standing_index = MEMORY:ReadShort(current + 0x0C),
                    match_group_id = MEMORY:ReadChar(current + 0x0E),
                    home_score = MEMORY:ReadChar(current + 0x0F),
                    home_penalties = hp,
                    away_score = MEMORY:ReadChar(current + 0x11),
                    away_penalties = ap,
                    completed = MEMORY:ReadBool(current + 0x13)
                }
            end
        end
    end)
    return result
end

local function get_db_fixtures_map()
    local map = {}
    pcall(function()
        local tbl = LE.db:GetTable("fixtures")
        if not tbl then return end
        local r = tbl:GetFirstRecord()
        while r and r > 0 do
            local fid = tonumber(tbl:GetRecordFieldValue(r, "fixtureid")) or 0
            local hid = tonumber(tbl:GetRecordFieldValue(r, "hometeamid")) or 0
            local aid = tonumber(tbl:GetRecordFieldValue(r, "awayteamid")) or 0
            local cid = tonumber(tbl:GetRecordFieldValue(r, "competitionid")) or 0
            if fid > 0 then
                map[fid] = { home_id = hid, away_id = aid, comp_id = cid }
            end
            r = tbl:GetNextValidRecord()
        end
    end)
    return map
end

-- Strictly determines stage: Always Matchday XX for leagues (unless playoffs); cup round for cups
local function determine_stage(comp_name, comp_match_count)
    local is_league = is_league_competition(comp_name)
    local c = tostring(comp_name or ""):lower()

    if is_league then
        return "Matchday " .. tostring(math.max(1, comp_match_count))
    end

    if c:find("play%-?off") then
        if comp_match_count >= 3 then return "Play-off Final"
        elseif comp_match_count == 2 then return "Play-off Semi-Final (Leg 2)"
        else return "Play-off Semi-Final (Leg 1)" end
    end

    if c:find("fa cup") or c:find("emirates") then
        if comp_match_count >= 6 then return "Final"
        elseif comp_match_count == 5 then return "Semi-Final"
        elseif comp_match_count == 4 then return "Quarter-Final"
        elseif comp_match_count == 3 then return "Round 5"
        elseif comp_match_count == 2 then return "Round 4"
        else return "Round 3" end
    end

    if c:find("carabao") or c:find("efl cup") then
        if comp_match_count >= 6 then return "Final"
        elseif comp_match_count == 5 then return "Semi-Final"
        elseif comp_match_count == 4 then return "Quarter-Final"
        elseif comp_match_count == 3 then return "Round 4"
        elseif comp_match_count == 2 then return "Round 3"
        else return "Round 2" end
    end

    if c:find("champions") or c:find("ucl") then
        if comp_match_count >= 13 then return "Final"
        elseif comp_match_count >= 11 then return "Semi-Final"
        elseif comp_match_count >= 9 then return "Quarter-Final"
        elseif comp_match_count >= 7 then return "Round of 16"
        else return "League Phase " .. tostring(comp_match_count) end
    end

    return "Round " .. tostring(math.max(1, comp_match_count))
end

local function get_all_managed_fixtures(managed_team_id)
    local standings = read_valid_standings()
    local fixtures = read_valid_fixtures()
    local managed_fixtures = {}
    -- The fixtures table is large: only read it (and cache it) when memory did not give us both teams.
    local function db_fixture(fid)
        local now = os.time()
        if not _G["FC26_DB_FIXTURES_CACHE"] or now - (_G["FC26_DB_FIXTURES_TIME"] or 0) > 120 then
            _G["FC26_DB_FIXTURES_CACHE"] = get_db_fixtures_map()
            _G["FC26_DB_FIXTURES_TIME"] = now
        end
        return _G["FC26_DB_FIXTURES_CACHE"][fid]
    end

    for i = 1, #fixtures do
        local f = fixtures[i]
        local h_idx = tonumber(f.home_standing_index) or 0
        local a_idx = tonumber(f.away_standing_index) or 0
        local home = standings[h_idx]
        local away = standings[a_idx]
        local home_team_id = home and home.team_id or 0
        local away_team_id = away and away.team_id or 0

        if home_team_id == 0 or away_team_id == 0 then
            local dbf = db_fixture(f.fixture_id)
            if dbf then
                if home_team_id == 0 then home_team_id = dbf.home_id or 0 end
                if away_team_id == 0 then away_team_id = dbf.away_id or 0 end
            end
        end

        if home_team_id == managed_team_id or away_team_id == managed_team_id then
            local is_home = (home_team_id == managed_team_id)
            local opp_id = is_home and away_team_id or home_team_id
            local opp_name = safe_team_name(opp_id)
            local user_team_name = safe_team_name(managed_team_id)
            local comp_name = safe_competition_name(f.competition_object_id)

            local home_sc = tonumber(f.home_score) or 0
            local away_sc = tonumber(f.away_score) or 0
            local is_league = is_league_competition(comp_name)

            local home_pen = 0
            local away_pen = 0
            local is_et = false

            -- In soccer rules: League matches NEVER have extra time or penalty shootouts!
            if is_league then
                home_pen = 0
                away_pen = 0
                is_et = false
            else
                local raw_hp = tonumber(f.home_penalties) or 0
                local raw_ap = tonumber(f.away_penalties) or 0
                if raw_hp > 0 and raw_hp < 255 then home_pen = raw_hp end
                if raw_ap > 0 and raw_ap < 255 then away_pen = raw_ap end
                if home_pen > 0 or away_pen > 0 then
                    is_et = true
                end
            end

            local gf = is_home and home_sc or away_sc
            local ga = is_home and away_sc or home_sc
            local user_pen = is_home and home_pen or away_pen
            local opp_pen = is_home and away_pen or home_pen

            local badge = "D"
            if home_pen > 0 or away_pen > 0 then
                badge = (user_pen > opp_pen) and "W" or "L"
            else
                badge = (gf > ga) and "W" or ((gf == ga) and "D" or "L")
            end

            local date_str = ea_day_to_iso(f.date_raw)
            local time_str = fixture_time_to_text(f.time_raw)

            table.insert(managed_fixtures, {
                fixture_id = f.fixture_id,
                compobjid = f.competition_object_id,
                competition = comp_name,
                date_raw = tonumber(f.date_raw) or 0,
                time_raw = tonumber(f.time_raw) or 0,
                date = date_str,
                time = time_str,
                is_home = is_home,
                home_id = home_team_id,
                away_id = away_team_id,
                opponent_id = opp_id,
                opponent_name = opp_name,
                user_team_name = user_team_name,
                completed = f.completed,
                home_score = home_sc,
                away_score = away_sc,
                goals_for = gf,
                goals_against = ga,
                home_pen = home_pen,
                away_pen = away_pen,
                is_extra_time = is_et,
                result = badge
            })
        end
    end

    table.sort(managed_fixtures, function(a, b)
        if a.date_raw == b.date_raw then
            return a.time_raw < b.time_raw
        end
        return a.date_raw < b.date_raw
    end)

    return managed_fixtures
end

local function get_current_squad_details(managed_team_id)
    local starters = {}
    local substitutes = {}
    local all_players = {}

    pcall(function()
        local tpl = LE.db:GetTable("teamplayerlinks")
        if not tpl then return end
        local r = tpl:GetFirstRecord()
        while r and r > 0 do
            local tid = tonumber(tpl:GetRecordFieldValue(r, "teamid")) or 0
            local pid = tonumber(tpl:GetRecordFieldValue(r, "playerid")) or 0
            local pos = tonumber(tpl:GetRecordFieldValue(r, "position")) or 0
            if tid == managed_team_id and pid > 0 then
                local pName = safe_player_name(pid)
                local pPos = safe_position_name(pos)
                local item = {
                    id = pid,
                    name = pName,
                    position = pPos,
                    pos_raw = pos,
                    goals = 0,
                    assists = 0,
                    appearances = 0,
                    yellowcards = 0,
                    redcards = 0
                }
                all_players[pid] = item
                if pos <= 27 then
                    table.insert(starters, item)
                elseif pos == 28 then
                    table.insert(substitutes, item)
                end
            end
            r = tpl:GetNextValidRecord()
        end
    end)

    return all_players, starters, substitutes
end

-- The ids of everybody linked to a club (used to find the other side's scorers in the season statistics).
local function get_team_player_ids(team_id)
    local ids = {}
    if not team_id or team_id <= 0 then return ids end
    pcall(function()
        local tpl = LE.db:GetTable("teamplayerlinks")
        if not tpl then return end
        local r = tpl:GetFirstRecord()
        while r and r > 0 do
            if (tonumber(tpl:GetRecordFieldValue(r, "teamid")) or 0) == team_id then
                local pid = tonumber(tpl:GetRecordFieldValue(r, "playerid")) or 0
                if pid > 0 then ids[pid] = true end
            end
            r = tpl:GetNextValidRecord()
        end
    end)
    return ids
end

-- Read cumulative player info to detect delta goals/assists/cards
local function get_player_info_snapshot(managed_team_id)
    local snapshot = {}
    pcall(function()
        local cpi = LE.db:GetTable("career_playerinfo")
        if not cpi then return end
        local r = cpi:GetFirstRecord()
        while r and r > 0 do
            local pid = tonumber(cpi:GetRecordFieldValue(r, "playerid")) or 0
            if pid > 0 then
                local g = tonumber(cpi:GetRecordFieldValue(r, "goals")) or tonumber(cpi:GetRecordFieldValue(r, "leaguegoals")) or 0
                local a = tonumber(cpi:GetRecordFieldValue(r, "assists")) or tonumber(cpi:GetRecordFieldValue(r, "leagueassists")) or 0
                local apps = tonumber(cpi:GetRecordFieldValue(r, "appearances")) or tonumber(cpi:GetRecordFieldValue(r, "leagueappearances")) or 0
                local yc = tonumber(cpi:GetRecordFieldValue(r, "yellowcards")) or 0
                local rc = tonumber(cpi:GetRecordFieldValue(r, "redcards")) or 0
                snapshot[pid] = { goals = g, assists = a, appearances = apps, yellowcards = yc, redcards = rc }
            end
            r = cpi:GetNextValidRecord()
        end
    end)
    return snapshot
end

-- ---------------------------------------------------------------------------
-- CSV reader (RFC 4180): empty cells, quoted commas, doubled quotes, CRLF, BOM.
-- (The old reader dropped empty cells, which shifted every later column.)
-- ---------------------------------------------------------------------------
local function parse_csv_text(content)
    content = content:gsub("^\239\187\191", "")
    local rows, row, field = {}, {}, {}
    local in_quotes = false
    local i, n = 1, #content

    local function end_field()
        row[#row + 1] = table.concat(field)
        field = {}
    end
    local function end_row()
        end_field()
        if not (#row == 1 and row[1] == "") then rows[#rows + 1] = row end -- ignore blank lines
        row = {}
    end

    while i <= n do
        local c = content:sub(i, i)
        if in_quotes then
            if c == '"' then
                if content:sub(i + 1, i + 1) == '"' then
                    field[#field + 1] = '"'
                    i = i + 1
                else
                    in_quotes = false
                end
            else
                field[#field + 1] = c
            end
        elseif c == '"' then
            in_quotes = true
        elseif c == "," then
            end_field()
        elseif c == "\r" then
            if content:sub(i + 1, i + 1) ~= "\n" then end_row() end
        elseif c == "\n" then
            end_row()
        else
            field[#field + 1] = c
        end
        i = i + 1
    end
    if #field > 0 or #row > 0 then end_row() end
    return rows
end

local function parse_simple_csv(filepath)
    local f = io.open(filepath, "rb")
    if not f then return nil end
    local content = f:read("*a")
    f:close()
    if not content or content == "" then return nil end

    local grid = parse_csv_text(content)
    if #grid < 2 then return nil end

    local headers = {}
    for idx, col in ipairs(grid[1]) do headers[idx] = trim(col) end

    local rows = {}
    for r = 2, #grid do
        local row = {}
        for c, h in ipairs(headers) do
            if h ~= "" then row[h] = trim(grid[r][c] or "") end
        end
        rows[#rows + 1] = row
    end
    return rows
end

-- ---------------------------------------------------------------------------
-- Minimal JSON syntax check. Every payload is validated before it leaves the
-- game, so one odd player name can never put a broken event on the bridge.
-- ---------------------------------------------------------------------------
local function json_check(text)
    local pos = 1
    local len = #text
    local fail_msg = nil

    local function fail(msg)
        if not fail_msg then fail_msg = msg .. " at char " .. tostring(pos) end
        return false
    end
    local function skip_ws()
        pos = text:find("[^ \t\r\n]", pos) or (len + 1)
    end

    local parse_value

    local function parse_string()
        pos = pos + 1 -- opening quote
        while pos <= len do
            local c = text:sub(pos, pos)
            if c == '"' then
                pos = pos + 1
                return true
            elseif c == "\\" then
                local e = text:sub(pos + 1, pos + 1)
                if e == "u" then
                    if not text:sub(pos + 2, pos + 5):match("^%x%x%x%x$") then return fail("bad \\u escape") end
                    pos = pos + 6
                elseif e:match('^["\\/bfnrt]$') then
                    pos = pos + 2
                else
                    return fail("bad escape")
                end
            elseif c:byte() < 32 then
                return fail("control character in string")
            else
                pos = pos + 1
            end
        end
        return fail("unterminated string")
    end

    local function parse_number()
        local s = text:match("^-?%d+%.?%d*[eE]?[+-]?%d*", pos)
        if not s or s == "" or s == "-" then return fail("bad number") end
        if not tonumber(s) then return fail("bad number") end
        pos = pos + #s
        return true
    end

    local function parse_array()
        pos = pos + 1
        skip_ws()
        if text:sub(pos, pos) == "]" then pos = pos + 1 return true end
        while true do
            skip_ws()
            if not parse_value() then return false end
            skip_ws()
            local c = text:sub(pos, pos)
            if c == "," then pos = pos + 1
            elseif c == "]" then pos = pos + 1 return true
            else return fail("expected , or ]") end
        end
    end

    local function parse_object()
        pos = pos + 1
        skip_ws()
        if text:sub(pos, pos) == "}" then pos = pos + 1 return true end
        while true do
            skip_ws()
            if text:sub(pos, pos) ~= '"' then return fail("expected key") end
            if not parse_string() then return false end
            skip_ws()
            if text:sub(pos, pos) ~= ":" then return fail("expected :") end
            pos = pos + 1
            skip_ws()
            if not parse_value() then return false end
            skip_ws()
            local c = text:sub(pos, pos)
            if c == "," then pos = pos + 1
            elseif c == "}" then pos = pos + 1 return true
            else return fail("expected , or }") end
        end
    end

    parse_value = function()
        skip_ws()
        local c = text:sub(pos, pos)
        if c == "{" then return parse_object()
        elseif c == "[" then return parse_array()
        elseif c == '"' then return parse_string()
        elseif text:sub(pos, pos + 3) == "true" then pos = pos + 4 return true
        elseif text:sub(pos, pos + 4) == "false" then pos = pos + 5 return true
        elseif text:sub(pos, pos + 3) == "null" then pos = pos + 4 return true
        elseif c == "-" or c:match("%d") then return parse_number()
        end
        return fail("unexpected character")
    end

    if type(text) ~= "string" or text == "" then return false, "empty payload" end
    local ok = parse_value()
    if ok then
        skip_ws()
        if pos <= len then return false, "trailing characters at char " .. tostring(pos) end
        return true
    end
    return false, fail_msg or "invalid JSON"
end

-- ---------------------------------------------------------------------------
-- Reliable delivery to the bridge ("outbox").
-- Every payload is saved as a small .json file in Documents\FC26 Export\outbox (written under a temporary name and
-- renamed, so nobody ever reads half a file). The PC bridge reads that folder itself, deletes each file once it has
-- stored it, and refreshes bridge_watching.txt every few seconds so this script knows it is doing so: then no program
-- is started at all. If a file is still there after a while (older bridge, bridge not running yet) ONE sender posts
-- everything that is waiting in a single batch (hidden where Live Editor allows it). Files are only deleted after the
-- bridge answered; whatever is left is retried every 30 s and picked up again the next time the script starts.
-- ---------------------------------------------------------------------------
local SEND_LOG = COMMUNITY_ROOT .. "/bridge_send_log.txt"
local WATCH_FILE = COMMUNITY_ROOT .. "/bridge_watching.txt"
local SENDER_LOCK = COMMUNITY_ROOT .. "/sender.lock"
local WATCH_FRESH_SECONDS = 20

-- BEGIN sender_bat (this exact text is also checked by tests/lua/test_transfer_logic.lua)
local SENDER_BAT = [[
@echo off
set "OUT=@@OUTBOX@@"
set "L=@@LOG@@"
set "LOCK=@@LOCK@@"
>"%LOCK%" echo %date% %time%
>>"%L%" echo %date% %time% [sender] started
for /f "usebackq delims=" %%N in (`dir /b /o:n "%OUT%\*.json" 2^>NUL`) do (
  call :send "%OUT%\%%N" "%%~nN"
  if errorlevel 1 goto :done
)
:done
del "%LOCK%" >nul 2>&1
(goto) 2>nul & del "%~f0" >nul 2>&1 & exit
:send
set "J=%~1"
set "ID=%~2"
set "CODE=000"
call :try http://127.0.0.1:5050
if not "%CODE%"=="200" ping -n 2 127.0.0.1 >nul
if not "%CODE%"=="200" call :try http://127.0.0.1:5050
if not "%CODE%"=="200" call :try http://127.0.0.1:8080
if not "%CODE%"=="200" call :psfallback
>>"%L%" echo %date% %time% [%ID%] HTTP %CODE%
if "%CODE%"=="200" del "%J%" >nul 2>&1
if "%CODE%"=="200" exit /b 0
exit /b 1
:try
for /f "usebackq delims=" %%C in (`curl.exe -s -o NUL -w "%%{http_code}" -m 6 -X POST -H "Content-Type: application/json; charset=utf-8" --data-binary @"%J%" %1 2^>NUL`) do set "CODE=%%C"
exit /b
:psfallback
for /f "usebackq delims=" %%C in (`powershell -NoProfile -Command "try { (Invoke-WebRequest -UseBasicParsing -Uri http://127.0.0.1:5050 -Method Post -ContentType 'application/json; charset=utf-8' -InFile '%J%' -TimeoutSec 6).StatusCode } catch { 0 }" 2^>NUL`) do set "CODE=%%C"
exit /b
]]
-- END sender_bat

local outbox_pending = {} -- json path -> { id = , tries = , queued_at = , last_try = }
local outbox_serial = 0
local sender_state = { last_launch = 0, seq = 0, hidden = false, last_note = 0 }

-- true while the bridge is reading the outbox folder itself (it refreshes bridge_watching.txt every few seconds)
local function bridge_is_watching()
    local f = io.open(WATCH_FILE, "r")
    if not f then return false end
    local text = f:read("*a") or ""
    f:close()
    local stamp = tonumber(text:match("%d+"))
    return stamp ~= nil and math.abs(os.time() - stamp) <= WATCH_FRESH_SECONDS
end

-- Starts ONE sender that posts every file waiting in the outbox. Returns true when it was started.
local function launch_outbox_sender()
    local temp_dir = os.getenv("TEMP") or os.getenv("TMP") or "."
    sender_state.seq = sender_state.seq + 1
    local bat_path = temp_dir .. "/fc26_send_" .. tostring(os.time()) .. "_" .. sender_state.seq .. ".bat"
    local body = SENDER_BAT
    body = body:gsub("@@OUTBOX@@", function() return win_path(OUTBOX_DIR) end)
    body = body:gsub("@@LOG@@", function() return win_path(SEND_LOG) end)
    body = body:gsub("@@LOCK@@", function() return win_path(SENDER_LOCK) end)
    local bf = io.open(bat_path, "w")
    if not bf then return false end
    bf:write(body)
    bf:close()
    local started, hidden = QUIET.run_bat(bat_path)
    sender_state.last_launch = os.time()
    sender_state.hidden = hidden
    return started
end

local function track_outbox_file(json_path, id)
    outbox_pending[json_path] = outbox_pending[json_path] or { id = id, tries = 0, queued_at = os.time(), last_try = 0 }
    return true
end

local function write_outbox_file(json_path, payload)
    -- written under a temporary name first, so the bridge never reads half a payload
    local tmp_path = json_path .. ".tmp"
    local jf = io.open(tmp_path, "wb")
    if jf then
        jf:write(payload)
        jf:close()
        if os.rename(tmp_path, json_path) then return true end
        pcall(os.remove, tmp_path)
    end
    jf = io.open(json_path, "wb")
    if not jf then return false end
    jf:write(payload)
    jf:close()
    return true
end

local function send_payload_to_bridge(payload, log_label)
    local ok, err = xpcall(function()
        local valid, why = json_check(payload)
        if not valid then
            log_monitor("PAYLOAD REJECTED (" .. tostring(why) .. ") for " .. tostring(log_label) .. " - not sent")
            local bad = io.open(COMMUNITY_ROOT .. "/last_bad_payload.json", "wb")
            if bad then bad:write(payload) bad:close() end
            return
        end

        -- Several payloads are often saved within the same second (a transfer window): the running number keeps their
        -- file names unique and in order, so a later payload never overwrites an earlier one before it is delivered.
        local stamp = tostring(os.time())
        local id, json_path
        repeat
            outbox_serial = outbox_serial % 999999 + 1
            id = stamp .. "_" .. string.format("%06d", outbox_serial)
            json_path = OUTBOX_DIR .. "/" .. id .. ".json"
        until not file_exists(json_path) and not outbox_pending[json_path]
        if not write_outbox_file(json_path, payload) then
            log_monitor("Cannot write " .. json_path .. " - payload not sent")
            return
        end

        track_outbox_file(json_path, id)
        local kind = payload:match('"dataType"%s*:%s*"(%w+)"')
        if kind == "match" or kind == "transfer" then status.career_dirty_at = os.time() end  -- tables, squad and fixtures changed
        if kind == "match" then status.last_match = os.time()
        elseif kind == "transfer" then status.last_transfer = os.time()
        elseif kind == "squad" then status.last_squad = os.time() end
        log_monitor("Queued for bridge [" .. id .. "]: " .. tostring(log_label) .. "  (result is written to bridge_send_log.txt)")
    end, debug.traceback)
    if not ok then
        log_monitor("Error in send_payload_to_bridge: " .. tostring(err))
    end
end

-- Every 2 s: forget what was delivered. The bridge reads the outbox folder itself (bridge 3.0 and newer), so no program
-- is ever started here - every program a Live Editor script starts flashes a console window on top of the game. A
-- payload simply waits until the bridge picks it up, also when the bridge is started later (it reads every file that
-- is waiting, from this session or an earlier one). FC26_AutoCapture_SendNow() sends them at once when asked.
local function service_outbox()
    local now = os.time()
    local waiting, oldest = 0, nil
    for json_path, entry in pairs(outbox_pending) do
        if not file_exists(json_path) then
            outbox_pending[json_path] = nil -- delivered (the bridge deleted it)
        else
            waiting = waiting + 1
            if not oldest or entry.queued_at < oldest then oldest = entry.queued_at end
        end
    end
    if waiting == 0 or now - oldest < 60 then return end
    if now - (sender_state.last_note or 0) < 600 then return end
    sender_state.last_note = now
    if bridge_is_watching() then
        log_monitor(waiting .. " payload(s) still waiting in the outbox although the bridge reads the folder - check the bridge window for errors")
    else
        log_monitor(waiting .. " payload(s) waiting in the outbox: the bridge is not running. They are delivered as soon as it starts "
            .. "(FC26_AutoCapture_SendNow() sends them right away, with a short console window).")
    end
end

-- On startup nothing has to be picked up: the bridge reads every file in the outbox, also those an earlier session left
-- (listing the folder here would open a console window when Live Editor has no folder function of its own).
local function adopt_outbox_leftovers()
    if not bridge_is_watching() then
        log_monitor("The bridge is not running yet: payloads wait in the outbox until it starts")
    end
    return 0
end

-- Sends everything waiting in the outbox right away (only when asked: the sender is a program, so a console window may
-- flash when Live Editor cannot start it hidden).
function FC26_AutoCapture_SendNow()
    local ok = launch_outbox_sender()
    local msg = ok and ("Sending everything in the outbox to the bridge now" .. (sender_state.hidden and " (hidden)" or ""))
        or "Could not start the sender"
    log_monitor(msg)
    print("[FC26 AutoCapture] " .. msg)
    return ok
end

-- ---------------------------------------------------------------------------
-- Project Atlas MatchStats export folders (Documents\FC26 Export\MatchStats\ready)
-- ---------------------------------------------------------------------------
-- BEGIN atlas_lookup (this exact block is also run by tests/lua/test_transfer_logic.lua)
_G["FC26_ATLAS_USED"] = _G["FC26_ATLAS_USED"] or {}
local ATLAS_MARKERS = { "MATCH_READY", "READY_FOR_MANUAL_ZIP_AND_UPLOAD.txt" }

local function atlas_folder_is_ready(folder_path)
    local marker = false
    for _, m in ipairs(ATLAS_MARKERS) do
        if file_exists(folder_path .. "/" .. m) then marker = true break end
    end
    return marker
        and file_exists(folder_path .. "/16_match_participants_wide.csv")
        and file_exists(folder_path .. "/18_match_teams_wide.csv")
end

-- Folder names, newest first.
local function list_atlas_folders()
    return QUIET.list_dirs(MATCH_STATS_ROOT .. "/ready", true)
end

-- The Atlas exporter says itself which folder it published last (no folder lookup needed).
local function last_export_folder()
    local result = rawget(_G, "FC26_EXPORT_MATCH_STATS_LAST_EXPORT_RESULT")
    if type(result) == "table" and result.success == true and type(result.ready_dir) == "string" and result.ready_dir ~= "" then
        local path = (result.ready_dir:gsub("\\", "/")):gsub("/+$", "")
        local name = path:match("([^/]+)$")
        if name then return name, path end
    end
    return nil, nil
end

-- The exporter (1.1.2 and newer) also writes the folder it published last into MatchStats\last_export.txt, for when it
-- runs on its own and its answer above is not visible here. Reading a file starts no program (a folder listing would).
local function pointer_export_folder()
    local f = io.open(MATCH_STATS_ROOT .. "/last_export.txt", "rb")
    if not f then return nil, nil end
    local text = f:read("*a") or ""
    f:close()
    local line = ((text:match("[^\r\n]+") or ""):gsub("^%s+", ""):gsub("%s+$", ""))
    if line == "" then return nil, nil end
    local path = (line:gsub("\\", "/")):gsub("/+$", "")
    local name = path:match("([^/]+)$")
    if not name then return nil, nil end
    return name, path
end

local SCRIPT_START_STAMP = os.date("%Y%m%d_%H%M%S")
-- The export last_export.txt names when this script starts belongs to a match that was handled before. Any other name
-- in it later is a new export, even when the exporter was armed (and named it) before this script started: running the
-- script again while Live Editor stays open keeps the exporter that is already running.
_G["FC26_ATLAS_POINTER_AT_START"] = (pointer_export_folder())
local ATLAS_LISTING_AFTER_SECONDS = 12   -- only look at the folder itself when the exporter has not reported one by then
local ATLAS_LISTING_EVERY_SECONDS = 8
local last_atlas_listing = 0

-- The newest export that has not been attached to a match yet and is completely written.
-- include_used = true (manual "sync latest") accepts the newest export even if it was used before.
-- waited = seconds the match has been waiting (the folder listing is only a fallback, so it starts after a while).
local function find_atlas_folder(include_used, waited)
    local used = _G["FC26_ATLAS_USED"]

    local name, path = last_export_folder()
    if name and (include_used or not used[name]) and atlas_folder_is_ready(path) then
        return name, path, { name }
    end
    local pname, ppath = pointer_export_folder()
    if pname and (include_used or not used[pname]) then
        -- an export made before this script started belongs to an earlier match
        local stamp = pname:match("(%d%d%d%d%d%d%d%d_%d%d%d%d%d%d)")
        local fresh = pname ~= rawget(_G, "FC26_ATLAS_POINTER_AT_START") or stamp == nil or stamp >= SCRIPT_START_STAMP
        if (include_used or fresh) and atlas_folder_is_ready(ppath) then
            return pname, ppath, { pname }
        end
    end

    local now = os.time()
    if not include_used then
        if (waited or 0) < ATLAS_LISTING_AFTER_SECONDS or now - last_atlas_listing < ATLAS_LISTING_EVERY_SECONDS then
            return nil, nil, nil
        end
        last_atlas_listing = now
    end
    local names = list_atlas_folders()
    for _, folder in ipairs(names) do
        if not include_used and not used[folder] then
            -- export folders are named ..._YYYYMMDD_HHMMSS_...: one made before this script started belongs to an earlier match
            local stamp = folder:match("(%d%d%d%d%d%d%d%d_%d%d%d%d%d%d)")
            if stamp and stamp < SCRIPT_START_STAMP then used[folder] = true end
        end
        if include_used or not used[folder] then
            local folder_path = MATCH_STATS_ROOT .. "/ready/" .. folder
            if atlas_folder_is_ready(folder_path) then return folder, folder_path, names end
            return nil, nil, names -- newest export is still being written: wait for it, do not fall back to an older one
        end
    end
    return nil, nil, names
end
-- END atlas_lookup

local function percent_value(v)
    local n = to_num(v, 0)
    if n > 0 and n <= 1 then n = n * 100 end
    return n
end

local function truthy_flag(v)
    local s = tostring(v or ""):lower()
    return s == "true" or s == "1" or s == "yes"
end

local function read_table_rows(table_name, fields)
    local rows = {}
    pcall(function()
        if type(GetDBTableRows) == "function" then
            local raw = GetDBTableRows(table_name)
            if type(raw) == "table" then
                for _, rr in ipairs(raw) do
                    local row = {}
                    for _, f in ipairs(fields) do
                        local v = rr[f]
                        if v == nil then
                            for k, val in pairs(rr) do
                                if tostring(k):lower() == f then v = val break end
                            end
                        end
                        if type(v) == "table" then v = v.value end
                        row[f] = v
                    end
                    rows[#rows + 1] = row
                end
            end
        end
    end)
    if #rows == 0 then
        pcall(function()
            if LE and LE.db then
                local t = LE.db:GetTable(table_name)
                if t then
                    local r = t:GetFirstRecord()
                    while r and r > 0 do
                        local row = {}
                        for _, f in ipairs(fields) do row[f] = t:GetRecordFieldValue(r, f) end
                        rows[#rows + 1] = row
                        r = t:GetNextValidRecord()
                    end
                end
            end
        end)
    end
    return rows
end

-- BEGIN match_logic (this exact block is also run by tests/lua/test_transfer_logic.lua)
-- Who played, who replaced whom and the numbers of one finished match. Nothing in this block touches the game, so the
-- tests run it as it is. It needs jstr, to_int, to_num, percent_value and truthy_flag from above.

-- The game keeps a record of every club's last match: one row per player { id, minutes, pos = position id,
-- sub = came on as a substitute }. A replacement can be told from it, because the substitute's minutes plus the
-- minutes of the player he replaced add up to the length of the match, and he takes over the same position.
-- Returns the pairs { minute, out = row, ["in"] = row }, the minute marks per player id and the match length used.
local function pair_substitutions(history)
    local starters, subs, counts = {}, {}, {}
    for _, h in ipairs(history) do
        if (h.minutes or 0) > 0 then
            if h.sub then
                subs[#subs + 1] = h
            else
                starters[#starters + 1] = h
                counts[h.minutes] = (counts[h.minutes] or 0) + 1
            end
        end
    end

    -- Players who stayed on all game share one number (90, or 120 after extra time): the most common one.
    local full, most = 0, 0
    for minutes, n in pairs(counts) do
        if n > most or (n == most and minutes > full) then full, most = minutes, n end
    end
    local outgoing = {}
    for _, s in ipairs(starters) do
        if s.minutes < full then outgoing[#outgoing + 1] = s end
    end

    local best = { score = -1, list = {}, length = full }
    if #subs > 0 and #subs <= 8 and #outgoing > 0 then
        for length = full, full + 12 do      -- stoppage time is not part of the 90 the starters are capped at
            local used, current = {}, {}
            local found = { score = 0, list = {} }
            local function search(i, score)
                if i > #subs then
                    if score > found.score then
                        found.score = score
                        found.list = {}
                        for k, v in ipairs(current) do found.list[k] = v end
                    end
                    return
                end
                local s = subs[i]
                for oi, o in ipairs(outgoing) do
                    if not used[oi] then
                        local diff = math.abs(s.minutes + o.minutes - length)
                        if diff <= 1 then
                            local same = (s.pos == nil or o.pos == nil or s.pos == o.pos)
                            used[oi] = true
                            current[#current + 1] = { sub = s, out = o }
                            search(i + 1, score + 100 - diff - (same and 0 or 2))
                            current[#current] = nil
                            used[oi] = nil
                        end
                    end
                end
                search(i + 1, score)         -- this substitute replaced nobody that can be identified
            end
            search(1, 0)
            if found.score > best.score then best = { score = found.score, list = found.list, length = length } end
        end
    end

    local marks, pairs_out = {}, {}
    local function mark(id)
        marks[id] = marks[id] or {}
        return marks[id]
    end
    for _, p in ipairs(best.list) do
        pairs_out[#pairs_out + 1] = { minute = p.out.minutes, out = p.out, ["in"] = p.sub }
        mark(p.sub.id).in_minute = p.out.minutes
    end
    for _, o in ipairs(outgoing) do mark(o.id).out_minute = o.minutes end   -- also a sending-off or an injury
    for _, s in ipairs(subs) do
        if not (marks[s.id] and marks[s.id].in_minute) and best.length > 0 then
            mark(s.id).in_minute = math.max(1, best.length - s.minutes)
        end
    end
    table.sort(pairs_out, function(a, b)
        if a.minute == b.minute then return a.out.id < b.out.id end
        return a.minute < b.minute
    end)
    return pairs_out, marks, best.length
end

-- Is the game's last-match record really about the match in the export? The same players must appear in both.
-- exported_ids: set of the club's players in the export (string ids), or nil when there is no export.
local function lineup_matches_export(history, exported_ids)
    local played, hit, exported = 0, 0, 0
    for _ in pairs(exported_ids or {}) do exported = exported + 1 end
    for _, h in ipairs(history) do
        if (h.minutes or 0) > 0 then
            played = played + 1
            if exported_ids and exported_ids[tostring(h.id)] then hit = hit + 1 end
        end
    end
    if played < 11 then return false end
    if exported == 0 then return true end
    return hit / played >= 0.7 and hit / exported >= 0.7
end

-- "Player" and "Player 123456" are what safe_player_name answers when the game gives no name.
local function is_unnamed_player(name)
    if name == nil then return true end
    local n = tostring(name):gsub("^%s+", ""):gsub("%s+$", "")
    return n == "" or n:lower() == "player" or n:match("^[Pp]layer %d+$") ~= nil or n:match("^%d+$") ~= nil
end

-- The players of the other side who scored, worked out from the season statistics the game keeps for everybody
-- (career_playerinfo, read before and after the match). That record also holds anything else the club's opponent
-- played in between, so it is only believed when it adds up to exactly the goals the club conceded.
-- pre/post: pid -> { goals = n }; opponent_pids: set of the other club's player ids.
-- Returns a list of { name =, goals = } or nil.
local function opponent_scorers_from_deltas(pre, post, opponent_pids, goals_conceded, name_for)
    if not pre or not post or not opponent_pids or (goals_conceded or 0) <= 0 then return nil end
    local found, total = {}, 0
    for pid in pairs(opponent_pids) do
        local before, after = pre[pid], post[pid]
        if before and after then
            local scored = (after.goals or 0) - (before.goals or 0)
            if scored > 0 then
                found[#found + 1] = { pid = pid, goals = scored }
                total = total + scored
            end
        end
    end
    if total ~= goals_conceded then return nil end
    table.sort(found, function(a, b) return a.pid < b.pid end)
    local list = {}
    for _, f in ipairs(found) do
        local name = name_for and name_for(f.pid) or ""
        if is_unnamed_player(name) then return nil end
        list[#list + 1] = { name = name, goals = f.goals }
    end
    return list
end

local function team_row_for(teams, idx)
    for _, t in ipairs(teams) do
        if t.team_index ~= nil and tonumber(t.team_index) == idx then return t end
    end
    return teams[idx + 1] or teams[1]
end

-- Reads the two Atlas CSV files (already parsed into rows) for the club's side of the match.
-- ctx: squad (set of player ids that belong to the club), is_home, goals_against, player_name(id) for blank names.
local function analyse_atlas_export(participants, teams, ctx)
    local squad = ctx.squad or {}
    local count0, count1 = 0, 0
    for _, p in ipairs(participants) do
        local pid = tonumber(p.player_id) or 0
        if squad[pid] ~= nil then
            if (tonumber(p.team_index) or 0) == 0 then count0 = count0 + 1 else count1 = count1 + 1 end
        end
    end
    local managed_idx = ctx.is_home and 0 or 1
    if count0 > count1 then managed_idx = 0 elseif count1 > count0 then managed_idx = 1 end
    local opp_idx = (managed_idx == 0) and 1 or 0

    local function name_of(p)
        local n = p.player_name
        if n ~= nil and n ~= "" then return n end
        if ctx.player_name then return ctx.player_name(tonumber(p.player_id) or 0) end
        return "Player"
    end

    -- Man of the match: the game's own flag (either team can win it), else the best rating of the match.
    local motm_row
    for _, p in ipairs(participants) do
        if truthy_flag(p.man_of_the_match) then motm_row = p break end
    end
    if not motm_row then
        local highest = -1.0
        for _, p in ipairs(participants) do
            local r = to_num(p.game_rating, 0)
            if r > highest then highest, motm_row = r, p end
        end
    end
    local motm_name, motm_is_opponent = "", false
    if motm_row then
        motm_name = name_of(motm_row)
        motm_is_opponent = (tonumber(motm_row.team_index) or 0) ~= managed_idx
    end

    local max_minutes = 0
    for _, p in ipairs(participants) do
        local m = to_num(p.minutes_played, 0)
        if m > max_minutes then max_minutes = m end
    end

    -- Team numbers come from the club's own row (found by its team_index, not by its position in the file).
    local m_row = team_row_for(teams, managed_idx)
    local o_row = team_row_for(teams, opp_idx)
    local passes_f = to_num(m_row.passes, 0)
    local passes_a = to_num(o_row.passes, 0)
    local possession = to_int(percent_value(
        (m_row.possession ~= nil and m_row.possession ~= "") and m_row.possession or m_row.possession_pct))
    local possession_estimated = false
    if possession <= 0 then
        -- The exporter has no possession figure: the share of all passes is the closest thing it records.
        possession_estimated = true
        if passes_f + passes_a > 0 then
            possession = math.max(25, math.min(75, math.floor(passes_f / (passes_f + passes_a) * 100 + 0.5)))
        else
            possession = 50
        end
    end

    -- Who scored for the other side (the export lists every player of both teams). The export leaves most names
    -- blank and often has no usable id for the other side's players: a name that could not be found stays "" (the
    -- goal then reads "<club> Goal"), it is never a made-up "Player".
    local opp_scorers = {}
    for _, p in ipairs(participants) do
        if (tonumber(p.team_index) or 0) == opp_idx then
            local g = to_int(tonumber(p.goals_derived) or tonumber(p.npk_goals) or 0)
            if g > 0 then
                local shown = name_of(p)
                if is_unnamed_player(shown) then shown = "" end
                opp_scorers[#opp_scorers + 1] = { name = shown, goals = g }
            end
        end
    end

    local goals_against = ctx.goals_against or 0
    local rows = {}
    for _, p in ipairs(participants) do
        local minutes = tonumber(p.minutes_played)
        -- The export can list the whole matchday squad: players who never came on did not play.
        local played = not (max_minutes > 0 and minutes ~= nil and minutes <= 0)
        if (tonumber(p.team_index) or 0) == managed_idx and played then
            local goals = to_int(tonumber(p.goals_derived) or tonumber(p.npk_goals) or 0)
            local shots = to_int(tonumber(p.shots) or goals)
            local on_target = to_num(p.shots_on_target, 0)
            local shot_acc = 0
            if shots > 0 and on_target > 0 then shot_acc = math.min(100, math.floor((on_target / shots) * 100)) end
            local tackles = to_int(p.tackles, 0)
            local tackle_pct
            if tonumber(p.stand_tackle_success_pct_derived) then
                tackle_pct = to_int(percent_value(p.stand_tackle_success_pct_derived))
            else
                -- The exporter leaves that column blank for a player with no stand tackles (slide tackles only):
                -- use all tackles won / attempted instead of reporting 100%.
                local attempts, won
                if p.stand_tackles ~= nil or p.slide_tackles ~= nil then
                    attempts = to_num(p.stand_tackles, 0) + to_num(p.slide_tackles, 0)
                    won = to_num(p.stand_tackles_won, 0) + to_num(p.slide_tackles_won, 0)
                else
                    attempts = tackles
                    won = to_num(p.tackles_completed, tackles)
                end
                tackle_pct = (attempts > 0) and math.min(100, math.floor((won / attempts) * 100)) or 0
            end
            rows[#rows + 1] = {
                id = tostring(p.player_id or ""),
                name = name_of(p),
                minutes = minutes,
                rating = to_num(p.game_rating, 6.5),
                goals = goals,
                assists = to_int(p.assists, 0),
                passes = to_int(p.passes, 0),
                pass_acc = to_int(percent_value(p.pass_accuracy_pct_derived)),
                shots = shots,
                shot_acc = shot_acc,
                saves = to_int(tonumber(p.saves) or 0),
                penalty_saves = to_int(tonumber(p.penalty_saves) or 0),
                faced = to_num(p.shots_against, 0),
                tackles = tackles,
                tackle_pct = tackle_pct,
                interceptions = to_int(p.interceptions, 0),
                yellow = to_num(p.yellow_card_events, 0) > 0,
                red = to_num(p.red_dismissals, 0) > 0,
                clean_sheet = (goals_against == 0),
                motm = (p == motm_row)
            }
        end
    end

    local m_shootout = to_int(m_row.shootout_goals, 0)
    local o_shootout = to_int(o_row.shootout_goals, 0)
    if m_shootout >= 255 or m_shootout < 0 then m_shootout = 0 end
    if o_shootout >= 255 or o_shootout < 0 then o_shootout = 0 end

    return {
        managed_idx = managed_idx,
        rows = rows,
        motm = motm_name,
        motm_is_opponent = motm_is_opponent,
        shots_for = to_int(tonumber(m_row.shots) or tonumber(m_row.shots_on_target) or 0),
        shots_against = to_int(tonumber(o_row.shots) or tonumber(o_row.shots_on_target) or 0),
        shots_on_target_against = to_int(tonumber(o_row.shots_on_target) or 0),
        xg_for = to_num(m_row.npk_expected_goals, 0.0),
        xg_against = to_num(o_row.npk_expected_goals, 0.0),
        possession = possession,
        possession_estimated = possession_estimated,
        passing_accuracy = to_int(percent_value(m_row.pass_accuracy_pct_derived)),
        goals_against = goals_against,
        opp_scorers = opp_scorers,
        max_minutes = max_minutes,
        m_shootout = m_shootout,
        o_shootout = o_shootout
    }
end

-- Adds what the game's own last-match record knows to the export's rows: the players the export missed (a player
-- who joined on loan, say), who played which position, and every substitution. Returns the list of substitutions.
-- opts: player_name(id) and rating_for(id) (both optional).
local function apply_lineup(analysis, history, opts)
    opts = opts or {}
    local by_id = {}
    for _, r in ipairs(analysis.rows) do by_id[r.id] = r end
    local pairs_out, marks = pair_substitutions(history)
    analysis.added = {}

    for _, h in ipairs(history) do
        if (h.minutes or 0) > 0 then
            local key = tostring(h.id)
            local r = by_id[key]
            if not r then
                r = {
                    id = key,
                    name = opts.player_name and opts.player_name(h.id) or ("Player " .. key),
                    minutes = h.minutes,
                    rating = opts.rating_for and opts.rating_for(h.id) or 0,
                    goals = 0, assists = 0, passes = 0, pass_acc = 0, shots = 0, shot_acc = 0,
                    saves = 0, penalty_saves = 0, faced = 0, tackles = 0, tackle_pct = 0, interceptions = 0,
                    yellow = false, red = false, clean_sheet = (analysis.goals_against or 0) == 0,
                    motm = false, from_history = true
                }
                analysis.rows[#analysis.rows + 1] = r
                by_id[key] = r
                analysis.added[#analysis.added + 1] = r.name
            end
            r.pos = h.pos
            local m = marks[h.id]
            if m then r.in_minute, r.out_minute = m.in_minute, m.out_minute end
        end
    end

    -- Starters first, then the substitutes in the order they came on.
    local ordered, bench = {}, {}
    for _, r in ipairs(analysis.rows) do
        if r.in_minute then bench[#bench + 1] = r else ordered[#ordered + 1] = r end
    end
    table.sort(bench, function(a, b)
        if a.in_minute == b.in_minute then return a.id < b.id end
        return a.in_minute < b.in_minute
    end)
    for _, r in ipairs(bench) do ordered[#ordered + 1] = r end
    analysis.rows = ordered

    local subs = {}
    for _, p in ipairs(pairs_out) do
        local out_row, in_row = by_id[tostring(p.out.id)], by_id[tostring(p["in"].id)]
        if out_row and in_row then
            subs[#subs + 1] = {
                minute = p.minute,
                out_id = out_row.id, out_name = out_row.name,
                in_id = in_row.id, in_name = in_row.name
            }
        end
    end
    return subs
end

-- The export has no saves for a goalkeeper whose column is empty: shots on target that were not goals were saved.
local function estimate_keeper_saves(analysis, goals_against)
    local total = 0
    for _, r in ipairs(analysis.rows) do total = total + (r.saves or 0) end
    local saved = (analysis.shots_on_target_against or 0) - (goals_against or 0)
    if total > 0 or saved <= 0 then return false end
    local keeper
    for _, r in ipairs(analysis.rows) do
        if r.pos == 0 and (not keeper or (r.minutes or 0) > (keeper.minutes or 0)) then keeper = r end
    end
    if not keeper then
        for _, r in ipairs(analysis.rows) do
            if (r.faced or 0) > 0 and (not keeper or r.faced > keeper.faced) then keeper = r end
        end
    end
    if not keeper then return false end
    keeper.saves = saved
    return true
end

-- The position ids of the game's last-match record (EA's usual numbering) and the short names the app sorts the
-- line-up by (strikers first, goalkeeper last). Bench / reserve ids (28, 29) have no entry: no position is sent.
local POSITION_LABELS = {
    [0] = "GK", [1] = "SW", [2] = "RWB", [3] = "RB", [4] = "RCB", [5] = "CB", [6] = "LCB", [7] = "LB", [8] = "LWB",
    [9] = "RDM", [10] = "CDM", [11] = "LDM", [12] = "RM", [13] = "RCM", [14] = "CM", [15] = "LCM", [16] = "LM",
    [17] = "RAM", [18] = "CAM", [19] = "LAM", [20] = "RF", [21] = "CF", [22] = "LF", [23] = "RW", [24] = "RS",
    [25] = "ST", [26] = "LS", [27] = "LW"
}

local function format_performance(r)
    local extra = ""
    if r.in_minute then extra = extra .. string.format(', "subInMinute": %d', r.in_minute) end
    if r.out_minute then extra = extra .. string.format(', "subOutMinute": %d', r.out_minute) end
    local label = r.pos ~= nil and POSITION_LABELS[tonumber(r.pos)] or nil
    if label then extra = extra .. string.format(', "position": "%s"', label) end
    return string.format(
        '{"playerId": "%s", "playerName": "%s", "rating": %.1f, "goals": %d, "assists": %d, "passes": %d, "passAccuracy": %d, "shots": %d, "shotAccuracy": %d, "saves": %d, "penaltySaves": %d, "tackles": %d, "tacklePercentage": %d, "interceptions": %d, "cleanSheet": %s, "yellowCard": %s, "redCard": %s, "isMotm": %s%s}',
        jstr(r.id), jstr(r.name), r.rating or 0, r.goals or 0, r.assists or 0, r.passes or 0, r.pass_acc or 0,
        r.shots or 0, r.shot_acc or 0, r.saves or 0, r.penalty_saves or 0, r.tackles or 0, r.tackle_pct or 0,
        r.interceptions or 0,
        r.clean_sheet and "true" or "false", r.yellow and "true" or "false", r.red and "true" or "false",
        r.motm and "true" or "false", extra)
end

local function format_substitution(s)
    return string.format(
        '{"minute": %d, "playerOutName": "%s", "playerInName": "%s", "playerOutId": "%s", "playerInId": "%s"}',
        s.minute, jstr(s.out_name), jstr(s.in_name), jstr(s.out_id), jstr(s.in_id))
end
-- END match_logic

-- BEGIN league_table (this exact block is also run by tests/lua/test_transfer_logic.lua)
-- The league table worked out from the season's fixtures, because the position stored in the database is only
-- refreshed now and then. fixtures/standings are the lists read_valid_fixtures() / read_valid_standings() return.
-- Only results up to max_date_raw count, so a match that is reported late still gets the position it had.
local function build_league_table(fixtures, standings, comp_obj_id, max_date_raw)
    local by_team, rows = {}, {}
    local pending_that_day = 0
    local function team(tid)
        local t = by_team[tid]
        if not t then
            t = { team_id = tid, played = 0, won = 0, drawn = 0, lost = 0, gf = 0, ga = 0, pts = 0 }
            by_team[tid] = t
            rows[#rows + 1] = t
        end
        return t
    end
    for _, f in ipairs(fixtures) do
        if f.competition_object_id == comp_obj_id then
            local h = standings[f.home_standing_index]
            local a = standings[f.away_standing_index]
            if h and a and (h.team_id or 0) > 0 and (a.team_id or 0) > 0 then
                local ht, at = team(h.team_id), team(a.team_id)   -- every club is listed, even before its first game
                local in_time = (max_date_raw == nil) or ((f.date_raw or 0) <= max_date_raw)
                if f.completed and in_time then
                    local hs, as = tonumber(f.home_score) or -1, tonumber(f.away_score) or -1
                    if hs >= 0 and hs < 60 and as >= 0 and as < 60 then
                        ht.played, at.played = ht.played + 1, at.played + 1
                        ht.gf, ht.ga = ht.gf + hs, ht.ga + as
                        at.gf, at.ga = at.gf + as, at.ga + hs
                        if hs > as then
                            ht.won, ht.pts, at.lost = ht.won + 1, ht.pts + 3, at.lost + 1
                        elseif hs < as then
                            at.won, at.pts, ht.lost = at.won + 1, at.pts + 3, ht.lost + 1
                        else
                            ht.drawn, at.drawn = ht.drawn + 1, at.drawn + 1
                            ht.pts, at.pts = ht.pts + 1, at.pts + 1
                        end
                    end
                elseif (not f.completed) and max_date_raw ~= nil and f.date_raw == max_date_raw then
                    pending_that_day = pending_that_day + 1
                end
            end
        end
    end
    table.sort(rows, function(x, y)
        if x.pts ~= y.pts then return x.pts > y.pts end
        local xd, yd = x.gf - x.ga, y.gf - y.ga
        if xd ~= yd then return xd > yd end
        if x.gf ~= y.gf then return x.gf > y.gf end
        return x.team_id < y.team_id
    end)
    local position = {}
    for i, t in ipairs(rows) do position[t.team_id] = i end
    return { rows = rows, position = position, pending_that_day = pending_that_day }
end

-- The league the club plays in: the match's own competition when it is a league match, otherwise the league the
-- club has played most matches in.
local function pick_league_comp(fixtures, match, is_league)
    if match and (match.compobjid or 0) > 0 and is_league(match.competition) then return match.compobjid end
    local counts, best, best_n = {}, nil, 0
    for _, f in ipairs(fixtures or {}) do
        if (f.compobjid or 0) > 0 and is_league(f.competition) then
            counts[f.compobjid] = (counts[f.compobjid] or 0) + 1
            if counts[f.compobjid] > best_n then best, best_n = f.compobjid, counts[f.compobjid] end
        end
    end
    return best
end
-- END league_table

-- The game's record of the club's last match, one row per player: { id, minutes, pos, sub }.
-- playerfact bit 8 marks a player who came on as a substitute.
local function read_lineup(team_id)
    local rows = {}
    for _, r in ipairs(read_table_rows("career_playerlastmatchhistory",
        { "playerid", "teamid", "minsplayed", "position", "playerfact" })) do
        if (tonumber(r.teamid) or 0) == team_id then
            local pid = tonumber(r.playerid) or 0
            if pid > 0 then
                local fact = tonumber(r.playerfact) or 0
                rows[#rows + 1] = {
                    id = pid,
                    minutes = tonumber(r.minsplayed) or 0,
                    pos = tonumber(r.position),
                    sub = (math.floor(fact / 8) % 2) == 1
                }
            end
        end
    end
    return rows
end

-- The game's own match rating for a player on a given day (career_playermatchratinghistory), loaded on first use.
local function make_rating_lookup(date_raw)
    local loaded, by_player = false, {}
    return function(pid)
        if not loaded then
            loaded = true
            for _, r in ipairs(read_table_rows("career_playermatchratinghistory", { "playerid", "rating", "date" })) do
                if date_raw and tonumber(r.date) == tonumber(date_raw) then
                    by_player[tonumber(r.playerid) or 0] = tonumber(r.rating)
                end
            end
        end
        local v = by_player[pid]
        if not v then return 0 end
        if v > 10 then v = v / 10 end
        return math.floor(v * 10 + 0.5) / 10
    end
end

-- Position in the club's league as it stood on the day of `match`, from the season's results.
-- Returns nil when it cannot be worked out, else { position, clubs, ready } (ready = all of that day's league
-- matches are in, so the position is final).
local function live_league_position(managed_team_id, match, remembered_comp)
    local comp = pick_league_comp({}, match, is_league_competition) or remembered_comp
    if not comp or comp <= 0 then return nil end
    local table_now = build_league_table(read_valid_fixtures(), read_valid_standings(), comp, tonumber(match.date_raw))
    local pos = table_now.position[managed_team_id]
    if not pos or #table_now.rows < 2 then return nil end
    return { position = pos, clubs = #table_now.rows, ready = (table_now.pending_that_day == 0) }
end

local function try_read_atlas_match_stats(managed_team_id, is_home, goals_against, folder_path, history, match)
    local participants = parse_simple_csv(folder_path .. "/16_match_participants_wide.csv")
    local teams = parse_simple_csv(folder_path .. "/18_match_teams_wide.csv")
    if not participants or not teams or #participants == 0 or #teams == 0 then
        return nil
    end

    -- Which team index (0 or 1) is the managed club? Match the export against the current squad.
    local squad = get_current_squad_details(managed_team_id)
    local function analyse(known)
        return analyse_atlas_export(participants, teams, {
            squad = known, is_home = is_home, goals_against = goals_against, player_name = safe_player_name
        })
    end
    local analysis = analyse(squad)

    -- The game's last-match record tells who replaced whom and who played besides the players the export lists
    -- (a player on loan, say). It is only trusted when it is about the same players as the export.
    local usable = false
    local substitutions = {}
    history = history or {}
    if #history > 0 then
        local function exported_ids(a)
            local set = {}
            for _, r in ipairs(a.rows) do set[r.id] = true end
            return set
        end
        if lineup_matches_export(history, exported_ids(analysis)) then
            usable = true
        else
            -- maybe the squad list missed a loaned-in player and the wrong side was picked
            local wider = {}
            for k, v in pairs(squad) do wider[k] = v end
            for _, h in ipairs(history) do
                if (h.minutes or 0) > 0 and wider[h.id] == nil then wider[h.id] = true end
            end
            local again = analyse(wider)
            if lineup_matches_export(history, exported_ids(again)) then
                analysis, usable = again, true
            end
        end
        if usable then
            substitutions = apply_lineup(analysis, history, {
                player_name = safe_player_name,
                rating_for = make_rating_lookup(match and match.date_raw)
            })
            if #analysis.added > 0 then
                log_monitor("Players the export left out were added from the game's own record: " .. table.concat(analysis.added, ", "))
            end
        else
            log_monitor("The game's last-match record is about different players than this export - substitutions are not sent")
        end
    end
    if estimate_keeper_saves(analysis, goals_against) then
        log_monitor("The export has no saves: the goalkeeper's saves are worked out from shots on target")
    end

    local player_perfs, goal_players, goal_details = {}, {}, {}
    for _, r in ipairs(analysis.rows) do
        player_perfs[#player_perfs + 1] = format_performance(r)
        for _ = 1, (r.goals or 0) do goal_players[#goal_players + 1] = r.name end
    end

    -- The export has no goal minutes, so spread them evenly over the match (flagged as estimated).
    for k, name in ipairs(goal_players) do
        local minute = math.max(1, math.min(90, math.floor(90 * k / (#goal_players + 1))))
        goal_details[#goal_details + 1] = string.format(
            '{"playerName": "%s", "minute": %d, "isOpponent": false, "minuteEstimated": true}', jstr(name), minute)
    end

    local subs_json = {}
    for _, s in ipairs(substitutions) do subs_json[#subs_json + 1] = format_substitution(s) end

    return {
        motm = analysis.motm,
        motm_is_opponent = analysis.motm_is_opponent,
        shots_for = analysis.shots_for,
        shots_against = analysis.shots_against,
        xg_for = analysis.xg_for,
        xg_against = analysis.xg_against,
        possession = analysis.possession,
        possession_estimated = analysis.possession_estimated,
        passing_accuracy = analysis.passing_accuracy,
        player_performances = player_perfs,
        goal_details = goal_details,
        opp_scorers = analysis.opp_scorers,
        substitutions = subs_json,
        max_minutes = analysis.max_minutes,
        m_shootout = analysis.m_shootout,
        o_shootout = analysis.o_shootout
    }
end

-- Key that stays unique across seasons (fixture ids repeat every season).
local function fixture_key(f)
    return tostring(f.fixture_id) .. "_" .. tostring(f.date_raw or 0)
end

local function beam_match_to_bridge(latest_match, managed_team_id, pre_match_cpi, atlas_folder_path)
    if not latest_match then return end
    local ok, err = xpcall(function()
        local isHome = latest_match.is_home
        local oppName = latest_match.opponent_name or "Opponent"
        local userClubName = safe_team_name(managed_team_id)
        local gf = to_int(latest_match.goals_for, 0)
        local ga = to_int(latest_match.goals_against, 0)
        local badge = latest_match.result or "D"
        local rawCompName = latest_match.competition or "EFL Championship"
        local matchDate = latest_match.date
        if not matchDate or matchDate == "" then
            matchDate = get_current_career_date()
        end

        local lowComp = rawCompName:lower()
        local lowStage = tostring(latest_match.stage or ""):lower()
        local is_playoff = lowComp:find("play%-?off") or lowComp:find("promotion") or lowStage:find("play%-?off")
        local is_league = is_league_competition(rawCompName)

        -- 1. League position worked out from the season's results. The position stored in the database is only
        --    refreshed now and then (it can stay at the pre-season value), so it is not used.
        --    get_real_league_position still records the primary league's name in the state.
        get_real_league_position(managed_team_id)
        local league_info = nil
        local lok, l_or_err = pcall(live_league_position, managed_team_id, latest_match, state and state.league_comp_id)
        if lok then
            league_info = l_or_err
        else
            log_monitor("League table could not be worked out (" .. tostring(l_or_err) .. ")")
        end
        local leaguePos = league_info and league_info.position or 0

        -- Rule: a playoff match is still reported under the primary domestic league
        local compName = rawCompName
        if (is_playoff or is_league) and state and state.primary_league_name and state.primary_league_name ~= "" then
            compName = state.primary_league_name
        end

        local stage = latest_match.stage or "Matchday 1"

        -- 2. Real Atlas export for this match (if the exporter produced one)
        local lineup_history = {}
        do
            local hok, h_or_err = pcall(read_lineup, managed_team_id)
            if hok and type(h_or_err) == "table" then lineup_history = h_or_err end
        end
        local atlasStats = nil
        if atlas_folder_path then
            local aok, a_or_err = pcall(try_read_atlas_match_stats, managed_team_id, isHome, ga, atlas_folder_path,
                lineup_history, latest_match)
            if aok then
                atlasStats = a_or_err
            else
                log_monitor("Atlas export could not be read (" .. tostring(a_or_err) .. ") - using database fallback")
            end
        end

        local isExtraTime = false
        local homePen = 0
        local awayPen = 0
        local is_leg_1 = (stage:lower():find("leg 1") ~= nil)

        if (is_league and not is_playoff) or is_leg_1 then
            -- League games and a playoff first leg never have extra time or penalties
            isExtraTime, homePen, awayPen = false, 0, 0
        elseif atlasStats then
            if atlasStats.m_shootout > 0 or atlasStats.o_shootout > 0 then
                homePen = isHome and atlasStats.m_shootout or atlasStats.o_shootout
                awayPen = isHome and atlasStats.o_shootout or atlasStats.m_shootout
                isExtraTime = true
            elseif atlasStats.max_minutes >= 115 then
                isExtraTime = true
            end
        else
            local raw_hp = to_int(latest_match.home_pen, 0)
            local raw_ap = to_int(latest_match.away_pen, 0)
            if raw_hp >= 255 or raw_hp < 0 then raw_hp = 0 end
            if raw_ap >= 255 or raw_ap < 0 then raw_ap = 0 end
            homePen, awayPen = raw_hp, raw_ap
            isExtraTime = (homePen > 0 or awayPen > 0)
        end
        if homePen >= 255 or homePen < 0 then homePen = 0 end
        if awayPen >= 255 or awayPen < 0 then awayPen = 0 end

        local et_label = ""
        if homePen > 0 or awayPen > 0 then
            et_label = string.format(" (Pens: %d-%d)", homePen, awayPen)
        elseif isExtraTime then
            et_label = " (A.E.T.)"
        end

        local msg = string.format("Full Time: %s %s %d - %d %s (%s - %s)%s",
            userClubName, isHome and "vs" or "@", gf, ga, oppName, compName, stage, et_label)

        local goalDetails = {}
        local playerPerfs = {}
        local topPlayer = ""
        local shotsFor, shotsAgainst, poss, passAcc = 0, 0, 50, 82
        local xG, xGA = 0.0, 0.0
        local detailSource = "estimated"
        local substitutionJson = {}
        local motmIsOpponent = false
        local possessionEstimated = true

        if atlasStats and #atlasStats.player_performances > 0 then
            detailSource = "atlas"
            topPlayer = atlasStats.motm
            shotsFor = atlasStats.shots_for
            shotsAgainst = atlasStats.shots_against
            poss = atlasStats.possession
            passAcc = atlasStats.passing_accuracy
            xG = atlasStats.xg_for
            xGA = atlasStats.xg_against
            playerPerfs = atlasStats.player_performances
            goalDetails = atlasStats.goal_details
            substitutionJson = atlasStats.substitutions
            motmIsOpponent = atlasStats.motm_is_opponent
            possessionEstimated = atlasStats.possession_estimated

            -- the export names the other side's scorers when it can; a goal it cannot name stays "<club> Goal"
            local opp_names = {}
            local named = 0
            for _, sc in ipairs(atlasStats.opp_scorers or {}) do
                for _ = 1, sc.goals do
                    opp_names[#opp_names + 1] = sc.name
                    if sc.name ~= "" then named = named + 1 end
                end
            end
            if ga > 0 and named < ga then
                -- not named by the export: the game's own season statistics know who scored
                local from_stats = opponent_scorers_from_deltas(
                    pre_match_cpi, get_player_info_snapshot(managed_team_id),
                    get_team_player_ids(latest_match.opponent_id), ga, safe_player_name)
                if from_stats then
                    opp_names = {}
                    for _, sc in ipairs(from_stats) do
                        for _ = 1, sc.goals do opp_names[#opp_names + 1] = sc.name end
                    end
                    log_monitor("The other side's scorers come from the game's season statistics")
                else
                    log_monitor("The other side's scorers could not be named: their goals are sent as \"" .. oppName .. " Goal\"")
                end
            end
            for j = 1, ga do
                local minute = math.max(1, math.min(90, math.floor(90 * j / (ga + 1))))
                local scorer = opp_names[j]
                if scorer == nil or scorer == "" then scorer = oppName .. " Goal" end
                goalDetails[#goalDetails + 1] = string.format(
                    '{"playerName": "%s", "minute": %d, "isOpponent": true, "minuteEstimated": true}',
                    jstr(scorer), minute)
            end
        else
            -- Database fallback: real goal/assist/card changes from career_playerinfo between the
            -- pre-match and post-match snapshots, applied to the actual lineup positions.
            local post_cpi = get_player_info_snapshot(managed_team_id)
            local _, starters, subs = get_current_squad_details(managed_team_id)

            local delta = {}
            local real_scorers = {}
            if pre_match_cpi and post_cpi then
                for pid, cur in pairs(post_cpi) do
                    local prev = pre_match_cpi[pid]
                    if prev then
                        local dg = cur.goals - prev.goals
                        local da = cur.assists - prev.assists
                        local dy = cur.yellowcards - prev.yellowcards
                        local dr = cur.redcards - prev.redcards
                        if dg > 0 or da > 0 or dy > 0 or dr > 0 then
                            delta[pid] = { g = math.max(dg, 0), a = math.max(da, 0), y = math.max(dy, 0), r = math.max(dr, 0) }
                        end
                        if dg > 0 then
                            local pName = safe_player_name(pid)
                            for _ = 1, dg do real_scorers[#real_scorers + 1] = pName end
                        end
                    end
                end
            end

            -- Who actually played comes from the game's record of the last match; the current squad is only used
            -- when that record is missing (it lists whoever is in the squad now, not who played).
            local match_participants = {}
            if lineup_matches_export(lineup_history, nil) then
                local pair_list, marks = pair_substitutions(lineup_history)
                for _, h in ipairs(lineup_history) do
                    if (h.minutes or 0) > 0 then
                        local m = marks[h.id] or {}
                        match_participants[#match_participants + 1] = {
                            id = h.id, name = safe_player_name(h.id), position = safe_position_name(h.pos),
                            in_minute = m.in_minute, out_minute = m.out_minute
                        }
                    end
                end
                table.sort(match_participants, function(a, b)
                    local ai, bi = a.in_minute or 0, b.in_minute or 0
                    if ai ~= bi then return ai < bi end
                    return a.id < b.id
                end)
                for _, pr in ipairs(pair_list) do
                    substitutionJson[#substitutionJson + 1] = format_substitution({
                        minute = pr.minute,
                        out_id = tostring(pr.out.id), out_name = safe_player_name(pr.out.id),
                        in_id = tostring(pr["in"].id), in_name = safe_player_name(pr["in"].id)
                    })
                end
            else
                for _, sp in ipairs(starters) do match_participants[#match_participants + 1] = sp end
                for s_idx = 1, math.min(#subs, 5) do match_participants[#match_participants + 1] = subs[s_idx] end
            end

            if #real_scorers == 0 and gf > 0 then
                for _, sp in ipairs(starters) do
                    local pos = tostring(sp.position):upper()
                    if pos:find("F") or pos:find("W") or pos:find("S") or pos == "ST" or pos == "CF" or pos == "CAM" then
                        real_scorers[#real_scorers + 1] = sp.name
                        if #real_scorers >= gf then break end
                    end
                end
            end

            for i = 1, gf do
                local sName = real_scorers[i] or (userClubName .. " Striker")
                local minute = math.max(1, math.min(90, math.floor(90 * i / (gf + 1))))
                goalDetails[#goalDetails + 1] = string.format(
                    '{"playerName": "%s", "minute": %d, "isOpponent": false, "minuteEstimated": true}', jstr(sName), minute)
            end
            for j = 1, ga do
                local minute = math.max(1, math.min(90, math.floor(90 * j / (ga + 1))))
                goalDetails[#goalDetails + 1] = string.format(
                    '{"playerName": "%s", "minute": %d, "isOpponent": true, "minuteEstimated": true}',
                    jstr(oppName .. " Goal"), minute)
            end

            local topRating = -1.0
            for _, p in ipairs(match_participants) do
                local d = delta[p.id] or { g = 0, a = 0, y = 0, r = 0 }
                local isScorer = d.g > 0
                for _, sName in ipairs(real_scorers) do
                    if sName == p.name then isScorer = true break end
                end

                local pRating = 6.8
                if isScorer then pRating = pRating + 1.2 end
                if d.a > 0 then pRating = pRating + 0.6 end
                if ga == 0 and (p.position == "GK" or tostring(p.position):find("B")) then pRating = pRating + 0.8 end
                if badge == "W" then pRating = pRating + 0.3 end
                if badge == "L" then pRating = pRating - 0.2 end
                pRating = math.max(5.5, math.min(9.5, pRating))

                if pRating > topRating then
                    topRating = pRating
                    topPlayer = p.name
                end

                local goalsHere = d.g
                if goalsHere == 0 and isScorer then goalsHere = 1 end
                playerPerfs[#playerPerfs + 1] = format_performance({
                    id = p.id, name = p.name, rating = pRating, goals = goalsHere, assists = d.a,
                    shots = (goalsHere > 0) and (goalsHere * 2) or 0,
                    clean_sheet = (ga == 0), yellow = (d.y > 0), red = (d.r > 0), motm = false,
                    in_minute = p.in_minute, out_minute = p.out_minute
                })
            end

            if topPlayer == "" then
                topPlayer = (#real_scorers > 0 and real_scorers[1]) or (#starters > 0 and starters[1].name) or (userClubName .. " Captain")
            end

            shotsFor = gf * 3 + 2
            shotsAgainst = ga * 2 + 1
            xG = math.floor((gf * 0.85 + 0.25) * 100) / 100
            xGA = math.floor((ga * 0.75 + 0.15) * 100) / 100
            poss = (gf > ga) and 54 or ((gf == ga) and 50 or 47)
            passAcc = 84
        end

        local parts = {
            '"dataType": "match"',
            '"title": "Live Match Final"',
            string.format('"message": "%s"', jstr(msg)),
            string.format('"opponentName": "%s"', jstr(oppName)),
            string.format('"isHome": %s', isHome and "true" or "false"),
            string.format('"goalsFor": %d', gf),
            string.format('"goalsAgainst": %d', ga),
            string.format('"resultBadge": "%s"', jstr(badge)),
            string.format('"date": "%s"', jstr(matchDate)),
            string.format('"competition": "%s"', jstr(compName)),
            string.format('"stage": "%s"', jstr(stage)),
            string.format('"isExtraTime": %s', isExtraTime and "true" or "false"),
            string.format('"homePenalties": %d', homePen),
            string.format('"awayPenalties": %d', awayPen),
            string.format('"shotsFor": %d', to_int(shotsFor)),
            string.format('"shotsAgainst": %d', to_int(shotsAgainst)),
            string.format('"possession": %d', to_int(poss)),
            string.format('"passingAccuracy": %d', to_int(passAcc)),
            string.format('"expectedGoals": %.2f', to_num(xG)),
            string.format('"expectedGoalsAgainst": %.2f', to_num(xGA)),
            string.format('"motmName": "%s"', jstr(topPlayer)),
            string.format('"possessionEstimated": %s', possessionEstimated and "true" or "false"),
            string.format('"motmIsOpponent": %s', motmIsOpponent and "true" or "false"),
            string.format('"leaguePosition": %s', (leaguePos > 0) and tostring(to_int(leaguePos)) or "null"),
            string.format('"detailSource": "%s"', detailSource),
            string.format('"fixtureKey": "%s"', jstr(fixture_key(latest_match))),
            '"playerPerformances": [' .. table.concat(playerPerfs, ",") .. "]",
            '"substitutions": [' .. table.concat(substitutionJson, ",") .. "]",
            '"goalDetails": [' .. table.concat(goalDetails, ",") .. "]"
        }
        local payload = "{\n  " .. table.concat(parts, ",\n  ") .. "\n}"

        log_monitor(string.format("Match detail source: %s (%d player rows)", detailSource, #playerPerfs))
        send_payload_to_bridge(payload, "Match: " .. msg)
    end, debug.traceback)
    if not ok then
        log_monitor("beam_match_to_bridge FAILED: " .. tostring(err))
    end
end

-- Returns "YYYY-MM-DD", y, m, d or nil when the value is not a date we recognise.
local function decode_fifa_date(days)
    days = tonumber(days) or 0
    if days > 19000000 and days < 21000000 then
        local y = math.floor(days / 10000)
        local m = math.floor((days % 10000) / 100)
        local d = days % 100
        return string.format("%04d-%02d-%02d", y, m, d), y, m, d
    elseif days > 100000 and days < 250000 then
        -- EA day count (days since 1582-10-14)
        local jd = days + 2299160
        local l = jd + 68569
        local n = math.floor((4 * l) / 146097)
        l = l - math.floor((146097 * n + 3) / 4)
        local i = math.floor((4000 * (l + 1)) / 1461001)
        l = l - math.floor((1461 * i) / 4) + 31
        local j = math.floor((80 * l) / 2447)
        local d = l - math.floor((2447 * j) / 80)
        l = math.floor(j / 11)
        local m = j + 2 - (12 * l)
        local y = 100 * (n - 49) + i + l
        return string.format("%04d-%02d-%02d", y, m, d), y, m, d
    end
    return nil
end

-- BEGIN player_profile (this exact block is also run by tests/lua/test_transfer_logic.lua)
-- A player's details from his players row: his positions, birthday, nation (the game's id), height, weight, foot,
-- weak foot and ratings. They go with a signing, so the app's new card is right from the start, and with the career
-- report, so a card that was made with guessed details is corrected. Nothing is guessed here: a value that is missing
-- or impossible is left out. Needs to_int and decode_fifa_date (above). One table, PROFILE: the main chunk is near
-- Lua's limit of 200 locals.
local PROFILE = {}
do
    PROFILE.FIELDS = { "playerid", "preferredposition1", "preferredposition2", "preferredposition3", "birthdate", "nationality",
        "height", "weight", "preferredfoot", "weakfootabilitytypecode", "overallrating", "potential" }
    -- preferredposition 0-27, named the way the app names positions (left and right variants as the central one)
    PROFILE.POSITIONS = { [0] = "GK", "CB", "RWB", "RB", "CB", "CB", "CB", "LB", "LWB", "CDM", "CDM", "CDM", "RM", "CM",
        "CM", "CM", "LM", "CAM", "CAM", "CAM", "CF", "CF", "CF", "RW", "ST", "ST", "ST", "LW" }

    local function value(row, field)
        local v = row[field]
        if type(v) == "table" then v = v.value end
        return v
    end

    -- row: a players row (plain values or { value = ... } cells). Returns nil when it carries nothing usable.
    function PROFILE.from_row(row)
        if type(row) ~= "table" then return nil end
        local p, positions, seen = {}, {}, {}
        for i = 1, 3 do
            local id = tonumber(value(row, "preferredposition" .. i))
            local name = id and id >= 0 and PROFILE.POSITIONS[math.floor(id)] or nil
            if name and not seen[name] then
                seen[name] = true
                positions[#positions + 1] = name
            end
        end
        if #positions > 0 then p.positions = positions end
        local iso, y = decode_fifa_date(value(row, "birthdate"))
        if iso and y >= 1900 and y <= 2200 then p.birthDate = iso end
        local nation = to_int(value(row, "nationality"), 0)
        if nation > 0 and nation < 1000 then p.nation = nation end
        local height, weight = to_int(value(row, "height"), 0), to_int(value(row, "weight"), 0)
        if height >= 140 and height <= 220 then p.height = height end
        if weight >= 40 and weight <= 130 then p.weight = weight end
        local foot = to_int(value(row, "preferredfoot"), 0)
        if foot == 1 then p.foot = "Right" elseif foot == 2 then p.foot = "Left" end
        local weak = to_int(value(row, "weakfootabilitytypecode"), 0)
        if weak >= 1 and weak <= 5 then p.weakFoot = weak end
        local overall, potential = to_int(value(row, "overallrating"), 0), to_int(value(row, "potential"), 0)
        if overall >= 1 and overall <= 99 then p.overall = overall end
        if potential >= 1 and potential <= 99 then p.potential = potential end
        if next(p) == nil then return nil end
        return p
    end

    -- The profile as JSON, the keys always in the same order (the same player always gives the same text).
    function PROFILE.json(p)
        local parts = {}
        if p.positions then
            local names = {}
            for i, n in ipairs(p.positions) do names[i] = '"' .. n .. '"' end
            parts[#parts + 1] = '"positions": [' .. table.concat(names, ", ") .. ']'
        end
        if p.birthDate then parts[#parts + 1] = '"birthDate": "' .. p.birthDate .. '"' end
        for _, k in ipairs({ "nation", "height", "weight" }) do
            if p[k] then parts[#parts + 1] = string.format('"%s": %d', k, p[k]) end
        end
        if p.foot then parts[#parts + 1] = '"foot": "' .. p.foot .. '"' end
        for _, k in ipairs({ "weakFoot", "overall", "potential" }) do
            if p[k] then parts[#parts + 1] = string.format('"%s": %d', k, p[k]) end
        end
        return "{" .. table.concat(parts, ", ") .. "}"
    end
end
-- END player_profile

-- Profiles of the wanted players (wanted[pid] = true), read from the players table: pid -> profile.
function PROFILE.read(wanted)
    local out = {}
    if type(wanted) ~= "table" or next(wanted) == nil then return out end
    pcall(function()
        if type(GetDBTableRows) == "function" then
            local rows = GetDBTableRows("players")
            if type(rows) == "table" then
                for _, r in ipairs(rows) do
                    local cell = r.playerid
                    local pid = tonumber(type(cell) == "table" and cell.value or cell) or 0
                    if pid > 0 and wanted[pid] then out[pid] = PROFILE.from_row(r) end
                end
            end
        end
    end)
    if next(out) ~= nil then return out end
    pcall(function()
        if LE and LE.db then
            local pt = LE.db:GetTable("players")
            if pt then
                local r = pt:GetFirstRecord()
                while r and r > 0 do
                    local pid = tonumber(pt:GetRecordFieldValue(r, "playerid")) or 0
                    if pid > 0 and wanted[pid] then
                        local row = {}
                        for _, f in ipairs(PROFILE.FIELDS) do
                            local rec = r
                            local ok, v = pcall(function() return pt:GetRecordFieldValue(rec, f) end)
                            if ok then row[f] = v end
                        end
                        out[pid] = PROFILE.from_row(row)
                    end
                    r = pt:GetNextValidRecord()
                end
            end
        end
    end)
    return out
end

function get_current_career_date()
    local ok, d = pcall(function() return GetCurrentDate() end)
    if ok and d and d.year and d.year > 2000 then
        return string.format("%04d-%02d-%02d", d.year, d.month, d.day), d.year, d.month, d.day
    end

    local y, m, day = 0, 0, 0
    pcall(function()
        if type(GetDBTableRows) == "function" then
            local rows = GetDBTableRows("career_calendar")
            if type(rows) == "table" and #rows > 0 then
                for _, r in ipairs(rows) do
                    local rawD = tonumber(r.currdate and r.currdate.value or r.currdate)
                              or tonumber(r.caldate and r.caldate.value or r.caldate) or 0
                    if rawD > 20200000 then
                        y = math.floor(rawD / 10000)
                        m = math.floor((rawD % 10000) / 100)
                        day = rawD % 100
                        break
                    end
                end
            end
        end
    end)
    if y > 2000 then
        return string.format("%04d-%02d-%02d", y, m, day), y, m, day
    end

    pcall(function()
        if LE and LE.db then
            local cc = LE.db:GetTable("career_calendar")
            if cc then
                local r = cc:GetFirstRecord()
                while r and r > 0 do
                    local rawD = tonumber(cc:GetRecordFieldValue(r, "currdate")) or tonumber(cc:GetRecordFieldValue(r, "caldate")) or 0
                    if rawD > 20200000 then
                        y = math.floor(rawD / 10000)
                        m = math.floor((rawD % 10000) / 100)
                        day = rawD % 100
                        break
                    end
                    r = cc:GetNextValidRecord()
                end
            end
        end
    end)
    if y > 2000 then
        return string.format("%04d-%02d-%02d", y, m, day), y, m, day
    end

    -- Last resort: deduce the season year from the number of completed seasons
    local pastSeasons = 0
    pcall(function()
        if type(GetDBTableRows) == "function" then
            local mh = GetDBTableRows("career_managerhistory")
            if type(mh) == "table" then pastSeasons = #mh end
        end
    end)
    local deducedYear = 2025 + pastSeasons
    return string.format("%04d-07-01", deducedYear), deducedYear, 7, 1
end

-- Teams that play in a league (clubs). A player also has links to national / special teams; when we
-- remember "which club did he come from" those must be ignored. Empty set = unknown, accept everything.
local club_team_cache = nil
local function get_club_team_set()
    if club_team_cache then return club_team_cache end
    local set, count = {}, 0
    pcall(function()
        if type(GetDBTableRows) == "function" then
            local rows = GetDBTableRows("leagueteamlinks")
            if type(rows) == "table" then
                for _, r in ipairs(rows) do
                    local tid = tonumber(r.teamid and r.teamid.value or r.teamid) or 0
                    if tid > 0 then set[tid] = true count = count + 1 end
                end
            end
        end
    end)
    if count == 0 then
        pcall(function()
            if LE and LE.db then
                local tbl = LE.db:GetTable("leagueteamlinks")
                if tbl then
                    local r = tbl:GetFirstRecord()
                    while r and r > 0 do
                        local tid = tonumber(tbl:GetRecordFieldValue(r, "teamid")) or 0
                        if tid > 0 then set[tid] = true count = count + 1 end
                        r = tbl:GetNextValidRecord()
                    end
                end
            end
        end)
    end
    if count > 0 then club_team_cache = set end
    return set
end

-- Returns: managed squad (pid -> true), pid -> club team id for everyone, and pid -> list of every club
-- team the player is linked to (a loaned player can be linked to both his parent club and the loan club).
local function get_managed_squad_state(managed_tid)
    local squad = {}
    local all_links = {}
    local club_links = {}
    local clubs = get_club_team_set()
    local have_clubs = next(clubs) ~= nil

    local function consider(pid, tid)
        if pid > 0 and tid > 0 then
            if tid == managed_tid then squad[pid] = true end
            if (not have_clubs) or clubs[tid] then
                all_links[pid] = tid
                local list = club_links[pid]
                if not list then list = {} club_links[pid] = list end
                list[#list + 1] = tid
            end
        end
    end

    pcall(function()
        if LE and LE.db then
            local tpl = LE.db:GetTable("teamplayerlinks")
            if tpl then
                local r = tpl:GetFirstRecord()
                while r and r > 0 do
                    consider(tonumber(tpl:GetRecordFieldValue(r, "playerid")) or 0,
                             tonumber(tpl:GetRecordFieldValue(r, "teamid")) or 0)
                    r = tpl:GetNextValidRecord()
                end
            end
        end
    end)

    if next(squad) == nil then
        pcall(function()
            if type(GetDBTableRows) == "function" then
                local rows = GetDBTableRows("teamplayerlinks")
                if type(rows) == "table" then
                    for _, row in ipairs(rows) do
                        consider(tonumber(row.playerid and row.playerid.value or row.playerid) or 0,
                                 tonumber(row.teamid and row.teamid.value or row.teamid) or 0)
                    end
                end
            end
        end)
    end

    return squad, all_links, club_links
end

-- BEGIN classify_loans (this exact block is also run by tests/lua/test_transfer_logic.lua)
-- FC 26's "playerloans" table only says which club a player is loaned FROM (teamidloanedfrom). It has no
-- "loaned to" column: asking for one makes Live Editor log "no field teamidloanedto" and gives nothing back.
-- So the direction is worked out from the club links instead (FC26_Snapshot.lua does the same):
--   * parent club is the managed club                          -> the player is loaned OUT; he is now linked to
--                                                                 another club, and that is the destination
--   * parent club is another club and the player is in our squad -> loaned IN from that club
-- rows: list of { pid =, from = }; squad: pid -> true; club_links: pid -> list of club team ids.
-- Returns loans_in[pid] = parent club id and loans_out[pid] = destination club id (0 when it cannot be read).
local function classify_loans(rows, managed_tid, squad, club_links)
    local loans_in, loans_out = {}, {}
    for _, row in ipairs(rows) do
        local pid, from_tid = row.pid or 0, row.from or 0
        if pid > 0 then
            if from_tid == managed_tid then
                local destination = 0
                for _, tid in ipairs((club_links or {})[pid] or {}) do
                    if tid > 0 and tid ~= managed_tid then destination = tid break end
                end
                loans_out[pid] = destination
            elseif from_tid > 0 and squad and squad[pid] then
                loans_in[pid] = from_tid
            end
        end
    end
    return loans_in, loans_out
end

-- A loaned-out player can stay linked to the club, so he never leaves the squad list and a loan can only be seen
-- through playerloans. These are the loans out that were not there at the last look (sorted, so deals and log lines
-- come in a stable order). A player who is signed and loaned out a week later is a second deal of his own.
local function new_loans_out(loans_out, known)
    local fresh = {}
    for pid in pairs(loans_out or {}) do
        if not (known or {})[pid] then fresh[#fresh + 1] = pid end
    end
    table.sort(fresh)
    return fresh
end
-- END classify_loans

local function read_loan_rows()
    local rows = {}
    pcall(function()
        if LE and LE.db then
            local pl = LE.db:GetTable("playerloans")
            if pl then
                local r = pl:GetFirstRecord()
                while r and r > 0 do
                    local ended = nil
                    pcall(function() ended = decode_fifa_date(pl:GetRecordFieldValue(r, "loandateend")) end)
                    rows[#rows + 1] = {
                        pid = tonumber(pl:GetRecordFieldValue(r, "playerid")) or 0,
                        from = tonumber(pl:GetRecordFieldValue(r, "teamidloanedfrom")) or 0,
                        ends = ended,
                        to_buy = (tonumber(pl:GetRecordFieldValue(r, "isloantobuy")) or 0) == 1
                    }
                    r = pl:GetNextValidRecord()
                end
            end
        end
    end)
    if #rows == 0 then
        pcall(function()
            if type(GetDBTableRows) == "function" then
                local raw = GetDBTableRows("playerloans")
                if type(raw) == "table" then
                    for _, r in ipairs(raw) do
                        rows[#rows + 1] = {
                            pid = tonumber(r.playerid and r.playerid.value or r.playerid) or 0,
                            from = tonumber(r.teamidloanedfrom and r.teamidloanedfrom.value or r.teamidloanedfrom) or 0,
                            ends = decode_fifa_date(r.loandateend and r.loandateend.value or r.loandateend),
                            to_buy = (tonumber(r.isloantobuy and r.isloantobuy.value or r.isloantobuy) or 0) == 1
                        }
                    end
                end
            end
        end)
    end
    return rows
end

local function get_active_loans(managed_tid, squad, club_links)
    return classify_loans(read_loan_rows(), managed_tid, squad, club_links)
end

-- Join dates (EA day count or yyyymmdd) of the wanted players, from the player table: pid -> { join_days = n }.
-- Only two fields are read, so the 20 000+ rows of the table stay cheap.
local function get_player_join_info(wanted_pids)
    local info_map = {}
    local function add(pid, join_days)
        if pid > 0 and wanted_pids[pid] then
            info_map[pid] = { join_days = tonumber(join_days) or 0 }
        end
    end
    pcall(function()
        if LE and LE.db then
            local pt = LE.db:GetTable("players")
            if pt then
                local r = pt:GetFirstRecord()
                while r and r > 0 do
                    local pid = tonumber(pt:GetRecordFieldValue(r, "playerid")) or 0
                    if pid > 0 and wanted_pids[pid] then
                        add(pid, pt:GetRecordFieldValue(r, "playerjointeamdate"))
                    end
                    r = pt:GetNextValidRecord()
                end
            end
        end
    end)
    if next(info_map) == nil then
        pcall(function()
            if type(GetDBTableRows) == "function" then
                local rows = GetDBTableRows("players")
                if type(rows) == "table" then
                    for _, r in ipairs(rows) do
                        local pid = tonumber(r.playerid and r.playerid.value or r.playerid) or 0
                        if pid > 0 and wanted_pids[pid] then
                            add(pid, r.playerjointeamdate and r.playerjointeamdate.value or r.playerjointeamdate)
                        end
                    end
                end
            end
        end)
    end
    return info_map
end

-- Reads a small career table into plain { field = value } rows. GetDBTableRows comes first because it gives real
-- column names and text columns (the names in career_managerhistory); record-by-record access is the fallback.
-- Only ask for columns that exist: Live Editor logs "no field X" for a missing one.
-- Players in the club's youth academy right now (career_youthplayers). A player who leaves this list and joins the
-- first team was promoted, not bought.
local function read_youth_set()
    local set = {}
    for _, row in ipairs(read_table_rows("career_youthplayers", { "playerid" })) do
        local pid = tonumber(row.playerid) or 0
        if pid > 0 then set[pid] = true end
    end
    return set
end

-- Contracts of the managed club (career_playercontract): pid -> { signon, wage, date, changed, status, split }.
-- "date" is when the contract was signed (the day a signing joined); for a loan, "changed" is the day the loan began.
local function read_contracts(managed_tid)
    local map = {}
    local fields = { "playerid", "teamid", "contract_date", "last_status_change_date", "signon_bonus", "wage",
                     "contract_status", "loan_wage_split" }
    for _, row in ipairs(read_table_rows("career_playercontract", fields)) do
        local pid = tonumber(row.playerid) or 0
        local tid = tonumber(row.teamid) or 0
        if pid > 0 and (tid == managed_tid or tid == 0) then
            map[pid] = {
                signon = tonumber(row.signon_bonus) or 0,
                wage = tonumber(row.wage) or 0,
                date = decode_fifa_date(row.contract_date),
                changed = decode_fifa_date(row.last_status_change_date),
                status = tonumber(row.contract_status) or 0,
                split = tonumber(row.loan_wage_split) or -1
            }
        end
    end
    return map
end

-- The biggest signing and the biggest sale of the current season, from career_managerhistory (the last season row of
-- the managed club). The game keeps only the SURNAME and the fee of those two deals; every other fee is not stored.
local function read_big_deals(managed_tid)
    local fields = { "teamid", "season", "bigbuyplayername", "bigbuyamount", "bigsellplayername", "bigsellamount" }
    local best = nil
    for _, row in ipairs(read_table_rows("career_managerhistory", fields)) do
        local tid = tonumber(row.teamid) or 0
        local season = tonumber(row.season) or 0
        if (tid == managed_tid or tid == 0) and (not best or season >= best.season) then
            best = { season = season, row = row }
        end
    end
    local deals = {}
    if best then
        local function fee_m(amount)
            amount = tonumber(amount) or 0
            return math.floor(amount / 10000 + 0.5) / 100
        end
        local buy_name = trim(best.row.bigbuyplayername)
        local sell_name = trim(best.row.bigsellplayername)
        if buy_name ~= "" and (tonumber(best.row.bigbuyamount) or 0) > 0 then
            deals.buy_name, deals.buy_fee = buy_name, fee_m(best.row.bigbuyamount)
        end
        if sell_name ~= "" and (tonumber(best.row.bigsellamount) or 0) > 0 then
            deals.sell_name, deals.sell_fee = sell_name, fee_m(best.row.bigsellamount)
        end
    end
    return deals
end

-- BEGIN transfer_rules (this exact block is also run by tests/lua/test_transfer_logic.lua)
-- What an FC 26 career save really holds about transfers (checked against a real save and the game's own
-- transfer-history screen):
--   * there is NO transfer-history table, so fees and clubs are not stored for most deals;
--   * career_managerhistory keeps the surname and fee of the single biggest signing and sale of each season;
--   * a player's join date (players.playerjointeamdate) is the day he joined his CURRENT club: for a signing that is
--     the signing day, for a player we sold it is the sale day at the buyer;
--   * career_playercontract has the signing day (contract_date), the sign-on bonus and, for loans, the day the loan
--     began (last_status_change_date);
--   * career_youthplayers lists the academy: a player who leaves that list for the first team was promoted.
-- Whatever cannot be read is reported as unknown (feeUnknown / "Unknown club"), never guessed.

local function norm_name(s)
    local t = tostring(s or ""):lower()
    t = t:gsub("^%s+", ""):gsub("%s+$", ""):gsub("%s+", " ")
    return t
end

-- The manager history stores only a surname ("Guerra"). A full name fits when it is equal or ends with " surname".
local function name_matches(full_name, short_name)
    local f, s = norm_name(full_name), norm_name(short_name)
    if f == "" or s == "" then return false end
    if f == s then return true end
    return #f > #s and f:sub(-(#s + 1)) == " " .. s
end

-- candidates: list of { pid =, name = }. Returns the pid only when exactly one candidate has that surname.
local function match_big_deal(short_name, candidates)
    if not short_name or short_name == "" then return nil end
    local found = nil
    for _, c in ipairs(candidates) do
        if name_matches(c.name, short_name) then
            if found then return nil end
            found = c.pid
        end
    end
    return found
end

-- A date read from the save is trusted only when it lies inside the current season so far (ISO strings compare
-- correctly as text). Anything else (an old contract date, an unreadable value) falls back to the career date.
local function trusted_date(iso, season_start, today)
    if type(iso) == "string" and iso >= season_start and iso <= today then return iso end
    return nil
end

-- A loan's start day is the contract's last status change. It is used even when the loan began in an earlier season
-- (a loan can run for years), as long as it is a real date that is not in the future.
local function past_date(iso, today)
    if type(iso) == "string" and iso <= today then return iso end
    return nil
end

-- a: loan_parent (club id or nil), was_youth, have_youth_list, is_big_buy, signon (sign-on bonus or nil)
-- Returns the transfer type and the reason (the reason is written to the monitor log).
local function classify_arrival(a)
    if a.loan_parent then return "In (Loan)", "on loan from another club" end
    if a.was_youth then return "Promoted In", "was in the youth academy list" end
    if a.is_big_buy then return "In (Permanent)", "named as the season's biggest signing" end
    if a.have_youth_list then return "In (Permanent)", "not in the youth academy list" end
    -- No academy list from an earlier session to compare with. Promoted youth get a first contract without a sign-on
    -- bonus, signings and free agents get one. This is a best guess and is said so in the log.
    if (a.signon or 0) > 0 then return "In (Permanent)", "has a sign-on bonus (guess)" end
    return "Promoted In", "no sign-on bonus (guess)"
end

-- ctx: managed_tid, today, season_start, season_year, loans_in, loans_out, all_links, previous_teams, youth_before,
--      contracts, info, decode(raw)->iso, team_name(tid), player_name(pid), big_buy_pid/big_buy_fee,
--      big_sell_pid/big_sell_fee
-- Each describe_* returns a table ready to send: playerName, type, feeM, feeUnknown, fromToClub, date, message,
-- key (stable for the whole season, so a deal is sent once) and why.
local function describe_arrival(pid, ctx)
    local name = ctx.player_name(pid)
    local info = ctx.info[pid] or {}
    local contract = ctx.contracts[pid]
    local join = trusted_date(ctx.decode(info.join_days), ctx.season_start, ctx.today)
    local signed = contract and trusted_date(contract.date, ctx.season_start, ctx.today)
    local loan_parent = ctx.loans_in[pid]
    local youth_list = ctx.youth_before
    local kind, why = classify_arrival({
        loan_parent = loan_parent,
        was_youth = youth_list and youth_list[pid] or false,
        have_youth_list = youth_list ~= nil and next(youth_list) ~= nil,
        is_big_buy = (ctx.big_buy_pid == pid),
        signon = contract and contract.signon
    })
    local d = { pid = pid, playerName = name, type = kind, why = why, feeM = 0.0, feeUnknown = false }
    if kind == "In (Loan)" then
        d.fromToClub = ctx.team_name(loan_parent)
        d.date = (contract and past_date(contract.changed, ctx.today)) or join or ctx.today
        d.message = string.format("%s joined on loan from %s.", name, d.fromToClub)
        d.key = string.format("V25_LOANIN_%d_%d_%d", pid, loan_parent, ctx.season_year)
    elseif kind == "Promoted In" then
        d.fromToClub = "Youth Academy"
        d.date = join or signed or ctx.today
        d.message = string.format("%s was promoted from the youth academy.", name)
        d.key = string.format("V25_IN_%d_%d", pid, ctx.season_year)
    else
        local prev = ctx.previous_teams[pid]
        d.fromToClub = (prev and prev > 0 and prev ~= ctx.managed_tid) and ctx.team_name(prev) or "Unknown club"
        d.date = join or signed or ctx.today
        if ctx.big_buy_pid == pid and ctx.big_buy_fee then
            d.feeM = ctx.big_buy_fee
            d.message = string.format("%s signed for EUR %.2fM.", name, d.feeM)
        else
            d.feeUnknown = true
            d.message = string.format("%s signed (fee not stored in the save).", name)
        end
        d.key = string.format("V25_IN_%d_%d", pid, ctx.season_year)
    end
    return d
end

local function describe_departure(pid, ctx)
    local name = ctx.player_name(pid)
    local info = ctx.info[pid] or {}
    local contract = ctx.contracts[pid]
    local current = ctx.all_links[pid] or 0
    if current == ctx.managed_tid then current = 0 end
    local loan_dest = ctx.loans_out[pid]
    local d = { pid = pid, playerName = name, feeM = 0.0, feeUnknown = false }
    if loan_dest ~= nil then
        local dest = (loan_dest > 0) and loan_dest or current
        d.type = "Out (Loan)"
        d.fromToClub = (dest > 0) and ctx.team_name(dest) or "Unknown club"
        d.date = (contract and past_date(contract.changed, ctx.today)) or ctx.today
        d.message = (dest > 0) and string.format("%s loaned out to %s.", name, d.fromToClub)
            or string.format("%s loaned out.", name)
        d.key = string.format("V25_LOANOUT_%d_%d_%d", pid, dest, ctx.season_year)
        d.why = "listed in playerloans with us as the parent club"
    else
        local released = current <= 0
        d.type = "Out (Permanent)"
        d.fromToClub = released and "Free Agent / Released" or ctx.team_name(current)
        d.date = trusted_date(ctx.decode((ctx.info[pid] or {}).join_days), ctx.season_start, ctx.today) or ctx.today
        if released then
            d.message = string.format("%s left the club.", name)
            d.why = "no longer linked to any club"
        elseif ctx.big_sell_pid == pid and ctx.big_sell_fee then
            d.feeM = ctx.big_sell_fee
            d.message = string.format("%s sold to %s for EUR %.2fM.", name, d.fromToClub, d.feeM)
            d.why = "named as the season's biggest sale"
        else
            d.feeUnknown = true
            d.message = string.format("%s sold to %s (fee not stored in the save).", name, d.fromToClub)
            d.why = "now at another club"
        end
        d.key = string.format("V25_OUT_%d_%d", pid, ctx.season_year)
    end
    return d
end

-- Finds who the season's biggest signing / sale was among the players that just arrived / left.
local function assign_big_deals(ctx, deals, arrival_pids, departure_pids)
    local function candidates(pids)
        local list = {}
        for _, pid in ipairs(pids) do list[#list + 1] = { pid = pid, name = ctx.player_name(pid) } end
        return list
    end
    ctx.big_buy_pid, ctx.big_buy_fee = nil, nil
    ctx.big_sell_pid, ctx.big_sell_fee = nil, nil
    if deals.buy_name then
        local pid = match_big_deal(deals.buy_name, candidates(arrival_pids))
        if pid then ctx.big_buy_pid, ctx.big_buy_fee = pid, deals.buy_fee end
    end
    if deals.sell_name then
        local pid = match_big_deal(deals.sell_name, candidates(departure_pids))
        if pid then ctx.big_sell_pid, ctx.big_sell_fee = pid, deals.sell_fee end
    end
end
-- END transfer_rules

-- BEGIN manual_transfers (this exact block is also run by tests/lua/test_transfer_logic.lua)
-- Deals the game does not store (most fees, anything from before the script was first run) can be listed by hand in
-- Documents\FC26 Export\manual_transfers.csv, one per line:   date,player,type,fee_m,club
--   2028-07-06,Pedro Lima,Out (Permanent),20.0,Brighton
-- type: In (Permanent) | Out (Permanent) | In (Loan) | Out (Loan) | Promoted In (short forms such as in, out, loan in,
-- loan out, promoted are fine); fee_m: millions, "free", or empty when unknown; a leading # makes a comment line.
local function parse_manual_fee(text)
    local t = tostring(text or ""):lower():gsub("\226\130\172", ""):gsub("\194\163", ""):gsub("[%s$,]", "")
    if t == "" or t == "?" or t == "unknown" then return 0.0, true end
    if t == "free" or t == "0" then return 0.0, false end
    local num, unit = t:match("^([%d%.]+)([mk]?)")
    num = tonumber(num)
    if not num then return 0.0, true end
    if unit == "k" then num = num / 1000 end
    return math.floor(num * 100 + 0.5) / 100, false
end

local function parse_manual_type(text)
    local t = tostring(text or ""):lower():gsub("%s+", " "):gsub("^%s+", ""):gsub("%s+$", "")
    if t == "promoted in" or t == "promoted" or t == "promotion" then return "Promoted In" end
    local loan = t:find("loan", 1, true) ~= nil
    if t:find("out", 1, true) or t == "sold" or t == "sale" or t == "sell" then
        return loan and "Out (Loan)" or "Out (Permanent)"
    end
    if t:find("in", 1, true) or t == "signed" or t == "signing" or t == "bought" or t == "buy" then
        return loan and "In (Loan)" or "In (Permanent)"
    end
    return nil
end

-- Returns a list of { date, player, type, feeM, feeUnknown, club } and a list of lines that could not be read.
local function parse_manual_transfers(text)
    local rows, bad = {}, {}
    for line in tostring(text or ""):gmatch("[^\r\n]+") do
        local l = line:gsub("^\239\187\191", ""):gsub("^%s+", ""):gsub("%s+$", "")
        if l ~= "" and l:sub(1, 1) ~= "#" then
            local date, player, ttype, fee, club = l:match("^([^,]*),([^,]*),([^,]*),([^,]*),?(.*)$")
            if date then
                date = date:gsub("%s", "")
                local y, m, d = date:match("^(%d%d%d%d)-(%d%d?)-(%d%d?)$")
                local kind = parse_manual_type(ttype)
                player = tostring(player):gsub("^%s+", ""):gsub("%s+$", "")
                if y and kind and player ~= "" then
                    local fee_m, unknown = parse_manual_fee(fee)
                    if kind == "Promoted In" or kind:find("Loan", 1, true) then fee_m, unknown = 0.0, false end
                    club = tostring(club or ""):gsub("^%s+", ""):gsub("%s+$", "")
                    if club == "" then club = (kind == "Promoted In") and "Youth Academy" or "Unknown club" end
                    rows[#rows + 1] = {
                        date = string.format("%04d-%02d-%02d", tonumber(y), tonumber(m), tonumber(d)),
                        player = player, type = kind, feeM = fee_m, feeUnknown = unknown, club = club
                    }
                elseif not (date:lower() == "date") then
                    bad[#bad + 1] = l
                end
            else
                bad[#bad + 1] = l
            end
        end
    end
    return rows, bad
end
-- END manual_transfers

local function beam_transfer_to_bridge(t_data)
    local managed_club = t_data.clubName or (state and state.managed_team_name) or safe_team_name(t_data.managed_tid or get_managed_team_id())
    local player = tostring(t_data.playerName or "Player")
    local other = tostring(t_data.fromToClub or "")
    local feeM = to_num(t_data.feeM, 0.0)
    local msg = t_data.message or string.format("%s joined %s from %s!", player, managed_club, other)
    local date = tostring(t_data.date or get_current_career_date())

    local parts = {
        '"dataType": "transfer"',
        string.format('"title": "%s"', jstr(t_data.title or ("Transfer: " .. player))),
        string.format('"message": "%s"', jstr(msg)),
        string.format('"playerName": "%s"', jstr(player)),
        string.format('"type": "%s"', jstr(t_data.type or "In (Permanent)")),
        string.format('"feeM": %.2f', feeM),
        string.format('"fromToClub": "%s"', jstr(other)),
        string.format('"date": "%s"', jstr(date))
    }
    if t_data.feeUnknown then parts[#parts + 1] = '"feeUnknown": true' end
    -- the game's id (the app then finds the player whatever his name), and for a signing his details from the game
    local pid = math.floor(tonumber(t_data.pid) or 0)
    if pid > 0 then parts[#parts + 1] = string.format('"playerId": %d', pid) end
    if type(t_data.profile) == "table" then parts[#parts + 1] = '"profile": ' .. PROFILE.json(t_data.profile) end
    local payload = "{\n  " .. table.concat(parts, ",\n  ") .. "\n}"

    send_payload_to_bridge(payload, string.format("Transfer [%s]: %s (%s)", tostring(t_data.type), player, other))
    local feeText = t_data.feeUnknown and "fee unknown" or string.format("EUR %.2fM", feeM)
    log_monitor(string.format(">>> BEAMED TRANSFER: %s | %s | %s | %s | %s <<<",
        player, tostring(t_data.type), other, feeText, date))
    print(string.format("[FC26 AutoCapture TRANSFER] %s | %s | %s | %s | %s",
        player, tostring(t_data.type), other, feeText, date))
end

-- ---------------------------------------------------------------------------
-- Saved state (squad baseline, academy list, loans out) + keys of transfers that were already sent
-- ---------------------------------------------------------------------------
local STATE_FILE = COMMUNITY_ROOT .. "/auto_capture_state.lua"
local BEAMED_FILE = COMMUNITY_ROOT .. "/beamed_transfers.txt"
local MANUAL_FILE = COMMUNITY_ROOT .. "/manual_transfers.csv"

local function load_saved_telemetry_state()
    local candidatePaths = {
        STATE_FILE,
        user_profile .. "\\Desktop\\telemetry_state.lua",
        user_profile .. "\\Desktop\\FC26-LE\\telemetry_state.lua",
        "telemetry_state.lua"
    }
    for _, path in ipairs(candidatePaths) do
        local f = io.open(path, "r")
        if f then
            f:close()
            local ok, res = pcall(dofile, path)
            if ok and type(res) == "table" and res.squad then
                return res
            end
        end
    end
    return nil
end

local function save_auto_capture_state(squad, all_teams, youth, loans_out)
    pcall(function()
        local function dump_set(set)
            local parts = {}
            for pid in pairs(set or {}) do parts[#parts + 1] = "[" .. pid .. "]=true" end
            return table.concat(parts, ",")
        end
        local teamsDump = {}
        for pid, tid in pairs(all_teams or {}) do teamsDump[#teamsDump + 1] = "[" .. pid .. "]=" .. tid end

        local f = io.open(STATE_FILE, "w")
        if f then
            f:write("return { squad = {" .. dump_set(squad) .. "}, allPlayerTeams = {" .. table.concat(teamsDump, ",")
                .. "}, youth = {" .. dump_set(youth) .. "}, loansOut = {" .. dump_set(loans_out) .. "} }\n")
            f:close()
        end
    end)
end

local function load_beamed_keys()
    local keys = _G["FC26_BEAMED_TRANSFERS"]
    local f = io.open(BEAMED_FILE, "r")
    if f then
        for line in f:lines() do
            local k = trim(line)
            if k ~= "" then keys[k] = true end
        end
        f:close()
    end
end

local function mark_beamed(key)
    _G["FC26_BEAMED_TRANSFERS"][key] = true
    pcall(function()
        local f = io.open(BEAMED_FILE, "a")
        if f then
            f:write(key .. "\n")
            f:close()
        end
    end)
end

-- Sends one transfer unless its key was already sent (before or in an earlier session).
local function emit_transfer(key, data, force_sync)
    if force_sync or not _G["FC26_BEAMED_TRANSFERS"][key] then
        mark_beamed(key)
        beam_transfer_to_bridge(data)
        status.squad_dirty = true -- a signing or a sale changes who has which contract
        return true
    end
    return false
end

-- Players listed in manual_transfers.csv win over what the script works out for the same player and direction.
local manual_override = {}

local function transfer_direction(transfer_type)
    return (tostring(transfer_type):sub(1, 3) == "Out") and "OUT" or "IN"
end

local function import_manual_transfers(managed_club, force_sync)
    local f = io.open(MANUAL_FILE, "r")
    if not f then return 0 end
    local text = f:read("*a")
    f:close()
    local rows, bad = parse_manual_transfers(text)
    for _, line in ipairs(bad) do
        log_monitor("manual_transfers.csv: line not understood and skipped: " .. line)
    end
    local sent = 0
    for _, r in ipairs(rows) do
        manual_override[norm_name(r.player) .. "|" .. transfer_direction(r.type)] = true
        local message = r.feeUnknown and string.format("%s: %s (%s), fee not known.", r.type, r.player, r.club)
            or string.format("%s: %s (%s), EUR %.2fM.", r.type, r.player, r.club, r.feeM)
        local key = string.format("MANUAL_%s_%s_%s", r.date, norm_name(r.player), r.type)
        if emit_transfer(key, {
            playerName = r.player, type = r.type, feeM = r.feeM, feeUnknown = r.feeUnknown,
            fromToClub = r.club, date = r.date, clubName = managed_club, message = message
        }, force_sync) then
            sent = sent + 1
        end
    end
    if #rows > 0 then
        log_monitor(string.format("manual_transfers.csv: %d line(s) read, %d sent.", #rows, sent))
    end
    return sent
end

-- Sends one described deal (see describe_arrival / describe_departure).
local function emit_described(d, managed_club, force_sync)
    if manual_override[norm_name(d.playerName) .. "|" .. transfer_direction(d.type)] then return false end
    local sent = emit_transfer(d.key, {
        playerName = d.playerName, type = d.type, feeM = d.feeM, feeUnknown = d.feeUnknown,
        fromToClub = d.fromToClub, date = d.date, clubName = managed_club, message = d.message,
        pid = d.pid, profile = d.profile
    }, force_sync)
    if sent then
        log_monitor(string.format("Transfer rule: %s -> %s (%s)", d.playerName, d.type, d.why or "?"))
    end
    return sent
end

local function season_of(today_year, today_month)
    local year = (today_month < 7) and (today_year - 1) or today_year
    return year, string.format("%04d-07-01", year)
end

local function sorted_keys(set)
    local list = {}
    for pid in pairs(set) do list[#list + 1] = pid end
    table.sort(list)
    return list
end

local function make_transfer_context(managed_tid, today, season_year, season_start, d)
    return {
        managed_tid = managed_tid, today = today, season_year = season_year, season_start = season_start,
        loans_in = d.loans_in, loans_out = d.loans_out, all_links = d.all_links,
        previous_teams = d.previous_teams or {}, youth_before = d.youth_before,
        contracts = read_contracts(managed_tid), info = d.info,
        decode = function(raw) return (decode_fifa_date(raw)) end,
        team_name = safe_team_name, player_name = safe_player_name
    }
end

-- Sync current-season transfers that happened before / while the script was not running.
--   * loans come from playerloans,
--   * signings and promotions from the join dates of the squad (and from the squad saved at the end of the last run),
--   * sales and releases from the squad saved at the end of the last run (the very first run has no such baseline,
--     so deals made before it can only be added through manual_transfers.csv).
local function sync_season_transfers(managed_tid, force_sync)
    managed_tid = managed_tid or (state and state.managed_team_id) or get_managed_team_id()
    local managed_club = (state and state.managed_team_name) or safe_team_name(managed_tid)
    local todayDateStr, curY, curM = get_current_career_date()
    local seasonYear, seasonStart = season_of(curY, curM)

    log_monitor(string.format("Scanning transfers for %s (ID: %d) | Date: %s | Season Start: %s",
        managed_club, managed_tid, todayDateStr, seasonStart))

    local current_squad, all_teams, club_links = get_managed_squad_state(managed_tid)
    if next(current_squad) == nil then
        log_monitor("Transfer scan skipped: could not read the squad (is a career loaded?)")
        return 0
    end

    local saved = load_saved_telemetry_state()
    local loans_in, loans_out = get_active_loans(managed_tid, current_squad, club_links)
    local was_on_loan = (saved and saved.loansOut) or {}

    -- Players who left permanently since the saved baseline (a loan is handled through playerloans).
    local leavers = {}
    if saved and saved.squad then
        for pid in pairs(saved.squad) do
            if not current_squad[pid] and not loans_out[pid] then leavers[#leavers + 1] = pid end
        end
        if #leavers > 15 then
            log_monitor(string.format("Saved squad differs by %d players - not treated as transfers (different career?)", #leavers))
            leavers = {}
        end
        table.sort(leavers)
    end

    local wanted = {}
    for pid in pairs(current_squad) do wanted[pid] = true end
    for _, pid in ipairs(leavers) do wanted[pid] = true end
    local info = get_player_join_info(wanted)

    -- Arrivals: loans in, join dates inside this season, and anyone new compared with the saved squad.
    local arrivals, seen = {}, {}
    local function add_arrival(pid)
        if not seen[pid] then seen[pid] = true arrivals[#arrivals + 1] = pid end
    end
    for _, pid in ipairs(sorted_keys(loans_in)) do add_arrival(pid) end

    local joins = {}
    for _, pid in ipairs(sorted_keys(current_squad)) do
        if not loans_in[pid] and not was_on_loan[pid] and info[pid] then
            local iso = decode_fifa_date(info[pid].join_days)
            if iso and iso >= seasonStart and iso <= todayDateStr then joins[#joins + 1] = pid end
        end
    end
    -- A real transfer window rarely brings in more than a handful of players: a long list means "initial squad".
    if #joins > 15 then
        log_monitor(string.format("Join-date scan found %d players - looks like the initial squad, not signings; skipped", #joins))
    else
        for _, pid in ipairs(joins) do add_arrival(pid) end
    end
    if saved and saved.squad then
        local newcomers = {}
        for _, pid in ipairs(sorted_keys(current_squad)) do
            if not saved.squad[pid] and not loans_in[pid] then
                if was_on_loan[pid] then
                    log_monitor(string.format("%s is back from a loan (not a transfer)", safe_player_name(pid)))
                else
                    newcomers[#newcomers + 1] = pid
                end
            end
        end
        if #newcomers > 15 then
            log_monitor(string.format("Squad differs from the saved one by %d new players - not treated as transfers (different career?)", #newcomers))
        else
            for _, pid in ipairs(newcomers) do add_arrival(pid) end
        end
    end

    local ctx = make_transfer_context(managed_tid, todayDateStr, seasonYear, seasonStart, {
        loans_in = loans_in, loans_out = loans_out, all_links = all_teams,
        previous_teams = saved and saved.allPlayerTeams, youth_before = saved and saved.youth, info = info
    })
    assign_big_deals(ctx, read_big_deals(managed_tid), arrivals, leavers)

    -- the new players' details from the game go with their signing (positions, birthday, nation...)
    local arrival_set = {}
    for _, pid in ipairs(arrivals) do arrival_set[pid] = true end
    local profiles = PROFILE.read(arrival_set)

    local synced_count = 0
    for _, pid in ipairs(arrivals) do
        local d = describe_arrival(pid, ctx)
        d.profile = profiles[pid]
        if emit_described(d, managed_club, force_sync) then synced_count = synced_count + 1 end
    end
    for _, pid in ipairs(sorted_keys(loans_out)) do
        if emit_described(describe_departure(pid, ctx), managed_club, force_sync) then synced_count = synced_count + 1 end
    end
    for _, pid in ipairs(leavers) do
        if emit_described(describe_departure(pid, ctx), managed_club, force_sync) then synced_count = synced_count + 1 end
    end

    local youth_now = read_youth_set()
    if next(youth_now) == nil and saved and saved.youth then youth_now = saved.youth end
    if state then
        state.youth = youth_now
        state.loans_out = loans_out
    end
    save_auto_capture_state(current_squad, all_teams, youth_now, loans_out)

    if synced_count > 0 then
        log_monitor(string.format("Synced %d current season transfer(s) to Bridge.", synced_count))
        print(string.format("[FC26 AutoCapture] >>> Successfully synced %d transfer(s) to Bridge! <<<", synced_count))
    else
        log_monitor("Transfer scan completed: 0 new un-synced transfers found.")
        print("[FC26 AutoCapture] Transfer scan: Up to date (0 pending transfers).")
    end
    return synced_count
end

-- Live transfer detector. A full read of teamplayerlinks is the expensive part, so it is throttled
-- (and never overlaps itself); details are only looked up when the squad really changed.
local TRANSFER_SCAN_MIN_INTERVAL = 6
local last_transfer_scan = 0
local transfer_scan_busy = false

local function scan_squad_changes()
    local managed_tid = state.managed_team_id
    local managed_club = state.managed_team_name or safe_team_name(managed_tid)
    local current_squad, current_all_teams, current_links = get_managed_squad_state(managed_tid)

    -- A failed read must never look like "every player left".
    if next(current_squad) == nil then return end

    if not state.squad or next(state.squad) == nil then
        state.squad = current_squad
        state.all_player_teams = current_all_teams
        return
    end

    local loans_in, loans_out = get_active_loans(managed_tid, current_squad, current_links)
    local was_on_loan = state.loans_out or {}

    local arrivals, departures, returned = {}, {}, {}
    local squad_size = 0
    for pid in pairs(state.squad) do squad_size = squad_size + 1 end
    for _, pid in ipairs(sorted_keys(current_squad)) do
        if not state.squad[pid] then
            if was_on_loan[pid] and not loans_in[pid] then
                returned[#returned + 1] = pid
            else
                arrivals[#arrivals + 1] = pid
            end
        end
    end
    for _, pid in ipairs(sorted_keys(state.squad)) do
        if not current_squad[pid] then departures[#departures + 1] = pid end
    end

    local function remember_state()
        state.squad = current_squad
        state.all_player_teams = current_all_teams
        state.loans_out = loans_out
        local youth_now = read_youth_set()
        if next(youth_now) ~= nil then state.youth = youth_now end
    end

    -- Loans out leave no gap in the squad list (the player can stay linked to us): spot them through playerloans.
    local leaving_now = {}
    for _, pid in ipairs(departures) do leaving_now[pid] = true end
    local fresh_loans = {}
    for _, pid in ipairs(new_loans_out(loans_out, was_on_loan)) do
        if not leaving_now[pid] then fresh_loans[#fresh_loans + 1] = pid end
    end

    if #arrivals == 0 and #departures == 0 and #returned == 0 and #fresh_loans == 0 then
        state.suspect_reads = 0
        remember_state()
        return
    end

    -- A sudden loss of half the squad is almost certainly a partial read. Accept only if it repeats.
    if #departures >= math.max(8, math.floor(squad_size / 2)) and #arrivals == 0 then
        state.suspect_reads = (state.suspect_reads or 0) + 1
        if state.suspect_reads < 3 then
            log_monitor(string.format("Ignoring suspicious squad read (%d players missing), checking again", #departures))
            return
        end
    end
    state.suspect_reads = 0

    local todayDateStr, curY, curM = get_current_career_date()
    local seasonYear, seasonStart = season_of(curY, curM)
    local wanted = {}
    for _, pid in ipairs(arrivals) do wanted[pid] = true end
    for _, pid in ipairs(departures) do wanted[pid] = true end
    for _, pid in ipairs(fresh_loans) do wanted[pid] = true end
    local ctx = make_transfer_context(managed_tid, todayDateStr, seasonYear, seasonStart, {
        loans_in = loans_in, loans_out = loans_out, all_links = current_all_teams,
        previous_teams = state.all_player_teams, youth_before = state.youth, info = get_player_join_info(wanted)
    })
    assign_big_deals(ctx, read_big_deals(managed_tid), arrivals, departures)

    for _, pid in ipairs(returned) do
        log_monitor(string.format("%s is back from a loan (not a transfer)", safe_player_name(pid)))
    end
    for _, pid in ipairs(arrivals) do emit_described(describe_arrival(pid, ctx), managed_club, false) end
    for _, pid in ipairs(departures) do emit_described(describe_departure(pid, ctx), managed_club, false) end
    for _, pid in ipairs(fresh_loans) do emit_described(describe_departure(pid, ctx), managed_club, false) end

    remember_state()
    save_auto_capture_state(current_squad, current_all_teams, state.youth, loans_out)
end

local function check_for_squad_transfers(force)
    if state and not state.active then return end
    if _G["FC26_AUTOCAPTURE_CURRENT_INSTANCE"] ~= SCRIPT_INSTANCE then return end
    if transfer_scan_busy then return end

    local now = os.time()
    if not force and (now - last_transfer_scan) < TRANSFER_SCAN_MIN_INTERVAL then return end
    last_transfer_scan = now

    transfer_scan_busy = true
    local ok, err = xpcall(scan_squad_changes, debug.traceback)
    transfer_scan_busy = false
    if not ok then log_monitor("Transfer scan error: " .. tostring(err)) end
end

-- ---------------------------------------------------------------------------
-- State initialisation
-- ---------------------------------------------------------------------------
load_beamed_keys()

local managed_team_id = get_managed_team_id()
local managed_team_name = safe_team_name(managed_team_id)
local all_fixtures = get_all_managed_fixtures(managed_team_id)

local baseline_completed_ids = {}
local completed_count = 0

-- Mark all CURRENT completed fixtures so we NEVER beam stale matches on launch!
for _, f in ipairs(all_fixtures) do
    if f.completed then
        baseline_completed_ids[fixture_key(f)] = true
        completed_count = completed_count + 1
    end
end

local baseline_squad, baseline_all_teams = get_managed_squad_state(managed_team_id)

-- Existing Atlas exports belong to earlier matches: never attach them to a new one.
-- (No folder listing here: the exporter's own report is marked as used, and a folder found later by looking at the
-- folder is judged by the date in its name, see find_atlas_folder.)
local atlas_seen_before = false
do
    local reported = last_export_folder()
    if reported then _G["FC26_ATLAS_USED"][reported] = true end
    atlas_seen_before = reported ~= nil or QUIET.dir_exists(MATCH_STATS_ROOT .. "/ready")
end

state = {
    active = true,
    instance_id = SCRIPT_INSTANCE,
    managed_team_id = managed_team_id,
    managed_team_name = managed_team_name,
    completed_ids = baseline_completed_ids,
    last_count = completed_count,
    last_check_time = 0,
    pre_match_cpi = get_player_info_snapshot(managed_team_id),
    squad = baseline_squad,
    all_player_teams = baseline_all_teams,
    pending_matches = {},
    atlas_seen = atlas_seen_before
}
_G["FC26_AUTO_CAPTURE_STATE"] = state

-- Anything left undelivered by an earlier session goes out first, then the catch-up transfer sync.
pcall(adopt_outbox_leftovers)
do
    local ok, err = xpcall(function()
        import_manual_transfers(managed_team_name, false)
        sync_season_transfers(managed_team_id, false)
    end, debug.traceback)
    if not ok then log_monitor("Startup transfer sync error: " .. tostring(err)) end
end

-- ---------------------------------------------------------------------------
-- Match detection. A finished fixture is queued; the Atlas exporter needs a few seconds to write
-- the detailed stats, so the match is sent as soon as they appear (or after a short wait without them).
-- ---------------------------------------------------------------------------
local ATLAS_WAIT_SECONDS = 30           -- an export has been seen on this PC before
local ATLAS_WAIT_SECONDS_EXPORTER = 45  -- the Atlas exporter is running: its snapshot + match export takes a while
local ATLAS_WAIT_SECONDS_IDLE = 10      -- no exporter and no export ever seen: do not hold matches back for long
local ATLAS_POLL_SECONDS = 4
local LEAGUE_WAIT_SECONDS = 20         -- the other results of the match day are still coming in
local LEAGUE_CHECK_SECONDS = 3

local SCRIPT_DIR = ""
do
    local ok, src = pcall(function() return debug.getinfo(1, "S").source end)
    if ok and type(src) == "string" and src:sub(1, 1) == "@" then
        local dir = src:sub(2):match("^(.*)[/\\][^/\\]*$")
        if dir and dir ~= "" then SCRIPT_DIR = dir .. "/" end
    end
end

-- The Project Atlas exporter is the "FC26 AutoCapture" script from the Reddit post (FC26_AutoCapture_v1.1.1.lua).
-- It has the snapshot and the detailed MatchStats exporters built in and publishes both after every finished
-- match. It is what produces the detailed player statistics this script sends to the phone.
local ATLAS_EXPORTER_FILES = { "FC26_AutoCapture_v1.1.1.lua", "FC26_AutoCapture.lua" }

local function atlas_exporter_active()
    return rawget(_G, "FC26_EXPORT_AUTO_CAPTURE_STATE") ~= nil
        or rawget(_G, "FC26_EXPORT_MATCH_STATS_AUTOMATIC_PLAYER_EXPORTER_STATE") ~= nil
end

-- BEGIN find_script (this exact block is also run by tests/lua/test_transfer_logic.lua)
-- Live Editor does not always tell a script which folder it was started from, so "next to this script" cannot be
-- trusted. Besides that folder, look where Live Editor scripts usually are: its working folder and lua\scripts, and
-- the FC26-LE folder on the Desktop (also when Windows keeps the Desktop in OneDrive), Downloads and Documents.
local function list_subdirs(path)
    if not QUIET.dir_exists(path) then return {} end -- most of these folders do not exist: no need to ask Windows
    return QUIET.list_dirs(path, false)
end

local function script_search_dirs(with_listing)
    local dirs, seen = {}, {}
    local function add(dir)
        dir = tostring(dir or "")
        if dir ~= "" then
            dir = win_path(dir)
            if dir:sub(-1) ~= "\\" then dir = dir .. "\\" end
        end
        if not seen[dir:lower()] then
            seen[dir:lower()] = true
            dirs[#dirs + 1] = dir
        end
    end

    add(SCRIPT_DIR)
    add("") -- Live Editor's working folder
    add("lua\\scripts")
    add("scripts")

    local roots = {}
    for _, var in ipairs({ "OneDrive", "OneDriveConsumer", "OneDriveCommercial" }) do
        local v = os.getenv(var)
        if v and v ~= "" then roots[#roots + 1] = v .. "\\Desktop" end
    end
    if user_profile ~= "" then
        roots[#roots + 1] = user_profile .. "\\Desktop"
        roots[#roots + 1] = user_profile .. "\\OneDrive\\Desktop"
        roots[#roots + 1] = user_profile .. "\\Downloads"
        roots[#roots + 1] = user_profile .. "\\Documents"
    end
    for _, root in ipairs(roots) do
        add(root)
        add(root .. "\\FC26-LE")
        add(root .. "\\FC26-LE\\lua\\scripts")
        for _, sub in ipairs(with_listing and list_subdirs(root .. "\\FC26-LE") or {}) do
            add(root .. "\\FC26-LE\\" .. sub .. "\\lua\\scripts")
            add(root .. "\\FC26-LE\\" .. sub)
        end
    end
    return dirs
end

-- Full path of one of our scripts, or nil. Returns the folders that were searched as a second value. The usual places
-- are tried first; the FC26-LE version folders are only listed when the script is in none of them (and only when that
-- can be done without a console window).
local function find_script(name)
    local dirs = script_search_dirs(false)
    for _, dir in ipairs(dirs) do
        local path = dir .. name
        if file_exists(path) then return path, dirs end
    end
    if not QUIET.can_list(QUIET.dir_api) then return nil, dirs end
    dirs = script_search_dirs(true)
    for _, dir in ipairs(dirs) do
        local path = dir .. name
        if file_exists(path) then return path, dirs end
    end
    return nil, dirs
end
-- END find_script

-- Starts the Atlas exporter unless it is already running (running it twice is harmless).
local function start_atlas_exporter()
    if atlas_exporter_active() then
        log_monitor("Project Atlas exporter is already running")
        return true
    end
    local searched = nil
    for _, name in ipairs(ATLAS_EXPORTER_FILES) do
        local path, dirs = find_script(name)
        searched = dirs
        if path then
            log_monitor("Starting the Project Atlas exporter: " .. path)
            local ok, err = pcall(dofile, path)
            if not ok then log_monitor("Project Atlas exporter error: " .. tostring(err)) end
            return ok
        end
    end
    local source = "unknown"
    pcall(function() source = tostring(debug.getinfo(1, "S").source):sub(1, 160) end)
    log_monitor("Project Atlas exporter NOT started: " .. ATLAS_EXPORTER_FILES[1] .. " was not found in any of "
        .. tostring(searched and #searched or 0) .. " folders (this script's own folder, Live Editor's working folder, "
        .. "lua\\scripts, the FC26-LE folder on the Desktop / OneDrive Desktop, Downloads, Documents). "
        .. "Script source reported by Live Editor: " .. source .. ". "
        .. "Matches are still sent, but without detailed player statistics, and NO snapshots are written "
        .. "(Documents\\FC26 Export\\Snapshots stays empty). Workaround: run FC26_AutoCapture_v1.1.1.lua yourself first, "
        .. "then run this script again; it notices the running exporter.")
    return false
end

local function run_snapshot()
    -- FC26 AutoCapture publishes the paired snapshot by itself; a second one would only slow the game down
    if rawget(_G, "FC26_EXPORT_AUTO_CAPTURE_STATE") ~= nil then return true end
    local path = find_script("FC26_Snapshot.lua")
    if path then
        local ok, err = pcall(dofile, path)
        if not ok then log_monitor("FC26_Snapshot.lua error: " .. tostring(err)) end
        return ok
    end
    log_monitor("FC26_Snapshot.lua not found - snapshot skipped (FC26_AutoCapture_v1.1.1.lua publishes the snapshot by itself)")
    return false
end

-- Number of completed matches of the same competition group up to and including fixture index idx.
local function completed_in_competition(fixtures, idx)
    local target = fixtures[idx]
    local is_league = is_league_competition(target.competition)
    local count = 0
    for i = 1, idx do
        local c = fixtures[i]
        if c.completed or i == idx then
            if is_league and is_league_competition(c.competition) then
                count = count + 1
            elseif (not is_league) and c.competition == target.competition then
                count = count + 1
            end
        end
    end
    return count
end

local function check_for_new_completed_match()
    if not state.active then return end
    if _G["FC26_AUTOCAPTURE_CURRENT_INSTANCE"] ~= SCRIPT_INSTANCE then return end

    local now = os.time()
    if now - state.last_check_time < CHECK_INTERVAL_SECONDS then return end
    state.last_check_time = now

    local current_fixtures = get_all_managed_fixtures(state.managed_team_id)
    local queued_any = false

    -- remember which competition is the club's league (used to work out the table after a cup match too)
    local league_comp = pick_league_comp(current_fixtures, nil, is_league_competition)
    if league_comp then state.league_comp_id = league_comp end

    for idx, f in ipairs(current_fixtures) do
        local key = fixture_key(f)
        if f.completed and not state.completed_ids[key] then
            state.completed_ids[key] = true
            if not _G["FC26_BEAMED_FIXTURE_KEYS"][key] then
                _G["FC26_BEAMED_FIXTURE_KEYS"][key] = true
                state.last_count = state.last_count + 1
                f.stage = determine_stage(f.competition, completed_in_competition(current_fixtures, idx))

                log_monitor(string.format(">>> NEW MATCH DETECTED! Fixture #%d: %s %d-%d %s (%s - %s) <<<",
                    f.fixture_id, state.managed_team_name, f.goals_for, f.goals_against,
                    f.opponent_name, f.competition, f.stage))
                state.pending_matches[#state.pending_matches + 1] = { fixture = f, detected_at = now, last_atlas_check = 0 }
                queued_any = true
            end
        end
    end

    -- Snapshot the career folders once per detection (used by the bridge's hot-folder importer)
    if queued_any then run_snapshot() end
end

local function service_pending_matches()
    if not state or not state.pending_matches or #state.pending_matches == 0 then return end
    if _G["FC26_AUTOCAPTURE_CURRENT_INSTANCE"] ~= SCRIPT_INSTANCE then return end

    local item = state.pending_matches[1]
    local now = os.time()
    local waited = now - item.detected_at
    local atlas_name, atlas_path, atlas_names = nil, nil, nil

    -- The exporter said it published this match's statistics (or that it could not): nothing else is coming, so the
    -- match goes now. Waiting for the next career event could take minutes (the game runs scripts only on those).
    local pub = rawget(_G, "FC26_ATLAS_PUBLISHED")
    if type(pub) == "table" and not pub.used and (pub.at or 0) >= item.detected_at - 300 then
        pub.used = true
        item.atlas_final = true
        local r = pub.result
        if type(r) == "table" and r.success == true and type(r.ready_dir) == "string" and r.ready_dir ~= "" then
            local path = (r.ready_dir:gsub("\\", "/")):gsub("/+$", "")
            local name = path:match("([^/]+)$")
            if name and not _G["FC26_ATLAS_USED"][name] and atlas_folder_is_ready(path) then
                item.atlas_found = { name, path, { name } }
            end
        end
    end

    if now - item.last_atlas_check >= ATLAS_POLL_SECONDS then
        item.last_atlas_check = now
        atlas_name, atlas_path, atlas_names = find_atlas_folder(false, waited)
        if atlas_names and #atlas_names > 0 then state.atlas_seen = true end
        if atlas_path then item.atlas_found = { atlas_name, atlas_path, atlas_names } end
    end
    -- the export found on an earlier tick still counts while the match waits for the rest of the day's results
    if not atlas_path and item.atlas_found then
        atlas_name, atlas_path, atlas_names = item.atlas_found[1], item.atlas_found[2], item.atlas_found[3]
    end

    local limit = ATLAS_WAIT_SECONDS_IDLE
    if atlas_exporter_active() then
        limit = ATLAS_WAIT_SECONDS_EXPORTER
    elseif state.atlas_seen then
        limit = ATLAS_WAIT_SECONDS
    end
    if item.atlas_final then limit = 0 end
    if not atlas_path and waited < limit then return end

    -- The league position is worked out from the results, so the other matches of the day must be in first.
    if (item.league_checked_at or 0) == 0 or now - item.league_checked_at >= LEAGUE_CHECK_SECONDS then
        item.league_checked_at = now
        local ok_l, info = pcall(live_league_position, state.managed_team_id, item.fixture, state.league_comp_id)
        if ok_l and info and not info.ready then
            item.league_waiting_since = item.league_waiting_since or now
            -- once the statistics are in, the match is not held back for the table (the career report updates it)
            if now - item.league_waiting_since < LEAGUE_WAIT_SECONDS and not item.atlas_final then
                item.league_pending = true
                return
            end
            log_monitor("The other results of the match day are still missing - the league position is sent as it stands")
        end
        item.league_pending = false
    elseif item.league_pending then
        return
    end

    table.remove(state.pending_matches, 1)
    local stale = rawget(_G, "FC26_ATLAS_PUBLISHED")
    if type(stale) == "table" then stale.used = true end
    if atlas_name then
        -- this export is now spoken for; anything older is stale
        for _, n in ipairs(atlas_names) do _G["FC26_ATLAS_USED"][n] = true end
        log_monitor("Using Atlas export " .. atlas_name .. " (waited " .. waited .. "s)")
    else
        log_monitor("No Atlas export appeared within " .. waited .. "s - sending with database data")
    end

    beam_match_to_bridge(item.fixture, state.managed_team_id, state.pre_match_cpi, atlas_path)
    state.pre_match_cpi = get_player_info_snapshot(state.managed_team_id)
end

-- Sends the latest completed match again. Returns "sent" and a short description, or "nothing" and the reason.
-- Used by FC26_AutoCapture_SyncLatest() and by the phone's "Re-send last match" button.
local function resend_latest_match()
    local current_fixtures = get_all_managed_fixtures(state.managed_team_id)
    local latest_idx = nil
    for idx, f in ipairs(current_fixtures) do
        if f.completed then latest_idx = idx end
    end
    if not latest_idx then
        return "nothing", "No completed match found in this career yet"
    end

    local latest = current_fixtures[latest_idx]
    latest.stage = determine_stage(latest.competition, completed_in_competition(current_fixtures, latest_idx))

    run_snapshot()
    local atlas_name, atlas_path = find_atlas_folder(true)
    if atlas_name then _G["FC26_ATLAS_USED"][atlas_name] = true end

    beam_match_to_bridge(latest, state.managed_team_id, state.pre_match_cpi, atlas_path)
    _G["FC26_BEAMED_FIXTURE_KEYS"][fixture_key(latest)] = true
    state.completed_ids[fixture_key(latest)] = true
    local text = string.format("%s %d-%d %s (%s - %s)%s",
        tostring(latest.user_team_name), to_int(latest.goals_for), to_int(latest.goals_against),
        tostring(latest.opponent_name), tostring(latest.competition), tostring(latest.stage),
        atlas_name and " [with Atlas stats]" or " [no Atlas export found]")
    return "sent", text
end

-- Force sync function: Immediately beams the latest completed match
function FC26_AutoCapture_SyncLatest()
    local result, text = resend_latest_match()
    if result == "sent" then
        print("[FC26 AutoCapture] Force Synced Latest Match: " .. text)
    else
        print("[FC26 AutoCapture] " .. text .. " - nothing to sync.")
    end
end

FC26_AutoCapture_SyncNow = FC26_AutoCapture_SyncLatest
FC26_SyncLatestMatch = FC26_AutoCapture_SyncLatest

-- Force sync function: Immediately scans and beams current season transfers
function FC26_AutoCapture_SyncTransfers()
    print("[FC26 AutoCapture] Force syncing current season transfers...")
    local count = import_manual_transfers(state.managed_team_name, true)
    count = count + sync_season_transfers(state.managed_team_id, true)
    print(string.format("[FC26 AutoCapture] Transfers sync complete! Processed %d transfer(s).", count))
end

FC26_SyncTransfers = FC26_AutoCapture_SyncTransfers

-- On-demand transfer beam command for Live Editor console
-- Usage: FC26_BeamTransfer("Player Name", "In (Permanent)", 15.0, "Arsenal")
-- Or: FC26_BeamTransfer(player_id)
function FC26_BeamTransfer(player_name_or_id, transfer_type, fee_m, other_club, transfer_date)
    local pid = tonumber(player_name_or_id) or 0
    local pName = (pid > 0) and safe_player_name(pid) or tostring(player_name_or_id or "New Player")
    local tType = transfer_type or "In (Permanent)"
    local feeM = tonumber(fee_m) or 0.0
    local club = other_club or "External Club"
    local todayDateStr = get_current_career_date()
    local tDate = transfer_date or todayDateStr

    beam_transfer_to_bridge({
        playerName = pName,
        type = tType,
        feeM = feeM,
        fromToClub = club,
        date = tDate,
        message = string.format("%s joined %s from %s for EUR %.2fM!", pName, state and state.managed_team_name or "Club", club, feeM)
    })
    print(string.format("[FC26 AutoCapture] Manually beamed transfer: %s (%s, EUR %.2fM)", pName, tType, feeM))
end

-- Prints what the script is doing: queue, undelivered payloads, last bridge answers.
function FC26_AutoCapture_Status()
    local pending_out = 0
    for json_path in pairs(outbox_pending) do
        if file_exists(json_path) then pending_out = pending_out + 1 end
    end
    print("=== FC26 AutoCapture status ===")
    print("Club: " .. tostring(state.managed_team_name) .. " (ID " .. tostring(state.managed_team_id) .. ")")
    print("Matches waiting for Atlas stats: " .. tostring(#state.pending_matches))
    print("Payloads not yet acknowledged by the bridge: " .. tostring(pending_out))
    print("Bridge reads the outbox folder itself: " .. tostring(bridge_is_watching()))
    print("Quiet mode - " .. QUIET.describe())
    print("Atlas exporter running: " .. tostring(atlas_exporter_active()) .. " | export folder seen: " .. tostring(state.atlas_seen))
    local f = io.open(SEND_LOG, "r")
    if f then
        local lines = {}
        for line in f:lines() do lines[#lines + 1] = line end
        f:close()
        print("Last bridge answers (" .. SEND_LOG .. "):")
        for i = math.max(1, #lines - 5), #lines do print("  " .. lines[i]) end
    else
        print("No bridge answers recorded yet (nothing sent, or the .bat could not run).")
    end
end

-- ---------------------------------------------------------------------------
-- Health report, squad contracts and "re-send the last match"
-- ---------------------------------------------------------------------------
-- BEGIN career_report (this exact block is also run by tests/lua/test_transfer_logic.lua)
-- The "career" report: league tables, fixtures still to play, the league's top scorers, the squad's numbers, roles,
-- wages, injuries and suspensions, the club's facts, the team sheet, the European ranking and, in June and July, last
-- season's winners. Nothing in this block touches the game: it turns plain tables into the payload, so the tests run
-- it as it is. It needs jstr, to_int and to_num from above.
local CR = {}          -- everything of the career report lives in this one table (the main chunk is near Lua's 200-locals limit)
do

    CR.ARRAY = {}            -- metatable that marks a Lua table as a JSON array (also when it is empty)
    function CR.array(t) return setmetatable(t or {}, CR.ARRAY) end

    CR.TOP_FIVE = { "Premier League", "LALIGA EA SPORTS", "Serie A", "Bundesliga", "Ligue 1" }
    CR.LADDERS = {
        { "Premier League", "EFL Championship", "EFL League One", "EFL League Two" },
        { "LALIGA EA SPORTS", "LALIGA HYPERMOTION" },
        { "Serie A", "Serie BKT", "Serie B" },
        { "Bundesliga", "Bundesliga 2", "2. Bundesliga", "3. Liga" },
        { "Ligue 1", "Ligue 2" }
    }

    -- Where a competition belongs on the Around the World page (nil = not shown there).
    function CR.region(name)
        local n = tostring(name or ""):lower()
        if n == "" then return nil end
        for _, bad in ipairs({ "women", "féminine", "feminine", "frauen", "femenina", "femminile", "u21", "u-21", "u19", "u-19", "u17",
                               "youth", "world cup", "nations league", "international", "friendly", "euro 20", "copa américa", "copa america" }) do
            if n:find(bad, 1, true) then return nil end
        end
        if n:find("uefa", 1, true) or n:find("champions league", 1, true) or n:find("europa league", 1, true) or n:find("conference league", 1, true) then return "Europe" end
        if n == "premier league" or n:find("fa cup", 1, true) or n:find("carabao", 1, true) or n:find("efl cup", 1, true) or n:find("community shield", 1, true) then return "England" end
        if n:find("laliga ea sports", 1, true) or n == "la liga" or n:find("copa del rey", 1, true) or n:find("supercopa", 1, true) then return "Spain" end
        if n == "serie a" or n:find("coppa italia", 1, true) or n:find("supercoppa", 1, true) then return "Italy" end
        if n == "bundesliga" or n:find("dfb", 1, true) or n:find("dfl", 1, true) then return "Germany" end
        if n == "ligue 1" or n:find("coupe de france", 1, true) or n:find("troph", 1, true) then return "France" end
        return nil
    end

    CR.COUNTRY = {
        ["premier league"] = "England", ["efl championship"] = "England", ["efl league one"] = "England", ["efl league two"] = "England",
        ["laliga ea sports"] = "Spain", ["laliga hypermotion"] = "Spain", ["serie a"] = "Italy", ["serie bkt"] = "Italy", ["serie b"] = "Italy",
        ["bundesliga"] = "Germany", ["bundesliga 2"] = "Germany", ["2. bundesliga"] = "Germany", ["3. liga"] = "Germany",
        ["ligue 1"] = "France", ["ligue 1 mcdonald's"] = "France", ["ligue 2"] = "France", ["ligue 2 bkt"] = "France",
        ["liga portugal"] = "Portugal", ["liga portugal betclic"] = "Portugal", ["eredivisie"] = "Netherlands",
        ["scottish premiership"] = "Scotland", ["1a pro league"] = "Belgium", ["süper lig"] = "Türkiye", ["trendyol süper lig"] = "Türkiye"
    }
    function CR.country(league_name) return CR.COUNTRY[tostring(league_name or ""):lower()] or "" end

    -- The leagues worth a table: the club's own, the five big ones, and the leagues above and below the club's own.
    function CR.wanted_leagues(own)
        local wanted = {}
        if own and own ~= "" then wanted[own:lower()] = true end
        for _, l in ipairs(CR.TOP_FIVE) do wanted[l:lower()] = true end
        for _, ladder in ipairs(CR.LADDERS) do
            for _, l in ipairs(ladder) do
                if own and l:lower() == own:lower() then
                    for _, x in ipairs(ladder) do wanted[x:lower()] = true end
                end
            end
        end
        return wanted
    end

    CR.ROLES = { [1] = "Crucial", [2] = "Important", [3] = "Rotation", [4] = "Sporadic", [5] = "Prospect" }
    function CR.role(n) return CR.ROLES[to_int(n, 0)] end

    -- The game's injury days count from 22 December 1995 (checked against the injury list in the game).
    CR.INJURY_EPOCH_DAYS = 9486          -- days from 1970-01-01 to 1995-12-22
    function CR.iso_from_days(days_since_1970)
        local z = math.floor(days_since_1970) + 719468
        local era = (z >= 0 and z or z - 146096) // 146097
        local doe = z - era * 146097
        local yoe = (doe - doe // 1460 + doe // 36524 - doe // 146096) // 365
        local y = yoe + era * 400
        local doy = doe - (365 * yoe + yoe // 4 - yoe // 100)
        local mp = (5 * doy + 2) // 153
        local d = doy - (153 * mp + 2) // 5 + 1
        local m = mp + (mp < 10 and 3 or -9)
        if m <= 2 then y = y + 1 end
        return string.format("%04d-%02d-%02d", y, m, d)
    end
    function CR.days_from_iso(iso)
        local y, m, d = tostring(iso or ""):match("^(%d%d%d%d)%-(%d%d)%-(%d%d)")
        if not y then return nil end
        y, m, d = tonumber(y), tonumber(m), tonumber(d)
        if m <= 2 then y = y - 1 end
        local era = (y >= 0 and y or y - 399) // 400
        local yoe = y - era * 400
        local doy = (153 * (m + (m > 2 and -3 or 9)) + 2) // 5 + d - 1
        local doe = yoe * 365 + yoe // 4 - yoe // 100 + doy
        return era * 146097 + doe - 719468
    end

    -- An injury from career_playerlastgrowth: { start, back } (yyyy-mm-dd), or nil when there is none or it is over.
    function CR.injury(injurydate, duration, today_iso)
        local day, dur = to_int(injurydate, 0), to_int(duration, 0)
        if day <= 0 or dur <= 0 or dur > 400 then return nil end
        local start = CR.iso_from_days(CR.INJURY_EPOCH_DAYS + day)
        local back = CR.iso_from_days(CR.INJURY_EPOCH_DAYS + day + dur)
        if today_iso and today_iso ~= "" and back <= today_iso then return nil end
        return { start = start, back = back }
    end

    -- "4-3-3" from the team sheet's position names (goalkeeper left out); nil when it does not add up to ten.
    function CR.formation(position_names)
        local def, mid, att = 0, 0, 0
        local D = { CB = 1, LCB = 1, RCB = 1, LB = 1, RB = 1, LWB = 1, RWB = 1, SW = 1 }
        local M = { CDM = 1, LDM = 1, RDM = 1, CM = 1, LCM = 1, RCM = 1, LM = 1, RM = 1, CAM = 1, LAM = 1, RAM = 1 }
        local A = { ST = 1, LS = 1, RS = 1, CF = 1, LF = 1, RF = 1, LW = 1, RW = 1 }
        for _, p in ipairs(position_names) do
            local n = tostring(p):upper()
            if D[n] then def = def + 1 elseif M[n] then mid = mid + 1 elseif A[n] then att = att + 1 end
        end
        if def + mid + att ~= 10 or def == 0 or att == 0 then return nil end
        return string.format("%d-%d-%d", def, mid, att)
    end

    -- The last five results of a club, oldest first: results = list of { date, gf, ga }.
    function CR.form(results)
        table.sort(results, function(a, b) return (a.date or 0) < (b.date or 0) end)
        local out = {}
        for i = math.max(1, #results - 4), #results do
            local r = results[i]
            out[#out + 1] = (r.gf > r.ga) and "W" or ((r.gf == r.ga) and "D" or "L")
        end
        return table.concat(out)
    end

    -- The final of a cup from its last fixture: { home, away, hs, as, hp, ap } (team ids, scores, shoot-out or nil).
    function CR.final(f)
        if not f or not f.completed or not f.home or not f.away or f.home <= 0 or f.away <= 0 then return nil end
        local hs, as = to_int(f.hs, -1), to_int(f.as, -1)
        if hs < 0 or as < 0 or hs > 30 or as > 30 then return nil end
        local hp, ap = to_int(f.hp, 0), to_int(f.ap, 0)
        if hp >= 255 or ap >= 255 then hp, ap = 0, 0 end
        local pens = (hs == as and (hp > 0 or ap > 0)) and (hp .. "–" .. ap) or nil
        local home_won = hs > as or (pens ~= nil and hp > ap)
        if hs == as and not pens then return nil end
        return { winner = home_won and f.home or f.away, runner = home_won and f.away or f.home,
                 score = home_won and (hs .. "–" .. as) or (as .. "–" .. hs), pens = pens and (home_won and (hp .. "–" .. ap) or (ap .. "–" .. hp)) or nil }
    end

    -- Kick-off from the fixture list: minutes after midnight (900 = 15:00), or hhmm when that cannot be a time of day.
    function CR.kickoff(raw)
        local n = to_int(raw, -1)
        if n >= 0 and n < 1440 then return string.format("%02d:%02d", n // 60, n % 60) end
        if n >= 0 and n <= 2359 and n % 100 < 60 then return string.format("%02d:%02d", n // 100, n % 100) end
        return ""
    end

    -- Attribute columns of the players table, with the names the app shows.
    CR.ATTRIBUTES = {
        { "acceleration", "Acceleration" }, { "sprintspeed", "Sprint speed" }, { "agility", "Agility" }, { "balance", "Balance" },
        { "reactions", "Reactions" }, { "ballcontrol", "Ball control" }, { "dribbling", "Dribbling" }, { "finishing", "Finishing" },
        { "shotpower", "Shot power" }, { "longshots", "Long shots" }, { "volleys", "Volleys" }, { "penalties", "Penalties" },
        { "positioning", "Positioning" }, { "vision", "Vision" }, { "crossing", "Crossing" }, { "freekickaccuracy", "Free kick accuracy" },
        { "shortpassing", "Short passing" }, { "longpassing", "Long passing" }, { "curve", "Curve" }, { "interceptions", "Interceptions" },
        { "headingaccuracy", "Heading accuracy" }, { "defensiveawareness", "Defensive awareness" }, { "standingtackle", "Standing tackle" },
        { "slidingtackle", "Sliding tackle" }, { "jumping", "Jumping" }, { "stamina", "Stamina" }, { "strength", "Strength" },
        { "aggression", "Aggression" }, { "composure", "Composure" }, { "gkdiving", "GK diving" }, { "gkhandling", "GK handling" },
        { "gkkicking", "GK kicking" }, { "gkreflexes", "GK reflexes" }, { "gkpositioning", "GK positioning" }
    }
    -- A player's attributes from a players row ({ ["Sprint speed"] = 81, ... }); nil when the row does not carry them.
    function CR.attributes(row)
        local out, n = {}, 0
        for _, a in ipairs(CR.ATTRIBUTES) do
            local v = to_int(row[a[1]], 0)
            if v >= 1 and v <= 99 then
                out[a[2]] = v
                n = n + 1
            end
        end
        if n < 10 then return nil end
        return out
    end

    -- The game's form value (1 = worst, 5 = best) in the app's words.
    CR.FORMS = { [1] = "Poor", [2] = "Poor", [3] = "Okay", [4] = "Good", [5] = "Excellent" }
    function CR.form_label(v) return CR.FORMS[to_int(v, 0)] end

    -- The season a day belongs to: from August on it is the new one (a cup final in May 2027 belongs to 2026/27).
    function CR.season_start(iso)
        local y, m = tostring(iso or ""):match("^(%d%d%d%d)%-(%d%d)")
        if not y then return nil end
        y, m = tonumber(y), tonumber(m)
        if m >= 8 then return y end
        return y - 1
    end
    function CR.season_label(start) return string.format("%d/%02d", start, (start + 1) % 100) end

    -- Money the game keeps in units, as millions for the app (a value already below 10,000 is taken as millions).
    function CR.millions(v)
        local n = to_num(v, -1)
        if n < 0 then return nil end
        if n >= 10000 then n = n / 1000000 end
        return math.floor(n * 100 + 0.5) / 100
    end

    -- A weekly wage in thousands (the app's unit); at least 1 for a paid player.
    function CR.wage_k(v)
        local n = to_num(v, 0)
        if n <= 0 then return nil end
        if n >= 1000 then return math.floor(n / 1000 + 0.5) end
        return 1
    end

    function CR.json_value(v, indent)
        local t = type(v)
        if t == "string" then return '"' .. jstr(v) .. '"' end
        if t == "boolean" then return tostring(v) end
        if t == "number" then
            if v ~= v or v == math.huge or v == -math.huge then return "0" end
            if math.type and math.type(v) == "integer" then return string.format("%d", v) end
            if v == math.floor(v) and math.abs(v) < 1e15 then return string.format("%d", math.floor(v)) end
            local s = string.format("%.3f", v):gsub("0+$", ""):gsub("%.$", "")
            return s
        end
        if t ~= "table" then return "null" end
        local pad = indent and ("\n" .. string.rep("  ", indent + 1)) or ""
        local close = indent and ("\n" .. string.rep("  ", indent)) or ""
        local next_indent = indent and indent + 1 or nil
        if getmetatable(v) == CR.ARRAY or (#v > 0) then
            if #v == 0 then return "[]" end
            local parts = {}
            for i = 1, #v do parts[i] = CR.json_value(v[i], nil) end
            if indent then return "[" .. pad .. table.concat(parts, "," .. pad) .. close .. "]" end
            return "[" .. table.concat(parts, ", ") .. "]"
        end
        local keys = {}
        for k in pairs(v) do keys[#keys + 1] = tostring(k) end
        table.sort(keys)
        if #keys == 0 then return "{}" end
        local parts = {}
        for _, k in ipairs(keys) do
            local val = v[k]
            if val == nil then val = v[tonumber(k)] end
            parts[#parts + 1] = '"' .. jstr(k) .. '": ' .. CR.json_value(val, next_indent)
        end
        if indent then return "{" .. pad .. table.concat(parts, "," .. pad) .. close .. "}" end
        return "{" .. table.concat(parts, ", ") .. "}"
    end

    -- The payload text. report holds the sections (any of them may be missing); the order of keys is fixed, so the same
    -- report always gives the same text and an unchanged report is not sent again.
    function CR.payload(report)
        local body = {}
        for k, v in pairs(report) do body[k] = v end
        body.dataType = "career"
        body.title = "Career report"
        body.message = "Tables, fixtures and squad details from the game"
        return CR.json_value(body, 0) .. "\n"
    end
end
-- END career_report

-- BEGIN squad_status (this exact block is also run by tests/lua/test_transfer_logic.lua)
local SCRIPT_PROTOCOL = 2

-- FC 26 contracts run to the end of June of players.contractvaliduntil. Checked against the game's own squad screen:
-- in August 2028 a player with contractvaliduntil 2031 shows "2y 10m". Anything that is not a plausible year is unknown.
local function contract_end_iso(valid_until)
    local year = math.floor(tonumber(valid_until) or 0)
    if year < 2000 or year > 2100 then return nil end
    return string.format("%04d-06-30", year)
end

-- row: { pid =, name =, contract_year =, loan = { dir = "in"|"out", club =, ends = "yyyy-mm-dd" | nil, to_buy = bool } | nil }
local function squad_row_json(row)
    local parts = {
        '"name": "' .. jstr(row.name) .. '"',
        string.format('"pid": %d', math.floor(tonumber(row.pid) or 0))
    }
    local contract_end = contract_end_iso(row.contract_year)
    if contract_end then parts[#parts + 1] = '"contractEnd": "' .. contract_end .. '"' end
    if row.loan then
        local loan = {
            '"dir": "' .. jstr(row.loan.dir) .. '"',
            '"club": "' .. jstr(row.loan.club or "") .. '"'
        }
        if tostring(row.loan.ends or ""):match("^%d%d%d%d%-%d%d%-%d%d$") then
            loan[#loan + 1] = '"end": "' .. row.loan.ends .. '"'
        end
        if row.loan.to_buy then loan[#loan + 1] = '"toBuy": true' end
        parts[#parts + 1] = '"loan": {' .. table.concat(loan, ", ") .. '}'
    end
    return "{" .. table.concat(parts, ", ") .. "}"
end

-- The payload the app reads: dataType "squad". Rows are sorted so the same squad always gives the same text.
local function squad_payload(rows, career_date)
    local sorted = {}
    for _, row in ipairs(rows) do sorted[#sorted + 1] = row end
    table.sort(sorted, function(a, b)
        if (a.name or "") ~= (b.name or "") then return (a.name or "") < (b.name or "") end
        return (a.pid or 0) < (b.pid or 0)
    end)
    local items = {}
    for _, row in ipairs(sorted) do items[#items + 1] = "    " .. squad_row_json(row) end
    return table.concat({
        "{",
        '  "dataType": "squad",',
        '  "title": "Squad contracts",',
        '  "message": "Contracts and loans of ' .. tostring(#sorted) .. ' players",',
        '  "careerDate": "' .. jstr(career_date or "") .. '",',
        '  "players": [',
        table.concat(items, ",\n"),
        "  ]",
        "}"
    }, "\n")
end

-- Only a change in what is reported is worth sending again.
local function squad_signature(rows)
    local keys = {}
    for _, row in ipairs(rows) do keys[#keys + 1] = squad_row_json(row) end
    table.sort(keys)
    return table.concat(keys, "|")
end

-- script_status.json: what the bridge and the app show as "Capture script". Numbers only where there is a value.
local function status_json(s, now)
    local function num(v) return string.format("%d", math.floor(tonumber(v) or 0)) end
    local parts = {
        '"app": "fc26-capture"',
        '"version": "' .. jstr(s.version) .. '"',
        '"protocol": ' .. num(SCRIPT_PROTOCOL),
        '"time": ' .. num(now),
        '"started": ' .. num(s.started),
        '"exporter": ' .. tostring(s.exporter == true),
        '"club": "' .. jstr(s.club or "") .. '"',
        '"careerDate": "' .. jstr(s.career_date or "") .. '"',
        '"scans": ' .. num(s.scans),
        '"lastMatch": ' .. num(s.last_match),
        '"lastTransfer": ' .. num(s.last_transfer),
        '"lastSquad": ' .. num(s.last_squad),
        '"pendingMatches": ' .. num(s.pending_matches),
        '"undelivered": ' .. num(s.undelivered)
    }
    if s.resend then
        parts[#parts + 1] = '"resend": {"id": "' .. jstr(s.resend.id) .. '", "time": ' .. num(s.resend.time) ..
            ', "result": "' .. jstr(s.resend.result) .. '", "detail": "' .. jstr(s.resend.detail) .. '"}'
    end
    return "{\n  " .. table.concat(parts, ",\n  ") .. "\n}\n"
end

-- resend_request.json is written by the bridge: { "id": "...", "time": ... }. Returns the id (never empty).
local function parse_resend_request(text)
    local id = tostring(text or ""):match('"id"%s*:%s*"([^"]*)"')
    if id == nil or id == "" then id = "manual" end
    return id:sub(1, 64)
end
-- END squad_status

local STATUS_FILE = COMMUNITY_ROOT .. "/script_status.json"
local RESEND_FILE = COMMUNITY_ROOT .. "/resend_request.json"
-- the health report is written every 5 s; the squad's contracts are checked 20 s after the start (the start-up scans
-- come first), then every 10 minutes and after a transfer
local squad_scan = { started = os.time(), last_check = 0, signature = nil, status_every = 5, first_after = 20, every = 600 }

local function write_script_status(force)
    local now = os.time()
    if not force and now - status.written < squad_scan.status_every then return end
    status.written = now
    local pending_out = 0
    for json_path in pairs(outbox_pending) do
        if file_exists(json_path) then pending_out = pending_out + 1 end
    end
    local _, career_date = pcall(function() return (get_current_career_date()) end)
    local text = status_json({
        version = VERSION,
        started = status.started,
        exporter = atlas_exporter_active(),
        club = state and state.managed_team_name or "",
        career_date = type(career_date) == "string" and career_date or "",
        scans = status.scans,
        last_match = status.last_match,
        last_transfer = status.last_transfer,
        last_squad = status.last_squad,
        pending_matches = state and state.pending_matches and #state.pending_matches or 0,
        undelivered = pending_out,
        resend = status.resend
    }, now)
    local f = io.open(STATUS_FILE, "wb")
    if f then
        f:write(text)
        f:close()
    end
end

-- The phone's "Re-send last match" button reaches the bridge, which leaves resend_request.json here.
local function service_resend_request()
    local f = io.open(RESEND_FILE, "rb")
    if not f then return end
    local text = f:read("*a") or ""
    f:close()
    pcall(os.remove, RESEND_FILE)
    local id = parse_resend_request(text)
    log_monitor("Re-send of the latest match requested from the app [" .. id .. "]")
    local ok, result, detail = pcall(resend_latest_match)
    if ok then
        status.resend = { id = id, time = os.time(), result = result, detail = tostring(detail or "") }
    else
        status.resend = { id = id, time = os.time(), result = "error", detail = tostring(result):sub(1, 200) }
        log_monitor("Re-send failed: " .. tostring(result))
    end
    write_script_status(true)
end

-- Contract end (players.contractvaliduntil) of the wanted players: pid -> year.
local function read_contract_years(wanted)
    local years = {}
    pcall(function()
        if LE and LE.db then
            local pt = LE.db:GetTable("players")
            if pt then
                local r = pt:GetFirstRecord()
                while r and r > 0 do
                    local pid = tonumber(pt:GetRecordFieldValue(r, "playerid")) or 0
                    if pid > 0 and wanted[pid] then
                        years[pid] = tonumber(pt:GetRecordFieldValue(r, "contractvaliduntil")) or 0
                    end
                    r = pt:GetNextValidRecord()
                end
            end
        end
    end)
    if next(years) == nil then
        pcall(function()
            if type(GetDBTableRows) == "function" then
                local rows = GetDBTableRows("players")
                if type(rows) == "table" then
                    for _, r in ipairs(rows) do
                        local pid = tonumber(r.playerid and r.playerid.value or r.playerid) or 0
                        if pid > 0 and wanted[pid] then
                            years[pid] = tonumber(r.contractvaliduntil and r.contractvaliduntil.value or r.contractvaliduntil) or 0
                        end
                    end
                end
            end
        end)
    end
    return years
end

local function collect_squad_rows(managed_tid)
    local squad, _, club_links = get_managed_squad_state(managed_tid)
    local loan_rows = read_loan_rows()
    local loans_in, loans_out = classify_loans(loan_rows, managed_tid, squad, club_links)
    local loan_info = {}
    for _, row in ipairs(loan_rows) do
        if row.pid and row.pid > 0 then loan_info[row.pid] = row end
    end
    local wanted = {}
    for pid in pairs(squad) do wanted[pid] = true end
    for pid in pairs(loans_out) do wanted[pid] = true end
    local years = read_contract_years(wanted)

    local rows = {}
    for pid in pairs(wanted) do
        local name = safe_player_name(pid)
        if not is_unnamed_player(name) then
            local loan = nil
            local info = loan_info[pid] or {}
            if loans_in[pid] then
                loan = { dir = "in", club = safe_team_name(loans_in[pid]), ends = info.ends, to_buy = info.to_buy }
            elseif loans_out[pid] then
                local dest = loans_out[pid]
                loan = { dir = "out", club = dest > 0 and safe_team_name(dest) or "", ends = info.ends, to_buy = info.to_buy }
            end
            rows[#rows + 1] = { pid = pid, name = name, contract_year = years[pid], loan = loan }
        end
    end
    return rows
end

-- Sends the squad's contract and loan end dates when they changed (and once after the script starts).
local function check_squad_contracts()
    if not state or not state.active then return end
    if _G["FC26_AUTOCAPTURE_CURRENT_INSTANCE"] ~= SCRIPT_INSTANCE then return end
    local now = os.time()
    if now - squad_scan.started < squad_scan.first_after then return end
    if not status.squad_dirty and now - squad_scan.last_check < squad_scan.every then return end
    squad_scan.last_check = now
    status.squad_dirty = false

    local rows = collect_squad_rows(state.managed_team_id)
    if #rows == 0 then return end
    local signature = squad_signature(rows)
    if signature == squad_scan.signature then return end
    squad_scan.signature = signature
    local _, career_date = pcall(function() return (get_current_career_date()) end)
    send_payload_to_bridge(squad_payload(rows, type(career_date) == "string" and career_date or ""),
        "Squad contracts (" .. #rows .. " players)")
end

-- ---------------------------------------------------------------------------------------------------------------------
-- Career report: what the game knows beyond matches and transfers (see the career_report block). Read every few
-- minutes, and soon after a match or a transfer; sent only when something changed.
-- Tables it reads (all opened before by the table census without trouble): leagueteamlinks, teamplayerlinks,
-- career_playercontract, career_playerlastgrowth, playersuspensions, players, teams, teamstadiumlinks, stadiums,
-- clubcoefficients, competition, career_competitionprogress. It never opens "transfers" or "scenarioevents" (those
-- closed the game).
-- ---------------------------------------------------------------------------------------------------------------------
local check_career_report
do
    local CAREER_NEVER = { transfers = true, scenarioevents = true }
    local CAREER_FIRST_SECONDS = 40
    local CAREER_CHECK_SECONDS = 300
    local CAREER_AFTER_EVENT_SECONDS = 30
    -- champions: each competition's holder at the last report; won: competitions the club took over while running
    local career_scan = { started = os.time(), last_check = 0, signature = nil, file = nil, champions = nil, won = {} }

    -- Rows of a table with the wanted fields; a field the table does not have is simply missing (nil). Returns rows and
    -- whether the table could be opened at all.
    -- only = { field = "playerid", ids = { [id] = true } } keeps just those rows (a big table is not copied whole).
    -- fields a table does not have: Live Editor answers nil and logs an error each time, so they are asked for once
    local career_missing = {}
    local function career_rows(name, fields, only)
        if CAREER_NEVER[tostring(name):lower()] then return {}, false end
        local missing = career_missing[name] or {}
        career_missing[name] = missing
        local rows, opened, seen = {}, false, 0
        local function wanted(get)
            if not only then return true end
            local v = get(only.field)
            if type(v) == "table" then v = v.value end
            return only.ids[tonumber(v) or -1] == true
        end
        pcall(function()
            if type(GetDBTableRows) == "function" then
                local raw = GetDBTableRows(name)
                if type(raw) == "table" then
                    opened = true
                    for _, rr in ipairs(raw) do
                        seen = seen + 1
                        if wanted(function(f) return rr[f] end) then
                            local row = {}
                            for _, f in ipairs(fields) do
                                local v = rr[f]
                                if type(v) == "table" then v = v.value end
                                row[f] = v
                            end
                            rows[#rows + 1] = row
                        end
                    end
                end
            end
        end)
        if opened and seen > 0 then return rows, true end
        pcall(function()
            if LE and LE.db then
                local t = LE.db:GetTable(name)
                if t then
                    opened = true
                    local r = t:GetFirstRecord()
                    while r and r > 0 do
                        local rec = r
                        -- a field that has never given a value is one the game does not have: it is not read again
                        -- (the Live Editor logs an error for every try). Once it gave one, an empty row does not count.
                        local function get(f)
                            if missing[f] == true then return nil end
                            local ok, v = pcall(function() return t:GetRecordFieldValue(rec, f) end)
                            if ok and v ~= nil then missing[f] = false return v end
                            if missing[f] == nil then missing[f] = true end
                            return nil
                        end
                        if wanted(get) then
                            local row = {}
                            for _, f in ipairs(fields) do row[f] = get(f) end
                            rows[#rows + 1] = row
                        end
                        r = t:GetNextValidRecord()
                    end
                end
            end
        end)
        return rows, opened
    end

    local function career_comp_name(obj_id)
        local ok, name = pcall(function() return GetCompetitionNameByObjID(tonumber(obj_id)) end)
        if ok and name and tostring(name) ~= "" and tostring(name) ~= "INVALID" then return tostring(name) end
        return nil
    end

    local function collect_career_report(managed_tid)
        local report = {}
        local today = (get_current_career_date())
        if type(today) ~= "string" then today = "" end
        if today ~= "" then report.careerDate = today end
        local month = tonumber(today:sub(6, 7)) or 0
        local season_end = (month == 6 or month == 7)

        local team_names = {}
        local function tname(tid)
            tid = tonumber(tid) or 0
            if tid <= 0 then return "" end
            if not team_names[tid] then team_names[tid] = safe_team_name(tid) end
            return team_names[tid]
        end

        -- Fixtures still to play (from the game's fixture list, all competitions).
        local managed = get_all_managed_fixtures(managed_tid)
        local per_comp, upcoming, managed_comp_name = {}, CR.array(), {}
        for _, f in ipairs(managed) do
            managed_comp_name[f.compobjid] = f.competition
            per_comp[f.competition] = (per_comp[f.competition] or 0) + 1
            if not f.completed and f.date ~= "" and (today == "" or f.date >= today) and #upcoming < 40 then
                upcoming[#upcoming + 1] = {
                    key = string.format("%d-%d", to_int(f.compobjid, 0), to_int(f.fixture_id, 0)),
                    date = f.date, time = CR.kickoff(f.time_raw), competition = f.competition,
                    round = determine_stage(f.competition, per_comp[f.competition]),
                    opponent = f.opponent_name, home = f.is_home == true
                }
            end
        end
        if #managed > 0 then report.fixtures = upcoming end

        -- League tables from the season's results, with every club's last five league results.
        local fixtures, standings = read_valid_fixtures(), read_valid_standings()
        local own_comp = pick_league_comp(managed, nil, is_league_competition)
        local own_league = own_comp and (career_comp_name(own_comp) or managed_comp_name[own_comp]) or nil
        local wanted = CR.wanted_leagues(own_league)
        local comps, comp_fixtures = {}, {}
        for _, f in ipairs(fixtures) do
            local c = f.competition_object_id
            if comps[c] == nil then comps[c] = career_comp_name(c) or managed_comp_name[c] or false end
            comp_fixtures[c] = comp_fixtures[c] or {}
            local list = comp_fixtures[c]
            list[#list + 1] = f
        end
        local tables_out, league_rows = CR.array(), {}
        for c, name in pairs(comps) do
            if name and wanted[name:lower()] and is_league_competition(name) then
                local built = build_league_table(fixtures, standings, c, nil)
                if #built.rows >= 4 then
                    local results = {}
                    local total, done = 0, 0
                    for _, f in ipairs(comp_fixtures[c]) do
                        total = total + 1
                        local h, a = standings[f.home_standing_index], standings[f.away_standing_index]
                        if f.completed and h and a then
                            done = done + 1
                            local hs, as = tonumber(f.home_score) or 0, tonumber(f.away_score) or 0
                            results[h.team_id] = results[h.team_id] or {}
                            results[a.team_id] = results[a.team_id] or {}
                            table.insert(results[h.team_id], { date = f.date_raw, gf = hs, ga = as })
                            table.insert(results[a.team_id], { date = f.date_raw, gf = as, ga = hs })
                        end
                    end
                    local rows = CR.array()
                    for _, t in ipairs(built.rows) do
                        rows[#rows + 1] = { club = tname(t.team_id), p = t.played, w = t.won, d = t.drawn, l = t.lost, gf = t.gf, ga = t.ga,
                                            pts = t.pts, form = CR.form(results[t.team_id] or {}), user = (t.team_id == managed_tid) }
                    end
                    tables_out[#tables_out + 1] = { league = name, country = CR.country(name), rows = rows }
                    league_rows[name] = { rows = built.rows, complete = (total > 0 and done == total) }
                end
            end
        end
        table.sort(tables_out, function(a, b) return a.league < b.league end)
        if #tables_out > 0 then report.tables = tables_out end

        -- Squad links: numbers and the team sheet of the club; league goals of everybody (top scorers).
        local links = career_rows("teamplayerlinks", { "teamid", "playerid", "jerseynumber", "position", "leaguegoals",
                                                       "leagueappearances", "yellows", "reds", "form" })
        local squad, xi, bench, goals_by_team = {}, {}, CR.array(), {}
        for _, r in ipairs(links) do
            local tid, pid = tonumber(r.teamid) or 0, tonumber(r.playerid) or 0
            if pid > 0 and tid > 0 then
                if tid == managed_tid then
                    squad[pid] = { number = tonumber(r.jerseynumber), pos = tonumber(r.position), apps = tonumber(r.leagueappearances),
                                   goals = tonumber(r.leaguegoals), yellow = tonumber(r.yellows), red = tonumber(r.reds),
                                   form = CR.form_label(r.form) }
                    local pos = tonumber(r.position) or 99
                    if pos <= 27 then xi[#xi + 1] = { pid = pid, pos = pos } elseif pos == 28 then bench[#bench + 1] = pid end
                end
                local g = tonumber(r.leaguegoals) or 0
                if g > 0 then
                    goals_by_team[tid] = goals_by_team[tid] or {}
                    table.insert(goals_by_team[tid], { pid = pid, goals = g })
                end
            end
        end
        local function top_scorers(league_name, limit)
            local lr = league_rows[league_name]
            if not lr then return {} end
            local all = {}
            for _, t in ipairs(lr.rows) do
                for _, g in ipairs(goals_by_team[t.team_id] or {}) do all[#all + 1] = { pid = g.pid, goals = g.goals, tid = t.team_id } end
            end
            table.sort(all, function(a, b) if a.goals ~= b.goals then return a.goals > b.goals end return a.pid < b.pid end)
            local out = {}
            for i = 1, math.min(limit, #all) do
                out[#out + 1] = { player = safe_player_name(all[i].pid), club = tname(all[i].tid), value = all[i].goals, user = (all[i].tid == managed_tid) }
            end
            return out
        end
        if own_league and league_rows[own_league] then
            local goals = top_scorers(own_league, 10)
            if #goals > 0 then report.leaders = CR.array({ { competition = own_league, goals = CR.array(goals) } }) end
        end

        -- The team sheet picked in the game.
        table.sort(xi, function(a, b) return a.pos < b.pos end)
        if #xi >= 7 then
            local ids, names, positions = CR.array(), {}, {}
            for _, e in ipairs(xi) do
                ids[#ids + 1] = e.pid
                local pname = safe_position_name(e.pos)
                names[#names + 1] = pname
                positions[tostring(e.pid)] = pname
            end
            report.teamsheet = { formation = CR.formation(names) or "", xi = ids, bench = bench, positions = positions }
        end

        -- The squad: roles and wages, ratings, injuries and suspensions.
        local contracts = {}
        for _, r in ipairs((career_rows("career_playercontract", { "playerid", "teamid", "playerrole", "wage" }))) do
            local pid = tonumber(r.playerid) or 0
            if squad[pid] then contracts[pid] = r end
        end
        local ratings, squad_ids = {}, {}
        for pid in pairs(squad) do squad_ids[pid] = true end
        local player_fields = { "playerid", "overallrating", "potential" }
        for _, a in ipairs(CR.ATTRIBUTES) do player_fields[#player_fields + 1] = a[1] end
        for _, f in ipairs(PROFILE.FIELDS) do player_fields[#player_fields + 1] = f end
        for _, r in ipairs((career_rows("players", player_fields, { field = "playerid", ids = squad_ids }))) do
            local pid = tonumber(r.playerid) or 0
            if squad[pid] then ratings[pid] = r end
        end
        local injuries = {}
        local growth, growth_open = career_rows("career_playerlastgrowth", { "playerid", "injurydate", "injuryduration" })
        for _, r in ipairs(growth) do
            local pid = tonumber(r.playerid) or 0
            if squad[pid] then injuries[pid] = CR.injury(r.injurydate, r.injuryduration, today) end
        end
        report.injuriesKnown = growth_open and #growth > 0
        local suspensions, susp_known = {}, false
        local susp_fields = { "playerid", "games", "numgames", "gamesremaining", "suspensiongames", "nummatches", "matches", "length", "duration" }
        local susp_rows, susp_open = career_rows("playersuspensions", susp_fields)
        if susp_open then
            susp_known = true
            for _, r in ipairs(susp_rows) do
                local pid = tonumber(r.playerid) or 0
                local games
                for i = 2, #susp_fields do
                    local v = tonumber(r[susp_fields[i]])
                    if v and v > 0 and v < 20 then games = math.floor(v) break end
                end
                if pid > 0 and not games then susp_known = false end      -- rows exist but the game count is not readable
                if pid > 0 and games and squad[pid] then suspensions[pid] = games end
            end
        end
        report.suspensionsKnown = susp_known
        local players = CR.array()
        for pid, info in pairs(squad) do
            local name = safe_player_name(pid)
            if not is_unnamed_player(name) then
                local p = { playerId = pid, name = name }
                if info.number and info.number > 0 and info.number < 100 then p.number = math.floor(info.number) end
                local c = contracts[pid]
                if c then
                    p.role = CR.role(c.playerrole)
                    p.wage = CR.wage_k(c.wage)
                end
                local rt = ratings[pid]
                if rt then
                    local o, pot = to_int(rt.overallrating, 0), to_int(rt.potential, 0)
                    if o > 0 and o < 100 then p.overall = o end
                    if pot > 0 and pot < 100 then p.potential = pot end
                    p.attributes = CR.attributes(rt)
                    -- positions, birthday, nation...: the app corrects a card that was made with guessed details
                    p.profile = PROFILE.from_row(rt)
                end
                if info.form then p.form = info.form end
                if injuries[pid] then p.injury = { name = "Injury", start = injuries[pid].start, back = injuries[pid].back } end
                if suspensions[pid] then p.suspension = { games = suspensions[pid] } end
                players[#players + 1] = p
            end
        end
        table.sort(players, function(a, b) return a.playerId < b.playerId end)
        if #players > 0 then report.players = players end

        -- The squad's league statistics as the game counts them (every match, also those not logged in the app).
        local stats = CR.array()
        for pid, info in pairs(squad) do
            if info.apps ~= nil and (info.apps > 0 or (info.goals or 0) > 0) then
                local name = safe_player_name(pid)
                if not is_unnamed_player(name) then
                    stats[#stats + 1] = { playerId = pid, name = name, competition = own_league or "League", apps = math.floor(info.apps),
                                          goals = math.floor(info.goals or 0), yellow = math.floor(info.yellow or 0), red = math.floor(info.red or 0) }
                end
            end
        end
        table.sort(stats, function(a, b) return a.playerId < b.playerId end)
        if #stats > 0 then report.stats = stats end

        -- The club: honours, founded, rival, stadium and the transfer budget.
        local club = {}
        for _, r in ipairs((career_rows("teams", { "teamid", "foundationyear", "rivalteam", "leaguetitles", "domesticcups", "uefa_cl_wins", "uefa_el_wins",
                                                   "uefa_uecl_wins", "domesticprestige", "internationalprestige" }))) do
            if tonumber(r.teamid) == managed_tid then
                local home, abroad = to_int(r.domesticprestige, 0), to_int(r.internationalprestige, 0)
                if home >= 1 and home <= 10 then club.prestigeHome = home end
                if abroad >= 1 and abroad <= 10 then club.prestigeAbroad = abroad end
                local founded = to_int(r.foundationyear, 0)
                if founded > 1800 and founded < 2100 then club.founded = tostring(founded) end
                local rival = tonumber(r.rivalteam) or 0
                if rival > 0 and rival ~= managed_tid then club.rival = tname(rival) end
                if r.leaguetitles ~= nil then club.leagueTitles = to_int(r.leaguetitles, 0) end
                if r.domesticcups ~= nil then club.domesticCups = to_int(r.domesticcups, 0) end
                if r.uefa_cl_wins ~= nil or r.uefa_el_wins ~= nil or r.uefa_uecl_wins ~= nil then
                    club.europeanTrophies = to_int(r.uefa_cl_wins, 0) + to_int(r.uefa_el_wins, 0) + to_int(r.uefa_uecl_wins, 0)
                end
                break
            end
        end
        local stadium_id
        for _, r in ipairs((career_rows("teamstadiumlinks", { "teamid", "stadiumid" }))) do
            if tonumber(r.teamid) == managed_tid then stadium_id = tonumber(r.stadiumid) break end
        end
        if stadium_id then
            for _, r in ipairs((career_rows("stadiums", { "stadiumid", "name", "capacity" }))) do
                if tonumber(r.stadiumid) == stadium_id then
                    if r.name and tostring(r.name) ~= "" then club.stadium = tostring(r.name) end
                    local cap = to_int(r.capacity, 0)
                    if cap > 0 then club.capacity = cap end
                    break
                end
            end
        end
        if type(GetUserTransferBudget) == "function" then
            local ok, v = pcall(GetUserTransferBudget)
            local m = ok and CR.millions(v) or nil
            if m then club.transferBudget = m end
        end
        if next(club) then report.club = club end

        -- The European club ranking: the top ten, the clubs of the club's own country, and the club itself.
        local coeff = career_rows("clubcoefficients", { "teamid", "points", "year1" })
        if #coeff > 0 then
            table.sort(coeff, function(a, b) return (tonumber(a.points) or 0) > (tonumber(b.points) or 0) end)
            local league_of = {}
            for _, r in ipairs((career_rows("leagueteamlinks", { "teamid", "leagueid" }))) do league_of[tonumber(r.teamid) or 0] = tonumber(r.leagueid) end
            local league_names = {}
            local function country_of(tid)
                local lid = league_of[tid]
                if not lid then return "" end
                if league_names[lid] == nil then league_names[lid] = safe_league_name_by_id(lid) or "" end
                return CR.country(league_names[lid])
            end
            local own_country = country_of(managed_tid)
            local out, same_country = CR.array(), 0
            for i, r in ipairs(coeff) do
                local tid = tonumber(r.teamid) or 0
                local mine = tid == managed_tid
                local country = (i <= 100 or mine) and country_of(tid) or ""
                local take = i <= 10 or mine or (own_country ~= "" and country == own_country and i <= 100 and same_country < 12)
                if take and tid > 0 then
                    if i > 10 and not mine then same_country = same_country + 1 end
                    out[#out + 1] = { rank = i, club = tname(tid), country = country, season = to_num(r.year1, 0), total = to_num(r.points, 0), user = mine }
                end
            end
            if #out > 0 then report.coefficients = out end
        end

        -- Every competition's holder as the game keeps it.
        local champions = {}
        for _, r in ipairs((career_rows("competition", { "competitionid", "competitionchampionid" }))) do
            local holder = tonumber(r.competitionchampionid) or 0
            local ok, cname = pcall(function() return GetCompetitionNameByID(tonumber(r.competitionid)) end)
            cname = ok and cname and tostring(cname) or ""
            if cname ~= "" and cname ~= "INVALID" and holder > 0 and holder < 4294967295 then champions[cname] = holder end
        end

        -- Trophies the club won: what the game marks as won for the club (career_competitionprogress), a finished league
        -- it tops, a cup final it won (judged in June and July, when every final has been played), and any competition
        -- whose holder became the club while this script was running. The season is the one of the club's last match in
        -- that competition (or of the day it was noticed).
        local trophies, have = CR.array(), {}
        local function add_trophy(name, iso, is_league, start_year)
            local start = start_year or CR.season_start(iso)
            if not name or name == "" or not start then return end
            local key = name:lower() .. "|" .. start
            if have[key] then return end
            have[key] = true
            trophies[#trophies + 1] = { competition = name, season = CR.season_label(start), startYear = start, league = is_league == true }
        end
        local progress = career_rows("career_competitionprogress", { "teamid", "compobjid", "compshortname", "season", "hasteamwon" })
        local latest_season
        for _, r in ipairs(progress) do
            local sv = tonumber(r.season)
            if tonumber(r.teamid) == managed_tid and sv and (latest_season == nil or sv > latest_season) then latest_season = sv end
        end
        for _, r in ipairs(progress) do
            local sv = tonumber(r.season)
            local won = r.hasteamwon == true or tonumber(r.hasteamwon) == 1
            if tonumber(r.teamid) == managed_tid and won and (latest_season == nil or sv == latest_season) then
                local obj = tonumber(r.compobjid) or 0
                local name = career_comp_name(obj) or managed_comp_name[obj] or (r.compshortname and tostring(r.compshortname)) or ""
                local last_date
                for _, f in ipairs(managed) do
                    if tonumber(f.compobjid) == obj and f.completed and f.date ~= "" and (not last_date or f.date > last_date) then last_date = f.date end
                end
                if last_date then
                    add_trophy(name, last_date, is_league_competition(name))
                else
                    local now_season = CR.season_start(today)
                    if sv and now_season and (sv == now_season or sv == now_season - 1) then add_trophy(name, nil, is_league_competition(name), sv) end
                end
            end
        end
        for c, name in pairs(comps) do
            if name then
                local last
                for _, f in ipairs(comp_fixtures[c] or {}) do
                    if not last or (f.date_raw or 0) >= (last.date_raw or 0) then last = f end
                end
                local iso = last and ea_day_to_iso(last.date_raw) or ""
                if is_league_competition(name) and not name:lower():find("play", 1, true) then
                    local lr = league_rows[name]
                    if lr and lr.complete and lr.rows[1] and lr.rows[1].team_id == managed_tid then add_trophy(name, iso, true) end
                elseif season_end and last then
                    local h, a = standings[last.home_standing_index], standings[last.away_standing_index]
                    local fin = CR.final({ completed = last.completed, home = h and h.team_id, away = a and a.team_id, hs = last.home_score,
                                           as = last.away_score, hp = last.home_penalties, ap = last.away_penalties })
                    if fin and fin.winner == managed_tid then add_trophy(name, iso, false) end
                end
            end
        end
        if career_scan.champions ~= nil then
            for cname, holder in pairs(champions) do
                local before = career_scan.champions[cname]
                if holder == managed_tid and before ~= nil and before ~= managed_tid and today ~= "" then career_scan.won[cname] = today end
            end
        end
        career_scan.champions = champions
        for cname, iso in pairs(career_scan.won) do add_trophy(cname, iso, is_league_competition(cname)) end
        if #trophies > 0 then
            table.sort(trophies, function(a, b) if a.startYear ~= b.startYear then return a.startYear < b.startYear end return a.competition < b.competition end)
            report.trophies = trophies
        end

        -- June and July: last season's winners and top scorers (Around the World).
        if season_end then
            local holders, seen = CR.array(), {}
            for c, name in pairs(comps) do
                local region = name and CR.region(name)
                if region then
                    if is_league_competition(name) and not tostring(name):lower():find("play", 1, true) then
                        local lr = league_rows[name]
                        if lr and lr.complete and lr.rows[1] then
                            local first, second = lr.rows[1], lr.rows[2]
                            holders[#holders + 1] = { region = region, competition = name, winner = tname(first.team_id), league = true,
                                                      detail = first.pts .. " pts" .. (second and (" · " .. tname(second.team_id) .. " 2nd") or "") }
                            seen[name:lower()] = true
                        end
                    else
                        local last
                        for _, f in ipairs(comp_fixtures[c] or {}) do
                            if not last or (f.date_raw or 0) >= (last.date_raw or 0) then last = f end
                        end
                        if last then
                            local h, a = standings[last.home_standing_index], standings[last.away_standing_index]
                            local fin = CR.final({ completed = last.completed, home = h and h.team_id, away = a and a.team_id, hs = last.home_score,
                                                       as = last.away_score, hp = last.home_penalties, ap = last.away_penalties })
                            if fin then
                                holders[#holders + 1] = { region = region, competition = name, winner = tname(fin.winner), runnerUp = tname(fin.runner),
                                                          score = fin.score, pens = fin.pens or "" }
                                seen[name:lower()] = true
                            end
                        end
                    end
                end
            end
            -- Competitions without a final in the fixture list: the game's holder, without a score.
            for name, holder in pairs(champions) do
                local region = CR.region(name)
                if region and not seen[name:lower()] then
                    holders[#holders + 1] = { region = region, competition = name, winner = tname(holder), league = is_league_competition(name) }
                    seen[name:lower()] = true
                end
            end
            if #holders > 0 then
                table.sort(holders, function(a, b) return a.competition < b.competition end)
                report.holders = holders
            end
            local scorers = CR.array()
            for _, league in ipairs(CR.TOP_FIVE) do
                local best = top_scorers(league, 1)[1]
                if best then scorers[#scorers + 1] = { region = CR.region(league), competition = league, player = best.player, club = best.club, goals = best.value } end
            end
            if #scorers > 0 then report.topScorers = scorers end
        end
        return report
    end

    -- Sends the career report when it changed: shortly after the start, every few minutes, and soon after a match or a
    -- transfer. A report still waiting in the outbox is replaced by the newer one.
    function check_career_report()
        if not state or not state.active then return end
        if _G["FC26_AUTOCAPTURE_CURRENT_INSTANCE"] ~= SCRIPT_INSTANCE then return end
        local now = os.time()
        if now - career_scan.started < CAREER_FIRST_SECONDS then return end
        local due = now - career_scan.last_check >= CAREER_CHECK_SECONDS
        if status.career_dirty_at and now - status.career_dirty_at >= CAREER_AFTER_EVENT_SECONDS then due = true end
        if not due then return end
        career_scan.last_check = now
        status.career_dirty_at = nil
        local report = collect_career_report(state.managed_team_id)
        local payload = CR.payload(report)
        if payload == career_scan.signature then return end
        career_scan.signature = payload
        if career_scan.file and outbox_pending[career_scan.file] and file_exists(career_scan.file) then
            pcall(os.remove, career_scan.file)
            outbox_pending[career_scan.file] = nil
        end
        local before = {}
        for path in pairs(outbox_pending) do before[path] = true end
        send_payload_to_bridge(payload, "Career report (" .. tostring(report.players and #report.players or 0) .. " players, " ..
            tostring(report.tables and #report.tables or 0) .. " tables)")
        for path in pairs(outbox_pending) do
            if not before[path] then career_scan.file = path end
        end
    end
end

-- Run a periodic job; an error is logged (once a minute at most) instead of silently swallowed.
local last_error_log = {}
local function guarded(name, fn)
    local ok, err = xpcall(fn, debug.traceback)
    if not ok then
        local now = os.time()
        if now - (last_error_log[name] or 0) >= 60 then
            last_error_log[name] = now
            log_monitor("Error in " .. name .. ": " .. tostring(err))
        end
    end
end

local function tick()
    status.scans = status.scans + 1
    guarded("match detection", check_for_new_completed_match)
    guarded("pending matches", service_pending_matches)
    guarded("transfer scan", function() check_for_squad_transfers(false) end)
    guarded("outbox", service_outbox)
    guarded("re-send request", service_resend_request)
    guarded("squad contracts", check_squad_contracts)
    guarded("career report", check_career_report)
    guarded("status report", write_script_status)
end

-- The Atlas exporter (1.1.3 and newer) calls this the moment it has published a match's statistics, also when they
-- came out incomplete. The match waiting for them is sent right away.
_G["FC26_MATCH_STATS_PUBLISHED"] = function(result)
    if _G["FC26_AUTOCAPTURE_CURRENT_INSTANCE"] ~= SCRIPT_INSTANCE then return end
    _G["FC26_ATLAS_PUBLISHED"] = { at = os.time(), result = result }
    local ok = type(result) == "table" and result.success == true
    log_monitor("Atlas exporter published the match statistics" .. (ok and "" or " (incomplete: the game's own numbers are used)"))
    state.last_check_time = 0                 -- the fixture may only now be marked completed: look again at once
    guarded("match detection", check_for_new_completed_match)
    guarded("pending matches", service_pending_matches)
    guarded("outbox", service_outbox)
    guarded("status report", function() write_script_status(true) end)
end

-- Background Polling Timer (every 2 seconds, independent of the event listener)
if type(createTimer) == "function" then
    if _G["FC26_AUTOCAPTURE_TIMER"] then
        pcall(function() _G["FC26_AUTOCAPTURE_TIMER"].destroy() end)
    end
    local okT, timer = pcall(function()
        local t = createTimer(nil)
        t.Interval = 2000
        t.OnTimer = function()
            if _G["FC26_AUTOCAPTURE_CURRENT_INSTANCE"] ~= SCRIPT_INSTANCE then
                pcall(function() t.destroy() end)
                return
            end
            tick()
        end
        return t
    end)
    if okT and timer then
        _G["FC26_AUTOCAPTURE_TIMER"] = timer
    else
        log_monitor("createTimer failed - relying on Career Mode events only: " .. tostring(timer))
    end
end

-- Event Handler Hook
pcall(function()
    AddEventHandler("post__CareerModeEvent", function(events_manager, event_id, event_pointer)
        if _G["FC26_AUTOCAPTURE_CURRENT_INSTANCE"] ~= SCRIPT_INSTANCE then return end
        tick()
    end)
end)

pcall(write_script_status, true)
-- script_alive.lock stays open while the game runs this script. The game runs scripts only on career events, so in a
-- match or a menu the heartbeat above pauses; the open file tells the bridge the script is still there (Windows does
-- not let another program rename an open file). Closing the game closes the file.
pcall(function()
    local h = rawget(_G, "FC26_ALIVE_LOCK")
    if h == nil or io.type(h) ~= "file" then
        h = io.open(COMMUNITY_ROOT .. "/script_alive.lock", "wb")
        rawset(_G, "FC26_ALIVE_LOCK", h)
    end
    if h then
        h:seek("set", 0)
        h:write(SCRIPT_INSTANCE .. " " .. tostring(os.time()) .. "\r\n")
        h:flush()
    end
end)
log_monitor(string.format("Monitoring armed for %s (ID: %d). Completed matches baseline: %d. Instance: %s",
    state.managed_team_name, state.managed_team_id, state.last_count, SCRIPT_INSTANCE))

-- Detailed player statistics come from the Project Atlas exporter: start it unless it is already running
local atlas_started = false
do
    local okA, resA = xpcall(start_atlas_exporter, debug.traceback)
    if okA then atlas_started = resA == true else log_monitor("Could not start the Atlas exporter: " .. tostring(resA)) end
end
print("=========================================================================")
print(" [FC26 AutoCapture v" .. VERSION .. " ACTIVE] Tracking " .. state.managed_team_name .. "!")
print(" Real Data Capture: Match Telemetry & Instant Real-Time Transfer Tracking.")
if atlas_started then
    print(" Project Atlas exporter: running (detailed player stats). Matches wait up to " .. ATLAS_WAIT_SECONDS_EXPORTER .. "s for its export.")
else
    print(" Project Atlas exporter: NOT running. FC26_AutoCapture_v1.1.1.lua could not be found or started.")
    print("   Without it there are no snapshots (Documents\\FC26 Export\\Snapshots stays empty) and player stats are estimated.")
    print("   Run FC26_AutoCapture_v1.1.1.lua yourself first, then run FC26_Auto_Capture.lua again (see auto_capture_monitor.log).")
end
print(" Delivery log: Documents\\FC26 Export\\bridge_send_log.txt (undelivered items are retried).")
print(" Health report: Documents\\FC26 Export\\script_status.json (the bridge page and the app show it).")
print(" Background timer active (2s loop). Event listeners hooked.")
print(" Safe Startup: No old matches beamed on launch.")
print(" Commands:")
print("   - FC26_AutoCapture_SyncLatest()   : Force-send latest match")
print("   - FC26_AutoCapture_SyncTransfers(): Force-send current season transfers")
print("   - FC26_BeamTransfer(name, type, fee, club): Manually beam any transfer")
print("   - Deals the game does not store (fees, July deals made before the first run): list them in")
print("     Documents\\FC26 Export\\manual_transfers.csv  (date,player,type,fee_m,club) and run this script again")
print("   - FC26_AutoCapture_Status()       : Show queue + last bridge answers")
print("   - FC26_AutoCapture_SendNow()      : Send what waits in the outbox now (the bridge normally reads it itself)")
print("=========================================================================")
