-- FC26 AutoCapture v2.4.0 (Precision Live Telemetry & Atlas Tracker)
-- Complete support for EFL Championship, Premier League, FA Cup, Carabao Cup, Play-offs.
-- Gathers REAL in-game data: Real League Position, Real Lineups, Real Goal Scorers, Real MOTM.
-- Strict League Rules: League matches NEVER have extra time or penalty shootouts.
-- Accurate Matchday XX calculation for all league matches; dedicated tournament rounds for cups.
-- Safe session lifecycle: No phantom beaming on startup; immediate beam on actual match completion.

local MEMORY = require "imports/core/memory"
require "imports/other/helpers"
require "imports/services/enums"
require "imports/career_mode/helpers"

assert(IsInCM(), "FC26 AutoCapture must be executed while a career is loaded")

local VERSION = "2.4.0"
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

pcall(function()
    os.execute('if not exist "' .. win_path(COMMUNITY_ROOT) .. '" mkdir "' .. win_path(COMMUNITY_ROOT) .. '"')
    os.execute('if not exist "' .. win_path(EXPORT_ROOT) .. '" mkdir "' .. win_path(EXPORT_ROOT) .. '"')
    os.execute('if not exist "' .. win_path(MATCH_STATS_ROOT) .. '" mkdir "' .. win_path(MATCH_STATS_ROOT) .. '"')
    os.execute('if not exist "' .. win_path(OUTBOX_DIR) .. '" mkdir "' .. win_path(OUTBOX_DIR) .. '"')
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
    local c = tostring(comp_name or ""):lower()
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
-- Each payload is written to Documents\FC26 Export\outbox and a small .bat posts
-- it to the bridge on this PC. The .bat only deletes the file after the bridge
-- answers HTTP 200, and writes the HTTP code to bridge_send_log.txt. Anything
-- still in the outbox (bridge was closed, firewall hiccup...) is retried every
-- 30 s and picked up again the next time the script starts.
-- ---------------------------------------------------------------------------
local SEND_LOG = COMMUNITY_ROOT .. "/bridge_send_log.txt"
local OUTBOX_RETRY_SECONDS = 30
local OUTBOX_MAX_ATTEMPTS = 40
local OUTBOX_MAX_AGE_SECONDS = 7 * 24 * 3600

local SENDER_BAT = [[
@echo off
set "J=@@JSON@@"
set "L=@@LOG@@"
set "ID=@@ID@@"
set "CODE=000"
call :try http://127.0.0.1:5050
if not "%CODE%"=="200" ping -n 2 127.0.0.1 >nul
if not "%CODE%"=="200" call :try http://127.0.0.1:5050
if not "%CODE%"=="200" call :try http://127.0.0.1:8080
if not "%CODE%"=="200" call :psfallback
>>"%L%" echo %date% %time% [%ID%] HTTP %CODE%
if "%CODE%"=="200" del "%J%" >nul 2>&1
(goto) 2>nul & del "%~f0" >nul 2>&1 & exit
:try
for /f "usebackq delims=" %%C in (`curl.exe -s -o NUL -w "%%{http_code}" -m 6 -X POST -H "Content-Type: application/json; charset=utf-8" --data-binary @"%J%" %1 2^>NUL`) do set "CODE=%%C"
exit /b
:psfallback
for /f "usebackq delims=" %%C in (`powershell -NoProfile -Command "try { (Invoke-WebRequest -UseBasicParsing -Uri http://127.0.0.1:5050 -Method Post -ContentType 'application/json; charset=utf-8' -InFile '%J%' -TimeoutSec 6).StatusCode } catch { 0 }" 2^>NUL`) do set "CODE=%%C"
exit /b
]]

local outbox_pending = {} -- json path -> { id = , tries = , last_try = }

local function launch_outbox_sender(json_path, id)
    local temp_dir = os.getenv("TEMP") or os.getenv("TMP") or "."
    local bat_path = temp_dir .. "/fc26_send_" .. id .. ".bat"
    local body = SENDER_BAT
    body = body:gsub("@@JSON@@", function() return win_path(json_path) end)
    body = body:gsub("@@LOG@@", function() return win_path(SEND_LOG) end)
    body = body:gsub("@@ID@@", function() return id end)
    local bf = io.open(bat_path, "w")
    if not bf then return false end
    bf:write(body)
    bf:close()
    os.execute('start /min "" cmd /c "' .. win_path(bat_path) .. '"')
    return true
end

local function track_outbox_file(json_path, id)
    outbox_pending[json_path] = outbox_pending[json_path] or { id = id, tries = 0, last_try = 0 }
    local entry = outbox_pending[json_path]
    entry.tries = entry.tries + 1
    entry.last_try = os.time()
    return launch_outbox_sender(json_path, id)
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

        local id = tostring(os.time()) .. "_" .. tostring(math.random(1000, 9999))
        local json_path = OUTBOX_DIR .. "/" .. id .. ".json"
        local jf = io.open(json_path, "wb")
        if not jf then
            log_monitor("Cannot write " .. json_path .. " - payload not sent")
            return
        end
        jf:write(payload)
        jf:close()

        if track_outbox_file(json_path, id) then
            log_monitor("Queued for bridge [" .. id .. "]: " .. tostring(log_label) .. "  (result is written to bridge_send_log.txt)")
        else
            log_monitor("Could not start the sender for [" .. id .. "] - will retry")
        end
    end, debug.traceback)
    if not ok then
        log_monitor("Error in send_payload_to_bridge: " .. tostring(err))
    end
end

