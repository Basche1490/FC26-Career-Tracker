-- FC26 Probe v1.0.0 - read-only diagnostic for FC26 Career Tracker.
--
-- Writes Documents\FC26 Export\FC26_probe_<date>_<time>.txt describing what the game really stores about
-- transfers, loans, joining dates, manager history and club finances. The capture scripts were written against
-- guessed column names; this file lets them be fixed against the real ones.
--
-- It only reads. Nothing in the game is changed. Run it with a career loaded (FC26_Auto_Capture.lua may be
-- running at the same time), wait for "Probe finished" in the Live Editor console, then send the .txt file.
-- Live Editor prints a lot of "no field" errors while it runs. That is expected: the probe tries several
-- possible column names and the game answers "no field" for the ones that do not exist.

local MEMORY = require "imports/core/memory"
require "imports/other/helpers"
require "imports/services/enums"
require "imports/career_mode/helpers"

assert(IsInCM(), "FC26 Probe must be executed while a career is loaded")

local VERSION = "1.0.0"

local user_profile = os.getenv("USERPROFILE") or ""
local ROOT = (user_profile ~= "" and user_profile or ".") .. "\\Documents\\FC26 Export"
pcall(os.execute, 'if not exist "' .. ROOT .. '" mkdir "' .. ROOT .. '"')
-- FC26_PROBE_OUT_PATH only exists so the automated tests can choose where the report goes
local OUT_PATH = rawget(_G, "FC26_PROBE_OUT_PATH") or (ROOT .. "\\FC26_probe_" .. os.date("%Y%m%d_%H%M%S") .. ".txt")

local lines = {}

local function flush()
    local f = io.open(OUT_PATH, "wb")
    if f then
        f:write(table.concat(lines, "\r\n"))
        f:write("\r\n")
        f:close()
    end
end

