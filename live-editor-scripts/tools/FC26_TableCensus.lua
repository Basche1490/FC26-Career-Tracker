-- FC26 Table Census v1.0.0 - read-only inventory of the game data FC26 Career Tracker could use.
--
-- Writes two files to Documents\FC26 Export:
--   FC26_census_<date>_<time>.txt         the report: which tables exist, their columns and a few rows, plus a
--                                          season-end check (league champions, cup finals, awards, cup runs)
--   FC26_census_<date>_<time>_tables.csv  one line per table that exists: name, rows, columns
--
-- Live Editor has no "list every table" command, so the census asks for every table name the game is known or likely
-- to use (several hundred), plus any table name it finds in Live Editor's own Lua files. A name that does not exist
-- simply comes back empty.
--
-- It only reads. Nothing in the game is changed. Run it with a career loaded, wait for "Census finished" in the Live
-- Editor console (about a minute), then send both files. Live Editor may print "no table" or "no field" messages while
-- it runs; that is expected.
--
-- Best run twice: once now, and once in June of an in-game season (after the cup finals, before 1 July), because
-- the game replaces the fixture list, and with it every cup final, when the new season starts.

local MEMORY = require "imports/core/memory"
require "imports/other/helpers"
require "imports/services/enums"
require "imports/career_mode/helpers"

assert(IsInCM(), "FC26 Table Census must be executed while a career is loaded")

local VERSION = "1.0.0"

local user_profile = os.getenv("USERPROFILE") or ""
local ROOT = (user_profile ~= "" and user_profile or ".") .. "\\Documents\\FC26 Export"
pcall(os.execute, 'if not exist "' .. ROOT .. '" mkdir "' .. ROOT .. '"')
-- FC26_CENSUS_OUT_PATH only exists so the automated tests can choose where the report goes
local OUT_PATH = rawget(_G, "FC26_CENSUS_OUT_PATH") or (ROOT .. "\\FC26_census_" .. os.date("%Y%m%d_%H%M%S") .. ".txt")
local CSV_PATH = OUT_PATH:gsub("%.txt$", "") .. "_tables.csv"

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

local function section(title, fn)
    w("")
    w("=== " .. title .. " ===")
    pcall(print, "[FC26 Census] " .. title)
    local ok, err = pcall(fn)
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

-- Number of records, counted with Live Editor's record functions (works for every table, however big).
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

local function csv_cell(s)
    s = tostring(s or "")
    if s:find('[,"\r\n]') then s = '"' .. s:gsub('"', '""') .. '"' end
    return s
end

-- ---------------------------------------------------------------------------------------------------------
-- Names to ask for
-- ---------------------------------------------------------------------------------------------------------
-- Words that game tables are, or might be, named after. Each is tried as it is and with "career_" in front.
local WORDS = {
    -- known from the scripts and the probe
    "players", "teams", "leagues", "leagueteamlinks", "teamplayerlinks", "playerloans", "fixtures", "users", "calendar",
    "playercontract", "presignedcontract", "youthplayers", "youthplayerattributes", "regenplayerattributes",
    "playergrowthuserseason", "playerlastmatchhistory", "playermatchratinghistory", "playerawards", "competitionprogress",
    "managerhistory", "managerinfo", "managerpref", "scouts", "scoutmission", "scoutmissions", "youthscouts", "squadranking",
    -- squad database
    "playernames", "dcplayernames", "editedplayernames", "teamkits", "teamstadiumlinks", "stadiums", "stadiumassignments",
    "formations", "teamsheets", "default_teamsheets", "defaultteamsheets", "defaultteamdata", "manager", "managers",
    "referee", "referees", "leaguerefereelinks", "nations", "nationalteams", "rowteamnationlinks", "teamnationlinks",
    "teamballs", "playerboots", "boots", "balls", "kits", "rivals", "teamrivals", "rivalries", "derbies", "previousteam",
    "previousteams", "playerpositionzones", "playerformdiff", "teamformdiff", "competition", "competitions", "compobj",
    "compobjs", "competitionteamlinks", "compteamlinks", "competitiontrophies", "trophies", "trophy", "version",
    "audionation", "smallsidedteams", "customformations", "mentalities", "defaultmentalities", "teamtactics",
    "playstyles", "playerplaystyles", "traits", "playertraits", "roles", "playerroles",
    -- results, tables and history: what an "Around the World" page needs
    "standings", "leaguestandings", "competitionstandings", "table", "tables", "leaguetable", "leaguetables",
    "matches", "results", "matchresults", "matchhistory", "matchstats", "matchreports", "schedule", "schedules",
    "history", "leaguehistory", "competitionhistory", "cuphistory", "seasonhistory", "teamhistory", "clubhistory",
    "pastwinners", "winners", "competitionwinners", "leaguewinners", "cupwinners", "champions", "titles",
    "trophycabinet", "teamtrophies", "clubtrophies", "playertrophies", "trophyhistory", "honours", "honors",
    "cupprogress", "cupdraws", "draws", "knockout", "brackets", "groups", "groupstandings",
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
    "unexpectedevents", "events", "staff", "coaches", "playerperformancebonuses", "bonuses", "settings", "options",
    "save", "savegame", "globals", "user", "playasplayer", "createaclub", "createdclub", "customteams",
}

