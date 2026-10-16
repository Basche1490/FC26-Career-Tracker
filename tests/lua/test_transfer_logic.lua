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
print("2b. the exporter is found even when Live Editor does not say where the script lives")
do
    local src = read_file(scripts .. "FC26_Auto_Capture.lua")
    local block = src:match("%-%- BEGIN find_script[^\n]*\n(.-)%-%- END find_script")
    check(block ~= nil, "find_script block found between the markers")

    local function run_search(files, env, cwd, subdirs_of)
        local saved_getenv, saved_execute, saved_popen = os.getenv, os.execute, io.popen
        os.getenv = function(k) return env[k] end
        io.popen = function()
            return { read = function() return cwd end, close = function() end }
        end
        os.execute = function(cmd)
            -- mimic:  cmd /c dir /b /ad "<folder>" > "<file>" 2>nul
            local folder, out = cmd:match('dir /b /ad "([^"]*)" > "([^"]*)"')
            if folder then
                local list = subdirs_of[folder:lower()] or {}
                local f = io.open((out:gsub("\\", "/")), "w")
                if f then f:write(table.concat(list, "\r\n")) f:close() end
            end
            return true
        end
        local prelude = [[
            local SCRIPT_DIR, user_profile, FILES = ...
            local function trim(v) return (tostring(v or ""):gsub("^%s+", ""):gsub("%s+$", "")) end
            local function win_path(p) return (tostring(p or ""):gsub("/", "\\")) end
            local function file_exists(path) return FILES[path:lower()] == true end
        ]]
        local fn = assert(load(prelude .. block .. "\nreturn find_script", "find_script"))
        local lower = {}
        for path in pairs(files) do lower[path:lower()] = true end
        local find = fn("", env.USERPROFILE or "", lower)
        local found, dirs = find("FC26_AutoCapture_v1.1.1.lua")
        os.getenv, os.execute, io.popen = saved_getenv, saved_execute, saved_popen
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

    found = run_search({ ["D:\\Tools\\LE\\lua\\scripts\\" .. EXE] = true }, base_env, "D:\\Tools\\LE", {})
    check(found == "D:\\Tools\\LE\\lua\\scripts\\" .. EXE, "found through Live Editor's working folder", tostring(found))

    found = run_search({ ["C:\\Users\\Joel\\Downloads\\" .. EXE] = true }, base_env, nil, {})
    check(found == "C:\\Users\\Joel\\Downloads\\" .. EXE, "found in Downloads", tostring(found))

    local missing, searched = run_search({}, base_env, nil, {})
    check(missing == nil and #searched > 5, "returns nil and the searched folders when it is nowhere", tostring(missing))
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
                   remove = os.remove, dofile = dofile, print = print }

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
        function obj:close() end
        return obj
    end
    io.open = function(path, mode)
        mode = mode or "r"
        local m = mode:sub(1, 1)
        if m == "r" then
            if vfs[path] == nil then return nil end
            return reader(vfs[path])
        end
        if m == "w" then vfs[path] = "" end
        if m == "a" and vfs[path] == nil then vfs[path] = "" end
        local w = {}
        function w:write(...) for _, part in ipairs({ ... }) do vfs[path] = vfs[path] .. tostring(part) end return w end
        function w:close() end
        return w
    end
    io.popen = function() return { read = function() return "" end, close = function() end } end
    os.execute = function() return true end
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
    package.preload["imports/core/memory"] = function() return {} end
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
        _G.dofile, _G.print = real.dofile, real.print
        _G.AddEventHandler = nil
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


print(string.format("\n%d checks, %d failed", checks, failures))
os.exit(failures == 0 and 0 or 1)
