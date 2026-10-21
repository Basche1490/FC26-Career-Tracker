-- FC26 Table Census v1.2.0 - read-only inventory of the game data FC26 Career Tracker could use.
--
-- Writes to Documents\FC26 Export:
--   FC26_census_<date>_<time>.txt         the report: a season-end check (league champions, cup finals, awards, cup
--                                          runs), then which tables exist, their columns and a few rows
--   FC26_census_<date>_<time>_tables.csv  one line per table that exists: name, rows, columns
--   FC26_census_progress.txt              what the script is doing right now, written before every step
--   FC26_census_skip.txt                  steps that crashed the game in an earlier run (they are skipped)
--
-- Live Editor has no "list every table" command, so the census asks for every table name the game is known or likely
-- to use. A name that does not exist simply comes back empty.
--
-- It only reads. Nothing in the game or the save is changed. If the game ever closes while it runs, start the game,
-- load the career and run the census again: it reads FC26_census_progress.txt, skips the step that stopped the game
-- last time, says which one in the report, and carries on.
--
-- Version 1.0.0 crashed the game straight away for one player. It listed folders of Live Editor through
-- getFileList / getDirectoryList with a folder that may not exist, and looked inside Live Editor's own objects;
-- 1.1.0 does neither, does the season-end check first (it only uses what the other scripts already use every day),
-- and leaves the unknown table names for last. 1.1.0 then closed the game too, straight after the league tables: it
-- asked GetDBTableRows for the award table, which is empty early in a season, and nothing had ever asked it for an
-- empty table before. 1.2.0 counts a table's records first and never asks GetDBTableRows for an empty one. It also
-- reads its files the way the other scripts do (read("*a")).
--
-- Best run twice: once now, and once in June of an in-game season (after the cup finals, before 1 July), because
-- the game replaces the fixture list, and with it every cup final, when the new season starts.

local MEMORY = require "imports/core/memory"
require "imports/other/helpers"
require "imports/services/enums"
require "imports/career_mode/helpers"

assert(IsInCM(), "FC26 Table Census must be executed while a career is loaded")

local VERSION = "1.2.0"

local user_profile = os.getenv("USERPROFILE") or ""
local ROOT = (user_profile ~= "" and user_profile or ".") .. "\\Documents\\FC26 Export"
pcall(os.execute, 'if not exist "' .. ROOT .. '" mkdir "' .. ROOT .. '"')
-- FC26_CENSUS_OUT_PATH, _PROGRESS_PATH and _SKIP_PATH only exist so the automated tests can choose where files go
local OUT_PATH = rawget(_G, "FC26_CENSUS_OUT_PATH") or (ROOT .. "\\FC26_census_" .. os.date("%Y%m%d_%H%M%S") .. ".txt")
local CSV_PATH = OUT_PATH:gsub("%.txt$", "") .. "_tables.csv"
local PROGRESS_PATH = rawget(_G, "FC26_CENSUS_PROGRESS_PATH") or (ROOT .. "\\FC26_census_progress.txt")
local SKIP_PATH = rawget(_G, "FC26_CENSUS_SKIP_PATH") or (ROOT .. "\\FC26_census_skip.txt")

-- Tables with more rows than this are counted, but their rows are not loaded (the snapshot already exports them).
local BIG_TABLE = 3000
local SAMPLE_ROWS = 3

local lines = {}
local csv_lines = { "table,rows,columns" }

local function flush()
    local f = io.open(OUT_PATH, "wb")
    if f then
        f:write(table.concat(lines, "\r\n"))
        f:write("\r\n")
        f:close()
    end
    local c = io.open(CSV_PATH, "wb")
    if c then
        c:write(table.concat(csv_lines, "\r\n"))
        c:write("\r\n")
        c:close()
    end
end