local EXTRA_NAMES = {
    -- odd spellings seen in older EA databases
    "fifaGameSettings", "career_competitions", "career_fixtures", "career_standings", "career_leaguestandings",
    "career_trophywinners", "career_competitionwinners", "career_seasonsummary", "career_seasonreview",
    "career_endofseason", "career_awardwinners", "career_playerseasonstats", "career_playercareerstats",
}

-- Table names that Live Editor's own Lua files mention (lua\imports, lua\scripts ...), read without starting anything.
local function names_from_live_editor_files(found)
    local list_dirs = rawget(_G, "getDirectoryList")
    local list_files = rawget(_G, "getFileList")
    if type(list_dirs) ~= "function" or type(list_files) ~= "function" then
        return 0, "Live Editor has no getDirectoryList / getFileList: its files were not read"
    end
    local function base(entry) return (tostring(entry or ""):gsub("[/\\]+$", ""):match("([^/\\]+)$")) end
    local files_read = 0
    local function scan(folder, depth)
        if depth > 5 or files_read >= 600 then return end
        local okf, files = pcall(list_files, folder, "*.lua")
        if okf and type(files) == "table" then
            for _, entry in ipairs(files) do
                local name = base(entry)
                if name and name:lower():match("%.lua$") and files_read < 600 then
                    local f = io.open(folder .. "\\" .. name, "rb")
                    if f then
                        local text = f:read("a") or ""
                        f:close()
                        files_read = files_read + 1
                        for t in text:gmatch('GetTable%(%s*["\']([%w_]+)["\']') do found[t:lower()] = true end
                        for t in text:gmatch('GetDBTableRows%(%s*["\']([%w_]+)["\']') do found[t:lower()] = true end
                        for t in text:gmatch('["\'](career_[%w_]+)["\']') do found[t:lower()] = true end
                    end
                end
            end
        end
        local okd, dirs = pcall(list_dirs, folder)
        if okd and type(dirs) == "table" then
            for _, entry in ipairs(dirs) do
                local name = base(entry)
                if name and name ~= "." and name ~= ".." then scan(folder .. "\\" .. name, depth + 1) end
            end
        end
    end
    scan("lua", 0)
    return files_read, nil
end

-- ---------------------------------------------------------------------------------------------------------
-- Report
-- ---------------------------------------------------------------------------------------------------------
local managed_tid = 0
local existing = {}          -- name -> { rows = n, columns = {...} }

section("Environment", function()
    w("FC26 Table Census " .. VERSION .. " - " .. os.date("%Y-%m-%d %H:%M:%S"))
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
end)