-- Re-send anything the bridge has not acknowledged yet. Cheap: no process is started unless something is waiting.
local function service_outbox()
    local now = os.time()
    for json_path, entry in pairs(outbox_pending) do
        if not file_exists(json_path) then
            outbox_pending[json_path] = nil -- delivered (the .bat deleted it)
        elseif entry.tries >= OUTBOX_MAX_ATTEMPTS then
            -- leave the file in the outbox; it is adopted again on the next script start
            outbox_pending[json_path] = nil
        elseif now - entry.last_try >= OUTBOX_RETRY_SECONDS then
            entry.tries = entry.tries + 1
            entry.last_try = now
            launch_outbox_sender(json_path, entry.id)
            log_monitor("Retrying undelivered payload [" .. entry.id .. "] (attempt " .. entry.tries .. ")")
        end
    end
end

-- On startup: pick up payloads that were never delivered last time (bridge was not running).
local function adopt_outbox_leftovers()
    local temp_dir = os.getenv("TEMP") or os.getenv("TMP") or "."
    local list_file = temp_dir .. "/fc26_outbox_" .. tostring(os.time()) .. ".txt"
    pcall(os.execute, string.format('cmd /c dir /b "%s" > "%s" 2>nul', win_path(OUTBOX_DIR .. "/*.json"), win_path(list_file)))
    local lf = io.open(list_file, "r")
    if not lf then return 0 end
    local adopted = 0
    local now = os.time()
    for line in lf:lines() do
        local name = trim(line)
        local ts = tonumber(name:match("^(%d+)_%d+%.json$"))
        if ts then
            local path = OUTBOX_DIR .. "/" .. name
            if now - ts > OUTBOX_MAX_AGE_SECONDS then
                pcall(os.remove, path)
            elseif not outbox_pending[path] then
                outbox_pending[path] = { id = name:gsub("%.json$", ""), tries = 0, last_try = 0 }
                adopted = adopted + 1
            end
        end
    end
    lf:close()
    pcall(os.remove, list_file)
    if adopted > 0 then
        log_monitor("Found " .. adopted .. " undelivered payload(s) from an earlier session - sending now")
    end
    return adopted
end

