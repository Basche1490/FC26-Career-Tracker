-- Checks for the Live Editor scripts. Run from the repository root:  lua5.3 tests/lua/test_transfer_logic.lua
--
-- They cannot start the game, so they run the real script code against a small pretend database:
--   1. every script still parses,
--   2. classify_loans (the block between BEGIN/END markers in FC26_Auto_Capture.lua) reads loans correctly
--      without a "loaned to" column,
--   3. FC26_Probe.lua runs to the end and writes a readable report, both when the game offers
--      GetDBTableRows and when only record-by-record access exists.

local script_path = (arg and arg[0]) or "tests/lua/test_transfer_logic.lua"
local root = script_path:match("^(.*)[/\\]tests[/\\]lua[/\\][^/\\]*$") or "."
local scripts = root .. "/live-editor-scripts/"

local failures, checks = 0, 0
local function check(cond, label, detail)
    checks = checks + 1
    if cond then
        print("  ok    " .. label)
    else
        failures = failures + 1
        print("  FAIL  " .. label .. (detail and ("\n        " .. tostring(detail)) or ""))
    end
end

local function read_file(path)
    local f = assert(io.open(path, "rb"), "cannot open " .. path)
    local s = f:read("a")
    f:close()
    return s
end

-- ---------------------------------------------------------------------------------------------------------
print("1. every script parses")
for _, name in ipairs({
    "FC26_Auto_Capture.lua", "FC26_AutoCapture_v1.1.1.lua", "FC26_MatchStatsExport.lua", "FC26_Snapshot.lua",
    "FC26_Probe.lua", "send_latest_match.lua", "telemetry_auto_loop.lua", "telemetry_sender_v2.lua",
    "telemetry_sync.lua", "telemetry_test_connection.lua"
}) do
    local f, err = loadfile(scripts .. name)
    check(f ~= nil, name, err)
end

-- ---------------------------------------------------------------------------------------------------------
print("2. loans are read without a 'loaned to' column")
do
    local src = read_file(scripts .. "FC26_Auto_Capture.lua")
    local block = src:match("%-%- BEGIN classify_loans[^\n]*\n(.-)%-%- END classify_loans")
    check(block ~= nil, "classify_loans block found between the markers")
    local chunk = assert(load(block .. "\nreturn classify_loans", "classify_loans"))
    local classify = chunk()

    local MANAGED = 115486
    local rows = {
        { pid = 2, from = MANAGED },  -- linked only to the loan club now
        { pid = 3, from = MANAGED },  -- still linked to us AND to the loan club
        { pid = 4, from = MANAGED },  -- only linked to us: destination unreadable
        { pid = 5, from = 902 },      -- loaned in from club 902, plays for us
        { pid = 99, from = 950 },     -- somebody else's loan
        { pid = 6, from = 903 },      -- loaned from 903 to a club that is not ours (not in our squad)
    }
    local squad = { [3] = true, [4] = true, [5] = true }
    local links = {
        [2] = { 900 },
        [3] = { MANAGED, 901 },
        [4] = { MANAGED },
        [5] = { 902, MANAGED },
        [6] = { 777 },
    }
    local loans_in, loans_out = classify(rows, MANAGED, squad, links)
    check(loans_out[2] == 900, "loan out, only the loan club is linked: destination 900", tostring(loans_out[2]))
    check(loans_out[3] == 901, "loan out, still linked to us too: destination is the other club (901)", tostring(loans_out[3]))
    check(loans_out[4] == 0, "loan out, destination unreadable: 0", tostring(loans_out[4]))
    check(loans_in[5] == 902, "loan in is detected with its parent club", tostring(loans_in[5]))
    check(loans_in[99] == nil and loans_out[99] == nil, "someone else's loan is ignored")
    check(loans_in[6] == nil and loans_out[6] == nil, "a loan between two other clubs is ignored")
    local n_in, n_out = 0, 0
    for _ in pairs(loans_in) do n_in = n_in + 1 end
    for _ in pairs(loans_out) do n_out = n_out + 1 end
    check(n_in == 1 and n_out == 3, "exactly one loan in and three loans out", n_in .. " in, " .. n_out .. " out")

    local no_links_in, no_links_out = classify({ { pid = 2, from = MANAGED } }, MANAGED, {}, nil)
    check(no_links_out[2] == 0 and next(no_links_in) == nil, "missing club links do not crash")