section("What Live Editor offers to scripts (function names only)", function()
    local hints = { "table", "db", "fixture", "comp", "league", "award", "troph", "standing", "season", "injur",
                    "stat", "history", "winner", "result", "match", "cup", "title", "news", "budget", "finance" }
    local names = {}
    local standard = { getmetatable = true, setmetatable = true }
    for k, v in pairs(_G) do
        if type(v) == "function" and not standard[k] then
            local low = tostring(k):lower()
            for _, h in ipairs(hints) do
                if low:find(h, 1, true) then names[#names + 1] = tostring(k) break end
            end
        end
    end
    table.sort(names)
    w("global functions about tables, competitions, results, awards, stats (" .. #names .. "):")
    for i = 1, #names, 8 do w("  " .. table.concat(names, ", ", i, math.min(i + 7, #names))) end

    local function methods(obj)
        local out, seen = {}, {}
        local function take(t)
            if type(t) ~= "table" then return end
            for k, v in pairs(t) do
                if type(v) == "function" and not seen[k] then seen[k] = true out[#out + 1] = tostring(k) end
            end
        end
        if type(obj) == "table" then take(obj) end
        local ok, mt = pcall(getmetatable, obj)
        if ok and type(mt) == "table" then
            take(mt)
            if type(mt.__index) == "table" then take(mt.__index) end
        end
        table.sort(out)
        return out
    end
    if type(LE) == "table" and LE.db then
        local m = methods(LE.db)
        w("LE.db methods (" .. #m .. "): " .. table.concat(m, ", "))
        local probe = get_table("leagues") or get_table("players")
        if probe then
            local tm = methods(probe)
            w("methods of a table object (" .. #tm .. "): " .. table.concat(tm, ", "))
        end
        for _, mname in ipairs({ "GetTableNames", "GetTables", "GetAllTables", "GetAllTableNames", "GetTablesNames", "ListTables" }) do
            local ok2, res = pcall(function() return LE.db[mname](LE.db) end)
            if ok2 and type(res) == "table" then
                local got = {}
                for k, v in pairs(res) do got[#got + 1] = tostring(type(k) == "number" and v or k) end
                table.sort(got)
                w("LE.db:" .. mname .. "() returned " .. #got .. " names: " .. table.concat(got, ", "))
            end
        end
    end
end)

section("Every table the game answers to", function()
    local wanted, order = {}, {}
    local function want(name, why)
        name = tostring(name):lower()
        if name ~= "" and not wanted[name] then
            wanted[name] = why
            order[#order + 1] = name
        end
    end
    for _, word in ipairs(WORDS) do
        want(word, "list")
        if not word:find("^career_") then want("career_" .. word, "list") end
    end
    for _, name in ipairs(EXTRA_NAMES) do want(name, "list") end
    local from_files = {}
    local files_read, why_not = names_from_live_editor_files(from_files)
    local n_from_files = 0
    for name in pairs(from_files) do
        if not wanted[name] then n_from_files = n_from_files + 1 end
        want(name, "Live Editor files")
    end
    w(string.format("names asked: %d (%d of them found only in Live Editor's files; %s)", #order, n_from_files,
        why_not or (files_read .. " Lua files read")))

    local found = {}
    for _, name in ipairs(order) do
        local tbl = get_table(name)
        if tbl then
            local n = count_records(tbl, 200000)
            local cols = {}
            if n > 0 and n <= BIG_TABLE then
                local rows = api_rows(name)
                if rows and rows[1] then cols = sorted_keys(plain_row(rows[1])) end
            end
            existing[name] = { rows = n, columns = cols, source = wanted[name] }
            found[#found + 1] = name
            csv_lines[#csv_lines + 1] = csv_cell(name) .. "," .. n .. "," .. csv_cell(table.concat(cols, " "))
        end
    end
    table.sort(found)
    w("tables that exist: " .. #found)
    for _, name in ipairs(found) do
        local e = existing[name]
        local cols = #e.columns > 0 and table.concat(e.columns, ", ")
            or (e.rows > BIG_TABLE and "(not loaded: more than " .. BIG_TABLE .. " rows)") or (e.rows == 0 and "(empty)" or "(columns not readable)")
        w(string.format("  %-40s rows=%-7d %s%s", name, e.rows, e.source == "Live Editor files" and "[from Live Editor files] " or "", cols))
    end
end)

section("A few rows of each table that is not huge (career tables: rows mentioning your club first)", function()
    local names = {}
    for name, e in pairs(existing) do
        if e.rows > 0 and e.rows <= BIG_TABLE then names[#names + 1] = name end
    end
    table.sort(names)
    for _, name in ipairs(names) do
        local rows = api_rows(name)
        if rows and #rows > 0 then
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

-- ---------------------------------------------------------------------------------------------------------
-- Season-end check: what an "Around the World" page and the season summary would be built from
-- ---------------------------------------------------------------------------------------------------------
local TOP_LEAGUES = { { 13, "Premier League" }, { 53, "LALIGA EA SPORTS" }, { 31, "Serie A" }, { 19, "Bundesliga" }, { 16, "Ligue 1" } }

section("Season-end check 1: league tables and champions (leagueteamlinks)", function()
    local rows = api_rows("leagueteamlinks")
    local list = {}
    if rows then
        for _, r in ipairs(rows) do list[#list + 1] = plain_row(r) end
    else
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
        local team_id = MEMORY:ReadInt(current + 0x04)
        if MEMORY:ReadBool(current + 0x16) and team_id > 0 then
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

section("Season-end check 2: the last match of every competition in the fixture list (finals)", function()
    local ok, fixtures = pcall(read_valid_fixtures)
    if not ok or type(fixtures) ~= "table" then w("fixture list not readable: " .. tostring(fixtures)) return end
    local ok2, standings = pcall(read_valid_standings)
    if not ok2 then standings = {} end
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
        if f.completed and (f.home_penalties > 0 or f.away_penalties > 0) then
            score = score .. string.format(" (pens %d-%d)", f.home_penalties, f.away_penalties)
        end
        w(string.format("  %-30s %3d played of %3d  last: %s  %s v %s  %s", cname(c) .. " [" .. c .. "]", e.done, e.n,
            decode_date(f.date_raw) or tostring(f.date_raw), hn, an, score))
    end
end)

section("Season-end check 3: awards and cup runs", function()
    for _, name in ipairs({ "career_playerawards", "career_competitionprogress" }) do
        local rows = api_rows(name)
        if rows then
            w(name .. ": " .. #rows .. " rows")
            for i = 1, math.min(#rows, 60) do
                local row = plain_row(rows[i])
                w(string.format("  #%d  %s", i, describe_row(row, 30)))
                local note = annotate(row)
                if note ~= "" then w("        " .. note) end
            end
        else
            w(name .. ": not readable with GetDBTableRows")
        end
    end
end)

local done = "FC26 Table Census finished. Report written to: " .. OUT_PATH .. " and " .. CSV_PATH
w("")
w("=== end of report ===")
flush()
print("[FC26 Census] " .. done)