local function w(text)
    lines[#lines + 1] = tostring(text)
end

local function section(title, fn)
    w("")
    w("=== " .. title .. " ===")
    pcall(print, "[FC26 Probe] " .. title)
    local ok, err = pcall(fn)
    if not ok then w("SECTION FAILED: " .. tostring(err)) end
    flush()
end

-- ---------------------------------------------------------------------------------------------------------
-- Small helpers
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
    if ok and type(rows) == "table" and #rows > 0 then return rows end
    return nil
end

local function sorted_keys(row)
    local keys = {}
    for k in pairs(row) do keys[#keys + 1] = tostring(k) end
    table.sort(keys)
    return keys
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

-- Reads a whole table into plain { fieldname = value } rows. Uses GetDBTableRows when the game offers it
-- (that also reveals the real column names); otherwise it reads the candidate columns through Live Editor.
local function load_rows(name, candidates, cap)
    cap = cap or 60000
    local api = api_rows(name)
    if api then
        local rows = {}
        for i, r in ipairs(api) do
            if i > cap then break end
            local row = {}
            for k, v in pairs(r) do row[tostring(k):lower()] = raw(v) end
            rows[#rows + 1] = row
        end
        return rows, "GetDBTableRows (column names are the real ones)"
    end

    local tbl = get_table(name)
    if not tbl then return nil, "table not available" end
    local ok, first = pcall(function() return tbl:GetFirstRecord() end)
    if not ok or not first or first <= 0 then return {}, "table is empty" end

    local present = {}
    for _, f in ipairs(candidates or {}) do
        local ok2, v = pcall(function() return tbl:GetRecordFieldValue(first, f) end)
        if ok2 and v ~= nil then present[#present + 1] = f end
    end
    local rows = {}
    local rec = first
    while rec and rec > 0 and #rows < cap do
        local row = {}
        for _, f in ipairs(present) do
            local ok3, v = pcall(function() return tbl:GetRecordFieldValue(rec, f) end)
            if ok3 then row[f] = v end
        end
        rows[#rows + 1] = row
        local ok4, nxt = pcall(function() return tbl:GetNextValidRecord() end)
        if not ok4 then break end
        rec = nxt
    end
    return rows, "Live Editor records; only these candidate columns exist: " .. table.concat(present, ", ")
end

-- For columns that only hold a handful of different values (types, flags...) list the values and how often.
local function distinct_summary(rows)
    local counts, order = {}, {}
    for _, row in ipairs(rows) do
        for k, v in pairs(row) do
            local c = counts[k]
            if not c then c = { n = 0, values = {}, list = {}, many = false } counts[k] = c order[#order + 1] = k end
            if not c.many then
                local s = tostring(v)
                if c.values[s] == nil then
                    c.values[s] = 0
                    c.list[#c.list + 1] = s
                    if #c.list > 12 then c.many = true end
                end
                c.values[s] = (c.values[s] or 0) + 1
            end
        end
    end
    table.sort(order)
    for _, k in ipairs(order) do
        local c = counts[k]
        if c.many then
            w("  " .. k .. ": more than 12 different values")
        else
            local parts = {}
            for _, s in ipairs(c.list) do parts[#parts + 1] = s .. " (x" .. tostring(c.values[s]) .. ")" end
            w("  " .. k .. ": " .. table.concat(parts, ", "))
        end
    end
end

local function dump_rows(rows, managed_tid, only_managed, max_rows)
    local picked = {}
    for i, row in ipairs(rows) do
        if (not only_managed) or mentions_team(row, managed_tid) then picked[#picked + 1] = { i = i, row = row } end
    end
    w("  rows" .. (only_managed and " mentioning the managed team" or "") .. ": " .. #picked)
    local from = math.max(1, #picked - (max_rows or 150) + 1)
    if from > 1 then w("  (showing the last " .. tostring(#picked - from + 1) .. ")") end
    for n = from, #picked do
        local item = picked[n]
        w(string.format("  #%d  %s", item.i, describe_row(item.row, 60)))
        local note = annotate(item.row)
        if note ~= "" then w("        " .. note) end
    end
end

-- ---------------------------------------------------------------------------------------------------------
-- Candidate column / table names (names that do not exist are simply reported as missing)
-- ---------------------------------------------------------------------------------------------------------
local TH_CANDIDATES = {
    "playerid", "fromteamid", "toteamid", "teamidfrom", "teamidto", "sourceteamid", "destteamid", "targetteamid",
    "sellerteamid", "buyerteamid", "oldteamid", "newteamid", "previousteamid", "currentteamid", "teamid",
    "transferfee", "fee", "amount", "value", "price", "transferdate", "date", "day", "year", "season",
    "transfertype", "type", "isloan", "loan", "isfree", "contractlength", "wage", "playerwage", "isusertransfer",
    "userid", "transferid", "dealtype", "bonus", "sellonpercent", "buybackclause"
}
local LOAN_CANDIDATES = {
    "playerid", "teamidloanedfrom", "teamidloanedto", "loandateend", "loandatestart", "isloantobuy", "loanbuyfee",
    "loanfee", "buyoutfee", "wagecontribution", "wagepercent", "teamid"
}
local MH_CANDIDATES = {
    "teamid", "userid", "season", "games_played", "wins", "draws", "losses", "leagueid", "position",
    "bigbuyplayername", "bigbuyamount", "bigbuyplayerid", "bigsellplayername", "bigsellamount", "bigsellplayerid",
    "bigsaleplayername", "bigsaleamount", "bigsaleplayerid", "biggestbuy", "biggestsale",
    "transferspending", "transferincome", "goalsfor", "goalsagainst", "trophies"
}
local TEAM_CANDIDATES = {
    "teamid", "teamname", "transferbudget", "wagebudget", "balance", "initialbudget", "currentbudget", "budget",
    "clubworth", "profitability", "domesticprestige", "internationalprestige", "overallrating",
    "teamstadiumcapacity", "wagebill", "totalwages", "income", "expenses", "cashflow", "weeklybudget", "leagueid"
}
local TABLE_SCAN = {
    "career_finances", "career_finance", "career_budget", "career_transferbudget", "career_clubfinances",
    "career_teamfinances", "career_wagebudget", "career_financialsummary", "career_incomes", "career_expenses",
    "career_transferoffers", "career_transfers", "career_offers", "career_scouting", "career_scoutreports",
    "career_scouts", "career_boardobjectives", "career_objectives", "career_seasonobjectives",
    "career_clubobjectives", "career_news", "career_newsfeed", "career_playercontract", "career_playerinfo",
    "career_calendar", "career_competitionprogress", "career_youthplayers", "career_unexpectedevents",
    "career_pressconference", "career_playerstatus", "career_squadrole", "career_playermorale",
    "career_playerform", "career_staff", "career_coaches", "career_boardmessages", "career_transferdeals",
    "career_activetransfers", "career_incomingoffers", "career_outgoingoffers", "career_shortlist",
    "career_playeragents", "career_loanoffers"
}

-- ---------------------------------------------------------------------------------------------------------
-- Report
-- ---------------------------------------------------------------------------------------------------------
local managed_tid = 0

section("Environment", function()
    w("FC26 Probe " .. VERSION .. " - " .. os.date("%Y-%m-%d %H:%M:%S"))
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

    -- Does Live Editor offer a way to list every table / column?
    for _, m in ipairs({ "GetTableNames", "GetTables", "GetAllTables", "GetAllTableNames" }) do
        local ok2, res = pcall(function() return LE.db[m](LE.db) end)
        if ok2 and type(res) == "table" then
            local names = {}
            for k, v in pairs(res) do names[#names + 1] = tostring(type(k) == "number" and v or k) end
            table.sort(names)
            w("LE.db:" .. m .. "() returned " .. #names .. " names: " .. table.concat(names, ", "))
        end
    end
end)

section("career_transferhistory (does the game record your signings and sales, and with which columns?)", function()
    local rows, how = load_rows("career_transferhistory", TH_CANDIDATES)
    if not rows then w("table not available: " .. tostring(how)) return end
    w("source: " .. how)
    w("total rows: " .. #rows)
    if #rows == 0 then return end
    w("columns of the first row: " .. table.concat(sorted_keys(rows[1]), ", "))
    w("values per column (columns with at most 12 different values):")
    distinct_summary(rows)
    w("last 5 rows of the table:")
    for n = math.max(1, #rows - 4), #rows do
        w(string.format("  #%d  %s", n, describe_row(rows[n], 60)))
        local note = annotate(rows[n])
        if note ~= "" then w("        " .. note) end
    end
    dump_rows(rows, managed_tid, true, 150)
end)

section("playerloans (every active loan) and the clubs each loaned player is linked to", function()
    local rows, how = load_rows("playerloans", LOAN_CANDIDATES, 2000)
    if not rows then w("table not available: " .. tostring(how)) return end
    w("source: " .. how)
    w("total rows: " .. #rows)
    local wanted = {}
    for _, row in ipairs(rows) do
        local pid = tonumber(row.playerid) or 0
        if pid > 0 then wanted[pid] = true end
    end
    dump_rows(rows, managed_tid, false, 300)

    local links = {}
    local tpl = get_table("teamplayerlinks")
    if tpl then
        local rec = tpl:GetFirstRecord()
        while rec and rec > 0 do
            local pid = tonumber(tpl:GetRecordFieldValue(rec, "playerid")) or 0
            if wanted[pid] then
                local list = links[pid]
                if not list then list = {} links[pid] = list end
                list[#list + 1] = tonumber(tpl:GetRecordFieldValue(rec, "teamid")) or 0
            end
            rec = tpl:GetNextValidRecord()
        end
    end
    w("teamplayerlinks of the loaned players (a player linked to two clubs shows both):")
    local pids = {}
    for pid in pairs(wanted) do pids[#pids + 1] = pid end
    table.sort(pids)
    for _, pid in ipairs(pids) do
        local parts = {}
        for _, tid in ipairs(links[pid] or {}) do parts[#parts + 1] = tostring(tid) .. " " .. tname(tid) end
        w(string.format("  %d %s: %s", pid, pname(pid), #parts > 0 and table.concat(parts, " | ") or "no links found"))
    end
end)

section("Your squad: link, joining date and academy flag (most recent joiners first)", function()
    if managed_tid == 0 then w("managed team unknown") return end
    local squad = {}
    local tpl = get_table("teamplayerlinks")
    if tpl then
        local rec = tpl:GetFirstRecord()
        while rec and rec > 0 do
            if (tonumber(tpl:GetRecordFieldValue(rec, "teamid")) or 0) == managed_tid then
                local pid = tonumber(tpl:GetRecordFieldValue(rec, "playerid")) or 0
                if pid > 0 then squad[pid] = true end
            end
            rec = tpl:GetNextValidRecord()
        end
    end
    local info = {}
    local players = get_table("players")
    if players then
        local rec = players:GetFirstRecord()
        while rec and rec > 0 do
            local pid = tonumber(players:GetRecordFieldValue(rec, "playerid")) or 0
            if squad[pid] then
                info[pid] = {
                    join = tonumber(players:GetRecordFieldValue(rec, "playerjointeamdate")) or 0,
                    academy = players:GetRecordFieldValue(rec, "isacademy"),
                    ovr = players:GetRecordFieldValue(rec, "overallrating"),
                    pot = players:GetRecordFieldValue(rec, "potential")
                }
            end
            rec = players:GetNextValidRecord()
        end
    end
    local list = {}
    for pid in pairs(squad) do list[#list + 1] = pid end
    table.sort(list, function(a, b)
        local ja, jb = (info[a] and info[a].join or 0), (info[b] and info[b].join or 0)
        if ja ~= jb then return ja > jb end
        return a < b
    end)
    w("players linked to the managed team: " .. #list)
    for _, pid in ipairs(list) do
        local i = info[pid] or {}
        w(string.format("  %d %-26s joined=%s (%s) academy=%s ovr=%s pot=%s",
            pid, pname(pid), tostring(i.join), decode_date(i.join) or "unknown", tostring(i.academy), tostring(i.ovr), tostring(i.pot)))
    end
end)

section("career_managerhistory rows of the managed team (the old script read big signings from here)", function()
    local rows, how = load_rows("career_managerhistory", MH_CANDIDATES, 5000)
    if not rows then w("table not available: " .. tostring(how)) return end
    w("source: " .. how)
    w("total rows: " .. #rows)
    if #rows > 0 then w("columns of the first row: " .. table.concat(sorted_keys(rows[1]), ", ")) end
    dump_rows(rows, managed_tid, true, 20)
end)

section("teams row of the managed team (budget and finance columns)", function()
    if managed_tid == 0 then w("managed team unknown") return end
    local tbl = get_table("teams")
    if not tbl then w("table not available") return end
    local rec = tbl:GetFirstRecord()
    while rec and rec > 0 do
        if (tonumber(tbl:GetRecordFieldValue(rec, "teamid")) or 0) == managed_tid then
            local row = {}
            for _, f in ipairs(TEAM_CANDIDATES) do
                local ok, v = pcall(function() return tbl:GetRecordFieldValue(rec, f) end)
                if ok and v ~= nil then row[f] = v end
            end
            w("columns that exist: " .. describe_row(row, nil))
            w("Compare with the in-game finance screen (current budget, weekly budget) to find the matching column.")
            return
        end
        rec = tbl:GetNextValidRecord()
    end
    w("managed team row not found")
end)

section("Which other career tables exist? (name, rows and columns where the game allows it)", function()
    for _, name in ipairs(TABLE_SCAN) do
        local tbl = get_table(name)
        if not tbl then
            w(name .. ": not available")
        else
            local rows = api_rows(name)
            if rows then
                w(name .. ": " .. #rows .. " rows; columns: " .. table.concat(sorted_keys(rows[1]), ", "))
            else
                local ok, first = pcall(function() return tbl:GetFirstRecord() end)
                w(name .. ": exists" .. ((ok and first and first > 0) and " (has rows)" or " (empty)"))
            end
        end
    end
end)

local done = "FC26 Probe finished. Report written to: " .. OUT_PATH
w("")
w("=== end of report ===")
flush()
print("[FC26 Probe] " .. done)