local function w(text)
    lines[#lines + 1] = tostring(text)
end

-- ---------------------------------------------------------------------------------------------------------
-- Progress file: every step is written down before it starts and after it ends. The file is closed after each
-- line, so if the game closes in the middle of a step the line is already on disk, and the next run knows which
-- step it was.
-- ---------------------------------------------------------------------------------------------------------
local function read_lines(path)
    local out = {}
    local f = io.open(path, "rb")
    if not f then return out end
    for line in (f:read("*a") or ""):gmatch("[^\r\n]+") do out[#out + 1] = line end
    f:close()
    return out
end

local function append_line(path, text)
    local f = io.open(path, "ab")
    if f then
        f:write(text .. "\r\n")
        f:close()
    end
end

local skip = {}
for _, name in ipairs(read_lines(SKIP_PATH)) do skip[name] = true end

local crashed_at = nil
do
    local open = {}
    local finished = false
    for _, line in ipairs(read_lines(PROGRESS_PATH)) do
        local what, step_name = line:match("^(%a+) (.+)$")
        if what == "start" then
            open[#open + 1] = step_name
        elseif what == "done" then
            for i = #open, 1, -1 do
                if open[i] == step_name then
                    for _ = i, #open do table.remove(open) end
                    break
                end
            end
        elseif line == "finished" then
            finished = true
        end
    end
    if not finished and #open > 0 then
        crashed_at = open[#open]          -- the innermost step that never ended
        if not skip[crashed_at] then
            skip[crashed_at] = true
            append_line(SKIP_PATH, crashed_at)
        end
    end
end
do
    local f = io.open(PROGRESS_PATH, "wb")
    if f then
        f:write("census " .. VERSION .. " " .. os.date("%Y-%m-%d %H:%M:%S") .. "\r\n")
        f:close()
    end
end

-- Runs fn as a named step. A step that stopped the game in an earlier run is skipped. Returns ok, result.
local function step(name, fn)
    if skip[name] then
        return false, "skipped: this step stopped the game in an earlier run"
    end
    append_line(PROGRESS_PATH, "start " .. name)
    local ok, res = pcall(fn)
    append_line(PROGRESS_PATH, "done " .. name)
    return ok, res
end

local function section(title, fn)
    w("")
    w("=== " .. title .. " ===")
    pcall(print, "[FC26 Census] " .. title)
    local ok, err = step("section " .. title, fn)
    if not ok then w("SECTION FAILED: " .. tostring(err)) end
    flush()
end

-- ---------------------------------------------------------------------------------------------------------
-- Small helpers (the same ones as FC26_Probe.lua)
-- ---------------------------------------------------------------------------------------------------------
local function raw(v)
    if type(v) == "table" then return v.value end
    return v
end

local function show(v)
    v = raw(v)
    if v == nil then return "nil" end
    return tostring(v)
end

local function is_id(n)
    return n ~= nil and n > 0 and n < 4294967295
end

local function pname(pid)
    local ok, name = pcall(function() return GetPlayerName(tonumber(pid)) end)
    if ok and name and tostring(name) ~= "" then return tostring(name) end
    return "?"
end

local function tname(tid)
    local ok, name = pcall(function() return GetTeamName(tonumber(tid)) end)
    if ok and name and tostring(name) ~= "" then return tostring(name) end
    return "?"
end

local function cname(obj_id)
    local ok, name = pcall(function() return GetCompetitionNameByObjID(tonumber(obj_id)) end)
    if ok and name and tostring(name) ~= "" then return tostring(name) end
    return "competition " .. tostring(obj_id)
end

-- EA stores dates either as yyyymmdd or as a day count since 1582-10-14.
local function decode_date(days)
    days = tonumber(days) or 0
    if days > 19000000 and days < 21000000 then
        return string.format("%04d-%02d-%02d", math.floor(days / 10000), math.floor((days % 10000) / 100), days % 100)
    elseif days > 100000 and days < 250000 then
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
        return string.format("%04d-%02d-%02d", y, m, d)
    end
    return nil
end

local function get_table(name)
    local ok, t = pcall(function() return LE.db:GetTable(name) end)
    if ok and t then return t end
    return nil
end

local function api_rows(name)
    if type(GetDBTableRows) ~= "function" then return nil end
    local ok, rows = pcall(GetDBTableRows, name)
    if ok and type(rows) == "table" then return rows end
    return nil
end

local function sorted_keys(row)
    local keys = {}
    for k in pairs(row) do keys[#keys + 1] = tostring(k) end
    table.sort(keys)
    return keys
end

local function plain_row(r)
    local row = {}
    for k, v in pairs(r) do row[tostring(k):lower()] = raw(v) end
    return row
end

local function describe_row(row, limit)
    local parts = {}
    for _, k in ipairs(sorted_keys(row)) do
        parts[#parts + 1] = k .. "=" .. show(row[k])
        if limit and #parts >= limit then
            parts[#parts + 1] = "..."
            break
        end
    end
    return table.concat(parts, ", ")
end

-- Adds names and dates next to the raw numbers of a row, so the report can be read without the game open.
local function annotate(row)
    local notes = {}
    for _, k in ipairs(sorted_keys(row)) do
        local n = tonumber(row[k])
        if n then
            if k:find("date") or k:find("day") then
                local d = decode_date(n)
                if d then notes[#notes + 1] = k .. "->" .. d end
            elseif k:find("player") and is_id(n) then
                notes[#notes + 1] = k .. "->" .. pname(n)
            elseif (k:find("team") or k:find("club")) and is_id(n) then
                notes[#notes + 1] = k .. "->" .. tname(n)
            end
        end
    end
    return table.concat(notes, "; ")
end

local function mentions_team(row, tid)
    for k, v in pairs(row) do
        if (k:find("team") or k:find("club")) and tonumber(v) == tid then return true end
    end
    return false
end

-- Number of records, counted with Live Editor's record functions (the way every other script reads tables).
local function count_records(tbl, cap)
    local ok, first = pcall(function() return tbl:GetFirstRecord() end)
    if not ok or not first or first <= 0 then return 0 end
    local n, rec = 0, first
    while rec and rec > 0 and n < cap do
        n = n + 1
        local ok2, nxt = pcall(function() return tbl:GetNextValidRecord() end)
        if not ok2 then break end
        rec = nxt
    end
    return n
end

-- Rows through GetDBTableRows, but only for a table that has records: an empty table is never handed to it.
-- Returns an empty list for a missing or empty table, nil when GetDBTableRows cannot be used.
local function rows_of(name)
    local tbl = get_table(name)
    if not tbl or count_records(tbl, 1) == 0 then return {} end
    return api_rows(name)
end

local function csv_cell(s)
    s = tostring(s or "")
    if s:find('[,"\r\n]') then s = '"' .. s:gsub('"', '""') .. '"' end
    return s
end

-- A shoot-out score the game stores as 255 (or -1) means there was no shoot-out.
local function shootout(v)
    v = tonumber(v) or -1
    if v < 0 or v >= 100 then return nil end
    return v
end

-- ---------------------------------------------------------------------------------------------------------
-- Names to ask for
-- ---------------------------------------------------------------------------------------------------------
-- Tables the capture scripts already read every day: asked for first.
local KNOWN = {
    "players", "teams", "leagues", "leagueteamlinks", "teamplayerlinks", "playerloans", "fixtures", "career_users",
    "career_calendar", "career_playercontract", "career_youthplayers", "career_playergrowthuserseason",
    "career_playerlastmatchhistory", "career_playermatchratinghistory", "career_playerawards",
    "career_competitionprogress", "career_managerhistory", "career_playerinfo", "career_scouts",
}

-- Words that game tables are, or might be, named after. Each is tried as it is and with "career_" in front.
local WORDS = {
    "users", "calendar", "playercontract", "presignedcontract", "youthplayers", "youthplayerattributes",
    "regenplayerattributes", "playergrowthuserseason", "playerlastmatchhistory", "playermatchratinghistory",
    "playerawards", "competitionprogress", "managerhistory", "managerinfo", "managerpref", "scouts", "scoutmission",
    "scoutmissions", "youthscouts", "squadranking",
    -- squad database
    "playernames", "dcplayernames", "editedplayernames", "teamkits", "teamstadiumlinks", "stadiums", "stadiumassignments",
    "formations", "teamsheets", "default_teamsheets", "defaultteamsheets", "defaultteamdata", "manager", "managers",
    "referee", "referees", "leaguerefereelinks", "nations", "nationalteams", "rowteamnationlinks", "teamnationlinks",
    "teamballs", "playerboots", "boots", "balls", "kits", "rivals", "teamrivals", "rivalries", "derbies", "previousteam",
    "previousteams", "playerpositionzones", "playerformdiff", "teamformdiff", "competition", "competitions", "compobj",
    "compobjs", "competitionteamlinks", "compteamlinks", "competitiontrophies", "trophies", "trophy",
    "audionation", "smallsidedteams", "customformations", "mentalities", "defaultmentalities", "teamtactics",
    "playstyles", "playerplaystyles", "traits", "playertraits", "roles", "playerroles",
    -- results, tables and history: what an "Around the World" page needs
    "standings", "leaguestandings", "competitionstandings", "leaguetable", "leaguetables",
    "matches", "results", "matchresults", "matchhistory", "matchstats", "matchreports", "schedule", "schedules",
    "history", "leaguehistory", "competitionhistory", "cuphistory", "seasonhistory", "teamhistory", "clubhistory",
    "pastwinners", "winners", "competitionwinners", "leaguewinners", "cupwinners", "champions", "titles",
    "trophycabinet", "teamtrophies", "clubtrophies", "playertrophies", "trophyhistory", "honours", "honors",
    "cupprogress", "cupdraws", "knockout", "brackets", "groups", "groupstandings",
    -- awards and statistics
    "awards", "teamawards", "monthlyawards", "seasonawards", "playerofthemonth", "managerofthemonth", "teamoftheweek",
    "teamoftheseason", "totw", "tots", "goldenboot", "ballondor", "playerstats", "playerseasonstats", "seasonstats",
    "stats", "statistics", "teamstats", "teamseasonstats", "leaguestats", "competitionstats", "topscorers",
    "goalscorers", "playercareerstats", "careerstats", "playerhistory", "playermatchstats",
    -- squad state
    "injuries", "injury", "playerinjuries", "playerinjury", "playerinjuryhistory", "injuryhistory", "medical",
    "playerform", "playermorale", "morale", "playerstatus", "playerinfo", "playersharpness", "sharpness", "fitness",
    "playerfitness", "squadrole", "squadroles", "playerdevelopment", "development", "playergrowth", "training",
    "trainingplans", "playertraining", "suspensions", "playersuspensions", "bans",
    -- money and deals
    "finances", "finance", "budget", "budgets", "transferbudget", "wagebudget", "teamfinances", "clubfinances",
    "financialsummary", "incomes", "expenses", "revenue", "sponsors", "contracts", "loans", "loanhistory",
    "transfers", "transferhistory", "transferoffers", "transferdeals", "offers", "incomingoffers", "outgoingoffers",
    "activetransfers", "loanoffers", "shortlist", "playeragents", "agents", "releaseclauses", "negotiations",
    -- board, story and the rest of career mode
    "boardobjectives", "objectives", "seasonobjectives", "clubobjectives", "objectiveprogress", "jobsecurity",
    "news", "newsfeed", "newsitems", "emails", "inbox", "messages", "boardmessages", "pressconference",
    "unexpectedevents", "events", "staff", "coaches", "playerperformancebonuses", "bonuses",
    "playasplayer", "createaclub", "createdclub", "customteams",
}

local EXTRA_NAMES = {
    "career_fixtures", "career_standings", "career_leaguestandings", "career_trophywinners", "career_competitionwinners",
    "career_seasonsummary", "career_seasonreview", "career_endofseason", "career_awardwinners",
    "career_playerseasonstats", "career_playercareerstats",
}

-- ---------------------------------------------------------------------------------------------------------
-- Report
-- ---------------------------------------------------------------------------------------------------------
local managed_tid = 0
local existing = {}          -- name -> { rows = n, columns = {...} }

section("Environment", function()
    w("FC26 Table Census " .. VERSION .. " - " .. os.date("%Y-%m-%d %H:%M:%S"))
    if crashed_at then
        w("The last run stopped (the game closed) during: " .. crashed_at .. ". That step is skipped from now on.")
    end
    local skipped = {}
    for name in pairs(skip) do skipped[#skipped + 1] = name end
    table.sort(skipped)
    if #skipped > 0 then w("Steps skipped because they stopped the game before: " .. table.concat(skipped, "; ")) end
    w("GetDBTableRows available: " .. tostring(type(GetDBTableRows) == "function"))
    w("LE.db available: " .. tostring(type(LE) == "table" and LE.db ~= nil))
    pcall(function()
        if type(GetUserTeamID) == "function" then
            local uid = tonumber(GetUserTeamID())
            if is_id(uid) then managed_tid = uid end
        end
    end)
    if managed_tid == 0 then
        local users = get_table("career_users")
        if users then
            local rec = users:GetFirstRecord()
            while rec and rec > 0 do
                local tid = tonumber(users:GetRecordFieldValue(rec, "clubteamid")) or 0
                if is_id(tid) then managed_tid = tid break end
                rec = users:GetNextValidRecord()
            end
        end
    end
    w("Managed team: " .. tostring(managed_tid) .. " " .. tname(managed_tid))
    local ok, d = pcall(function() return GetCurrentDate() end)
    if ok and type(d) == "table" and d.year then
        w(string.format("In-game date: %04d-%02d-%02d", d.year, d.month, d.day))
    else
        w("In-game date: not readable through GetCurrentDate")
    end

    -- function names only (nothing is called): shows whether Live Editor offers something for winners, awards...
    local hints = { "table", "db", "fixture", "comp", "league", "award", "troph", "standing", "season", "injur",
                    "stat", "history", "winner", "result", "match", "cup", "title", "news", "budget", "finance" }
    local standard = { getmetatable = true, setmetatable = true }
    local names = {}
    for k, v in pairs(_G) do
        if type(v) == "function" and not standard[k] then
            local low = tostring(k):lower()
            for _, h in ipairs(hints) do
                if low:find(h, 1, true) then names[#names + 1] = tostring(k) break end
            end
        end
    end
    table.sort(names)
    w("Live Editor functions about tables, competitions, results, awards, stats (" .. #names .. "):")
    for i = 1, #names, 8 do w("  " .. table.concat(names, ", ", i, math.min(i + 7, #names))) end
end)

-- ---------------------------------------------------------------------------------------------------------
-- Season-end check: what an "Around the World" page and the season summary would be built from. First, because it
-- only uses tables and memory the other scripts already read.
-- ---------------------------------------------------------------------------------------------------------
local TOP_LEAGUES = { { 13, "Premier League" }, { 53, "LALIGA EA SPORTS" }, { 31, "Serie A" }, { 19, "Bundesliga" }, { 16, "Ligue 1" } }

section("Season-end check 1: league tables and champions (leagueteamlinks)", function()
    local list = {}
    local tbl = get_table("leagueteamlinks")
    if not tbl then w("leagueteamlinks not available") return end
    local fields = { "teamid", "leagueid", "prevleagueid", "currenttableposition", "points", "nummatchesplayed",
                     "previousyeartableposition", "champion" }
    local rec = tbl:GetFirstRecord()
    while rec and rec > 0 do
        local row = {}
        for _, f in ipairs(fields) do
            local ok, v = pcall(function() return tbl:GetRecordFieldValue(rec, f) end)
            if ok then row[f] = v end
        end
        list[#list + 1] = row
        rec = tbl:GetNextValidRecord()
    end
    local leagues = {}
    for _, l in ipairs(TOP_LEAGUES) do leagues[#leagues + 1] = l end
    local own_league
    for _, row in ipairs(list) do
        if tonumber(row.teamid) == managed_tid then own_league = tonumber(row.leagueid) end
    end
    local listed = false
    for _, l in ipairs(leagues) do if l[1] == own_league then listed = true end end
    if own_league and not listed then leagues[#leagues + 1] = { own_league, "your league" } end

    for _, l in ipairs(leagues) do
        local lid, label = l[1], l[2]
        local members, last_champion = {}, {}
        for _, row in ipairs(list) do
            if tonumber(row.leagueid) == lid then members[#members + 1] = row end
            if (tonumber(row.prevleagueid) == lid or (tonumber(row.prevleagueid) or 0) <= 0 and tonumber(row.leagueid) == lid)
                and (tonumber(row.previousyeartableposition) == 1) then
                last_champion[#last_champion + 1] = row
            end
        end
        table.sort(members, function(a, b) return (tonumber(a.currenttableposition) or 99) < (tonumber(b.currenttableposition) or 99) end)
        w(string.format("%s (league %d): %d teams", label, lid, #members))
        for i = 1, math.min(3, #members) do
            local m = members[i]
            w(string.format("   now %s. %s  pts=%s  played=%s  champion flag=%s", show(m.currenttableposition), tname(m.teamid),
                show(m.points), show(m.nummatchesplayed), show(m.champion)))
        end
        for _, c in ipairs(last_champion) do
            w(string.format("   last season's winner: %s (champion flag=%s, now in league %s)", tname(c.teamid), show(c.champion), show(c.leagueid)))
        end
    end
end)

section("Season-end check 2: awards and cup runs", function()
    for _, name in ipairs({ "career_playerawards", "career_competitionprogress" }) do
        local ok, rows = step("rows " .. name, function() return rows_of(name) end)
        if ok and rows then
            w(name .. ": " .. #rows .. " rows" .. (#rows == 0 and " (empty: nothing to show yet)" or ""))
            for i = 1, math.min(#rows, 60) do
                local row = plain_row(rows[i])
                w(string.format("  #%d  %s", i, describe_row(row, 30)))
                local note = annotate(row)
                if note ~= "" then w("        " .. note) end
            end
        else
            w(name .. ": not read (" .. tostring(rows or "GetDBTableRows not available") .. ")")
        end
    end
end)

-- The fixture list lives in the game's memory, read exactly the way FC26_Snapshot.lua reads it.
local function get_fce_data_manager()
    local interface = GetPlugin(ENUM_djb2IFCEInterface_CLSS)
    return MEMORY:ReadMultilevelPointer(interface, { 0x18, 0x10, 0x08, 0x00 })
end

local function read_valid_standings()
    local result = {}
    local manager = get_fce_data_manager()
    local list = MEMORY:ReadPointer(manager + 0x88)
    local begin_ptr = MEMORY:ReadPointer(list + 0x28)
    local max_items = MEMORY:ReadInt(list + 0x1C) - 1
    for index = 0, max_items do
        local current = begin_ptr + (0x18 * index)
        local is_used = MEMORY:ReadBool(current + 0x16)
        local team_id = MEMORY:ReadInt(current + 0x04)
        if is_used and team_id > 0 then
            result[index] = { competition_object_id = MEMORY:ReadShort(current + 0x02), team_id = team_id }
        end
    end
    return result
end

local function read_valid_fixtures()
    local result = {}
    local manager = get_fce_data_manager()
    local list = MEMORY:ReadPointer(manager + 0x60)
    local begin_ptr = MEMORY:ReadPointer(list + 0x28)
    local max_items = MEMORY:ReadInt(list + 0x1C) - 1
    for index = 0, max_items do
        local current = begin_ptr + (0x18 * index)
        if MEMORY:ReadBool(current + 0x14) then
            result[#result + 1] = {
                date_raw = MEMORY:ReadInt(current + 0x00),
                competition_object_id = MEMORY:ReadShort(current + 0x08),
                home_standing_index = MEMORY:ReadShort(current + 0x0A),
                away_standing_index = MEMORY:ReadShort(current + 0x0C),
                home_score = MEMORY:ReadChar(current + 0x0F),
                home_penalties = MEMORY:ReadChar(current + 0x10),
                away_score = MEMORY:ReadChar(current + 0x11),
                away_penalties = MEMORY:ReadChar(current + 0x12),
                completed = MEMORY:ReadBool(current + 0x13)
            }
        end
    end
    return result
end

section("Season-end check 3: the last match of every competition in the fixture list (finals)", function()
    local ok, fixtures = step("memory fixtures", read_valid_fixtures)
    if not ok or type(fixtures) ~= "table" then w("fixture list not readable: " .. tostring(fixtures)) return end
    local ok2, standings = step("memory standings", read_valid_standings)
    if not ok2 or type(standings) ~= "table" then standings = {} end
    local by_comp, comp_order = {}, {}
    for _, f in ipairs(fixtures) do
        local c = f.competition_object_id
        if not by_comp[c] then by_comp[c] = { n = 0, done = 0, last = nil } comp_order[#comp_order + 1] = c end
        local e = by_comp[c]
        e.n = e.n + 1
        if f.completed then e.done = e.done + 1 end
        if not e.last or f.date_raw >= e.last.date_raw then e.last = f end
    end
    table.sort(comp_order, function(a, b) return cname(a) < cname(b) end)
    w("fixtures in the list: " .. #fixtures .. ", competitions: " .. #comp_order)
    for _, c in ipairs(comp_order) do
        local e = by_comp[c]
        local f = e.last
        local home = standings[f.home_standing_index]
        local away = standings[f.away_standing_index]
        local hn = home and tname(home.team_id) or "(not decided yet)"
        local an = away and tname(away.team_id) or "(not decided yet)"
        local score = f.completed and string.format("%d-%d", f.home_score, f.away_score) or "not played"
        local hp, ap = shootout(f.home_penalties), shootout(f.away_penalties)
        if f.completed and hp and ap and (hp > 0 or ap > 0) then
            score = score .. string.format(" (pens %d-%d)", hp, ap)
        end
        w(string.format("  %-30s %3d played of %3d  last: %s  %s v %s  %s", cname(c) .. " [" .. c .. "]", e.done, e.n,
            decode_date(f.date_raw) or tostring(f.date_raw), hn, an, score))
    end
end)

-- ---------------------------------------------------------------------------------------------------------
-- Every table: the known ones first, then every other name. Each table is its own step.
-- ---------------------------------------------------------------------------------------------------------
section("Every table the game answers to", function()
    local wanted, order = {}, {}
    local function want(name)
        name = tostring(name):lower()
        if name ~= "" and not wanted[name] then
            wanted[name] = true
            order[#order + 1] = name
        end
    end
    for _, name in ipairs(KNOWN) do want(name) end
    for _, word in ipairs(WORDS) do
        want(word)
        if not word:find("^career_") then want("career_" .. word) end
    end
    for _, name in ipairs(EXTRA_NAMES) do want(name) end
    w("names asked: " .. #order)

    local found, skipped = {}, {}
    for i, name in ipairs(order) do
        local ok, info = step("table " .. name, function()
            local tbl = get_table(name)
            if not tbl then return nil end
            return { rows = count_records(tbl, 200000) }
        end)
        if not ok and skip["table " .. name] then
            skipped[#skipped + 1] = name
        elseif ok and info then
            info.columns = {}
            if info.rows > 0 and info.rows <= BIG_TABLE then
                local ok2, rows = step("columns " .. name, function() return rows_of(name) end)
                if ok2 and rows and rows[1] then info.columns = sorted_keys(plain_row(rows[1])) end
            end
            existing[name] = info
            found[#found + 1] = name
            csv_lines[#csv_lines + 1] = csv_cell(name) .. "," .. info.rows .. "," .. csv_cell(table.concat(info.columns, " "))
        end
        if i % 40 == 0 then flush() end
    end
    table.sort(found)
    w("tables that exist: " .. #found)
    for _, name in ipairs(found) do
        local e = existing[name]
        local cols = #e.columns > 0 and table.concat(e.columns, ", ")
            or (e.rows > BIG_TABLE and "(not loaded: more than " .. BIG_TABLE .. " rows)") or (e.rows == 0 and "(empty)" or "(columns not readable)")
        w(string.format("  %-40s rows=%-7d %s", name, e.rows, cols))
    end
    if #skipped > 0 then w("not asked, because asking stopped the game before: " .. table.concat(skipped, ", ")) end
end)

section("A few rows of each table that is not huge (career tables: rows mentioning your club first)", function()
    local names = {}
    for name, e in pairs(existing) do
        if e.rows > 0 and e.rows <= BIG_TABLE then names[#names + 1] = name end
    end
    table.sort(names)
    for _, name in ipairs(names) do
        local ok, rows = step("rows " .. name, function() return rows_of(name) end)
        if ok and rows and #rows > 0 then
            w("")
            w("-- " .. name .. " (" .. #rows .. " rows)")
            local picked, used = {}, {}
            if name:find("^career_") and managed_tid > 0 then
                for i, r in ipairs(rows) do
                    if #picked >= SAMPLE_ROWS then break end
                    local row = plain_row(r)
                    if mentions_team(row, managed_tid) then picked[#picked + 1] = { i = i, row = row } used[i] = true end
                end
            end
            for i, r in ipairs(rows) do
                if #picked >= SAMPLE_ROWS * 2 or i > SAMPLE_ROWS then break end
                if not used[i] then picked[#picked + 1] = { i = i, row = plain_row(r) } end
            end
            for _, item in ipairs(picked) do
                w(string.format("  #%d  %s", item.i, describe_row(item.row, 60)))
                local note = annotate(item.row)
                if note ~= "" then w("        " .. note) end
            end
        end
    end
end)

local done = "FC26 Table Census finished. Report written to: " .. OUT_PATH .. " and " .. CSV_PATH
w("")
w("=== end of report ===")
flush()
append_line(PROGRESS_PATH, "finished")
print("[FC26 Census] " .. done)