end

-- ---------------------------------------------------------------------------------------------------------
print("3. FC26_Probe.lua runs against a pretend game database")

local MANAGED = 115486
local TEAMS = {
    [MANAGED] = "Torquay AFC", [900] = "Juventus", [901] = "Fulham", [902] = "Monaco", [950] = "Elsewhere FC",
    [800] = "Leicester City"
}
local PLAYERS = {
    [1] = "Marc Palomino", [2] = "Anwar Missaoui", [3] = "Giacomo De Pieri", [4] = "Alex Canales",
    [5] = "Felipe Bravo", [6] = "Bruno Gonzaga", [7] = "Sold Winger"
}

local function build_data()
    return {
        career_users = { { userid = 1, clubteamid = MANAGED, usertype = 0 } },
        teamplayerlinks = {
            { teamid = MANAGED, playerid = 1 }, { teamid = 900, playerid = 2 }, { teamid = MANAGED, playerid = 3 },
            { teamid = 901, playerid = 3 }, { teamid = MANAGED, playerid = 4 }, { teamid = MANAGED, playerid = 5 },
            { teamid = 902, playerid = 5 }, { teamid = MANAGED, playerid = 6 }, { teamid = 800, playerid = 7 },
        },
        playerloans = {
            { playerid = 2, teamidloanedfrom = MANAGED }, { playerid = 3, teamidloanedfrom = MANAGED },
            { playerid = 4, teamidloanedfrom = MANAGED }, { playerid = 5, teamidloanedfrom = 902 },
        },
        players = {
            { playerid = 1, playerjointeamdate = 150000, isacademy = 0, overallrating = 81, potential = 81 },
            { playerid = 6, playerjointeamdate = 162802, isacademy = 0, overallrating = 79, potential = 80 }, -- 2028-07-09
            { playerid = 5, playerjointeamdate = 162833, isacademy = 0, overallrating = 77, potential = 77 }, -- 2028-08-09
        },
        career_transferhistory = {
            { playerid = 7, fromteamid = MANAGED, toteamid = 800, transferfee = 12500000, transferdate = 162810 },
            { playerid = 6, fromteamid = 950, toteamid = MANAGED, transferfee = 9000000, transferdate = 162802 },
            { playerid = 1, fromteamid = 950, toteamid = 800, transferfee = 1000000, transferdate = 160000 },
        },
        career_managerhistory = {
            { teamid = MANAGED, season = 2028, bigbuyplayername = "Bruno Gonzaga", bigbuyamount = 9000000 },
        },
        teams = {
            { teamid = MANAGED, teamname = "Torquay AFC", transferbudget = 8133637, clubworth = 40000000 },
        },
    }
end