-- ---------------------------------------------------------------------------
-- Project Atlas MatchStats export folders (Documents\FC26 Export\MatchStats\ready)
-- ---------------------------------------------------------------------------
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
    local ready_dir = MATCH_STATS_ROOT .. "/ready"
    local temp_dir = os.getenv("TEMP") or os.getenv("TMP") or "."
    local temp_file = temp_dir .. "/atlas_folders_" .. tostring(os.time()) .. ".txt"
    pcall(os.execute, string.format('cmd /c dir /b /ad /o:-d "%s" > "%s" 2>nul', win_path(ready_dir), win_path(temp_file)))
    local names = {}
    local tf = io.open(temp_file, "r")
    if tf then
        for line in tf:lines() do
            local name = trim(line)
            if name ~= "" then names[#names + 1] = name end
        end
        tf:close()
        pcall(os.remove, temp_file)
    end
    return names
end

-- The newest export that has not been attached to a match yet and is completely written.
-- include_used = true (manual "sync latest") accepts the newest export even if it was used before.
local function find_atlas_folder(include_used)
    local used = _G["FC26_ATLAS_USED"]
    local names = list_atlas_folders()
    for _, name in ipairs(names) do
        if include_used or not used[name] then
            local path = MATCH_STATS_ROOT .. "/ready/" .. name
            if atlas_folder_is_ready(path) then return name, path, names end
            return nil, nil, names -- newest export is still being written: wait for it, do not fall back to an older one
        end
    end
    return nil, nil, names
end

local function percent_value(v)
    local n = to_num(v, 0)
    if n > 0 and n <= 1 then n = n * 100 end
    return n
end

local function truthy_flag(v)
    local s = tostring(v or ""):lower()
    return s == "true" or s == "1" or s == "yes"
end

local function try_read_atlas_match_stats(managed_team_id, is_home, goals_against, folder_path)
    local participants = parse_simple_csv(folder_path .. "/16_match_participants_wide.csv")
    local teams = parse_simple_csv(folder_path .. "/18_match_teams_wide.csv")
    if not participants or not teams or #participants == 0 or #teams == 0 then
        return nil
    end

    -- Which team index (0 or 1) is the managed club? Match the export against the current squad.
    local squad_pids = get_current_squad_details(managed_team_id)
    local count0, count1 = 0, 0
    for _, p in ipairs(participants) do
        local pid = tonumber(p.player_id) or 0
        local tid = tonumber(p.team_index) or 0
        if squad_pids[pid] ~= nil then
            if tid == 0 then count0 = count0 + 1 else count1 = count1 + 1 end
        end
    end

    local managed_team_idx = is_home and 0 or 1
    if count0 > count1 then managed_team_idx = 0
    elseif count1 > count0 then managed_team_idx = 1 end
    local opp_team_idx = (managed_team_idx == 0) and 1 or 0

    -- Man of the match from the engine, else best rating
    local motm_player = ""
    local highest_rating = -1.0
    for _, p in ipairs(participants) do
        local name = p.player_name or ""
        if name ~= "" then
            if truthy_flag(p.man_of_the_match) then
                motm_player = name
                break
            end
            local r = to_num(p.game_rating, 6.0)
            if r > highest_rating then
                highest_rating = r
                motm_player = name
            end
        end
    end

    local m_team_row = teams[managed_team_idx + 1] or teams[1]
    local o_team_row = teams[opp_team_idx + 1] or teams[2] or teams[1]

    local shots_f = to_int(tonumber(m_team_row.shots) or tonumber(m_team_row.shots_on_target) or 0)
    local shots_a = to_int(tonumber(o_team_row.shots) or tonumber(o_team_row.shots_on_target) or 0)
    local xg_f = to_num(m_team_row.npk_expected_goals, 0.0)
    local xg_a = to_num(o_team_row.npk_expected_goals, 0.0)
    local passes_f = to_num(m_team_row.passes, 0)
    local passes_a = to_num(o_team_row.passes, 0)
    local pass_acc = to_int(percent_value(m_team_row.pass_accuracy_pct_derived))
    local poss = to_int(percent_value(m_team_row.possession ~= "" and m_team_row.possession or m_team_row.possession_pct))
    if poss <= 0 and (passes_f + passes_a) > 0 then
        poss = math.floor((passes_f / (passes_f + passes_a)) * 100)
    end

    local max_minutes = 0
    for _, p in ipairs(participants) do
        local m = to_num(p.minutes_played, 0)
        if m > max_minutes then max_minutes = m end
    end

    local player_perfs = {}
    local goal_details = {}
    local goal_players = {}
    local total_goals = 0

    for _, p in ipairs(participants) do
        local p_idx = tonumber(p.team_index) or 0
        local minutes = tonumber(p.minutes_played)
        -- The export can list the whole matchday squad: players who never came on did not play.
        local played = not (max_minutes > 0 and minutes ~= nil and minutes <= 0)
        if p_idx == managed_team_idx and played then
            local p_name = (p.player_name and p.player_name ~= "") and p.player_name or "Player"
            local p_id = p.player_id or ""
            local p_goals = to_int(tonumber(p.goals_derived) or tonumber(p.npk_goals) or 0)
            local p_assists = to_int(p.assists, 0)
            local p_rating = to_num(p.game_rating, 6.5)
            local p_passes = to_int(p.passes, 0)
            local p_pacc = to_int(percent_value(p.pass_accuracy_pct_derived))
            local p_shots = to_int(tonumber(p.shots) or p_goals)
            local sot = to_num(p.shots_on_target, 0)
            local p_sacc = 0
            if p_shots > 0 and sot > 0 then p_sacc = math.min(100, math.floor((sot / p_shots) * 100)) end
            local p_tackles = to_int(p.tackles, 0)
            local p_tpct
            if tonumber(p.stand_tackle_success_pct_derived) then
                p_tpct = to_int(percent_value(p.stand_tackle_success_pct_derived))
            else
                -- The exporter leaves that column blank for a player with no stand tackles (slide tackles only):
                -- use all tackles won / attempted instead of reporting 100%.
                local attempts, won
                if p.stand_tackles ~= nil or p.slide_tackles ~= nil then
                    attempts = to_num(p.stand_tackles, 0) + to_num(p.slide_tackles, 0)
                    won = to_num(p.stand_tackles_won, 0) + to_num(p.slide_tackles_won, 0)
                else
                    attempts = p_tackles
                    won = to_num(p.tackles_completed, p_tackles)
                end
                p_tpct = (attempts > 0) and math.min(100, math.floor((won / attempts) * 100)) or 0
            end
            local p_interceptions = to_int(p.interceptions, 0)
            local is_m = (p_name == motm_player) or truthy_flag(p.man_of_the_match)
            local yc = to_num(p.yellow_card_events, 0) > 0
            local rc = to_num(p.red_dismissals, 0) > 0
            local cs = (goals_against == 0)

            player_perfs[#player_perfs + 1] = string.format(
                '{"playerId": "%s", "playerName": "%s", "rating": %.1f, "goals": %d, "assists": %d, "passes": %d, "passAccuracy": %d, "shots": %d, "shotAccuracy": %d, "tackles": %d, "tacklePercentage": %d, "interceptions": %d, "cleanSheet": %s, "yellowCard": %s, "redCard": %s, "isMotm": %s}',
                jstr(p_id), jstr(p_name), p_rating, p_goals, p_assists, p_passes, p_pacc, p_shots, p_sacc,
                p_tackles, p_tpct, p_interceptions,
                cs and "true" or "false", yc and "true" or "false", rc and "true" or "false", is_m and "true" or "false")

            for _ = 1, p_goals do
                goal_players[#goal_players + 1] = p_name
                total_goals = total_goals + 1
            end
        end
    end

    -- The export has no goal minutes, so spread them evenly over the match (flagged as estimated).
    for k, name in ipairs(goal_players) do
        local minute = math.max(1, math.min(90, math.floor(90 * k / (total_goals + 1))))
        goal_details[#goal_details + 1] = string.format(
            '{"playerName": "%s", "minute": %d, "isOpponent": false, "minuteEstimated": true}', jstr(name), minute)
    end

    local m_shootout = to_int(m_team_row.shootout_goals, 0)
    local o_shootout = to_int(o_team_row.shootout_goals, 0)
    if m_shootout >= 255 or m_shootout < 0 then m_shootout = 0 end
    if o_shootout >= 255 or o_shootout < 0 then o_shootout = 0 end

    return {
        motm = motm_player,
        shots_for = shots_f,
        shots_against = shots_a,
        xg_for = xg_f,
        xg_against = xg_a,
        possession = poss,
        passing_accuracy = pass_acc,
        player_performances = player_perfs,
        goal_details = goal_details,
        max_minutes = max_minutes,
        m_shootout = m_shootout,
        o_shootout = o_shootout
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

        -- 1. Real league position (also records the primary league name in state)
        local leaguePos = get_real_league_position(managed_team_id)

        -- Rule: a playoff match is still reported under the primary domestic league
        local compName = rawCompName
        if (is_playoff or is_league) and state and state.primary_league_name and state.primary_league_name ~= "" then
            compName = state.primary_league_name
        end

        local stage = latest_match.stage or "Matchday 1"

        -- 2. Real Atlas export for this match (if the exporter produced one)
        local atlasStats = nil
        if atlas_folder_path then
            local aok, a_or_err = pcall(try_read_atlas_match_stats, managed_team_id, isHome, ga, atlas_folder_path)
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

            for j = 1, ga do
                local minute = math.max(1, math.min(90, math.floor(90 * j / (ga + 1))))
                goalDetails[#goalDetails + 1] = string.format(
                    '{"playerName": "%s", "minute": %d, "isOpponent": true, "minuteEstimated": true}',
                    jstr(oppName .. " Goal"), minute)
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

            local match_participants = {}
            for _, sp in ipairs(starters) do match_participants[#match_participants + 1] = sp end
            for s_idx = 1, math.min(#subs, 5) do match_participants[#match_participants + 1] = subs[s_idx] end

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
                playerPerfs[#playerPerfs + 1] = string.format(
                    '{"playerId": "%s", "playerName": "%s", "rating": %.1f, "goals": %d, "assists": %d, "passes": 0, "passAccuracy": 0, "shots": %d, "shotAccuracy": 0, "tackles": 0, "tacklePercentage": 0, "interceptions": 0, "cleanSheet": %s, "yellowCard": %s, "redCard": %s, "isMotm": false}',
                    jstr(p.id), jstr(p.name), pRating, goalsHere, d.a, (goalsHere > 0) and (goalsHere * 2) or 0,
                    (ga == 0) and "true" or "false", (d.y > 0) and "true" or "false", (d.r > 0) and "true" or "false")
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
            string.format('"leaguePosition": %s', (leaguePos > 0) and tostring(to_int(leaguePos)) or "null"),
            string.format('"detailSource": "%s"', detailSource),
            string.format('"fixtureKey": "%s"', jstr(fixture_key(latest_match))),
            '"playerPerformances": [' .. table.concat(playerPerfs, ",") .. "]",
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
-- END classify_loans

local function read_loan_rows()
    local rows = {}
    pcall(function()
        if LE and LE.db then
            local pl = LE.db:GetTable("playerloans")
            if pl then
                local r = pl:GetFirstRecord()
                while r and r > 0 do
                    rows[#rows + 1] = {
                        pid = tonumber(pl:GetRecordFieldValue(r, "playerid")) or 0,
                        from = tonumber(pl:GetRecordFieldValue(r, "teamidloanedfrom")) or 0
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
                            from = tonumber(r.teamidloanedfrom and r.teamidloanedfrom.value or r.teamidloanedfrom) or 0
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

-- Single-pass player info loader (OVR, join date, academy status) for just the players asked for.
local function get_squad_players_info(wanted_pids)
    local info_map = {}
    local function add(pid, ovr, join_days, isAcad)
        ovr = tonumber(ovr) or 70
        info_map[pid] = {
            ovr = ovr,
            join_days = tonumber(join_days) or 0,
            is_academy = ((tonumber(isAcad) or 0) == 1 or pid >= 300000),
            val_m = math.floor(((ovr * ovr * 800) / 100000) * 10) / 100
        }
    end
    pcall(function()
        if type(GetDBTableRows) == "function" then
            local rows = GetDBTableRows("players")
            if type(rows) == "table" and #rows > 0 then
                for _, r in ipairs(rows) do
                    local pid = tonumber(r.playerid and r.playerid.value or r.playerid) or 0
                    if pid > 0 and wanted_pids[pid] then
                        add(pid,
                            r.overallrating and r.overallrating.value or r.overallrating,
                            r.playerjointeamdate and r.playerjointeamdate.value or r.playerjointeamdate,
                            r.isacademy and r.isacademy.value or r.isacademy)
                    end
                end
            end
        end
    end)
    if next(info_map) == nil then
        pcall(function()
            if LE and LE.db then
                local pt = LE.db:GetTable("players")
                if pt then
                    local r = pt:GetFirstRecord()
                    while r and r > 0 do
                        local pid = tonumber(pt:GetRecordFieldValue(r, "playerid")) or 0
                        if pid > 0 and wanted_pids[pid] then
                            add(pid,
                                pt:GetRecordFieldValue(r, "overallrating"),
                                pt:GetRecordFieldValue(r, "playerjointeamdate"),
                                pt:GetRecordFieldValue(r, "isacademy"))
                        end
                        r = pt:GetNextValidRecord()
                    end
                end
            end
        end)
    end
    return info_map
end

-- Career transfer history rows that involve the managed club (case-insensitive column names).
local function get_career_transfer_history(managed_tid)
    local history = {}

    local function parse_cth_row(get_val)
        local pid = tonumber(get_val("playerid")) or 0
        local from_tid = tonumber(get_val("fromteamid") or get_val("teamidfrom") or get_val("sourceteamid") or get_val("sellerteamid")) or 0
        local to_tid = tonumber(get_val("toteamid") or get_val("teamidto") or get_val("targetteamid") or get_val("destteamid") or get_val("buyerteamid")) or 0
        local fee = tonumber(get_val("transferfee") or get_val("fee") or get_val("amount") or get_val("value")) or 0
        local date_raw = tonumber(get_val("transferdate") or get_val("date")) or 0

        if pid > 0 and (from_tid == managed_tid or to_tid == managed_tid) then
            history[#history + 1] = {
                playerid = pid,
                fromteamid = from_tid,
                toteamid = to_tid,
                fee = fee,
                date_raw = date_raw,
                date = decode_fifa_date(date_raw) -- nil when unknown
            }
        end
    end

    pcall(function()
        if type(GetDBTableRows) == "function" then
            local rows = GetDBTableRows("career_transferhistory")
            if type(rows) == "table" and #rows > 0 then
                for _, r in ipairs(rows) do
                    parse_cth_row(function(fieldName)
                        local target = fieldName:lower()
                        for k, v in pairs(r) do
                            if tostring(k):lower() == target then
                                return (type(v) == "table" and v.value) or v
                            end
                        end
                        return nil
                    end)
                end
            end
        end
    end)

    if #history == 0 then
        pcall(function()
            if LE and LE.db then
                local cth = LE.db:GetTable("career_transferhistory")
                if cth then
                    local r = cth:GetFirstRecord()
                    while r and r > 0 do
                        parse_cth_row(function(fieldName)
                            return cth:GetRecordFieldValue(r, fieldName)
                        end)
                        r = cth:GetNextValidRecord()
                    end
                end
            end
        end)
    end

    return history
end

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
    if t_data.feeEstimated then parts[#parts + 1] = '"feeEstimated": true' end
    local payload = "{\n  " .. table.concat(parts, ",\n  ") .. "\n}"

    send_payload_to_bridge(payload, string.format("Transfer [%s]: %s (%s)", tostring(t_data.type), player, other))
    log_monitor(string.format(">>> BEAMED TRANSFER: %s | %s | %s | EUR %.2fM | %s <<<",
        player, tostring(t_data.type), other, feeM, date))
    print(string.format("[FC26 AutoCapture TRANSFER] %s | %s | %s | EUR %.2fM | %s",
        player, tostring(t_data.type), other, feeM, date))
end

-- ---------------------------------------------------------------------------
-- Saved state (squad baseline + keys of transfers that were already sent)
-- ---------------------------------------------------------------------------
local STATE_FILE = COMMUNITY_ROOT .. "/auto_capture_state.lua"
local BEAMED_FILE = COMMUNITY_ROOT .. "/beamed_transfers.txt"

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

local function save_auto_capture_state(squad, all_teams)
    pcall(function()
        local squadDump = {}
        for pid, _ in pairs(squad or {}) do squadDump[#squadDump + 1] = "[" .. pid .. "]=true" end
        local teamsDump = {}
        for pid, tid in pairs(all_teams or {}) do teamsDump[#teamsDump + 1] = "[" .. pid .. "]=" .. tid end

        local f = io.open(STATE_FILE, "w")
        if f then
            f:write("return { squad = {" .. table.concat(squadDump, ",") .. "}, allPlayerTeams = {" .. table.concat(teamsDump, ",") .. "} }\n")
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
        return true
    end
    return false
end

-- Latest history row for a player entering / leaving the managed club (the table holds every past spell too).
local function latest_history_row(history, pid, field, managed_tid)
    local found = nil
    for _, th in ipairs(history) do
        if th.playerid == pid and th[field] == managed_tid then
            if not found or (th.date_raw or 0) >= (found.date_raw or 0) then found = th end
        end
    end
    return found
end

local function fee_from_history(th)
    if th and th.fee and th.fee > 0 then
        return math.floor((th.fee / 1000000) * 100) / 100
    end
    return nil
end

-- Sync current-season transfers that happened before / while the script was not running.
local function sync_season_transfers(managed_tid, force_sync)
    managed_tid = managed_tid or (state and state.managed_team_id) or get_managed_team_id()
    local managed_club = (state and state.managed_team_name) or safe_team_name(managed_tid)
    local todayDateStr, curY, curM, _ = get_current_career_date()
    local seasonStartYear = (curM < 7) and (curY - 1) or curY
    local seasonStartDateStr = string.format("%04d-07-01", seasonStartYear)

    log_monitor(string.format("Scanning transfers for %s (ID: %d) | Date: %s | Season Start: %s",
        managed_club, managed_tid, todayDateStr, seasonStartDateStr))

    local current_squad, all_teams, club_links = get_managed_squad_state(managed_tid)
    if next(current_squad) == nil then
        log_monitor("Transfer scan skipped: could not read the squad (is a career loaded?)")
        return 0
    end
    local loans_in, loans_out = get_active_loans(managed_tid, current_squad, club_links)
    local transfer_history = get_career_transfer_history(managed_tid)

    local wanted = {}
    for pid in pairs(current_squad) do wanted[pid] = true end
    local squad_info = get_squad_players_info(wanted)

    local synced_count = 0
    local synced_pids = {}

    -- 1. career_transferhistory: incoming AND outgoing deals of this season
    for _, th in ipairs(transfer_history) do
        local pid = th.playerid
        local inSeason
        if th.date then inSeason = (th.date >= seasonStartDateStr) else inSeason = force_sync end
        if inSeason then
            local pName = safe_player_name(pid)
            local feeM = fee_from_history(th) or 0.0
            local transferDate = th.date or todayDateStr

            if th.toteamid == managed_tid and th.fromteamid ~= managed_tid then
                local fromClub = (th.fromteamid > 0) and safe_team_name(th.fromteamid) or "Free Agency"
                local tType = loans_in[pid] and "In (Loan)" or "In (Permanent)"
                if emit_transfer(string.format("CTH_IN_%d_%d_%s", pid, th.fromteamid, transferDate), {
                    playerName = pName, type = tType, feeM = feeM, fromToClub = fromClub, date = transferDate,
                    clubName = managed_club,
                    message = string.format("%s joined %s from %s for EUR %.2fM!", pName, managed_club, fromClub, feeM)
                }, force_sync) then
                    synced_count = synced_count + 1
                end
                synced_pids[pid] = true
            elseif th.fromteamid == managed_tid and th.toteamid ~= managed_tid then
                local toClub = (th.toteamid > 0) and safe_team_name(th.toteamid) or "Free Agent / Released"
                local tType = loans_out[pid] and "Out (Loan)" or "Out (Permanent)"
                if emit_transfer(string.format("CTH_OUT_%d_%d_%s", pid, th.toteamid, transferDate), {
                    playerName = pName, type = tType, feeM = feeM, fromToClub = toClub, date = transferDate,
                    clubName = managed_club,
                    message = string.format("%s transferred to %s for EUR %.2fM.", pName, toClub, feeM)
                }, force_sync) then
                    synced_count = synced_count + 1
                end
            end
        end
    end

    -- 2. active loans table
    for pid, from_tid in pairs(loans_in) do
        if not synced_pids[pid] then
            local pName = safe_player_name(pid)
            local fromClub = safe_team_name(from_tid)
            if emit_transfer(string.format("LOAN_IN_%d_%d", pid, from_tid), {
                playerName = pName, type = "In (Loan)", feeM = 0.0, fromToClub = fromClub, date = todayDateStr,
                clubName = managed_club,
                message = string.format("%s joined %s on loan from %s!", pName, managed_club, fromClub)
            }, force_sync) then
                synced_count = synced_count + 1
            end
            synced_pids[pid] = true
        end
    end

    for pid, to_tid in pairs(loans_out) do
        local pName = safe_player_name(pid)
        -- to_tid is 0 when the loan club cannot be read; say so instead of sending an empty club name
        local toClub = (to_tid > 0) and safe_team_name(to_tid) or "Unknown club"
        local loanMessage = (to_tid > 0) and string.format("%s loaned out to %s.", pName, toClub)
            or string.format("%s loaned out.", pName)
        if emit_transfer(string.format("LOAN_OUT_%d_%d", pid, to_tid), {
            playerName = pName, type = "Out (Loan)", feeM = 0.0, fromToClub = toClub, date = todayDateStr,
            clubName = managed_club,
            message = loanMessage
        }, force_sync) then
            synced_count = synced_count + 1
        end
    end

    -- 3. join dates in the player table (signings the history table did not list). A real window rarely
    --    brings in more than a handful of players, so a long list means "initial squad": skip it.
    local joins = {}
    for pid in pairs(current_squad) do
        if not synced_pids[pid] then
            local pInfo = squad_info[pid]
            if pInfo and pInfo.join_days and pInfo.join_days > 0 then
                local joinDateStr, joinY, joinM = decode_fifa_date(pInfo.join_days)
                if joinDateStr then
                    local joinSeason = (joinM < 7) and (joinY - 1) or joinY
                    if joinSeason >= seasonStartYear then
                        joins[#joins + 1] = { pid = pid, info = pInfo, date = joinDateStr }
                    end
                end
            end
        end
    end
    if #joins > 15 then
        log_monitor(string.format("Join-date scan found %d players - looks like the initial squad, not signings; skipped", #joins))
    else
        for _, j in ipairs(joins) do
            local pName = safe_player_name(j.pid)
            local tType = j.info.is_academy and "Promoted In" or "In (Permanent)"
            local fromClub = j.info.is_academy and "Youth Academy" or "External Club"
            local feeM = j.info.is_academy and 0.0 or j.info.val_m
            if emit_transfer(string.format("JOIN_%d_%s_%s", j.pid, tType, j.date), {
                playerName = pName, type = tType, feeM = feeM, fromToClub = fromClub, date = j.date,
                clubName = managed_club, feeEstimated = (not j.info.is_academy),
                message = string.format("%s joined %s (%s)!", pName, managed_club, tType)
            }, force_sync) then
                synced_count = synced_count + 1
            end
            synced_pids[j.pid] = true
        end
    end

    -- 4. squad compared with the baseline saved at the end of the last session
    local saved_state = load_saved_telemetry_state()
    if saved_state and saved_state.squad then
        for pid in pairs(current_squad) do
            if not saved_state.squad[pid] and not synced_pids[pid] then
                local pName = safe_player_name(pid)
                local pInfo = squad_info[pid]
                local isAcad = pInfo and pInfo.is_academy
                local tType = isAcad and "Promoted In" or "In (Permanent)"
                local fromClub = isAcad and "Youth Academy" or "External Club"
                local prev = all_teams[pid]
                if not isAcad and prev and prev > 0 and prev ~= managed_tid then fromClub = safe_team_name(prev) end
                local feeM = (pInfo and not isAcad) and pInfo.val_m or 0.0
                if emit_transfer(string.format("STATE_DIFF_IN_%d_%s", pid, todayDateStr), {
                    playerName = pName, type = tType, feeM = feeM, fromToClub = fromClub, date = todayDateStr,
                    clubName = managed_club, feeEstimated = (feeM > 0),
                    message = string.format("%s joined %s from %s!", pName, managed_club, fromClub)
                }, force_sync) then
                    synced_count = synced_count + 1
                end
            end
        end

        local left = {}
        for pid in pairs(saved_state.squad) do
            if not current_squad[pid] then left[#left + 1] = pid end
        end
        if #left <= 15 then
            for _, pid in ipairs(left) do
                local pName = safe_player_name(pid)
                local newTid = all_teams[pid] or 0
                local toClub = (newTid > 0 and newTid ~= managed_tid) and safe_team_name(newTid) or "Free Agent / Released"
                if emit_transfer(string.format("STATE_DIFF_OUT_%d_%s", pid, todayDateStr), {
                    playerName = pName, type = "Out (Permanent)", feeM = 0.0, fromToClub = toClub, date = todayDateStr,
                    clubName = managed_club,
                    message = string.format("%s transferred out of %s.", pName, managed_club)
                }, force_sync) then
                    synced_count = synced_count + 1
                end
            end
        else
            log_monitor(string.format("Saved squad differs by %d players - not treated as transfers (different career?)", #left))
        end
    end

    save_auto_capture_state(current_squad, all_teams)

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

    local arrivals, departures = {}, {}
    local squad_size = 0
    for pid in pairs(state.squad) do squad_size = squad_size + 1 end
    for pid in pairs(current_squad) do
        if not state.squad[pid] then arrivals[#arrivals + 1] = pid end
    end
    for pid in pairs(state.squad) do
        if not current_squad[pid] then departures[#departures + 1] = pid end
    end

    if #arrivals == 0 and #departures == 0 then
        state.all_player_teams = current_all_teams
        state.suspect_reads = 0
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

    local todayDateStr = get_current_career_date()
    local loans_in, loans_out = get_active_loans(managed_tid, current_squad, current_links)
    local transfer_history = get_career_transfer_history(managed_tid)
    local wanted = {}
    for _, pid in ipairs(arrivals) do wanted[pid] = true end
    for _, pid in ipairs(departures) do wanted[pid] = true end
    local squad_info = get_squad_players_info(wanted)
    local previous_teams = state.all_player_teams or {}

    for _, pid in ipairs(arrivals) do
        local pName = safe_player_name(pid)
        local pInfo = squad_info[pid]
        local tType = "In (Permanent)"
        local fromClub = "External Club"
        local feeM = pInfo and pInfo.val_m or 0.0
        local feeEstimated = true

        local prevTid = previous_teams[pid] or 0
        if prevTid > 0 and prevTid ~= managed_tid then fromClub = safe_team_name(prevTid) end

        if loans_in[pid] then
            tType = "In (Loan)"
            fromClub = safe_team_name(loans_in[pid])
            feeM, feeEstimated = 0.0, false
        elseif (pInfo and pInfo.is_academy) or pid >= 300000 then
            tType = "Promoted In"
            fromClub = "Youth Academy"
            feeM, feeEstimated = 0.0, false
        else
            local th = latest_history_row(transfer_history, pid, "toteamid", managed_tid)
            if th then
                if th.fromteamid > 0 then fromClub = safe_team_name(th.fromteamid) end
                local f = fee_from_history(th)
                if f then feeM, feeEstimated = f, false end
            end
        end

        emit_transfer(string.format("IN_%d_%s_%s", pid, tType, todayDateStr), {
            playerName = pName, type = tType, feeM = feeM, fromToClub = fromClub, date = todayDateStr,
            clubName = managed_club, feeEstimated = feeEstimated and feeM > 0,
            message = string.format("%s joined %s from %s!", pName, managed_club, fromClub)
        }, false)
    end

    for _, pid in ipairs(departures) do
        local pName = safe_player_name(pid)
        local toClub = "Free Agent / Released"
        local tType = "Out (Permanent)"
        local feeM = 0.0
        local feeEstimated = false

        local newTid = current_all_teams[pid] or 0
        if newTid > 0 and newTid ~= managed_tid then toClub = safe_team_name(newTid) end

        if loans_out[pid] then
            tType = "Out (Loan)"
            if loans_out[pid] > 0 then
                toClub = safe_team_name(loans_out[pid])
            elseif toClub == "Free Agent / Released" then
                toClub = "Unknown club" -- a loan is never a release
            end
        else
            local th = latest_history_row(transfer_history, pid, "fromteamid", managed_tid)
            if th then
                if th.toteamid > 0 then toClub = safe_team_name(th.toteamid) end
                local f = fee_from_history(th)
                if f then feeM = f end
            end
            if feeM == 0.0 and toClub ~= "Free Agent / Released" then
                local pInfo = squad_info[pid]
                feeM = pInfo and pInfo.val_m or 0.0
                feeEstimated = feeM > 0
            end
        end

        emit_transfer(string.format("OUT_%d_%s_%s", pid, tType, todayDateStr), {
            playerName = pName, type = tType, feeM = feeM, fromToClub = toClub, date = todayDateStr,
            clubName = managed_club, feeEstimated = feeEstimated,
            message = string.format("%s transferred to %s.", pName, toClub)
        }, false)
    end

    state.squad = current_squad
    state.all_player_teams = current_all_teams
    save_auto_capture_state(current_squad, current_all_teams)
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
local baseline_atlas = list_atlas_folders()
for _, name in ipairs(baseline_atlas) do _G["FC26_ATLAS_USED"][name] = true end

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
    atlas_seen = (#baseline_atlas > 0)
}
_G["FC26_AUTO_CAPTURE_STATE"] = state

-- Anything left undelivered by an earlier session goes out first, then the catch-up transfer sync.
pcall(adopt_outbox_leftovers)
do
    local ok, err = xpcall(function() sync_season_transfers(managed_team_id, false) end, debug.traceback)
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

-- Starts the Atlas exporter next to this script unless it is already running (running it twice is harmless).
local function start_atlas_exporter()
    if atlas_exporter_active() then
        log_monitor("Project Atlas exporter is already running")
        return true
    end
    for _, name in ipairs(ATLAS_EXPORTER_FILES) do
        for _, base in ipairs({ SCRIPT_DIR, "" }) do
            local path = base .. name
            if file_exists(path) then
                log_monitor("Starting the Project Atlas exporter: " .. path)
                local ok, err = pcall(dofile, path)
                if not ok then log_monitor("Project Atlas exporter error: " .. tostring(err)) end
                return ok
            end
        end
    end
    log_monitor("Project Atlas exporter NOT started: " .. ATLAS_EXPORTER_FILES[1] .. " was not found in the same folder as this script ("
        .. (SCRIPT_DIR ~= "" and SCRIPT_DIR or "the Live Editor scripts folder") .. "). "
        .. "Matches are still sent, but without detailed player statistics, and NO snapshots are written "
        .. "(Documents\\FC26 Export\\Snapshots stays empty). Copy that file next to this script and run this script again.")
    return false
end

local function run_snapshot()
    -- FC26 AutoCapture publishes the paired snapshot by itself; a second one would only slow the game down
    if rawget(_G, "FC26_EXPORT_AUTO_CAPTURE_STATE") ~= nil then return true end
    for _, path in ipairs({ SCRIPT_DIR .. "FC26_Snapshot.lua", "FC26_Snapshot.lua" }) do
        if file_exists(path) then
            local ok, err = pcall(dofile, path)
            if not ok then log_monitor("FC26_Snapshot.lua error: " .. tostring(err)) end
            return ok
        end
    end
    log_monitor("FC26_Snapshot.lua not found next to this script - snapshot skipped")
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

    if now - item.last_atlas_check >= ATLAS_POLL_SECONDS then
        item.last_atlas_check = now
        atlas_name, atlas_path, atlas_names = find_atlas_folder(false)
        if atlas_names and #atlas_names > 0 then state.atlas_seen = true end
    end

    local limit = ATLAS_WAIT_SECONDS_IDLE
    if atlas_exporter_active() then
        limit = ATLAS_WAIT_SECONDS_EXPORTER
    elseif state.atlas_seen then
        limit = ATLAS_WAIT_SECONDS
    end
    if not atlas_path and waited < limit then return end

    table.remove(state.pending_matches, 1)
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

-- Force sync function: Immediately beams the latest completed match
function FC26_AutoCapture_SyncLatest()
    local current_fixtures = get_all_managed_fixtures(state.managed_team_id)
    local latest_idx = nil
    for idx, f in ipairs(current_fixtures) do
        if f.completed then latest_idx = idx end
    end
    if not latest_idx then
        print("[FC26 AutoCapture] No completed matches found to sync.")
        return
    end

    local latest = current_fixtures[latest_idx]
    latest.stage = determine_stage(latest.competition, completed_in_competition(current_fixtures, latest_idx))

    run_snapshot()
    local atlas_name, atlas_path = find_atlas_folder(true)
    if atlas_name then _G["FC26_ATLAS_USED"][atlas_name] = true end

    beam_match_to_bridge(latest, state.managed_team_id, state.pre_match_cpi, atlas_path)
    _G["FC26_BEAMED_FIXTURE_KEYS"][fixture_key(latest)] = true
    state.completed_ids[fixture_key(latest)] = true
    print(string.format("[FC26 AutoCapture] Force Synced Latest Match: %s vs %d-%d %s (%s - %s)%s",
        latest.user_team_name, latest.goals_for, latest.goals_against, latest.opponent_name,
        latest.competition, latest.stage, atlas_name and " [with Atlas stats]" or " [no Atlas export found]"))
end

FC26_AutoCapture_SyncNow = FC26_AutoCapture_SyncLatest
FC26_SyncLatestMatch = FC26_AutoCapture_SyncLatest

-- Force sync function: Immediately scans and beams current season transfers
function FC26_AutoCapture_SyncTransfers()
    print("[FC26 AutoCapture] Force syncing current season transfers...")
    local count = sync_season_transfers(state.managed_team_id, true)
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
    guarded("match detection", check_for_new_completed_match)
    guarded("pending matches", service_pending_matches)
    guarded("transfer scan", function() check_for_squad_transfers(false) end)
    guarded("outbox", service_outbox)
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
    print(" Project Atlas exporter: NOT running. FC26_AutoCapture_v1.1.1.lua is missing from the folder of this script.")
    print("   Without it there are no snapshots (Documents\\FC26 Export\\Snapshots stays empty) and player stats are estimated.")
    print("   Copy FC26_AutoCapture_v1.1.1.lua next to FC26_Auto_Capture.lua and run FC26_Auto_Capture.lua again.")
end
print(" Delivery log: Documents\\FC26 Export\\bridge_send_log.txt (undelivered items are retried).")
print(" Background timer active (2s loop). Event listeners hooked.")
print(" Safe Startup: No old matches beamed on launch.")
print(" Commands:")
print("   - FC26_AutoCapture_SyncLatest()   : Force-send latest match")
print("   - FC26_AutoCapture_SyncTransfers(): Force-send current season transfers")
print("   - FC26_BeamTransfer(name, type, fee, club): Manually beam any transfer")
print("   - FC26_AutoCapture_Status()       : Show queue + last bridge answers")
print("=========================================================================")
