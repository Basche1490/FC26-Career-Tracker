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
local out_print = print -- the pretend-game sessions below silence print while the script runs
local function check(cond, label, detail)
    checks = checks + 1
    if cond then
        out_print("  ok    " .. label)
    else
        failures = failures + 1
        out_print("  FAIL  " .. label .. (detail and ("\n        " .. tostring(detail)) or ""))
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
print("2b. the exporter is found even when Live Editor does not say where the script lives")
do
    local src = read_file(scripts .. "FC26_Auto_Capture.lua")
    local block = src:match("%-%- BEGIN find_script[^\n]*\n(.-)%-%- END find_script")
    check(block ~= nil, "find_script block found between the markers")

    local quiet_block = src:match("%-%- BEGIN quiet_exec[^\n]*\n(.-)%-%- END quiet_exec")
    check(quiet_block ~= nil, "quiet_exec block found between the markers")
    local popen_calls = 0

    local function run_search(files, env, cwd, subdirs_of)
        local saved_getenv, saved_execute, saved_popen, saved_rename = os.getenv, os.execute, io.popen, os.rename
        os.getenv = function(k) return env[k] end
        io.popen = function()
            popen_calls = popen_calls + 1 -- no program may be started just to find the current folder any more
            return { read = function() return cwd end, close = function() end }
        end
        -- a folder "exists" when the test says so (renaming a folder to itself is how the script asks)
        os.rename = function(a, b)
            if a == b and subdirs_of[a:lower()] then return true end
            return nil, "No such file or directory", 2
        end
        os.execute = function(cmd)
            -- mimic:  cmd /c dir /b /ad "<folder>" > "<file>" 2>nul & echo x> "<done file>"
            local folder, out = cmd:match('dir /b /ad "([^"]*)" > "([^"]*)"')
            if folder then
                local list = subdirs_of[folder:lower()] or {}
                local f = io.open((out:gsub("\\", "/")), "w")
                if f then f:write(table.concat(list, "\r\n")) f:close() end
            end
            return true
        end
        rawset(_G, "FC26_REAL_OS_EXECUTE", nil)
        local prelude = [[
            local SCRIPT_DIR, user_profile, FILES = ...
            local function trim(v) return (tostring(v or ""):gsub("^%s+", ""):gsub("%s+$", "")) end
            local function win_path(p) return (tostring(p or ""):gsub("/", "\\")) end
            local function file_exists(path) return FILES[path:lower()] == true end
        ]]
        local fn = assert(load(prelude .. quiet_block .. block .. "\nreturn find_script", "find_script"))
        local lower = {}
        for path in pairs(files) do lower[path:lower()] = true end
        local find = fn("", env.USERPROFILE or "", lower)
        local found, dirs = find("FC26_AutoCapture_v1.1.1.lua")
        os.getenv, os.execute, io.popen, os.rename = saved_getenv, saved_execute, saved_popen, saved_rename
        rawset(_G, "FC26_REAL_OS_EXECUTE", nil)
        return found, dirs
    end

    local base_env = { USERPROFILE = "C:\\Users\\Joel", TEMP = os.getenv("TMPDIR") or "/tmp" }
    local EXE = "FC26_AutoCapture_v1.1.1.lua"

    local found = run_search({ ["C:\\Users\\Joel\\Desktop\\FC26-LE\\FC 26 LE v26.3.6\\lua\\scripts\\" .. EXE] = true },
        base_env, nil, { ["c:\\users\\joel\\desktop\\fc26-le"] = { "FC 26 LE v26.3.6" } })
    check(found == "C:\\Users\\Joel\\Desktop\\FC26-LE\\FC 26 LE v26.3.6\\lua\\scripts\\" .. EXE,
        "found in Desktop\\FC26-LE\\<version>\\lua\\scripts", tostring(found))

    local env2 = { USERPROFILE = "C:\\Users\\Joel", OneDrive = "C:\\Users\\Joel\\OneDrive", TEMP = base_env.TEMP }
    found = run_search({ ["C:\\Users\\Joel\\OneDrive\\Desktop\\FC26-LE\\LE 26\\lua\\scripts\\" .. EXE] = true },
        env2, nil, { ["c:\\users\\joel\\onedrive\\desktop\\fc26-le"] = { "LE 26" } })
    check(found == "C:\\Users\\Joel\\OneDrive\\Desktop\\FC26-LE\\LE 26\\lua\\scripts\\" .. EXE,
        "found when the Desktop lives in OneDrive", tostring(found))

    found = run_search({ ["lua\\scripts\\" .. EXE] = true }, base_env, nil, {})
    check(found == "lua\\scripts\\" .. EXE, "found through Live Editor's working folder (lua\\scripts)", tostring(found))
    check(popen_calls == 0, "no program is started to look up the current folder", popen_calls)

    found = run_search({ ["C:\\Users\\Joel\\Downloads\\" .. EXE] = true }, base_env, nil, {})
    check(found == "C:\\Users\\Joel\\Downloads\\" .. EXE, "found in Downloads", tostring(found))

    local missing, searched = run_search({}, base_env, nil, {})
    check(missing == nil and #searched > 5, "returns nil and the searched folders when it is nowhere", tostring(missing))
end


-- ---------------------------------------------------------------------------------------------------------
print("2c. quiet folder handling and the Atlas export lookup")
do
    local src = read_file(scripts .. "FC26_Auto_Capture.lua")
    local quiet_block = src:match("%-%- BEGIN quiet_exec[^\n]*\n(.-)%-%- END quiet_exec")
    local atlas_block = src:match("%-%- BEGIN atlas_lookup[^\n]*\n(.-)%-%- END atlas_lookup")
    check(atlas_block ~= nil, "atlas_lookup block found between the markers")

    -- the exact commands the Atlas exporter builds (taken from its source) are answered without starting anything
    local exporter = read_file(scripts .. "FC26_AutoCapture_v1.1.1.lua")
    check(exporter:find([[os.execute('if not exist "' .. path .. '" mkdir "' .. path .. '"')]], 1, true) ~= nil
        and exporter:find([[os.execute('cmd.exe /C if not exist "' .. path .. '" mkdir "' .. path .. '"')]], 1, true) ~= nil,
        "the exporter still builds the mkdir commands this script answers")

    local function with_quiet(setup)
        local saved = { execute = os.execute, rename = os.rename, getenv = os.getenv, open = io.open, remove = os.remove }
        local real_calls, dirs, files = {}, {}, {}
        os.execute = function(cmd)
            real_calls[#real_calls + 1] = cmd
            local made = cmd:match('mkdir "([^"]+)"')
            if made then dirs[(made:gsub("\\", "/"))] = true end
            return true
        end
        os.rename = function(a, b)
            if a == b and dirs[(a:gsub("\\", "/"))] then return true end
            return nil, "No such file or directory", 2
        end
        os.getenv = function(k) if k == "TEMP" then return "/tmp" end return saved.getenv(k) end
        rawset(_G, "FC26_REAL_OS_EXECUTE", nil)
        local prelude = [[
            local FILES = ...
            local function trim(v) return (tostring(v or ""):gsub("^%s+", ""):gsub("%s+$", "")) end
            local function win_path(p) return (tostring(p or ""):gsub("/", "\\")) end
            local function file_exists(path) return FILES[path] == true end
        ]]
        local env = assert(load(prelude .. quiet_block .. "\nreturn QUIET", "quiet"))(files)
        local shimmed = os.execute
        local result = setup(env, shimmed, real_calls, dirs, files)
        os.execute, os.rename, os.getenv, os.remove = saved.execute, saved.rename, saved.getenv, saved.remove
        rawset(_G, "FC26_REAL_OS_EXECUTE", nil)
        _G.shellExecute, _G.getDirectoryList, _G.getFileList, _G.sleep = nil, nil, nil, nil
        return result
    end

    with_quiet(function(Q, execute, real_calls, dirs)
        dirs["C:/Users/Joel/Documents/FC26 Export"] = true
        local base = [[C:\Users\Joel\Documents\FC26 Export]]
        local ok, how, code = execute('if not exist "' .. base .. '" mkdir "' .. base .. '"')
        check(ok == true and how == "exit" and code == 0 and #real_calls == 0,
            "the exporter's 'if not exist ... mkdir' for an existing folder starts nothing")
        ok = execute('cmd.exe /C if not exist "' .. base .. '" mkdir "' .. base .. '"')
        check(ok == true and #real_calls == 0, "the 'cmd.exe /C ...' form is answered the same way")
        local fresh = base .. [[\MatchStats\incoming\FC26_MatchStats_pmc_1.partial]]
        ok = execute('if not exist "' .. fresh .. '" mkdir "' .. fresh .. '"')
        check(ok == true and #real_calls == 1 and Q.windows == 1, "a folder that really is missing is created (one window without shellExecute)", #real_calls)
        ok = execute('if not exist "' .. fresh .. '" mkdir "' .. fresh .. '"')
        check(#real_calls == 1, "asking again for the same folder starts nothing")
        execute('move /Y "a" "b" >NUL')
        check(real_calls[#real_calls] == 'move /Y "a" "b" >NUL', "any other command is passed on unchanged")
    end)

    with_quiet(function(Q, execute, real_calls, dirs, files)
        local created = {}
        _G.shellExecute = function(program, params, folder, show)
            created[#created + 1] = { program = program, params = params, show = show }
            local made = params:match('mkdir "([^"]+)"')
            if made then dirs[(made:gsub("\\", "/"))] = true end
        end
        local fresh = [[C:\Users\Joel\Documents\FC26 Export\Snapshots\incoming\x.partial]]
        local ok = execute('if not exist "' .. fresh .. '" mkdir "' .. fresh .. '"')
        check(ok == true and #real_calls == 0 and #created == 1 and created[1].show == 0 and Q.windows == 0,
            "with shellExecute a missing folder is created hidden (no window)", #real_calls .. "/" .. #created)
    end)

    with_quiet(function(Q)
        local calls = {}
        _G.getDirectoryList = function(path) calls[#calls + 1] = path return { "FC26_MatchStats_pmc_20261016_100000_a", "FC26_MatchStats_pmc_20261017_090000_b", "." } end
        Q.dir_api = true
        local names = Q.list_dirs([[C:\x]], true)
        check(false == (names == nil), "directory listing through Live Editor's function works")
    end)

    -- Atlas lookup: the exporter reports the folder it just published
    local function atlas_env(files, listing, start_stamp)
        local calls = { listing = 0 }
        local env = assert(load([[
            local MATCH_STATS_ROOT, FILES, LISTING, STAMP, CALLS, WIN = ...
            local QUIET = { list_dirs = function() CALLS.listing = CALLS.listing + 1 return LISTING end }
            local function file_exists(p) return FILES[p] == true end
            local function os_date() return STAMP end
            local os = setmetatable({ date = os_date }, { __index = _G.os })
            ]] .. atlas_block .. [[
            return { find = find_atlas_folder, last = last_export_folder, used = _G["FC26_ATLAS_USED"] }
        ]], "atlas"))("C:/FC26 Export/MatchStats", files, listing, start_stamp, calls)
        return env, calls
    end
    local function ready_files(path)
        return { [path .. "/MATCH_READY"] = true, [path .. "/16_match_participants_wide.csv"] = true, [path .. "/18_match_teams_wide.csv"] = true }
    end
    _G["FC26_ATLAS_USED"] = nil
    local R = "C:/FC26 Export/MatchStats/ready"
    local files = ready_files(R .. "/FC26_MatchStats_pmc_20261019_140000_c")
    local env, calls = atlas_env(files, {}, "20261019_135000")
    rawset(_G, "FC26_EXPORT_MATCH_STATS_LAST_EXPORT_RESULT", nil)
    check(env.find(false, 0) == nil and calls.listing == 0, "before the exporter reported anything and before the wait: no lookup")
    rawset(_G, "FC26_EXPORT_MATCH_STATS_LAST_EXPORT_RESULT", { success = true, ready_dir = [[C:\FC26 Export\MatchStats\ready\FC26_MatchStats_pmc_20261019_140000_c]] })
    local name, path = env.find(false, 0)
    check(name == "FC26_MatchStats_pmc_20261019_140000_c" and path == R .. "/FC26_MatchStats_pmc_20261019_140000_c" and calls.listing == 0,
        "the folder the exporter reported is used with no folder lookup", tostring(name))
    env.used[name] = true
    check(env.find(false, 5) == nil and calls.listing == 0, "once used it is not offered again, and nothing is listed too early")
    check(env.find(true, 0) == name, "the manual 'sync latest' accepts it again")
    rawset(_G, "FC26_EXPORT_MATCH_STATS_LAST_EXPORT_RESULT", { success = false, ready_dir = "x" })
    check(env.last() == nil, "a failed export report is ignored")
    rawset(_G, "FC26_EXPORT_MATCH_STATS_LAST_EXPORT_RESULT", nil)

    -- the exporter reports nothing: look at the folder after a while, ignore folders older than this script, wait for a half-written one
    local files2 = ready_files(R .. "/FC26_MatchStats_pmc_20261019_141500_new")
    local listing = { "FC26_MatchStats_pmc_20261019_141500_new", "FC26_MatchStats_pmc_20261019_100000_old" }
    env, calls = atlas_env(files2, listing, "20261019_120000")
    _G["FC26_ATLAS_USED"] = env.used
    local n = env.find(false, 6)
    check(n == nil and calls.listing == 0, "the folder itself is only looked at after 12 s", calls.listing)
    n = env.find(false, 13)
    check(n == "FC26_MatchStats_pmc_20261019_141500_new" and calls.listing == 1, "then the newest finished export is found", tostring(n))
    env.used[n] = true
    n = env.find(false, 14)
    check(n == nil and calls.listing == 1, "listing is repeated at most every 8 s", calls.listing)
    check(env.used["FC26_MatchStats_pmc_20261019_100000_old"] == nil, "(old folder not touched yet)")
    local nowt = os.time
    os.time = function() return nowt() + 30 end
    n = env.find(false, 40)
    os.time = nowt
    check(n == nil and env.used["FC26_MatchStats_pmc_20261019_100000_old"] == true, "a folder made before the script started is treated as an earlier match's")

    local half = {} -- newest folder has no MATCH_READY yet
    env, calls = atlas_env(half, { "FC26_MatchStats_pmc_20261019_150000_half", "FC26_MatchStats_pmc_20261019_141500_new" }, "20261019_120000")
    local nm, _, names = env.find(false, 20)
    check(nm == nil and names and #names == 2, "a half-written newest export is waited for, an older one is not used instead")
    _G["FC26_ATLAS_USED"] = nil
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


-- ---------------------------------------------------------------------------------------------------------
print("4. transfer rules (pure functions) on data taken from a real save")
do
    local src = read_file(scripts .. "FC26_Auto_Capture.lua")
    local rules = src:match("%-%- BEGIN transfer_rules[^\n]*\n(.-)%-%- END transfer_rules")
    local manual = src:match("%-%- BEGIN manual_transfers[^\n]*\n(.-)%-%- END manual_transfers")
    check(rules ~= nil and manual ~= nil, "transfer_rules and manual_transfers blocks found between the markers")
    local chunk = assert(load(rules .. "\n" .. manual .. [[
        return { name_matches = name_matches, match_big_deal = match_big_deal, trusted_date = trusted_date,
                 classify_arrival = classify_arrival, describe_arrival = describe_arrival,
                 describe_departure = describe_departure, assign_big_deals = assign_big_deals,
                 parse_manual_transfers = parse_manual_transfers, parse_manual_fee = parse_manual_fee,
                 norm_name = norm_name } ]], "rules"))
    local R = chunk()

    -- names the game stores in career_managerhistory are surnames
    check(R.name_matches("Leo Guerra", "Guerra"), "surname matches the full name")
    check(R.name_matches("Pedro Lima", "lima"), "match ignores case")
    check(not R.name_matches("Pedro Limaverde", "Lima"), "a longer surname is not a match")
    check(R.name_matches("Arturo", "Arturo"), "a single-name player matches himself")
    check(R.name_matches("Gregorio Rodr\195\173guez", "Rodr\195\173guez"), "accented surname matches")
    check(R.match_big_deal("Lima", { { pid = 1, name = "Pedro Lima" }, { pid = 2, name = "Tyler Corbett" } }) == 1,
        "one candidate with that surname is found")
    check(R.match_big_deal("Lima", { { pid = 1, name = "Pedro Lima" }, { pid = 2, name = "Julio Lima" } }) == nil,
        "two players with the same surname are ambiguous: nothing is guessed")
    check(R.match_big_deal("", { { pid = 1, name = "Pedro Lima" } }) == nil, "an empty name matches nobody")

    check(R.trusted_date("2028-07-09", "2028-07-01", "2028-08-15") == "2028-07-09", "a date in this season is trusted")
    check(R.trusted_date("2027-07-09", "2028-07-01", "2028-08-15") == nil, "last season's date is not trusted")
    check(R.trusted_date("2028-09-01", "2028-07-01", "2028-08-15") == nil, "a date in the future is not trusted")
    check(R.trusted_date(nil, "2028-07-01", "2028-08-15") == nil, "no date is not trusted")

    local why = function(a) local t, w = R.classify_arrival(a) return t, w end
    check(why({ loan_parent = 69 }) == "In (Loan)", "a loanee is a loan in")
    check(why({ was_youth = true, signon = 100 }) == "Promoted In", "someone from the academy list is promoted")
    check(why({ is_big_buy = true }) == "In (Permanent)", "the season's biggest signing is a signing")
    check(why({ have_youth_list = true, signon = 0 }) == "In (Permanent)",
        "with an academy list to compare, a player who was not in it is a signing even without a bonus")
    check(why({ signon = 135000 }) == "In (Permanent)", "no academy list: a sign-on bonus means a signing")
    check(why({ signon = 0 }) == "Promoted In", "no academy list and no sign-on bonus: promoted (a guess)")
    check(select(2, why({ signon = 0 })):find("guess", 1, true) ~= nil, "the guess is labelled as a guess")

    -- the real July 2028 squad changes (ids, names, dates and contracts as exported by the snapshot)
    local TEAM = { [115486] = "Torquay AFC", [236] = "FC Porto", [1794] = "Sheffield Utd", [69] = "AS Monaco",
                   [1808] = "Brighton", [70] = "Montpellier", [1745] = "Como" }
    local NAME = { [463320] = "Leo Guerra", [462884] = "Oliver Ebner", [463808] = "Denilson Fernandes",
                   [460045] = "Jordan Anderson", [462221] = "Bruno Henrique Gonzaga", [461607] = "Fabi\195\161n Bravo",
                   [464205] = "Eliv\195\169lton Jesus", [73049] = "Pedro Lima", [460053] = "Ignacio Zapata",
                   [463994] = "Tawfiq Ahmed", [900001] = "Gone Player" }
    local DATES = { [1] = "2028-07-09", [2] = "2028-07-14", [3] = "2028-07-01", [4] = "2028-07-06", [5] = "2028-07-07",
                    [6] = "2026-07-01", [7] = "2028-07-08" }
    local function ctx_with(extra)
        local ctx = {
            managed_tid = 115486, today = "2028-08-09", season_start = "2028-07-01", season_year = 2028,
            loans_in = {}, loans_out = {}, all_links = {}, previous_teams = {}, youth_before = nil,
            contracts = {}, info = {},
            decode = function(raw) return DATES[raw] end,
            team_name = function(t) return TEAM[t] or ("Team " .. t) end,
            player_name = function(p) return NAME[p] or ("Player " .. p) end,
        }
        for k, v in pairs(extra or {}) do ctx[k] = v end
        return ctx
    end
    local contracts = {
        [463320] = { signon = 135000, date = "2028-07-09", changed = "2028-07-09" },
        [462884] = { signon = 155000, date = "2028-07-14", changed = "2028-07-14" },
        [463808] = { signon = 47000, date = "2028-07-01", changed = "2028-07-01" },
        [460045] = { signon = 0, date = "2028-07-01", changed = "2028-07-01" },
        [464205] = { signon = 0, date = "2028-07-01", changed = "2028-07-13" },
        [462221] = { signon = 0, date = "2028-07-17", changed = "2028-07-17" },
    }
    local info = { [463320] = { join_days = 1 }, [462884] = { join_days = 2 }, [463808] = { join_days = 3 },
                   [460045] = { join_days = 3 }, [73049] = { join_days = 4 }, [460053] = { join_days = 5 },
                   [463994] = { join_days = 7 } }

    local ctx = ctx_with({ contracts = contracts, info = info })
    R.assign_big_deals(ctx, { buy_name = "Guerra", buy_fee = 26.0, sell_name = "Lima", sell_fee = 20.0 },
        { 463320, 462884, 463808, 460045 }, { 73049, 460053, 463994 })

    local d = R.describe_arrival(463320, ctx)
    check(d.type == "In (Permanent)" and d.feeM == 26.0 and not d.feeUnknown, "Guerra: signing with the fee from the manager history",
        d.type .. " " .. tostring(d.feeM))
    check(d.date == "2028-07-09" and d.fromToClub == "Unknown club", "Guerra: signing day from the join date; previous club unknown")
    d = R.describe_arrival(462884, ctx)
    check(d.type == "In (Permanent)" and d.feeM == 0.0 and d.feeUnknown == true, "Ebner: signing, fee unknown (not guessed)")
    check(d.date == "2028-07-14", "Ebner: signing day from the join date")
    d = R.describe_arrival(463808, ctx)
    check(d.type == "In (Permanent)" and d.date == "2028-07-01", "Fernandes: signing, not a promotion")
    d = R.describe_arrival(460045, ctx)
    check(d.type == "Promoted In" and d.fromToClub == "Youth Academy" and d.feeM == 0.0 and not d.feeUnknown,
        "Anderson: promoted (no sign-on bonus), no fee")
    check(d.key == "V25_IN_460045_2028", "keys are stable for the season", d.key)

    ctx.loans_in = { [462221] = 69 }
    d = R.describe_arrival(462221, ctx)
    check(d.type == "In (Loan)" and d.fromToClub == "AS Monaco" and d.date == "2028-07-17" and not d.feeUnknown,
        "Gonzaga: loan in from Monaco on the day the loan began")
    check(d.key == "V25_LOANIN_462221_69_2028", "loan key names the parent club and season", d.key)

    ctx.youth_before = { [460045] = true, [460047] = true }
    check(R.describe_arrival(460045, ctx).type == "Promoted In", "with an academy list: in the list -> promoted")
    check(R.describe_arrival(462884, ctx).type == "In (Permanent)", "with an academy list: not in the list -> signing")
    check(R.describe_arrival(460045, ctx).why:find("academy", 1, true) ~= nil, "the reason mentions the academy list")

    ctx.previous_teams = { [462884] = 1808 }
    check(R.describe_arrival(462884, ctx).fromToClub == "Brighton", "a previous club known from the earlier squad is used")
    ctx.previous_teams = { [462884] = 115486 }
    check(R.describe_arrival(462884, ctx).fromToClub == "Unknown club", "our own club is never the previous club")

    -- departures
    ctx.all_links = { [73049] = 1808, [460053] = 70, [463994] = 1745, [464205] = 236 }
    d = R.describe_departure(73049, ctx)
    check(d.type == "Out (Permanent)" and d.fromToClub == "Brighton" and d.feeM == 20.0 and not d.feeUnknown,
        "Lima: sold to Brighton for the fee of the season's biggest sale")
    check(d.date == "2028-07-06", "Lima: sale day = join date at the buyer", d.date)
    d = R.describe_departure(460053, ctx)
    check(d.type == "Out (Permanent)" and d.fromToClub == "Montpellier" and d.feeUnknown and d.feeM == 0.0,
        "Zapata: sold to Montpellier, fee unknown (not the biggest sale)")
    d = R.describe_departure(900001, ctx)
    check(d.fromToClub == "Free Agent / Released" and not d.feeUnknown and d.feeM == 0.0,
        "a player linked to no club is a release with no fee")
    ctx.loans_out = { [464205] = 236, [461483] = 0 }
    d = R.describe_departure(464205, ctx)
    check(d.type == "Out (Loan)" and d.fromToClub == "FC Porto" and d.date == "2028-07-13" and d.feeM == 0.0 and not d.feeUnknown,
        "Jesus: loan out to FC Porto on the day the loan began")
    d = R.describe_departure(461483, ctx)
    check(d.type == "Out (Loan)" and d.fromToClub == "Unknown club", "unreadable loan club is 'Unknown club', never a release")
    -- a loan that began in an earlier season keeps its real start day
    ctx.contracts[461483] = { signon = 0, date = "2026-07-01", changed = "2027-08-05" }
    check(R.describe_departure(461483, ctx).date == "2027-08-05", "an older loan keeps its real start day")
    ctx.contracts[461483] = { signon = 0, date = "2026-07-01", changed = "2030-01-01" }
    check(R.describe_departure(461483, ctx).date == "2028-08-09", "a loan start in the future is not trusted")
    -- a join date outside the season is not used as the sale day
    DATES[4] = "2027-01-01"
    check(R.describe_departure(73049, ctx).date == "2028-08-09", "an untrusted join date falls back to today")

    -- manual_transfers.csv
    local rows, bad = R.parse_manual_transfers(table.concat({
        "\239\187\191date,player,type,fee_m,club",
        "# my July deals",
        "2028-07-06,Pedro Lima,Out (Permanent),20.0,Brighton",
        "2028-07-13,Joe Crook,out,280K,Metaloglobus",
        "2028-07-01,Denilson Fernandes,in,free,Free Agents",
        "2028-07-09,Leo Guerra,In (Permanent)," .. "\226\130\172" .. "26.0M,Roma",
        "2028-07-10,Eusebio Salinas,Out (Permanent),,Inter Miami",
        "2028-07-17,Bruno Henrique Gonzaga,loan in,0,AS Monaco",
        "2028-07-18,Giacomo De Pieri,Loan Out,,Fulham",
        "2028-07-01,Jordan Anderson,promoted,,",
        "not a deal at all",
        "2028-13-45,Nobody,sideways,1,X",
    }, "\r\n"))
    check(#rows == 8, "eight good lines are read", #rows)
    check(#bad == 2, "two bad lines are reported", #bad)
    local by = {}
    for _, r in ipairs(rows) do by[r.player] = r end
    check(by["Pedro Lima"].type == "Out (Permanent)" and by["Pedro Lima"].feeM == 20.0 and not by["Pedro Lima"].feeUnknown,
        "sale with a fee")
    check(by["Joe Crook"].type == "Out (Permanent)" and by["Joe Crook"].feeM == 0.28, "280K = 0.28M", tostring(by["Joe Crook"].feeM))
    check(by["Denilson Fernandes"].type == "In (Permanent)" and by["Denilson Fernandes"].feeM == 0.0
        and not by["Denilson Fernandes"].feeUnknown, "'free' is a known zero fee")
    check(by["Leo Guerra"].feeM == 26.0, "a euro sign and M suffix are accepted", tostring(by["Leo Guerra"].feeM))
    check(by["Eusebio Salinas"].feeUnknown == true, "an empty fee is unknown")
    check(by["Bruno Henrique Gonzaga"].type == "In (Loan)" and not by["Bruno Henrique Gonzaga"].feeUnknown, "loan in")
    check(by["Giacomo De Pieri"].type == "Out (Loan)" and by["Giacomo De Pieri"].club == "Fulham", "loan out with its club")
    check(by["Jordan Anderson"].type == "Promoted In" and by["Jordan Anderson"].club == "Youth Academy", "promotion defaults to the academy")
end

-- ---------------------------------------------------------------------------------------------------------
print("5. whole capture script against a pretend copy of the real July 2028 save")

local function run_capture_session(spec)
    -- spec: db (tables), vfs (shared pretend disk), clock, names/teams, keep_handler (true: return the event handler)
    local vfs = spec.vfs
    local home = "/home/test"
    local real = { open = io.open, popen = io.popen, execute = os.execute, getenv = os.getenv, time = os.time,
                   remove = os.remove, dofile = dofile, print = print, rename = os.rename }

    local function reader(content)
        local pos, obj = 1, {}
        function obj:read(fmt)
            fmt = tostring(fmt or "l"):gsub("^%*", "")
            if fmt == "a" then local rest = content:sub(pos) pos = #content + 1 return rest end
            return nil
        end
        function obj:lines()
            return function()
                if pos > #content then return nil end
                local nl = content:find("\n", pos, true)
                local line
                if nl then line = content:sub(pos, nl - 1) pos = nl + 1 else line = content:sub(pos) pos = #content + 1 end
                return (line:gsub("\r$", ""))
            end
        end
        function obj:seek(whence) if whence == "end" then return #content end return pos - 1 end
        function obj:close() end
        return obj
    end
    -- A folder "exists" when the test created it (spec.dirs, filled by the mkdir commands below) or holds a file.
    local dirs = spec.dirs or {}
    spec.dirs = dirs
    local function mark_dir(path)
        path = path:gsub("\\", "/")
        while path ~= "" and path ~= "/" do
            dirs[path] = true
            path = path:match("^(.*)/[^/]*$") or ""
        end
    end
    spec.mark_dir = mark_dir
    local function dir_known(dir)
        if dirs[dir] then return true end
        local prefix = dir .. "/"
        for p in pairs(vfs) do if p:sub(1, #prefix) == prefix then return true end end
        return false
    end
    io.open = function(path, mode)
        mode = mode or "r"
        local m = mode:sub(1, 1)
        if path:sub(-12) == "/.fc26_probe" and not dir_known(path:sub(1, -13)) then return nil end -- no such folder
        if m == "r" then
            if vfs[path] == nil then return nil end
            return reader(vfs[path])
        end
        if m == "w" then vfs[path] = "" end
        if m == "a" and vfs[path] == nil then vfs[path] = "" end
        local w = {}
        function w:write(...) for _, part in ipairs({ ... }) do vfs[path] = vfs[path] .. tostring(part) end return w end
        function w:seek() return #vfs[path] end
        function w:close() end
        return w
    end
    io.popen = function() return { read = function() return "" end, close = function() end } end
    os.execute = function(cmd)
        if spec.executed then spec.executed[#spec.executed + 1] = cmd end -- what would have opened a command window
        local made = cmd:match('mkdir "([^"]+)"')
        if made then mark_dir(made) end
        return true
    end
    os.rename = function(a, b)
        if a ~= b and vfs[a] ~= nil then vfs[b] = vfs[a] vfs[a] = nil return true end
        if a == b and vfs[a] ~= nil then return true end
        return nil, "No such file or directory", 2
    end
    rawset(_G, "FC26_REAL_OS_EXECUTE", nil)
    _G.shellExecute = spec.shell
    os.remove = function(path) vfs[path] = nil return true end
    os.time = function() return spec.clock() end
    os.getenv = function(k)
        if k == "USERPROFILE" then return home end
        if k == "TEMP" or k == "TMP" then return home .. "/tmp" end
        return nil
    end
    _G.dofile = function(path)
        local code = vfs[path]
        if code == nil then error("cannot open " .. tostring(path)) end
        return assert(load(code, "@" .. path))()
    end

    local function mock_table(rows)
        local t = { rows = rows, i = 0 }
        function t:GetFirstRecord() self.i = 1 return #self.rows > 0 and 1 or 0 end
        function t:GetNextValidRecord() self.i = self.i + 1 return self.i <= #self.rows and self.i or 0 end
        function t:GetRecordFieldValue(rec, field) local row = self.rows[rec] return row and row[field] end
        return t
    end
    package.loaded["imports/core/memory"] = nil
    package.preload["imports/core/memory"] = function() return spec.memory or {} end
    package.preload["imports/other/helpers"] = function() return {} end
    package.preload["imports/services/enums"] = function() return {} end
    package.preload["imports/career_mode/helpers"] = function() return {} end
    _G.IsInCM = function() return true end
    _G.LE = { db = { GetTable = function(_, name) if spec.db[name] then return mock_table(spec.db[name]) end return nil end } }
    _G.GetDBTableRows = function(name)
        local rows = spec.db[name]
        if not rows then return nil end
        local out = {}
        for _, row in ipairs(rows) do
            local r = {}
            for k, v in pairs(row) do r[k] = { value = v } end
            out[#out + 1] = r
        end
        return out
    end
    _G.GetPlayerName = function(pid) return spec.names[pid] end
    _G.GetTeamName = function(tid) return spec.teams[tid] end
    _G.GetUserTeamID = function() return 115486 end
    _G.GetCompetitionNameByObjID = function(id) return spec.comps and spec.comps[id] or nil end
    _G.GetPlayerPrimaryPositionName = function(id) return spec.positions and spec.positions[id] or nil end
    _G.GetCurrentDate = function() return spec.today end
    _G.createTimer = nil
    local handler = nil
    _G.AddEventHandler = function(_, fn) handler = fn end

    local real_random = math.random
    if spec.constant_random then math.random = function() return 1234 end end
    local quiet = function() end
    _G.print = quiet
    local ok, err = pcall(real.dofile, scripts .. "FC26_Auto_Capture.lua")
    math.random = real_random
    local function restore()
        io.open, io.popen, os.execute, os.getenv, os.time, os.remove = real.open, real.popen, real.execute, real.getenv,
            real.time, real.remove
        os.rename = real.rename
        rawset(_G, "FC26_REAL_OS_EXECUTE", nil)
        _G.shellExecute = nil
        _G.dofile, _G.print = real.dofile, real.print
        _G.AddEventHandler = nil
        _G.GetCompetitionNameByObjID, _G.GetPlayerPrimaryPositionName = nil, nil
    end
    return ok, err, handler, restore
end

local function transfer_events(vfs, seen)
    -- every payload the script wrote to the outbox that is a transfer, in write order of its file name
    local names = {}
    for path in pairs(vfs) do
        if path:find("/outbox/", 1, true) and path:sub(-5) == ".json" then names[#names + 1] = path end
    end
    table.sort(names)
    local list = {}
    for _, path in ipairs(names) do
        if not seen[path] then
            seen[path] = true
            local t = {}
            for k, v in vfs[path]:gmatch('"([%a_]+)":%s*([^\n]+)') do
                v = v:gsub(",%s*$", "")
                if v:sub(1, 1) == '"' then v = v:sub(2, -2) end
                t[k] = v
            end
            if t.dataType == "transfer" then list[#list + 1] = t end
        end
    end
    return list
end

local function by_player(events)
    local map, count = {}, 0
    for _, e in ipairs(events) do
        map[e.playerName] = map[e.playerName] or {}
        table.insert(map[e.playerName], e)
        count = count + 1
    end
    return map, count
end

do
    local MANAGED = 115486
    local TEAMS = { [MANAGED] = "Torquay AFC", [236] = "FC Porto", [1794] = "Sheffield Utd", [69] = "AS Monaco",
                    [1808] = "Brighton", [70] = "Montpellier", [112264] = "Torquay Youth" }
    local NAMES = { [463320] = "Leo Guerra", [462884] = "Oliver Ebner", [463808] = "Denilson Fernandes",
                    [460045] = "Jordan Anderson", [460055] = "Kento Takeuchi", [460129] = "Marc Palomino",
                    [462221] = "Bruno Henrique Gonzaga", [461607] = "Fabi\195\161n Bravo", [73049] = "Pedro Lima",
                    [460053] = "Ignacio Zapata", [464205] = "Elivélton Jesus", [461483] = "Enrique Iglesias",
                    [460047] = "Harry Dalton", [470001] = "Nico Striker" }

    local function build_db()
        return {
            career_users = { { clubteamid = MANAGED } },
            leagueteamlinks = { { teamid = MANAGED }, { teamid = 236 }, { teamid = 1794 }, { teamid = 69 },
                                { teamid = 1808 }, { teamid = 70 } },
            teamplayerlinks = {
                { teamid = MANAGED, playerid = 463320 }, { teamid = MANAGED, playerid = 462884 },
                { teamid = MANAGED, playerid = 463808 }, { teamid = MANAGED, playerid = 460045 },
                { teamid = MANAGED, playerid = 460055 }, { teamid = MANAGED, playerid = 460129 },
                { teamid = MANAGED, playerid = 462221 }, { teamid = MANAGED, playerid = 461607 },
                { teamid = MANAGED, playerid = 73049 }, { teamid = MANAGED, playerid = 460053 },
                { teamid = 236, playerid = 464205 }, { teamid = 1794, playerid = 461483 },
                { teamid = 112264, playerid = 460047 }, { teamid = 1808, playerid = 470001 },
            },
            playerloans = {
                { playerid = 464205, teamidloanedfrom = MANAGED }, { playerid = 461483, teamidloanedfrom = MANAGED },
                { playerid = 462221, teamidloanedfrom = 69 }, { playerid = 461607, teamidloanedfrom = 69 },
            },
            players = {
                { playerid = 463320, playerjointeamdate = 162802 },      -- EA day count of 2028-07-09
                { playerid = 462884, playerjointeamdate = 20280714 },
                { playerid = 463808, playerjointeamdate = 20280701 },
                { playerid = 460045, playerjointeamdate = 20280701 },
                { playerid = 460055, playerjointeamdate = 20250701 },
                { playerid = 460129, playerjointeamdate = 20260701 },
                { playerid = 462221, playerjointeamdate = 20280717 },
                { playerid = 461607, playerjointeamdate = 20280718 },
                { playerid = 73049, playerjointeamdate = 20270101 },
                { playerid = 460053, playerjointeamdate = 20260701 },
                { playerid = 464205, playerjointeamdate = 20280701 },
                { playerid = 461483, playerjointeamdate = 20270701 },
                { playerid = 460047, playerjointeamdate = 20260701 },
                { playerid = 470001, playerjointeamdate = 20270701 },
            },
            career_playercontract = {
                { playerid = 463320, teamid = MANAGED, contract_date = 20280709, last_status_change_date = 20280709, signon_bonus = 135000, wage = 15000, contract_status = 0, loan_wage_split = -1 },
                { playerid = 462884, teamid = MANAGED, contract_date = 20280714, last_status_change_date = 20280714, signon_bonus = 155000, wage = 17000, contract_status = 0, loan_wage_split = -1 },
                { playerid = 463808, teamid = MANAGED, contract_date = 20280701, last_status_change_date = 20280701, signon_bonus = 47000, wage = 5200, contract_status = 0, loan_wage_split = -1 },
                { playerid = 460045, teamid = MANAGED, contract_date = 20280701, last_status_change_date = 20280701, signon_bonus = 0, wage = 5200, contract_status = 0, loan_wage_split = -1 },
                { playerid = 460055, teamid = MANAGED, contract_date = 20280722, last_status_change_date = 20280722, signon_bonus = 29500, wage = 3400, contract_status = 0, loan_wage_split = -1 },
                { playerid = 464205, teamid = MANAGED, contract_date = 20280701, last_status_change_date = 20280713, signon_bonus = 0, wage = 10000, contract_status = 4, loan_wage_split = 50 },
                { playerid = 461483, teamid = MANAGED, contract_date = 20270701, last_status_change_date = 20280721, signon_bonus = 23500, wage = 2600, contract_status = 4, loan_wage_split = 100 },
                { playerid = 462221, teamid = MANAGED, contract_date = 20280717, last_status_change_date = 20280717, signon_bonus = 0, wage = 33500, contract_status = 3, loan_wage_split = 60 },
                { playerid = 461607, teamid = MANAGED, contract_date = 20280718, last_status_change_date = 20280718, signon_bonus = 0, wage = 22000, contract_status = 3, loan_wage_split = 50 },
            },
            career_youthplayers = { { playerid = 460047 }, { playerid = 460050 }, { playerid = 460042 } },
            career_managerhistory = {
                { teamid = MANAGED, season = 3, bigbuyplayername = "Rodr\195\173guez", bigbuyamount = 4000000, bigsellplayername = "Haigh", bigsellamount = 3400000 },
                { teamid = MANAGED, season = 4, bigbuyplayername = "Guerra", bigbuyamount = 26000000, bigsellplayername = "Lima", bigsellamount = 20000000 },
            },
        }
    end
    local function find_row(db, table_name, key, value)
        for _, row in ipairs(db[table_name]) do if row[key] == value then return row end end
    end

    local vfs, clock, seen = {}, 1792000000, {}
    local function session(db, today)
        clock = clock + 100
        return run_capture_session({ db = db, vfs = vfs, clock = function() return clock end, names = NAMES, teams = TEAMS,
                                     today = today or { year = 2028, month = 8, day = 9 } })
    end

    -- First run on the save from the snapshot: no saved squad, no academy list from an earlier session.
    local db = build_db()
    local ok, err, handler, restore = session(db)
    restore()
    check(ok, "script starts against the pretend save", err)
    local events = transfer_events(vfs, seen)
    local P, total = by_player(events)
    check(total == 8, "first run sends exactly the 8 transfers of the save", total)
    local function one(name) local list = P[name] return list and list[1] end
    local g = one("Leo Guerra")
    check(g and g.type == "In (Permanent)" and tonumber(g.feeM) == 26.0 and g.feeUnknown == nil,
        "Guerra is a signing for 26.0M (not 'Promoted In')", g and (g.type .. " " .. g.feeM))
    check(g and g.date == "2028-07-09", "Guerra: signing day from the EA day count", g and g.date)
    local e = one("Oliver Ebner")
    check(e and e.type == "In (Permanent)" and tonumber(e.feeM) == 0 and e.feeUnknown == "true",
        "Ebner is a signing with the fee marked unknown", e and (e.type .. " " .. tostring(e.feeUnknown)))
    local f = one("Denilson Fernandes")
    check(f and f.type == "In (Permanent)", "Fernandes is a signing (his sign-on bonus shows it)", f and f.type)
    local a = one("Jordan Anderson")
    check(a and a.type == "Promoted In" and a.fromToClub == "Youth Academy", "Anderson is promoted from the academy")
    local gon, bra = one("Bruno Henrique Gonzaga"), one("Fabi\195\161n Bravo")
    check(gon and gon.type == "In (Loan)" and gon.fromToClub == "AS Monaco" and gon.date == "2028-07-17", "Gonzaga: loan in from AS Monaco, real date")
    check(bra and bra.type == "In (Loan)" and bra.fromToClub == "AS Monaco" and bra.date == "2028-07-18", "Bravo: loan in from AS Monaco, real date")
    local jes, igl = one("Elivélton Jesus"), one("Enrique Iglesias")
    check(jes and jes.type == "Out (Loan)" and jes.fromToClub == "FC Porto" and jes.date == "2028-07-13", "Jesus: loan out to FC Porto on 13 July")
    check(igl and igl.type == "Out (Loan)" and igl.fromToClub == "Sheffield Utd" and igl.date == "2028-07-21", "Iglesias: loan out to Sheffield Utd on 21 July")
    check(P["Pedro Lima"] == nil and P["Kento Takeuchi"] == nil and P["Marc Palomino"] == nil,
        "players already in the squad (and a renewal) are not reported")

    local state_text = vfs["/home/test/Documents/FC26 Export/auto_capture_state.lua"] or ""
    local saved = assert(load(state_text))()
    check(saved.squad[73049] and saved.youth[460047] and saved.loansOut[464205] and not saved.squad[464205],
        "state file keeps the squad, the academy list and the loans out")

    -- A second session without any change sends nothing again.
    ok, err, handler, restore = session(db)
    restore()
    check(ok, "second session starts", err)
    check(#transfer_events(vfs, seen) == 0, "restarting the script repeats nothing")

    -- Pedro Lima (the season's biggest sale) and Ignacio Zapata are sold while the script was off.
    local lima = find_row(db, "teamplayerlinks", "playerid", 73049)
    lima.teamid = 1808
    find_row(db, "players", "playerid", 73049).playerjointeamdate = 20280706
    local zap = find_row(db, "teamplayerlinks", "playerid", 460053)
    zap.teamid = 70
    find_row(db, "players", "playerid", 460053).playerjointeamdate = 20280707
    ok, err, handler, restore = session(db)
    restore()
    check(ok, "session after the sales starts", err)
    events = transfer_events(vfs, seen)
    P, total = by_player(events)
    check(total == 2, "only the two new sales are sent", total)
    local li, za = one("Pedro Lima"), one("Ignacio Zapata")
    li, za = P["Pedro Lima"] and P["Pedro Lima"][1], P["Ignacio Zapata"] and P["Ignacio Zapata"][1]
    check(li and li.type == "Out (Permanent)" and li.fromToClub == "Brighton" and tonumber(li.feeM) == 20.0 and li.date == "2028-07-06",
        "Lima: sold to Brighton for 20.0M on 6 July", li and (li.type .. " " .. li.fromToClub .. " " .. li.feeM .. " " .. li.date))
    check(za and za.type == "Out (Permanent)" and za.fromToClub == "Montpellier" and za.feeUnknown == "true" and za.date == "2028-07-07",
        "Zapata: sold to Montpellier on 7 July, fee unknown", za and (za.type .. " " .. za.fromToClub))

    -- Live detection while the script runs: academy promotion, a signing, a loan out and a loan returning.
    ok, err, handler, restore = session(db, { year = 2028, month = 8, day = 15 })
    check(ok and type(handler) == "function", "event handler is registered", err)
    local before = #transfer_events(vfs, seen)
    -- Harry Dalton leaves the academy list for the first team
    for i, row in ipairs(db.career_youthplayers) do if row.playerid == 460047 then table.remove(db.career_youthplayers, i) break end end
    find_row(db, "teamplayerlinks", "playerid", 460047).teamid = MANAGED
    find_row(db, "players", "playerid", 460047).playerjointeamdate = 20280815
    table.insert(db.career_playercontract, { playerid = 460047, teamid = MANAGED, contract_date = 20280815, last_status_change_date = 20280815, signon_bonus = 0, wage = 900, contract_status = 0, loan_wage_split = -1 })
    -- Nico Striker is signed from Brighton
    find_row(db, "teamplayerlinks", "playerid", 470001).teamid = MANAGED
    find_row(db, "players", "playerid", 470001).playerjointeamdate = 20280815
    table.insert(db.career_playercontract, { playerid = 470001, teamid = MANAGED, contract_date = 20280815, last_status_change_date = 20280815, signon_bonus = 50000, wage = 8000, contract_status = 0, loan_wage_split = -1 })
    -- Marc Palomino is loaned to FC Porto
    find_row(db, "teamplayerlinks", "playerid", 460129).teamid = 236
    table.insert(db.playerloans, { playerid = 460129, teamidloanedfrom = MANAGED })
    table.insert(db.career_playercontract, { playerid = 460129, teamid = MANAGED, contract_date = 20260701, last_status_change_date = 20280810, signon_bonus = 0, wage = 6200, contract_status = 4, loan_wage_split = 50 })
    -- Elivélton Jesus comes back from his loan
    for i, row in ipairs(db.playerloans) do if row.playerid == 464205 then table.remove(db.playerloans, i) break end end
    find_row(db, "teamplayerlinks", "playerid", 464205).teamid = MANAGED

    clock = clock + 20
    _G.print = function() end
    handler(nil, 1, nil)
    _G.print = nil
    restore()
    events = transfer_events(vfs, seen)
    P, total = by_player(events)
    check(total == 3, "live scan sends the promotion, the signing and the loan out (3), not the loan return", total)
    local dal, str, pal = P["Harry Dalton"] and P["Harry Dalton"][1], P["Nico Striker"] and P["Nico Striker"][1], P["Marc Palomino"] and P["Marc Palomino"][1]
    check(dal and dal.type == "Promoted In", "Dalton (left the academy list): promoted", dal and dal.type)
    check(str and str.type == "In (Permanent)" and str.fromToClub == "Brighton" and str.feeUnknown == "true" and str.date == "2028-08-15",
        "Striker: signing from Brighton (previous club known), fee unknown", str and (str.type .. " " .. str.fromToClub))
    check(pal and pal.type == "Out (Loan)" and pal.fromToClub == "FC Porto" and pal.date == "2028-08-10", "Palomino: loan out to FC Porto", pal and pal.type)
    check(P["Elivélton Jesus"] == nil, "a player coming back from a loan is not reported as a transfer")
    local log = vfs["/home/test/Documents/FC26 Export/auto_capture_monitor.log"] or ""
    check(log:find("is back from a loan", 1, true) ~= nil, "the loan return is mentioned in the monitor log")
    check(log:find("Transfer rule: Harry Dalton -> Promoted In (was in the youth academy list)", 1, true) ~= nil,
        "the monitor log says why each transfer was classified")
end

do
    -- A player-plus-cash deal: Brighton take Pedro Lima and we sign Cole Swap from them on the same day. The save has no
    -- record that the two moves belong together, so each one is recorded as its own deal with the right club and day.
    local MANAGED = 115486
    local TEAMS = { [MANAGED] = "Torquay AFC", [1808] = "Brighton" }
    local NAMES = { [73049] = "Pedro Lima", [460129] = "Marc Palomino", [470002] = "Cole Swap" }
    local db = {
        career_users = { { clubteamid = MANAGED } },
        leagueteamlinks = { { teamid = MANAGED }, { teamid = 1808 } },
        teamplayerlinks = { { teamid = MANAGED, playerid = 73049 }, { teamid = MANAGED, playerid = 460129 }, { teamid = 1808, playerid = 470002 } },
        playerloans = {},
        players = { { playerid = 73049, playerjointeamdate = 20270101 }, { playerid = 460129, playerjointeamdate = 20260701 },
                    { playerid = 470002, playerjointeamdate = 20270701 } },
        career_playercontract = {
            { playerid = 73049, teamid = MANAGED, contract_date = 20270101, last_status_change_date = 20270101, signon_bonus = 90000, wage = 9000, contract_status = 0, loan_wage_split = -1 },
            { playerid = 460129, teamid = MANAGED, contract_date = 20260701, last_status_change_date = 20260701, signon_bonus = 0, wage = 6200, contract_status = 0, loan_wage_split = -1 },
        },
        career_youthplayers = {},
        career_managerhistory = { { teamid = MANAGED, season = 4, bigbuyplayername = "", bigbuyamount = 0, bigsellplayername = "", bigsellamount = 0 } },
    }
    local vfs, clock, seen = {}, 1794000000, {}
    local function session(today)
        clock = clock + 100
        return run_capture_session({ db = db, vfs = vfs, clock = function() return clock end, names = NAMES, teams = TEAMS, today = today })
    end
    local ok, err, _, restore = session({ year = 2028, month = 8, day = 9 })
    restore()
    check(ok, "exchange scenario: first session starts", err)
    transfer_events(vfs, seen)

    for _, row in ipairs(db.teamplayerlinks) do
        if row.playerid == 73049 then row.teamid = 1808 elseif row.playerid == 470002 then row.teamid = MANAGED end
    end
    for _, row in ipairs(db.players) do
        if row.playerid == 73049 or row.playerid == 470002 then row.playerjointeamdate = 20280820 end
    end
    table.insert(db.career_playercontract, { playerid = 470002, teamid = MANAGED, contract_date = 20280820, last_status_change_date = 20280820, signon_bonus = 60000, wage = 7000, contract_status = 0, loan_wage_split = -1 })
    ok, err, _, restore = session({ year = 2028, month = 8, day = 21 })
    restore()
    check(ok, "exchange scenario: session after the deal starts", err)
    local P, total = by_player(transfer_events(vfs, seen))
    check(total == 2, "both halves of the exchange are recorded (the player who arrived and the player who left)", total)
    local arrived, left = P["Cole Swap"] and P["Cole Swap"][1], P["Pedro Lima"] and P["Pedro Lima"][1]
    check(arrived and arrived.type == "In (Permanent)" and arrived.fromToClub == "Brighton" and arrived.date == "2028-08-20",
        "the arrival is a signing from Brighton on the day of the deal", arrived and (arrived.type .. " " .. arrived.fromToClub .. " " .. arrived.date))
    check(left and left.type == "Out (Permanent)" and left.fromToClub == "Brighton" and left.date == "2028-08-20",
        "the player who went the other way is a sale to Brighton on the same day", left and (left.type .. " " .. left.fromToClub .. " " .. left.date))
    check(arrived and arrived.feeUnknown == "true" and left and left.feeUnknown == "true",
        "the cash part is not stored in the save, so both fees are marked unknown rather than guessed")
end

do
    -- Hand-written deals (manual_transfers.csv) replace what the script works out for the same player.
    local MANAGED = 115486
    local vfs, clock, seen = {}, 1793000000, {}
    local db = {
        career_users = { { clubteamid = MANAGED } },
        leagueteamlinks = { { teamid = MANAGED }, { teamid = 236 } },
        teamplayerlinks = { { teamid = MANAGED, playerid = 463320 }, { teamid = MANAGED, playerid = 460045 } },
        playerloans = {},
        players = { { playerid = 463320, playerjointeamdate = 20280709 }, { playerid = 460045, playerjointeamdate = 20280701 } },
        career_playercontract = {
            { playerid = 463320, teamid = MANAGED, contract_date = 20280709, last_status_change_date = 20280709, signon_bonus = 135000, wage = 15000, contract_status = 0, loan_wage_split = -1 },
            { playerid = 460045, teamid = MANAGED, contract_date = 20280701, last_status_change_date = 20280701, signon_bonus = 0, wage = 5200, contract_status = 0, loan_wage_split = -1 },
        },
        career_youthplayers = {},
        career_managerhistory = { { teamid = MANAGED, season = 4, bigbuyplayername = "Guerra", bigbuyamount = 26000000, bigsellplayername = "Lima", bigsellamount = 20000000 } },
    }
    vfs["/home/test/Documents/FC26 Export/manual_transfers.csv"] = table.concat({
        "date,player,type,fee_m,club",
        "2028-07-09,Leo Guerra,In (Permanent),26.0,Roma",
        "2028-07-06,Pedro Lima,Out (Permanent),20.0,Brighton",
        "2028-07-13,Joe Crook,Out (Permanent),280K,Metaloglobus",
        "this line is broken",
    }, "\n")
    local ok, err, _, restore = run_capture_session({ db = db, vfs = vfs, clock = function() return clock end,
        names = { [463320] = "Leo Guerra", [460045] = "Jordan Anderson" }, teams = { [MANAGED] = "Torquay AFC" },
        today = { year = 2028, month = 8, day = 9 }, constant_random = true })
    restore()
    check(ok, "script starts with a manual_transfers.csv", err)
    local P, total = by_player(transfer_events(vfs, seen))
    check(total == 4, "three manual deals + Anderson's promotion are sent (Guerra only once); payloads sent in the same second with the same random number do not overwrite each other", total)
    local g = P["Leo Guerra"] and P["Leo Guerra"][1]
    check(g and #P["Leo Guerra"] == 1 and g.fromToClub == "Roma" and tonumber(g.feeM) == 26.0,
        "the manual line for Guerra wins over the automatic one", g and g.fromToClub)
    check(P["Pedro Lima"] and P["Pedro Lima"][1].fromToClub == "Brighton" and P["Pedro Lima"][1].date == "2028-07-06", "manual sale is sent")
    check(P["Joe Crook"] and tonumber(P["Joe Crook"][1].feeM) == 0.28, "manual fee 280K is sent as 0.28M")
    check(P["Jordan Anderson"] and P["Jordan Anderson"][1].type == "Promoted In", "other players still come from the save")
    local log = vfs["/home/test/Documents/FC26 Export/auto_capture_monitor.log"] or ""
    check(log:find("line not understood", 1, true) ~= nil, "a broken line is reported in the monitor log")
end

-- ---------------------------------------------------------------------------------------------------------
print("6. payloads go out without command windows")
do
    local src = read_file(scripts .. "FC26_Auto_Capture.lua")
    local bat_block = src:match("%-%- BEGIN sender_bat[^\n]*\n(.-)%-%- END sender_bat")
    check(bat_block ~= nil, "sender_bat block found between the markers")
    local bat = assert(load(bat_block .. "\nreturn SENDER_BAT", "sender_bat"))()
    check(bat:find("dir /b /o:n", 1, true) and bat:find("for /f", 1, true), "the sender loops over every file in the outbox")
    check(bat:find("exit /b 1", 1, true) and bat:find("if errorlevel 1 goto :done", 1, true),
        "the sender stops after the first failure (bridge down) instead of trying every file")
    check(bat:find("%LOCK%", 1, true) and bat:find("del \"%LOCK%\"", 1, true), "the sender holds a lock while it runs")
    check(select(2, bat:gsub("@@OUTBOX@@", "")) == 1 and bat:find("@@JSON@@", 1, true) == nil, "one outbox placeholder, no per-file placeholder")

    local MANAGED = 115486
    local ROOT = "/home/test/Documents/FC26 Export"
    local function small_db()
        return {
            career_users = { { clubteamid = MANAGED } },
            leagueteamlinks = { { teamid = MANAGED }, { teamid = 236 } },
            teamplayerlinks = { { teamid = MANAGED, playerid = 463320 }, { teamid = MANAGED, playerid = 462884 },
                                { teamid = MANAGED, playerid = 460045 } },
            playerloans = {},
            players = { { playerid = 463320, playerjointeamdate = 20280709 }, { playerid = 462884, playerjointeamdate = 20280714 },
                        { playerid = 460045, playerjointeamdate = 20280701 } },
            career_playercontract = {
                { playerid = 463320, teamid = MANAGED, contract_date = 20280709, last_status_change_date = 20280709, signon_bonus = 135000, wage = 15000, contract_status = 0, loan_wage_split = -1 },
                { playerid = 462884, teamid = MANAGED, contract_date = 20280714, last_status_change_date = 20280714, signon_bonus = 155000, wage = 17000, contract_status = 0, loan_wage_split = -1 },
                { playerid = 460045, teamid = MANAGED, contract_date = 20280701, last_status_change_date = 20280701, signon_bonus = 0, wage = 5200, contract_status = 0, loan_wage_split = -1 },
            },
            career_youthplayers = {},
            career_managerhistory = {},
        }
    end
    local NAMES = { [463320] = "Leo Guerra", [462884] = "Oliver Ebner", [460045] = "Jordan Anderson" }
    local TEAMS = { [MANAGED] = "Torquay AFC", [236] = "FC Porto" }

    local function outbox_files(vfs)
        local list = {}
        for path in pairs(vfs) do
            if path:find("/outbox/", 1, true) and path:sub(-5) == ".json" then list[#list + 1] = path end
        end
        table.sort(list)
        return list
    end
    local function count(list, needle)
        local n = 0
        for _, c in ipairs(list) do if c:find(needle, 1, true) then n = n + 1 end end
        return n
    end
    local function existing_dirs()
        return { [ROOT] = true, [ROOT .. "/outbox"] = true, [ROOT .. "/Snapshots"] = true, [ROOT .. "/MatchStats"] = true,
                 ["/home/test/tmp"] = true }
    end
    local function start(spec_extra, vfs, clock_ref)
        local spec = { db = small_db(), vfs = vfs, clock = function() return clock_ref.t end, names = NAMES, teams = TEAMS,
                       today = { year = 2028, month = 8, day = 9 }, dirs = existing_dirs() }
        for k, v in pairs(spec_extra or {}) do spec[k] = v end
        return run_capture_session(spec)
    end
    local function tick(handler, clock_ref, seconds)
        clock_ref.t = clock_ref.t + seconds
        handler(nil, 1, nil)
    end

    -- first start ever on a PC: the folders are created (one command each); later starts create nothing
    do
        local vfs, executed, clk = {}, {}, { t = 1793900000 }
        local ok, err, _, restore = start({ executed = executed, dirs = {} }, vfs, clk)
        restore()
        check(ok, "first start on a new PC works", err)
        check(count(executed, "mkdir") >= 3 and count(executed, "mkdir") <= 5, "missing folders are created once", count(executed, "mkdir"))
        local again = {}
        ok, err, _, restore = start({ executed = again, dirs = existing_dirs() }, {}, { t = 1793900100 })
        restore()
        check(count(again, "mkdir") == 0, "folders that exist are not created again", table.concat(again, " | "))
    end

    -- a) the bridge does not read the folder itself (older bridge): ONE sender for the whole burst
    do
        local vfs, executed, clk = {}, {}, { t = 1794000000 }
        local ok, err, handler, restore = start({ executed = executed }, vfs, clk)
        check(ok and type(handler) == "function", "script starts (older bridge)", err)
        local files = outbox_files(vfs)
        check(#files == 3, "three payloads are waiting in the outbox", #files)
        local sorted_ok = true
        for i = 2, #files do if files[i] <= files[i - 1] then sorted_ok = false end end
        check(sorted_ok and files[1]:match("/(%d+_%d+)%.json$") ~= nil, "payload file names are unique and in order", files[1])
        for _, p in ipairs(files) do check(vfs[p]:sub(1, 1) == "{", "outbox file is a complete JSON object: " .. p:match("[^/]+$")) end
        for path in pairs(vfs) do check(path:sub(-4) ~= ".tmp", "no half-written .tmp file is left behind") end
        check(count(executed, "start /min") == 0, "nothing is started while the burst is still being collected")
        tick(handler, clk, 1)
        check(count(executed, "start /min") == 0, "still collecting after 1 s")
        tick(handler, clk, 2)
        check(count(executed, "start /min") == 1, "one sender starts for all three payloads", count(executed, "start /min"))
        local bat_text
        for path, content in pairs(vfs) do if path:find("fc26_send_", 1, true) and path:sub(-4) == ".bat" then bat_text = content end end
        check(bat_text and bat_text:find("\\outbox", 1, true) and not bat_text:find("@@", 1, true), "the sender batch file points at the outbox folder")
        tick(handler, clk, 10)
        check(count(executed, "start /min") == 1, "no second sender while the first one's files are still being retried (30 s)")
        tick(handler, clk, 25)
        check(count(executed, "start /min") == 2, "undelivered files are retried after 30 s with one more sender", count(executed, "start /min"))
        -- the bridge answers: the sender deletes the files
        for _, p in ipairs(outbox_files(vfs)) do vfs[p] = nil end
        tick(handler, clk, 40)
        tick(handler, clk, 40)
        check(count(executed, "start /min") == 2, "nothing more is started once everything is delivered")
        restore()
    end

    -- b) the bridge reads the folder itself: no program at all unless a file stays unread
    do
        local vfs, executed, clk = {}, {}, { t = 1794100000 }
        vfs[ROOT .. "/bridge_watching.txt"] = tostring(clk.t)
        local ok, err, handler, restore = start({ executed = executed }, vfs, clk)
        check(ok, "script starts (bridge reads the folder)", err)
        check(#outbox_files(vfs) == 3, "payloads are saved in the outbox")
        check(#executed == 0, "no program is started at all at startup (no folder listing, no mkdir, no sender)", table.concat(executed, " | "))
        vfs[ROOT .. "/bridge_watching.txt"] = tostring(clk.t + 4)
        tick(handler, clk, 5)
        vfs[ROOT .. "/bridge_watching.txt"] = tostring(clk.t)
        tick(handler, clk, 5)
        check(count(executed, "start /min") == 0, "the bridge gets time to read the files first")
        for _, p in ipairs(outbox_files(vfs)) do vfs[p] = nil end -- the bridge read and deleted them
        vfs[ROOT .. "/bridge_watching.txt"] = tostring(clk.t + 20)
        tick(handler, clk, 20)
        tick(handler, clk, 20)
        check(#executed == 0, "the bridge read everything: nothing was ever started", table.concat(executed, " | "))
        restore()
    end

    -- c) the bridge says it reads the folder but a file is still there after the grace period: the sender takes over
    do
        local vfs, executed, clk = {}, {}, { t = 1794200000 }
        vfs[ROOT .. "/bridge_watching.txt"] = tostring(clk.t)
        local ok, err, handler, restore = start({ executed = executed }, vfs, clk)
        check(ok, "script starts (bridge claims to read the folder)", err)
        vfs[ROOT .. "/bridge_watching.txt"] = tostring(clk.t + 15)
        tick(handler, clk, 15)
        check(count(executed, "start /min") == 0, "no sender within the grace period")
        vfs[ROOT .. "/bridge_watching.txt"] = tostring(clk.t + 10)
        tick(handler, clk, 10)
        check(count(executed, "start /min") == 1, "a file still waiting after 20 s is sent by the sender", count(executed, "start /min"))
        restore()
    end

    -- d) Live Editor can start programs hidden (shellExecute): no window, and a sender that leaves no trace is replaced
    do
        local vfs, executed, clk = {}, {}, { t = 1794300000 }
        local calls, sender_writes_log = {}, true
        local function shell(program, params, folder, show)
            calls[#calls + 1] = { program = program, params = params, folder = folder, show = show }
            local echo_path = params:match('echo ok> "([^"]+)"')
            if echo_path then vfs[(echo_path:gsub("\\", "/"))] = "ok" end
            local list_out = params:match('> "([^"]+)" 2>nul')
            if list_out then vfs[(list_out:gsub("\\", "/"))] = "" end -- an empty folder listing
            local done = params:match('echo x> "([^"]+)"')
            if done then vfs[(done:gsub("\\", "/"))] = "x" end
            if params:find("fc26_send_", 1, true) and sender_writes_log then
                vfs[ROOT .. "/bridge_send_log.txt"] = (vfs[ROOT .. "/bridge_send_log.txt"] or "") .. "x [sender] started\n"
            end
        end
        local ok, err, handler, restore = start({ executed = executed, shell = shell }, vfs, clk)
        check(ok, "script starts with shellExecute available", err)
        local log = vfs[ROOT .. "/auto_capture_monitor.log"] or ""
        check(log:find("hidden program start: yes", 1, true) ~= nil, "the log says hidden start works", log:match("Quiet mode[^\n]*"))
        tick(handler, clk, 3)
        local sender_calls = 0
        for _, c in ipairs(calls) do if c.params:find("fc26_send_", 1, true) then sender_calls = sender_calls + 1 check(c.show == 0, "the sender is started hidden (SW_HIDE = 0)", c.show) end end
        check(sender_calls == 1, "one hidden sender for the burst", sender_calls)
        check(count(executed, "start /min") == 0 and count(executed, "cmd /c") == 0, "no visible window was needed", table.concat(executed, " | "))
        tick(handler, clk, 20)
        log = vfs[ROOT .. "/auto_capture_monitor.log"] or ""
        check(log:find("left no trace", 1, true) == nil, "a hidden sender that wrote its log line is trusted")
        restore()

        vfs, executed, clk = {}, {}, { t = 1794400000 }
        calls, sender_writes_log = {}, false
        ok, err, handler, restore = start({ executed = executed, shell = shell }, vfs, clk)
        check(ok, "script starts (hidden sender that does nothing)", err)
        tick(handler, clk, 3)
        check(count(executed, "start /min") == 0, "first attempt is hidden")
        tick(handler, clk, 16)
        log = vfs[ROOT .. "/auto_capture_monitor.log"] or ""
        check(log:find("left no trace", 1, true) ~= nil, "a hidden sender that left no trace is noticed")
        tick(handler, clk, 20)
        check(count(executed, "start /min") == 1, "the visible sender takes over so nothing is left undelivered", count(executed, "start /min"))
        restore()
    end

    -- e) shellExecute exists but does nothing: it is not used at all
    do
        local vfs, executed, clk = {}, {}, { t = 1794500000 }
        local ok, err, handler, restore = start({ executed = executed, shell = function() end }, vfs, clk)
        check(ok, "script starts with a shellExecute that does nothing", err)
        local log = vfs[ROOT .. "/auto_capture_monitor.log"] or ""
        check(log:find("hidden program start: no", 1, true) ~= nil, "the log says hidden start does not work here")
        tick(handler, clk, 3)
        check(count(executed, "start /min") == 1, "the visible sender is used", count(executed, "start /min"))
        restore()
    end
end


-- ---------------------------------------------------------------------------------------------------------
-- A small JSON reader so the tests can look inside the payloads the script sends.
local JSON_NULL = setmetatable({}, { __tostring = function() return "null" end })
local function json_decode(text)
    local pos = 1
    local function skip() pos = text:find("%S", pos) or (#text + 1) end
    local value
    local function str()
        local out = {}
        pos = pos + 1
        while true do
            local c = text:sub(pos, pos)
            if c == "" then error("unterminated string") end
            if c == '"' then pos = pos + 1 break end
            if c == "\\" then
                local n = text:sub(pos + 1, pos + 1)
                local map = { n = "\n", t = "\t", r = "\r", ['"'] = '"', ["\\"] = "\\", ["/"] = "/", b = "\b", f = "\f" }
                if n == "u" then
                    out[#out + 1] = utf8.char(tonumber(text:sub(pos + 2, pos + 5), 16))
                    pos = pos + 6
                else
                    out[#out + 1] = map[n] or error("bad escape \\" .. n)
                    pos = pos + 2
                end
            else
                out[#out + 1] = c
                pos = pos + 1
            end
        end
        return table.concat(out)
    end
    function value()
        skip()
        local c = text:sub(pos, pos)
        if c == "{" then
            local obj = {}
            pos = pos + 1
            skip()
            if text:sub(pos, pos) == "}" then pos = pos + 1 return obj end
            while true do
                skip()
                local k = str()
                skip()
                assert(text:sub(pos, pos) == ":", "colon expected at " .. pos)
                pos = pos + 1
                obj[k] = value()
                skip()
                local d = text:sub(pos, pos)
                pos = pos + 1
                if d == "}" then return obj end
                assert(d == ",", "comma expected at " .. pos .. " near " .. text:sub(pos - 20, pos + 20))
            end
        elseif c == "[" then
            local arr = {}
            pos = pos + 1
            skip()
            if text:sub(pos, pos) == "]" then pos = pos + 1 return arr end
            while true do
                arr[#arr + 1] = value()
                skip()
                local d = text:sub(pos, pos)
                pos = pos + 1
                if d == "]" then return arr end
                assert(d == ",", "comma expected in array at " .. pos)
            end
        elseif c == '"' then
            return str()
        elseif text:sub(pos, pos + 3) == "true" then pos = pos + 4 return true
        elseif text:sub(pos, pos + 4) == "false" then pos = pos + 5 return false
        elseif text:sub(pos, pos + 3) == "null" then pos = pos + 4 return JSON_NULL
        end
        local num = text:match("^-?%d+%.?%d*[eE]?[+-]?%d*", pos)
        assert(num and num ~= "", "unexpected character at " .. pos .. ": " .. text:sub(pos, pos + 10))
        pos = pos + #num
        return tonumber(num)
    end
    local result = value()
    skip()
    assert(pos > #text, "extra text after the JSON value")
    return result
end

-- ---------------------------------------------------------------------------------------------------------
print("7. match log: substitutions, players the export missed, saves, man of the match, table position")
local match_env
do
    local src = read_file(scripts .. "FC26_Auto_Capture.lua")
    local function grab(name)
        local fn = src:match("(local function " .. name .. "%(.-\nend\n)")
        assert(fn, "cannot find " .. name)
        return fn
    end
    local logic = src:match("%-%- BEGIN match_logic[^\n]*\n(.-)%-%- END match_logic")
    local league = src:match("%-%- BEGIN league_table[^\n]*\n(.-)%-%- END league_table")
    check(logic ~= nil and league ~= nil, "match_logic and league_table blocks found between the markers")
    match_env = assert(load(
        grab("jstr") .. grab("to_int") .. grab("to_num") .. grab("percent_value") .. grab("truthy_flag") ..
        logic .. league .. [[
        return { pair = pair_substitutions, lineup_matches = lineup_matches_export, analyse = analyse_atlas_export,
                 apply = apply_lineup, saves = estimate_keeper_saves, perf = format_performance,
                 sub = format_substitution, table = build_league_table, comp = pick_league_comp } ]], "match_logic"))()
end
local M7 = match_env
do
    local src = read_file(scripts .. "FC26_Auto_Capture.lua")
    local fn = src:match("(local function is_league_competition%(.-\nend\n)")
    local is_league = assert(load(fn .. "\nreturn is_league_competition", "is_league"))()
    check(is_league("EFL Championship"), "the EFL Championship counts as a league (it used to be taken for the Champions League)")
    check(is_league("Premier League") and is_league("Serie A") and is_league("LALIGA EA SPORTS"), "other leagues count as leagues")
    check(not is_league("FA Cup") and not is_league("Carabao Cup") and not is_league("UEFA Champions League"), "cups and the Champions League do not")
    check(not is_league("UEFA Europa League") and not is_league("EFL Championship Play-offs"), "nor do the Europa League or the play-offs")
end

-- the real record of Torquay's last match (from the save snapshot): three substitutes, 92 minutes long
local function torquay_record()
    return {
        { id = 462221, minutes = 0,  pos = 3,  sub = false },   -- unused substitute
        { id = 461360, minutes = 47, pos = 11, sub = true },    -- Bradshaw
        { id = 460045, minutes = 47, pos = 18, sub = true },    -- Anderson
        { id = 78859,  minutes = 35, pos = 23, sub = true },    -- Arturo
        { id = 259627, minutes = 90, pos = 27, sub = false },
        { id = 460106, minutes = 90, pos = 25, sub = false },
        { id = 460055, minutes = 57, pos = 23, sub = false },   -- Takeuchi, replaced by Arturo
        { id = 275436, minutes = 45, pos = 18, sub = false },   -- Darvich, replaced by Anderson
        { id = 460023, minutes = 45, pos = 11, sub = false },   -- Akua, replaced by Bradshaw
        { id = 461436, minutes = 90, pos = 9,  sub = false },
        { id = 461438, minutes = 90, pos = 7,  sub = false },
        { id = 73616,  minutes = 90, pos = 6,  sub = false },
        { id = 462884, minutes = 90, pos = 4,  sub = false },
        { id = 460038, minutes = 90, pos = 3,  sub = false },
        { id = 460129, minutes = 90, pos = 0,  sub = false },
    }
end
do
    local pairs_out, marks, length = M7.pair(torquay_record())
    local text = {}
    for _, p in ipairs(pairs_out) do text[#text + 1] = p.minute .. ":" .. p.out.id .. ">" .. p["in"].id end
    check(#pairs_out == 3, "all three substitutions of the real match are found", table.concat(text, " "))
    check(table.concat(text, " ") == "45:275436>460045 45:460023>461360 57:460055>78859",
        "each substitute is matched to the player he replaced (same position, same minute)", table.concat(text, " "))
    check(length == 92, "the match length (stoppage time included) is worked out", length)
    check(marks[78859] and marks[78859].in_minute == 57, "the substitute came on when the other player went off")
    check(marks[460055] and marks[460055].out_minute == 57, "the replaced player left at that minute")
    check(marks[259627] == nil, "a player who stayed on all game has no minute marks")
    check(marks[462221] == nil, "an unused substitute has no minute marks")

    -- two substitutes at the same minute: the position tells them apart
    local two = {
        { id = 1, minutes = 90, pos = 0 },  { id = 2, minutes = 45, pos = 11 }, { id = 3, minutes = 45, pos = 18 },
        { id = 4, minutes = 90, pos = 3 },  { id = 5, minutes = 90, pos = 4 },  { id = 6, minutes = 90, pos = 6 },
        { id = 7, minutes = 90, pos = 7 },  { id = 8, minutes = 90, pos = 9 },  { id = 9, minutes = 90, pos = 23 },
        { id = 10, minutes = 90, pos = 25 }, { id = 11, minutes = 90, pos = 27 },
        { id = 12, minutes = 45, pos = 18, sub = true }, { id = 13, minutes = 45, pos = 11, sub = true },
    }
    local p2 = M7.pair(two)
    local t2 = {}
    for _, p in ipairs(p2) do t2[#t2 + 1] = p.out.id .. ">" .. p["in"].id end
    table.sort(t2)
    check(table.concat(t2, " ") == "2>13 3>12", "same minute, different positions: nobody is swapped", table.concat(t2, " "))

    -- a sending-off has no replacement; a substitute nobody can be matched to still gets a minute
    local red = {}
    for i = 1, 11 do red[#red + 1] = { id = i, minutes = (i == 5) and 60 or 90, pos = i } end
    red[#red + 1] = { id = 12, minutes = 20, pos = 99, sub = true }
    local p3, m3, len3 = M7.pair(red)
    check(#p3 == 0, "a sending-off is not turned into a substitution")
    check(m3[5] and m3[5].out_minute == 60, "the player who left still gets his minute")
    check(m3[12] and m3[12].in_minute == len3 - 20, "a substitute without a partner gets an estimated minute", m3[12] and m3[12].in_minute)

    local none = {}
    for i = 1, 11 do none[#none + 1] = { id = i, minutes = 90, pos = i } end
    local p4 = M7.pair(none)
    check(#p4 == 0, "a match without substitutions has none")

    -- the game's record is only trusted when it is about the same players as the export
    local ids = {}
    for _, h in ipairs(torquay_record()) do if h.minutes > 0 then ids[tostring(h.id)] = true end end
    check(M7.lineup_matches(torquay_record(), ids), "the record of the same match is accepted")
    check(not M7.lineup_matches(torquay_record(), { ["1"] = true, ["2"] = true, ["3"] = true, ["4"] = true, ["5"] = true,
        ["6"] = true, ["7"] = true, ["8"] = true, ["9"] = true, ["10"] = true, ["11"] = true }),
        "the record of another match is refused")
    check(M7.lineup_matches(torquay_record(), nil), "without an export the record is accepted when 11 or more players played")
    check(not M7.lineup_matches({ { id = 1, minutes = 90 } }, nil), "a record with fewer than 11 players is refused")
    local one_missing = {}
    for k, v in pairs(ids) do one_missing[k] = v end
    one_missing["460129"] = nil
    check(M7.lineup_matches(torquay_record(), one_missing), "one player missing from the export does not make the record unusable")
end

-- an export in which our club is team_index 1 and the team file lists index 1 first
local function export_rows()
    local function p(team, id, name, minutes, extra)
        local r = { team_index = tostring(team), player_id = tostring(id), player_name = name, minutes_played = tostring(minutes),
                    game_rating = "6.5", man_of_the_match = "0", passes = "20", pass_accuracy_pct_derived = "80", shots = "0",
                    shots_on_target = "0", tackles = "1", interceptions = "0", goals_derived = "0", assists = "0" }
        for k, v in pairs(extra or {}) do r[k] = v end
        return r
    end
    return {
        p(0, 900001, "Opp Keeper", 90, { game_rating = "5.9" }),
        p(0, 900002, "Opp Striker", 90, { game_rating = "9.1", man_of_the_match = "1", goals_derived = "3" }),
        p(0, 900003, "Opp Winger", 90),
        p(1, 460129, "Marc Palomino", 90, { saves = "4", penalty_saves = "1", shots_against = "9", game_rating = "6.2" }),
        p(1, 462884, "Oliver Ebner", 90),
        p(1, 463320, "", 90),                                                       -- the export has no name for him
        p(1, 461483, "Enrique Iglesias", 90, { goals_derived = "1", game_rating = "7.4", shots = "3", shots_on_target = "2" }),
        p(1, 460053, "Ignacio Zapata", 45),
        p(1, 462221, "Bruno Henrique Gonzaga", 47),
        p(1, 470001, "Nico Striker", 0),                                            -- on the bench all game
    }, {
        { team_index = "1", passes = "300", shots = "7", shots_on_target = "3", npk_expected_goals = "0.8",
          pass_accuracy_pct_derived = "77.5", shootout_goals = "255" },
        { team_index = "0", passes = "500", shots = "14", shots_on_target = "7", npk_expected_goals = "2.6",
          pass_accuracy_pct_derived = "88", shootout_goals = "255" },
    }
end
do
    local participants, teams = export_rows()
    local squad = { [460129] = true, [462884] = true, [463320] = true, [461483] = true, [460053] = true, [462221] = true, [470001] = true }
    local a = M7.analyse(participants, teams, { squad = squad, is_home = true, goals_against = 3,
        player_name = function(id) return "Name of " .. id end })
    check(a.managed_idx == 1, "our side is found from the squad, not from home/away", a.managed_idx)
    check(#a.rows == 6, "players who never came on are left out", #a.rows)
    check(a.shots_for == 7 and a.shots_against == 14, "team shots come from our own row of the team file", a.shots_for .. "-" .. a.shots_against)
    check(a.passing_accuracy == 78 or a.passing_accuracy == 77, "pass accuracy comes from our own row", a.passing_accuracy)
    check(a.possession == 38 and a.possession_estimated, "possession is the pass share of our row, marked as an estimate", a.possession)
    check(a.motm == "Opp Striker" and a.motm_is_opponent, "an opposing man of the match is named and flagged as the opponent's", a.motm)
    local flagged = 0
    for _, r in ipairs(a.rows) do if r.motm then flagged = flagged + 1 end end
    check(flagged == 0, "none of our players is marked man of the match then", flagged)
    local keeper
    for _, r in ipairs(a.rows) do if r.name == "Marc Palomino" then keeper = r end end
    check(keeper and keeper.saves == 4 and keeper.penalty_saves == 1, "the goalkeeper's saves are read", keeper and keeper.saves)
    check(#a.opp_scorers == 1 and a.opp_scorers[1].name == "Opp Striker" and a.opp_scorers[1].goals == 3,
        "the other side's scorers are named from the export", a.opp_scorers[1] and a.opp_scorers[1].name)
    local blank
    for _, r in ipairs(a.rows) do if r.id == "463320" then blank = r end end
    check(blank and blank.name == "Name of 463320", "a player the export has no name for is named from the game", blank and blank.name)

    -- our own man of the match, and a real possession column
    participants[2].man_of_the_match = "0"
    participants[7].man_of_the_match = "1"
    teams[1].possession = "61.5"
    local b = M7.analyse(participants, teams, { squad = squad, is_home = true, goals_against = 3 })
    check(b.motm == "Enrique Iglesias" and not b.motm_is_opponent, "our own man of the match is not called an opponent")
    check(b.possession == 61 or b.possession == 62, "a possession figure in the export is used as it is", b.possession)
    check(not b.possession_estimated, "and is not marked as an estimate")
    local ours = 0
    for _, r in ipairs(b.rows) do if r.motm then ours = ours + 1 end end
    check(ours == 1, "exactly one of our players carries the man of the match star", ours)

    -- the game's record adds the loaned-in player the export left out, and the substitutions
    local history = {
        { id = 460129, minutes = 90, pos = 0 }, { id = 462884, minutes = 90, pos = 3 }, { id = 463320, minutes = 90, pos = 4 },
        { id = 461607, minutes = 90, pos = 6 },                                            -- loaned in, not in the export
        { id = 463808, minutes = 90, pos = 7 }, { id = 73049, minutes = 90, pos = 9 }, { id = 460053, minutes = 45, pos = 11 },
        { id = 460045, minutes = 57, pos = 18 }, { id = 460055, minutes = 90, pos = 23 }, { id = 464205, minutes = 90, pos = 27 },
        { id = 461483, minutes = 90, pos = 25 },
        { id = 462221, minutes = 47, pos = 11, sub = true }, { id = 460047, minutes = 35, pos = 18, sub = true },
        { id = 470001, minutes = 0, pos = 25 },
    }
    local c = M7.analyse(participants, teams, { squad = squad, is_home = true, goals_against = 3,
        player_name = function(id) return "Name of " .. id end })
    c.goals_against = 3
    local subs = M7.apply(c, history, {
        player_name = function(id) return ({ [461607] = "Fabi\195\161n Bravo", [463808] = "Denilson Fernandes",
            [73049] = "Pedro Lima", [460045] = "Jordan Anderson", [460055] = "Kento Takeuchi", [464205] = "Elivélton Jesus",
            [460047] = "Harry Dalton" })[id] or ("Name of " .. id) end,
        rating_for = function(id) return id == 461607 and 7.0 or 0 end })
    local names = {}
    for _, r in ipairs(c.rows) do names[#names + 1] = r.name end
    local function row_named(n) for _, r in ipairs(c.rows) do if r.name == n then return r end end end
    check(row_named("Fabi\195\161n Bravo") ~= nil, "the loaned-in player is in the match log although the export missed him")
    check(row_named("Fabi\195\161n Bravo").rating == 7.0, "his rating comes from the game's rating history")
    check(#c.added >= 5, "every player of the game's record who is not in the export is added", #c.added)
    check(row_named("Ignacio Zapata").out_minute == 45 and row_named("Bruno Henrique Gonzaga").in_minute == 45,
        "the player who went off and the substitute carry the minute")
    check(#subs == 2, "two substitutions are listed", #subs)
    check(subs[1] and subs[1].minute == 45 and subs[1].out_name == "Ignacio Zapata" and subs[1].in_name == "Bruno Henrique Gonzaga",
        "Zapata came off for Gonzaga at 45'", subs[1] and (subs[1].out_name .. ">" .. subs[1].in_name))
    check(subs[2] and subs[2].minute == 57 and subs[2].out_name == "Jordan Anderson" and subs[2].in_name == "Harry Dalton",
        "Anderson came off for Dalton at 57', although Anderson is not in the export", subs[2] and (subs[2].out_name .. ">" .. subs[2].in_name))
    local last_started = 0
    for i, r in ipairs(c.rows) do if not r.in_minute then last_started = i end end
    local first_sub = #c.rows
    for i, r in ipairs(c.rows) do if r.in_minute and i < first_sub then first_sub = i end end
    check(last_started < first_sub, "starters are listed before the substitutes")

    -- saves: only worked out when the export has none
    local with_saves = M7.saves(c, 3)
    check(not with_saves, "saves in the export are never replaced by an estimate")
    for _, r in ipairs(c.rows) do r.saves = 0 end
    c.shots_on_target_against = 7
    check(M7.saves(c, 3) and row_named("Marc Palomino").saves == 4, "without saves in the export the keeper gets shots on target minus goals")

    -- the lines sent to the app are valid JSON with the new fields
    local line = json_decode(M7.perf(row_named("Ignacio Zapata")))
    check(line.playerName == "Ignacio Zapata" and line.subOutMinute == 45 and line.subInMinute == nil, "a substituted-off player is sent with subOutMinute")
    line = json_decode(M7.perf(row_named("Bruno Henrique Gonzaga")))
    check(line.subInMinute == 45 and line.saves == 0 and line.penaltySaves == 0, "a substitute is sent with subInMinute")
    line = json_decode(M7.perf(row_named("Marc Palomino")))
    check(line.saves == 4 and line.penaltySaves == 1, "the goalkeeper is sent with his saves")
    local sub_line = json_decode(M7.sub(subs[1]))
    check(sub_line.playerOutName == "Ignacio Zapata" and sub_line.playerInName == "Bruno Henrique Gonzaga" and sub_line.minute == 45,
        "a substitution is sent with both names and the minute")
    local odd = json_decode(M7.sub({ minute = 70, out_id = "1", out_name = 'Quote"Name', in_id = "2", in_name = "Back\\slash" }))
    check(odd.playerOutName == 'Quote"Name' and odd.playerInName == "Back\\slash", "quotes and backslashes in names do not break the JSON")
end

-- the league table, worked out from a small pretend season
do
    local ids = { M = 115486, A = 50, B = 106, C = 88, D = 1795, E = 1806 }
    local standings = {}
    local order = { "M", "A", "B", "C", "D", "E" }
    for i, k in ipairs(order) do standings[i - 1] = { team_id = ids[k], competition_object_id = 779, team_index = i - 1 } end
    local function idx(k) for i, n in ipairs(order) do if n == k then return i - 1 end end end
    local function fx(date, h, a, hs, as, done, comp)
        return { competition_object_id = comp or 779, date_raw = date, home_standing_index = idx(h), away_standing_index = idx(a),
                 home_score = hs, away_score = as, completed = done }
    end
    local fixtures = {
        fx(20280805, "M", "A", 3, 0, true), fx(20280805, "B", "C", 1, 1, true), fx(20280805, "D", "E", 2, 1, true),
        fx(20280812, "M", "B", 1, 3, true), fx(20280812, "A", "C", 2, 2, true), fx(20280812, "D", "E", 0, 0, true),
        fx(20280819, "A", "M", 0, 0, false), fx(20280819, "C", "D", 0, 0, false),
        fx(20280809, "M", "D", 5, 0, true, 1856),                    -- a cup match: not part of the league table
    }
    local t = M7.table(fixtures, standings, 779, 20280812)
    local order_ids = {}
    for _, r in ipairs(t.rows) do order_ids[#order_ids + 1] = r.team_id end
    check(table.concat(order_ids, ",") == "106,1795,115486,88,1806,50",
        "table order: points, then goal difference, then goals scored", table.concat(order_ids, ","))
    check(t.position[115486] == 3, "the club is third after matchday 2", t.position[115486])
    check(t.pending_that_day == 0, "all matches of that day are in")
    local mine
    for _, r in ipairs(t.rows) do if r.team_id == 115486 then mine = r end end
    check(mine.played == 2 and mine.pts == 3 and mine.gf == 4 and mine.ga == 3, "the club's own line is right (the cup match is not counted)",
        mine.played .. "/" .. mine.pts .. "/" .. mine.gf .. "/" .. mine.ga)

    local early = M7.table(fixtures, standings, 779, 20280805)
    check(early.position[115486] == 1, "a match reported late keeps the position it had that day", early.position[115486])

    local before = M7.table(fixtures, standings, 779, 20280801)
    local all_zero = true
    for _, r in ipairs(before.rows) do if r.pts ~= 0 or r.played ~= 0 then all_zero = false end end
    check(#before.rows == 6 and all_zero, "before the first match every club is listed with no points")

    fixtures[6].completed = false
    local waiting = M7.table(fixtures, standings, 779, 20280812)
    check(waiting.pending_that_day == 1, "an unplayed match of the same day is noticed", waiting.pending_that_day)
    check(waiting.position[115486] == 2, "while that match is missing the table is different (so the script waits for it)", waiting.position[115486])

    local league_names = function(name) return name ~= "FA Cup" end
    check(M7.comp({}, { compobjid = 779, competition = "EFL Championship" }, league_names) == 779, "a league match names its own league")
    check(M7.comp({ { compobjid = 1856, competition = "FA Cup" }, { compobjid = 779, competition = "EFL Championship" },
                    { compobjid = 779, competition = "EFL Championship" } }, { compobjid = 1856, competition = "FA Cup" }, league_names) == 779,
        "after a cup match the club's league is the competition it plays most in")
    check(M7.comp({}, nil, league_names) == nil, "no league can be found without fixtures")
end


-- ---------------------------------------------------------------------------------------------------------
print("7c. a finished match, start to end, against a pretend game")
do
    local MANAGED = 115486
    local ROOT = "/home/test/Documents/FC26 Export"
    local EXPORT_DIR = ROOT .. "/MatchStats/ready/FC26_MatchStats_pmc_20281012_120000_abc"
    local TEAMS = { [MANAGED] = "Torquay AFC", [50] = "Parma", [106] = "Sunderland", [88] = "Birmingham City",
                    [1795] = "Watford", [1806] = "Stoke City" }
    local NAMES = { [460129] = "Marc Palomino", [462884] = "Oliver Ebner", [463320] = "Leo Guerra", [461607] = "Fabi\195\161n Bravo",
                    [463808] = "Denilson Fernandes", [73049] = "Pedro Lima", [460053] = "Ignacio Zapata",
                    [460045] = "Jordan Anderson", [460055] = "Kento Takeuchi", [464205] = "Elivélton Jesus",
                    [461483] = "Enrique Iglesias", [462221] = "Bruno Henrique Gonzaga", [460047] = "Harry Dalton",
                    [470001] = "Nico Striker" }
    local POSITIONS = { [0] = "GK", [3] = "RB", [4] = "RCB", [6] = "LCB", [7] = "LB", [9] = "RDM", [11] = "LDM",
                        [18] = "CAM", [23] = "RW", [25] = "ST", [27] = "LW" }
    local STANDINGS = { MANAGED, 50, 106, 88, 1795, 1806 }          -- standing index 0..5
    local function sidx(tid) for i, t in ipairs(STANDINGS) do if t == tid then return i - 1 end end end

    local function fixture(id, date, home, away, hs, as, done, comp)
        return { id = id, date = date, h = sidx(home), a = sidx(away), hs = hs, as = as, done = done, comp = comp }
    end
    local function season()
        return {
            fixture(1, 20280805, MANAGED, 50, 3, 0, true), fixture(2, 20280805, 106, 88, 1, 1, true),
            fixture(3, 20280805, 1795, 1806, 2, 1, true),
            fixture(4, 20280812, MANAGED, 106, 0, 0, false),          -- the match that is about to be played
            fixture(5, 20280812, 50, 88, 2, 2, true), fixture(6, 20280812, 1795, 1806, 0, 0, false),
            fixture(7, 20280819, 50, MANAGED, 0, 0, false),
        }
    end

    -- a pretend game memory holding the standings and the fixtures, laid out the way the script reads them
    local function build_memory(fixtures)
        local cells = {}
        local BASE, P1, P2, MGR, L1, B1, L2, B2 = 0x10000, 0x20000, 0x30000, 0x40000, 0x50000, 0x60000, 0x70000, 0x80000
        cells[BASE + 0xCFDBE08], cells[P1 + 0x60], cells[P2 + 0x48] = P1, P2, MGR
        cells[MGR + 0x88], cells[L1 + 0x28], cells[L1 + 0x1C] = L1, B1, #STANDINGS
        for i, tid in ipairs(STANDINGS) do
            local a = B1 + 0x18 * (i - 1)
            cells[a], cells[a + 0x02], cells[a + 0x04], cells[a + 0x08], cells[a + 0x16] = i, 779, tid, i - 1, true
        end
        cells[MGR + 0x60], cells[L2 + 0x28], cells[L2 + 0x1C] = L2, B2, #fixtures
        local function write(i, f)
            local a = B2 + 0x18 * (i - 1)
            cells[a], cells[a + 0x04], cells[a + 0x06], cells[a + 0x08] = f.date, 1500, f.id, f.comp or 779
            cells[a + 0x0A], cells[a + 0x0C], cells[a + 0x0E] = f.h, f.a, 0
            cells[a + 0x0F], cells[a + 0x10], cells[a + 0x11], cells[a + 0x12] = f.hs, 255, f.as, 255
            cells[a + 0x13], cells[a + 0x14] = f.done and true or false, true
        end
        for i, f in ipairs(fixtures) do write(i, f) end
        local function num(_, a) return cells[a] or 0 end
        local mem = { GetModuleBase = function() return BASE end, ReadPointer = num, ReadInt = num, ReadShort = num,
                      ReadChar = num, ReadBool = function(_, a) return cells[a] == true end }
        return mem, function(i, f) write(i, f) end
    end

    local function history_rows(ours_ids_replaced)
        local function h(pid, minutes, pos, fact) return { playerid = pid, teamid = MANAGED, minsplayed = minutes, position = pos, playerfact = fact } end
        local rows = {
            h(460129, 90, 0, 0), h(462884, 90, 3, 0), h(463320, 90, 4, 0), h(461607, 90, 6, 0), h(463808, 90, 7, 0),
            h(73049, 90, 9, 0), h(460053, 45, 11, 0), h(460045, 57, 18, 0), h(460055, 90, 23, 0), h(464205, 90, 27, 0),
            h(461483, 90, 25, 0), h(462221, 47, 11, 10), h(460047, 35, 18, 10), h(470001, 0, 25, 6),
            { playerid = 999001, teamid = 50, minsplayed = 90, position = 0, playerfact = 0 },     -- another club's record
        }
        if ours_ids_replaced then
            -- the record of an earlier match: different players
            for i, r in ipairs(rows) do if r.teamid == MANAGED then r.playerid = 880000 + i end end
        end
        return rows
    end

    local function build_db(stale_history)
        local squad = {}
        for pid in pairs(NAMES) do
            if pid ~= 461607 then squad[#squad + 1] = { teamid = MANAGED, playerid = pid } end   -- the loanee is not listed
        end
        return {
            career_users = { { clubteamid = MANAGED } },
            leagueteamlinks = { { teamid = MANAGED, leagueid = 1, nummatchesplayed = 1, currenttableposition = 2 } },  -- the stale "2"
            teamplayerlinks = squad, playerloans = {}, players = {}, career_playercontract = {},
            career_youthplayers = {}, career_managerhistory = {},
            career_playerlastmatchhistory = history_rows(stale_history),
            career_playermatchratinghistory = { { playerid = 461607, rating = 7, date = 20280812, minsplayed = 90 } },
        }
    end

    local PART_COLS = { "team_index", "player_id", "player_name", "minutes_played", "game_rating", "man_of_the_match", "passes",
                        "pass_accuracy_pct_derived", "shots", "shots_on_target", "tackles", "interceptions", "goals_derived",
                        "assists", "saves", "penalty_saves", "shots_against", "yellow_card_events", "red_dismissals" }
    local function csv(cols, rows)
        local out = { table.concat(cols, ",") }
        for _, r in ipairs(rows) do
            local cells = {}
            for i, c in ipairs(cols) do cells[i] = r[c] or "" end
            out[#out + 1] = table.concat(cells, ",")
        end
        return table.concat(out, "\n") .. "\n"
    end
    local function write_export(vfs)
        local function p(team, id, name, minutes, extra)
            local r = { team_index = team, player_id = id, player_name = name, minutes_played = minutes, game_rating = "6.5",
                        man_of_the_match = "0", passes = "20", pass_accuracy_pct_derived = "80", shots = "0", shots_on_target = "0",
                        tackles = "1", interceptions = "0", goals_derived = "0", assists = "0" }
            for k, v in pairs(extra or {}) do r[k] = v end
            return r
        end
        local rows = {
            p(0, 900001, "Opp Keeper", 90), p(0, 900002, "Opp Striker", 90, { game_rating = "9.1", man_of_the_match = "1", goals_derived = "3" }),
            p(0, 900003, "Opp Winger", 90),
            p(1, 460129, "Marc Palomino", 90, { saves = "4", penalty_saves = "1", shots_against = "9", game_rating = "6.2" }),
            p(1, 462884, "Oliver Ebner", 90), p(1, 463320, "Leo Guerra", 90), p(1, 463808, "Denilson Fernandes", 90),
            p(1, 73049, "Pedro Lima", 90), p(1, 460053, "Ignacio Zapata", 45), p(1, 460045, "Jordan Anderson", 57),
            p(1, 460055, "Kento Takeuchi", 90), p(1, 464205, "Elivélton Jesus", 90),
            p(1, 461483, "Enrique Iglesias", 90, { goals_derived = "1", game_rating = "7.4", shots = "3", shots_on_target = "2" }),
            p(1, 462221, "Bruno Henrique Gonzaga", 47), p(1, 460047, "Harry Dalton", 35),
            p(1, 470001, "Nico Striker", 0),
        }
        vfs[EXPORT_DIR .. "/16_match_participants_wide.csv"] = csv(PART_COLS, rows)
        vfs[EXPORT_DIR .. "/18_match_teams_wide.csv"] = csv(
            { "team_index", "passes", "shots", "shots_on_target", "npk_expected_goals", "pass_accuracy_pct_derived", "shootout_goals" },
            { { team_index = "1", passes = "300", shots = "7", shots_on_target = "3", npk_expected_goals = "0.8", pass_accuracy_pct_derived = "77.5", shootout_goals = "255" },
              { team_index = "0", passes = "500", shots = "14", shots_on_target = "7", npk_expected_goals = "2.6", pass_accuracy_pct_derived = "88", shootout_goals = "255" } })
        vfs[EXPORT_DIR .. "/MATCH_READY"] = "ready"
    end

    local function match_payloads(vfs)
        local list = {}
        for path, text in pairs(vfs) do
            if path:find("/outbox/", 1, true) and path:sub(-5) == ".json" and text:find('"dataType": "match"', 1, true) then
                list[#list + 1] = json_decode(text)
            end
        end
        return list
    end

    local function play(opts)
        local vfs, clk = {}, { t = 1795000000 }
        local fixtures = season()
        local mem, set_fixture = build_memory(fixtures)
        local spec = { db = build_db(opts.stale_history), vfs = vfs, clock = function() return clk.t end, names = NAMES, teams = TEAMS,
                       today = { year = 2028, month = 8, day = 12 }, memory = mem, positions = POSITIONS,
                       comps = { [779] = "EFL Championship", [1856] = "FA Cup" },
                       dirs = { [ROOT] = true, [ROOT .. "/outbox"] = true, [ROOT .. "/Snapshots"] = true, [ROOT .. "/MatchStats"] = true,
                                ["/home/test/tmp"] = true } }
        if opts.exporter then _G.FC26_EXPORT_AUTO_CAPTURE_STATE = {} end
        local ok, err, handler, restore = run_capture_session(spec)
        local function tick(seconds)
            clk.t = clk.t + seconds
            handler(nil, 1, nil)
        end
        local game = { ok = ok, err = err, vfs = vfs, tick = tick, fixtures = fixtures, set = set_fixture }
        function game.finish_match()
            fixtures[4].hs, fixtures[4].as, fixtures[4].done = 1, 3, true
            set_fixture(4, fixtures[4])
        end
        function game.finish_other()
            fixtures[6].done = true
            set_fixture(6, fixtures[6])
        end
        function game.close()
            restore()
            _G.FC26_EXPORT_AUTO_CAPTURE_STATE = nil
            _G.FC26_EXPORT_MATCH_STATS_LAST_EXPORT_RESULT = nil
            -- the script keeps these across reloads on purpose; every pretend session starts clean
            _G.FC26_BEAMED_FIXTURE_KEYS, _G.FC26_ATLAS_USED = nil, nil
            _G.FC26_DB_FIXTURES_CACHE, _G.FC26_DB_FIXTURES_TIME = nil, nil
        end
        function game.log() return vfs[ROOT .. "/auto_capture_monitor.log"] or "" end
        return game
    end

    -- A. the Atlas exporter produced the match files
    do
        local g = play({ exporter = true })
        check(g.ok, "script starts against the pretend game", g.err)
        write_export(g.vfs)
        _G.FC26_EXPORT_MATCH_STATS_LAST_EXPORT_RESULT = { success = true, ready_dir = EXPORT_DIR }
        g.tick(3)
        g.finish_match()
        g.tick(3)
        g.tick(3)
        check(#match_payloads(g.vfs) == 0, "the match waits while another match of the day is still unplayed")
        g.tick(3)
        check(#match_payloads(g.vfs) == 0, "and keeps waiting")
        g.finish_other()
        g.tick(4)
        g.tick(4)
        local list = match_payloads(g.vfs)
        check(#list == 1, "once the day's results are in, the match is sent once", #list)
        local m = list[1] or {}
        if not list[1] then out_print("        log: " .. g.log():sub(-1500)) end
        check(m.opponentName == "Sunderland" and m.goalsFor == 1 and m.goalsAgainst == 3, "the score and the opponent are right")
        check(m.leaguePosition == 3, "league position comes from the results (3rd), not the stored value (2)", m.leaguePosition)
        if m.leaguePosition ~= 3 then out_print("        log: " .. g.log():sub(-1800)) end
        check(m.detailSource == "atlas", "the match carries the exported player data", m.detailSource)
        check(m.possession == 38 and m.possessionEstimated == true,
            "possession is our share of the passes (300 of 800) and is marked as an estimate", m.possession)
        check(m.shotsFor == 7 and m.shotsAgainst == 14, "shots are ours and theirs the right way round", tostring(m.shotsFor) .. "-" .. tostring(m.shotsAgainst))
        check(m.motmName == "Opp Striker" and m.motmIsOpponent == true, "the opposing man of the match is named and flagged", m.motmName)
        local starred = 0
        for _, p in ipairs(m.playerPerformances or {}) do if p.isMotm then starred = starred + 1 end end
        check(starred == 0, "none of our players is starred when the opponent won it", starred)

        local by_name = {}
        for _, p in ipairs(m.playerPerformances or {}) do by_name[p.playerName] = p end
        check(by_name["Fabi\195\161n Bravo"] ~= nil, "the loaned-in player, missing from the export, is in the match log")
        check(by_name["Fabi\195\161n Bravo"] and by_name["Fabi\195\161n Bravo"].rating == 7, "with the rating the game recorded")
        check(by_name["Marc Palomino"] and by_name["Marc Palomino"].saves == 4 and by_name["Marc Palomino"].penaltySaves == 1,
            "the goalkeeper's saves are in the match log")
        check(by_name["Nico Striker"] == nil, "a substitute who never came on is not in the match log")
        check(#(m.playerPerformances or {}) == 13, "13 players took part", #(m.playerPerformances or {}))
        check(by_name["Ignacio Zapata"] and by_name["Ignacio Zapata"].subOutMinute == 45, "the player who went off carries his minute")
        check(by_name["Bruno Henrique Gonzaga"] and by_name["Bruno Henrique Gonzaga"].subInMinute == 45, "the substitute carries his minute")
        local subs = m.substitutions or {}
        check(#subs == 2, "two substitutions are in the match log", #subs)
        check(subs[1] and subs[1].minute == 45 and subs[1].playerOutName == "Ignacio Zapata" and subs[1].playerInName == "Bruno Henrique Gonzaga",
            "Zapata came off for Gonzaga at 45'")
        check(subs[2] and subs[2].minute == 57 and subs[2].playerOutName == "Jordan Anderson" and subs[2].playerInName == "Harry Dalton",
            "Anderson came off for Dalton at 57'")
        check(g.log():find("Players the export left out were added", 1, true) ~= nil, "the log says which players were added")
        check(by_name["Marc Palomino"].position == "GK" and by_name["Enrique Iglesias"].position == "ST"
            and by_name["Fabi\195\161n Bravo"].position == "LCB" and by_name["Oliver Ebner"].position == "RB",
            "each player carries the position he played, so the app can line the team up", by_name["Enrique Iglesias"].position)
        check(by_name["Bruno Henrique Gonzaga"].position == nil or by_name["Bruno Henrique Gonzaga"].position == "LDM",
            "a substitute carries a real position or none")
        local theirs = {}
        for _, gd in ipairs(m.goalDetails or {}) do if gd.isOpponent then theirs[#theirs + 1] = gd.playerName end end
        check(#theirs == 3 and theirs[1] == "Opp Striker" and theirs[2] == "Opp Striker" and theirs[3] == "Opp Striker",
            "the three goals against are credited to the opposing scorer by name, not to a stand-in", table.concat(theirs, "|"))
        local ours = {}
        for _, gd in ipairs(m.goalDetails or {}) do if not gd.isOpponent then ours[#ours + 1] = gd.playerName end end
        check(#ours == 1 and ours[1] == "Enrique Iglesias", "our goal is credited to our scorer", table.concat(ours, "|"))
        g.close()
    end

    -- B. no Atlas export at all: the match is built from the game's own records
    do
        local g = play({ exporter = false })
        check(g.ok, "script starts without the Atlas exporter", g.err)
        g.tick(3)
        g.finish_match()
        g.finish_other()
        for _ = 1, 8 do g.tick(3) end
        local list = match_payloads(g.vfs)
        check(#list == 1, "without an export the match is still sent, after a short wait", #list)
        local m = list[1] or {}
        if not list[1] then out_print("        log: " .. g.log():sub(-1500)) end
        check(m.detailSource == "estimated", "and is marked as estimated", m.detailSource)
        check(m.leaguePosition == 3, "the league position is right here too", m.leaguePosition)
        local by_name = {}
        for _, p in ipairs(m.playerPerformances or {}) do by_name[p.playerName] = p end
        check(by_name["Fabi\195\161n Bravo"] ~= nil, "the loaned-in player is in the match log")
        check(by_name["Nico Striker"] == nil, "the unused substitute is not")
        check(#(m.substitutions or {}) == 2, "the substitutions come from the game's record", #(m.substitutions or {}))
        check(m.possessionEstimated == true, "possession is flagged as a guess")
        g.close()
    end

    -- C. the game's last-match record belongs to another match: nothing is guessed from it
    do
        local g = play({ exporter = true, stale_history = true })
        write_export(g.vfs)
        _G.FC26_EXPORT_MATCH_STATS_LAST_EXPORT_RESULT = { success = true, ready_dir = EXPORT_DIR }
        g.tick(3)
        g.finish_match()
        g.finish_other()
        for _ = 1, 4 do g.tick(4) end
        local list = match_payloads(g.vfs)
        check(#list == 1, "a record of another match does not stop the match from being sent", #list)
        local m = list[1] or {}
        check(#(m.substitutions or {}) == 0, "but no substitutions are made up from it", #(m.substitutions or {}))
        check(#(m.playerPerformances or {}) == 12, "the match log keeps the export's own players", #(m.playerPerformances or {}))
        check(g.log():find("different players than this export", 1, true) ~= nil, "the log explains why")
        g.close()
    end
end


print(string.format("\n%d checks, %d failed", checks, failures))
os.exit(failures == 0 and 0 or 1)