local function install_env(data, with_api_rows, out_path)
    local env_missing = 0
    local function mock_table(rows)
        local t = { rows = rows, i = 0 }
        function t:GetFirstRecord() self.i = 1 return #self.rows > 0 and 1 or 0 end
        function t:GetNextValidRecord()
            self.i = self.i + 1
            if self.i <= #self.rows then return self.i end
            return 0
        end
        function t:GetRecordFieldValue(rec, field)
            local row = self.rows[rec]
            local v = row and row[field]
            if v == nil then env_missing = env_missing + 1 end -- the real game logs "no field" and returns nothing
            return v
        end
        return t
    end

    os.execute = function() return true end -- the scripts create folders with Windows commands; not needed here

    package.preload["imports/core/memory"] = function() return {} end
    package.preload["imports/other/helpers"] = function() return {} end
    package.preload["imports/services/enums"] = function() return {} end
    package.preload["imports/career_mode/helpers"] = function() return {} end

    _G.IsInCM = function() return true end
    _G.LE = { db = { GetTable = function(_, name)
        if data[name] then return mock_table(data[name]) end
        return nil
    end } }
    _G.GetPlayerName = function(pid) return PLAYERS[pid] end
    _G.GetTeamName = function(tid) return TEAMS[tid] end
    _G.GetUserTeamID = function() return MANAGED end
    _G.GetCurrentDate = function() return { year = 2028, month = 8, day = 15 } end
    if with_api_rows then
        _G.GetDBTableRows = function(name)
            local rows = data[name]
            if not rows then return nil end
            local out = {}
            for _, row in ipairs(rows) do
                local r = {}
                for k, v in pairs(row) do r[k] = { value = v } end
                out[#out + 1] = r
            end
            return out
        end
    else
        _G.GetDBTableRows = nil
    end
    _G.FC26_PROBE_OUT_PATH = out_path
    return function() return env_missing end
end

local function run_probe(with_api_rows)
    local out_path = os.tmpname()
    local missing = install_env(build_data(), with_api_rows, out_path)
    local quiet_print = _G.print
    _G.print = function() end
    local ok, err = pcall(dofile, scripts .. "FC26_Probe.lua")
    _G.print = quiet_print
    local report = ""
    local f = io.open(out_path, "rb")
    if f then report = f:read("a") f:close() os.remove(out_path) end
    return ok, err, report, missing()
end

for _, mode in ipairs({ { true, "with GetDBTableRows" }, { false, "record by record (candidate columns)" } }) do
    print("   mode: " .. mode[2])
    local ok, err, report, missing = run_probe(mode[1])
    check(ok, "probe runs to the end", err)
    check(report:find("SECTION FAILED", 1, true) == nil, "no section failed", report:match("SECTION FAILED[^\r\n]*"))
    check(report:find("Managed team: 115486 Torquay AFC", 1, true) ~= nil, "managed team is found")
    check(report:find("In-game date: 2028-08-15", 1, true) ~= nil, "in-game date is shown")
    check(report:find("total rows: 3", 1, true) ~= nil, "career_transferhistory is counted")
    check(report:find("transferfee=12500000", 1, true) ~= nil, "the sale with its fee is dumped")
    check(report:find("toteamid->Torquay AFC", 1, true) ~= nil, "team ids are shown with names")
    check(report:find("transferdate->2028-07-09", 1, true) ~= nil, "transfer dates are decoded")
    check(report:find("900 Juventus", 1, true) ~= nil, "the loan club of a loaned-out player is shown")
    check(report:find("901 Fulham", 1, true) ~= nil and report:find("115486 Torquay AFC", 1, true) ~= nil,
        "a player linked to two clubs shows both")
    check(report:find("players linked to the managed team: 5", 1, true) ~= nil, "squad list counts the players linked to us")
    check(report:find("(2028-07-09)", 1, true) ~= nil, "joining dates are decoded")
    check(report:find("bigbuyamount=9000000", 1, true) ~= nil, "manager history big buy is shown")
    check(report:find("transferbudget=8133637", 1, true) ~= nil, "team finance column is shown")
    -- the squad list puts the newest joiner first
    local squad_part = report:match("Your squad:.*") or ""
    local pos_newest = squad_part:find("Felipe Bravo", 1, true)
    local pos_middle = squad_part:find("Bruno Gonzaga", 1, true)
    local pos_oldest = squad_part:find("Marc Palomino", 1, true)
    check(pos_newest and pos_middle and pos_oldest and pos_newest < pos_middle and pos_middle < pos_oldest,
        "squad list is sorted by joining date, newest first")
    if not mode[1] then check(missing > 0, "candidate mode really probed columns the game does not have") end
end

print(string.format("\n%d checks, %d failed", checks, failures))
os.exit(failures == 0 and 0 or 1)
