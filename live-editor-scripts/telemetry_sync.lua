-- =========================================================================
-- EA SPORTS FC LIVE TELEMETRY SYNC (v4.9 - reliable delivery, lighter polling)
-- Supports Create-a-Club, League Matches, Cup Matches, Pre-Season & Transfers
--
-- NOTE: FC26_Auto_Capture.lua is the recommended script (it reads the real fixture, lineup and
-- stats data). This script is the database-delta fallback; it stands down automatically while
-- FC26_Auto_Capture.lua is running so the same match is never reported twice.
-- =========================================================================

-- SET TO true TO FORCE-SEND THE MOST RECENT MATCH IMMEDIATELY ON RUN:
local FORCE_SYNC_LATEST = false

-- AUTO-SYNC CONFIGURATION:
-- Each pass reads several very large tables, so it runs every 15 seconds (it was every 3 seconds).
-- Match and squad changes are cumulative, so nothing is missed by checking less often.
local AUTO_SYNC = true
local AUTO_SYNC_INTERVAL_SEC = 15

local user_profile = os.getenv("USERPROFILE") or ""
local COMMUNITY_ROOT = (user_profile ~= "" and user_profile or ".") .. "/Documents/FC26 Export"
local OUTBOX_DIR = COMMUNITY_ROOT .. "/outbox"

local function win_path(p)
    local converted = tostring(p or ""):gsub("/", string.char(92))
    return converted
end

local function trim(value)
    local text = tostring(value or "")
    text = text:gsub("^%s+", "")
    text = text:gsub("%s+$", "")
    return text
end

local function file_exists(path)
    local f = io.open(path, "rb")
    if f then f:close() return true end
    return false
end

pcall(function()
    os.execute('if not exist "' .. win_path(COMMUNITY_ROOT) .. '" mkdir "' .. win_path(COMMUNITY_ROOT) .. '"')
    os.execute('if not exist "' .. win_path(OUTBOX_DIR) .. '" mkdir "' .. win_path(OUTBOX_DIR) .. '"')
end)

-- Files used to live on the Desktop, which is often redirected into OneDrive (the folder then does not
-- exist at %USERPROFILE%\Desktop and every save silently failed - so no change was ever detected).
-- Everything now lives in Documents\FC26 Export; the old Desktop files are still read once for migration.
local function my_log(msg)
    local s = "[TelemetrySync] " .. tostring(msg)
    pcall(print, s)
    pcall(function()
        local f = io.open(COMMUNITY_ROOT .. "/telemetry_sync_log.txt", "a")
        if f then
            f:write(os.date("%Y-%m-%d %H:%M:%S") .. " " .. s .. "\n")
            f:close()
        end
    end)
end

local function log_monitor(msg) my_log(msg) end

local function safe_get_rows(tbl_name)
    local ok, res = pcall(GetDBTableRows, tbl_name)
    if ok and type(res) == "table" then return res end
    return {}
end

local function raw_val(innerVal)
    if innerVal == nil then return nil end
    if type(innerVal) == "table" then
        if innerVal.value ~= nil then return innerVal.value end
        if innerVal[1] ~= nil then return innerVal[1] end
    end
    if type(innerVal) == "userdata" then
        local ok, uv = pcall(function() return innerVal.value end)
        if ok and uv ~= nil then return uv end
    end
    return innerVal
end

local function get_field(row, colName)
    if not row or colName == nil then return nil end
    local v = raw_val(row[colName])
    if v ~= nil then return v end
    local targetLower = tostring(colName):lower()
    for k, valItem in pairs(row) do
        if tostring(k):lower() == targetLower then
            return raw_val(valItem)
        end
    end
    return nil
end

local function val(row, field, def)
    local v = get_field(row, field)
    if v ~= nil then return v end
    return def
end

local function escape_str(s)
    if s == nil then return "" end
    local str = tostring(s):gsub("\\", "\\\\"):gsub('"', '\\"'):gsub("\n", " "):gsub("\r", "")
    return str:gsub("^%s*(.-)%s*$", "%1")
end

local function decode_fifa_date(days, defYear)
    days = tonumber(days) or 0
    if days > 19000000 and days < 21000000 then
        local y = math.floor(days / 10000); local m = math.floor((days % 10000) / 100); local d = days % 100
        return string.format("%04d-%02d-%02d", y, m, d)
    elseif days > 100000 and days < 250000 then
        local jd = days + 2299160; local l = jd + 68569
        local n = math.floor((4 * l) / 146097); l = l - math.floor((146097 * n + 3) / 4)
        local i = math.floor((4000 * (l + 1)) / 1461001); l = l - math.floor((1461 * i) / 4) + 31
        local j = math.floor((80 * l) / 2447); local d = l - math.floor((2447 * j) / 80)
        l = math.floor(j / 11); local m = j + 2 - (12 * l); local y = 100 * (n - 49) + i + l
        return string.format("%04d-%02d-%02d", y, m, d)
    else
        return string.format("%04d-08-01", defYear or 2027)
    end
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

local function send_to_bridge(json_payload, label)
    send_payload_to_bridge(json_payload, label or "payload")
end

-- Lookup tables (names, leagues, teams, players) are large and almost never change: build them once and
-- reuse them for five minutes instead of re-reading ~100k rows on every pass.
local function build_lookup_cache()
    local playernamesMap = {}
    for _, pn in ipairs(safe_get_rows("playernames")) do
        local nid = tonumber(val(pn, "nameid", 0)) or 0
        local n = val(pn, "name", "")
        if nid > 0 and n ~= "" then playernamesMap[nid] = escape_str(n) end
    end

    local leagueMap = {}
    for _, l in ipairs(safe_get_rows("leagues")) do
        local lId = tonumber(val(l, "leagueid", 0)) or 0
        local lName = val(l, "leaguename", "")
        if lId > 0 and lName ~= "" and not tostring(lName):find("^%*") then leagueMap[lId] = escape_str(lName) end
    end

    local teamMap = {}
    for _, t in ipairs(safe_get_rows("teams")) do
        local tId = tonumber(val(t, "teamid", 0)) or 0
        local tName = val(t, "teamname", "")
        if tId > 0 and tName ~= "" and not tostring(tName):find("^%*") then
            teamMap[tId] = escape_str(tostring(tName))
        end
    end

    -- Created Clubs & Custom Team Names resolution (Create-a-Club mode)
    for _, ct in ipairs(safe_get_rows("createdteams")) do
        local tId = tonumber(val(ct, "teamid", 0)) or 0
        local cName = val(ct, "teamname", val(ct, "customteamname", val(ct, "clubname", "")))
        if tId > 0 and cName ~= "" and not tostring(cName):find("^%*") then
            teamMap[tId] = escape_str(tostring(cName))
        end
    end
    for _, etn in ipairs(safe_get_rows("editedteamnames")) do
        local tId = tonumber(val(etn, "teamid", 0)) or 0
        local cName = val(etn, "teamname", val(etn, "customteamname", ""))
        if tId > 0 and cName ~= "" and not tostring(cName):find("^%*") then
            teamMap[tId] = escape_str(tostring(cName))
        end
    end

    -- Fast player lookup map
    local playersMap = {}
    for _, p in ipairs(safe_get_rows("players")) do
        local pid = tonumber(val(p, "playerid", 0)) or 0
        if pid > 0 then playersMap[pid] = p end
    end

    return { built = os.time(), playernames = playernamesMap, leagues = leagueMap, teams = teamMap, players = playersMap }
end

function run_telemetry_sync(isAuto)
    -- Real-data script is running: do not report the same matches/transfers a second time.
    local ac = _G["FC26_AUTO_CAPTURE_STATE"]
    if ac and ac.active and _G["FC26_AUTOCAPTURE_CURRENT_INSTANCE"] == ac.instance_id then
        if not _G["FC26_SYNC_STOOD_DOWN"] then
            _G["FC26_SYNC_STOOD_DOWN"] = true
            my_log("FC26_Auto_Capture.lua is active - telemetry_sync.lua stands down (no duplicate events).")
        end
        return
    end
    _G["FC26_SYNC_STOOD_DOWN"] = nil

    if not isAuto then
        my_log(">>> RUNNING TELEMETRY SYNC V4.9 <<<")
    end
    service_outbox()

    local statePath = COMMUNITY_ROOT .. "/telemetry_state.lua"
    local legacyStatePaths = {
        user_profile .. "\\Desktop\\FC26-LE\\telemetry_state.lua",
        user_profile .. "\\Desktop\\telemetry_state.lua",
        "telemetry_state.lua"
    }
    if not file_exists(statePath) then
        for _, sp in ipairs(legacyStatePaths) do
            if file_exists(sp) then
                local okm, resm = pcall(dofile, sp)
                if okm and type(resm) == "table" then
                    _G["FC26_SYNC_MIGRATED_STATE"] = resm
                end
                break
            end
        end
    end

    -- 1. Lookup maps (cached)
    local cache = _G["FC26_SYNC_CACHE"]
    if not cache or (os.time() - cache.built) > 300 then
        cache = build_lookup_cache()
        _G["FC26_SYNC_CACHE"] = cache
    end
    local playernamesMap, leagueMap, teamMap, playersMap = cache.playernames, cache.leagues, cache.teams, cache.players

    local function get_team_name(tId)
        local n = teamMap[tonumber(tId) or 0]
        if n and n ~= "" and not tostring(n):find("^%*") then return n end
        return "Team " .. tostring(tId)
    end

    local function get_player_name(pId)
        local p = playersMap and playersMap[pId]
        if not p and (os.time() - cache.built) > 30 then
            -- player created after the cache was built (youth intake, regen): refresh once
            cache = build_lookup_cache()
            _G["FC26_SYNC_CACHE"] = cache
            playernamesMap, leagueMap, teamMap, playersMap = cache.playernames, cache.leagues, cache.teams, cache.players
            p = playersMap[pId]
        end
        if p then
            local pfname = val(p, "firstname", "")
            if pfname == "" then pfname = playernamesMap[tonumber(val(p, "firstnameid", 0)) or 0] or "" end
            local psname = val(p, "surname", "")
            if psname == "" then psname = val(p, "lastname", "") end
            if psname == "" then psname = playernamesMap[tonumber(val(p, "lastnameid", 0)) or 0] or "" end
            local pcname = val(p, "commonname", "")
            if pcname == "" then pcname = playernamesMap[tonumber(val(p, "commonnameid", 0)) or 0] or "" end
            
            local rawFullName = (pcname ~= "" and pcname) or ((pfname .. " " .. psname):gsub("^%s*(.-)%s*$", "%1"))
            if rawFullName == "" then rawFullName = psname ~= "" and psname or pfname end
            return escape_str(rawFullName)
        end
        return "Player " .. tostring(pId)
    end

    -- 2. Detect userTeamId
    local userTeamId = 0
    for _, u in ipairs(safe_get_rows("career_users")) do
        local uid = tonumber(val(u, "clubteamid", val(u, "teamid", val(u, "userteamid", 0)))) or 0
        if uid > 0 then
            userTeamId = uid
            break
        end
    end
    if userTeamId == 0 then
        for _, mh in ipairs(safe_get_rows("career_managerhistory")) do
            local tid = tonumber(val(mh, "teamid", 0)) or 0
            if tid > 0 then
                userTeamId = tid
                break
            end
        end
    end
    if userTeamId == 0 then userTeamId = 115486 end

    local userClubName = get_team_name(userTeamId)
    if tostring(userClubName):find("^%*") or tostring(userClubName):find("^Team ") or userClubName == "Unknown" then
        local u = safe_get_rows("career_users")[1]
        local cuName = val(u, "clubname", val(u, "teamname", ""))
        if cuName ~= "" and not tostring(cuName):find("^%*") then
            userClubName = escape_str(cuName)
        else
            userClubName = "Torquay United"
        end
        teamMap[userTeamId] = userClubName
    end

    -- 3. Resolve Current Calendar Date
    local currdate = 0
    for _, cal in ipairs(safe_get_rows("career_calendar")) do
        local d = tonumber(val(cal, "currdate", val(cal, "caldate", 0))) or 0
        if d > 20200000 then
            currdate = d
            break
        end
    end
    if currdate == 0 then
        currdate = 20270815
    end
    local cYear = math.floor(currdate / 10000)
    local cMonth = math.floor((currdate % 10000) / 100)
    local cDay = currdate % 100
    if cYear < 2020 or cMonth < 1 or cMonth > 12 or cDay < 1 or cDay > 31 then
        cYear = 2027
        cMonth = 8
        cDay = 15
    end
    local todayDateStr = string.format("%04d-%02d-%02d", cYear, cMonth, cDay)

    -- 4. Extract Squad & Player Stats
    local currentSquad = {}
    local currentPlayerStats = {}
    local playerToTeamMap = {}
    local totalSquadForm = 0
    local squadCount = 0

    -- Players are also linked to national / special teams. Only league clubs count when we remember
    -- "which club did he come from / go to", otherwise a signing shows up as coming from "England".
    local ltlRows = safe_get_rows("leagueteamlinks")
    local clubSet, haveClubs = {}, false
    for _, ltl in ipairs(ltlRows) do
        local ctid = tonumber(val(ltl, "teamid", 0)) or 0
        if ctid > 0 then clubSet[ctid] = true haveClubs = true end
    end

    for _, tpl in ipairs(safe_get_rows("teamplayerlinks")) do
        local tid = tonumber(val(tpl, "teamid", 0)) or 0
        local pId = tonumber(val(tpl, "playerid", 0)) or 0
        if pId > 0 and tid > 0 then
            if (not haveClubs) or clubSet[tid] then playerToTeamMap[pId] = tid end
            if tid == userTeamId then
                currentSquad[pId] = true
                squadCount = squadCount + 1
                local form = tonumber(val(tpl, "form", 3)) or 3
                totalSquadForm = totalSquadForm + form
                local estRating = 6.0 + (form * 0.5)
                if estRating > 9.5 then estRating = 9.5 end
                currentPlayerStats[pId] = { goals=0, assists=0, apps=0, yellows=0, reds=0, rating=estRating }
            end
        end
    end

    for _, pi in ipairs(safe_get_rows("career_playerinfo")) do
        local pId = tonumber(val(pi, "playerid", 0)) or 0
        if currentSquad[pId] then
            local apps = math.max(
                tonumber(val(pi, "appearances", 0)) or 0,
                tonumber(val(pi, "leagueappearances", 0)) or 0,
                tonumber(val(pi, "allappearances", 0)) or 0,
                tonumber(val(pi, "appearancescup", 0)) or 0,
                tonumber(val(pi, "appearancescontinental", 0)) or 0,
                tonumber(val(pi, "matchesplayed", 0)) or 0,
                tonumber(val(pi, "gamesplayed", 0)) or 0
            )
            local goals = math.max(
                tonumber(val(pi, "goals", 0)) or 0,
                tonumber(val(pi, "leaguegoals", 0)) or 0,
                tonumber(val(pi, "allgoals", 0)) or 0,
                tonumber(val(pi, "goalscup", 0)) or 0,
                tonumber(val(pi, "goalscontinental", 0)) or 0
            )
            local assists = math.max(
                tonumber(val(pi, "assists", 0)) or 0,
                tonumber(val(pi, "leagueassists", 0)) or 0,
                tonumber(val(pi, "allassists", 0)) or 0,
                tonumber(val(pi, "assistscup", 0)) or 0
            )
            local rating = tonumber(val(pi, "matchrating", 0)) or 0
            local yellows = math.max(
                tonumber(val(pi, "yellows", 0)) or 0,
                tonumber(val(pi, "yellowcards", 0)) or 0,
                tonumber(val(pi, "yellowcardsleague", 0)) or 0,
                tonumber(val(pi, "yellowcardscup", 0)) or 0
            )
            local reds = math.max(
                tonumber(val(pi, "reds", 0)) or 0,
                tonumber(val(pi, "redcards", 0)) or 0,
                tonumber(val(pi, "redcardsleague", 0)) or 0,
                tonumber(val(pi, "redcardscup", 0)) or 0
            )
            
            local prev = currentPlayerStats[pId] or {}
            currentPlayerStats[pId] = {
                goals = math.max(prev.goals or 0, goals),
                assists = math.max(prev.assists or 0, assists),
                apps = math.max(prev.apps or 0, apps),
                yellows = math.max(prev.yellows or 0, yellows),
                reds = math.max(prev.reds or 0, reds),
                rating = rating > 0 and rating or (prev.rating or 7.0)
            }
        end
    end

    -- 5. Extract League Stats
    local currentStats = { pld=0, hw=0, aw=0, hd=0, ad=0, hl=0, al=0, hgf=0, agf=0, hga=0, aga=0, leagueId=14 }
    local maxLeaguePld = -1
    local bestLtl = nil
    local totalUserLtls = 0

    for _, ltl in ipairs(ltlRows) do
        local tId = tonumber(val(ltl, "teamid", 0)) or 0
        if tId == userTeamId then
            totalUserLtls = totalUserLtls + 1
            local pld = tonumber(val(ltl, "nummatchesplayed", 0)) or 0
            local hw = tonumber(val(ltl, "homewins", 0)) or 0
            local aw = tonumber(val(ltl, "awaywins", 0)) or 0
            local hd = tonumber(val(ltl, "homedraws", 0)) or 0
            local ad = tonumber(val(ltl, "awaydraws", 0)) or 0
            local hl = tonumber(val(ltl, "homelosses", 0)) or 0
            local al = tonumber(val(ltl, "awaylosses", 0)) or 0
            local sumPld = hw + aw + hd + ad + hl + al
            if sumPld > pld then pld = sumPld end
            
            if pld > maxLeaguePld then
                maxLeaguePld = pld
                bestLtl = ltl
            end
        end
    end

    if bestLtl then
        currentStats.leagueId = tonumber(val(bestLtl, "leagueid", 14)) or 14
        currentStats.pld = maxLeaguePld
        currentStats.hw = tonumber(val(bestLtl, "homewins", 0)) or 0
        currentStats.aw = tonumber(val(bestLtl, "awaywins", 0)) or 0
        currentStats.hd = tonumber(val(bestLtl, "homedraws", 0)) or 0
        currentStats.ad = tonumber(val(bestLtl, "awaydraws", 0)) or 0
        currentStats.hl = tonumber(val(bestLtl, "homelosses", 0)) or 0
        currentStats.al = tonumber(val(bestLtl, "awaylosses", 0)) or 0
        currentStats.hgf = tonumber(val(bestLtl, "homegf", 0)) or 0
        currentStats.agf = tonumber(val(bestLtl, "awaygf", 0)) or 0
        currentStats.hga = tonumber(val(bestLtl, "homega", 0)) or 0
        currentStats.aga = tonumber(val(bestLtl, "awayga", 0)) or 0
    end

    -- 6. Parse Manager History & Big Buys
    -- Sort rows by season descending so the latest season is picked
    local allMhRows = safe_get_rows("career_managerhistory")
    local bigBuysMap = {}
    local currManagerMatches = 0
    local currManagerWins = 0
    local currManagerDraws = 0
    local currManagerLosses = 0
    local currManagerGF = 0
    local currManagerGA = 0
    local allTimeManagerMatches = 0
    local latestSeasonNum = 0

    for _, mh in ipairs(allMhRows) do
        local bName = val(mh, "bigbuyplayername", "")
        local bAmt = tonumber(val(mh, "bigbuyamount", 0)) or 0
        if bName ~= "" and bAmt > 0 then
            bigBuysMap[tostring(bName):lower()] = bAmt / 1000000
        end

        local tId = tonumber(val(mh, "teamid", 0)) or 0
        if tId == userTeamId then
            local sNum = tonumber(val(mh, "season", 0)) or 0
            local gp = tonumber(val(mh, "games_played", val(mh, "matchesplayed", 0))) or 0
            local w = tonumber(val(mh, "wins", 0)) or 0
            local d = tonumber(val(mh, "draws", 0)) or 0
            local l = tonumber(val(mh, "losses", 0)) or 0
            local gf = tonumber(val(mh, "goals_for", 0)) or 0
            local ga = tonumber(val(mh, "goals_against", 0)) or 0
            if gp == 0 and (w + d + l > 0) then gp = w + d + l end

            allTimeManagerMatches = allTimeManagerMatches + gp

            if sNum >= latestSeasonNum then
                latestSeasonNum = sNum
                currManagerMatches = gp
                currManagerWins = w
                currManagerDraws = d
                currManagerLosses = l
                currManagerGF = gf
                currManagerGA = ga
            end
        end
    end

    -- 7. Load Previous State
    local state = nil
    local sf = io.open(statePath, "r")
    if sf then
        sf:close()
        local ok, res = pcall(dofile, statePath)
        if ok and type(res) == "table" then state = res end
    end

    if not state and _G["FC26_SYNC_MIGRATED_STATE"] then
        state = _G["FC26_SYNC_MIGRATED_STATE"]
        _G["FC26_SYNC_MIGRATED_STATE"] = nil
    end

    if state then
        state.squad = state.squad or {}
        state.playerStats = state.playerStats or {}
        state.allPlayerTeams = state.allPlayerTeams or {}
    end

    -- Calculate squad performance deltas
    local totalAppsNow, totalAppsPrev = 0, 0
    local totalGoalsNow, totalGoalsPrev = 0, 0
    local totalCardsNow, totalCardsPrev = 0, 0
    for pId, pStats in pairs(currentPlayerStats) do
        totalAppsNow = totalAppsNow + (pStats.apps or 0)
        totalGoalsNow = totalGoalsNow + (pStats.goals or 0)
        totalCardsNow = totalCardsNow + (pStats.yellows or 0) + (pStats.reds or 0)
        local prevStats = (state and state.playerStats and state.playerStats[pId]) or {apps=0, goals=0, yellows=0, reds=0}
        totalAppsPrev = totalAppsPrev + (prevStats.apps or 0)
        totalGoalsPrev = totalGoalsPrev + (prevStats.goals or 0)
        totalCardsPrev = totalCardsPrev + (prevStats.yellows or 0) + (prevStats.reds or 0)
    end
    local appsDiffTotal = totalAppsNow - totalAppsPrev
    local goalsDiffTotal = totalGoalsNow - totalGoalsPrev
    local cardsDiffTotal = totalCardsNow - totalCardsPrev

    -- Calculate deltas
    local leagueMatchDiff = (state and state.pld) and (currentStats.pld - state.pld) or 0
    local managerDiff = (state and state.currManagerMatches) and (currManagerMatches - state.currManagerMatches) or 0
    local allTimeManagerDiff = (state and state.allTimeManagerMatches) and (allTimeManagerMatches - state.allTimeManagerMatches) or 0

    if not isAuto then
        my_log(string.format("Club: %s (ID: %d) | Season: %d | Date: %s | Squad: %d", userClubName, userTeamId, latestSeasonNum, todayDateStr, squadCount))
        my_log(string.format("League Links: Matches=%d | Latest Season Manager: %d Matches (W:%d D:%d L:%d | GF:%d GA:%d)",
            currentStats.pld, currManagerMatches, currManagerWins, currManagerDraws, currManagerLosses, currManagerGF, currManagerGA))
        my_log(string.format("Deltas -> League: +%d | ManagerSeason: +%d | Apps: +%d | Goals: +%d",
            leagueMatchDiff, managerDiff, appsDiffTotal, goalsDiffTotal))
    end

    local matchTriggered = false
    local triggerReason = "None"

    if FORCE_SYNC_LATEST then
        matchTriggered = true
        triggerReason = "FORCE_SYNC_LATEST manual override"
    elseif leagueMatchDiff > 0 then
        matchTriggered = true
        triggerReason = string.format("League Matches Delta (+%d matches)", leagueMatchDiff)
    elseif managerDiff > 0 then
        matchTriggered = true
        triggerReason = string.format("Manager Season Delta (+%d matches)", managerDiff)
    elseif allTimeManagerDiff > 0 then
        matchTriggered = true
        triggerReason = string.format("Manager History Delta (+%d matches)", allTimeManagerDiff)
    elseif appsDiffTotal > 0 or goalsDiffTotal > 0 or cardsDiffTotal > 0 then
        matchTriggered = true
        triggerReason = string.format("Squad Performance Delta (Apps: +%d, Goals: +%d, Cards: +%d)", appsDiffTotal, goalsDiffTotal, cardsDiffTotal)
    end

    if matchTriggered then
        my_log(">>> MATCH DETECTED! Reason: " .. triggerReason .. " <<<")

        local oppName = "League Opponent"
        local isHome = true
        local gf, ga = 2, 1
        local badge = "W"
        local competitionName = leagueMap[currentStats.leagueId] or "Championship"
        local isCup = false
        local isET = false
        local homePens = 0
        local awayPens = 0
        local stageName = "Matchday " .. math.max(1, currentStats.pld > 0 and currentStats.pld or (currManagerMatches > 0 and currManagerMatches or 1))

        local prevW = state and state.currManagerWins or 0
        local prevD = state and state.currManagerDraws or 0
        local prevL = state and state.currManagerLosses or 0
        local prevGF = state and state.currManagerGF or 0
        local prevGA = state and state.currManagerGA or 0

        if currManagerWins > prevW then
            badge = "W"
            gf = math.max(1, currManagerGF - prevGF)
            ga = math.max(0, currManagerGA - prevGA)
        elseif currManagerLosses > prevL then
            badge = "L"
            gf = math.max(0, currManagerGF - prevGF)
            ga = math.max(1, currManagerGA - prevGA)
        elseif currManagerDraws > prevD then
            badge = "D"
            gf = math.max(0, currManagerGF - prevGF)
            ga = gf
        else
            gf = math.max(1, goalsDiffTotal)
            ga = 0
            badge = "W"
        end

        -- Build Player Performances & Goal Details
        local matchPerfsStr = ""
        local matchGoalsStr = ""
        local addedGoals = false
        local addedPerfs = false
        local perfIndex = 0
        local topPlayerName = ""
        local topScore = -1

        local squadList = {}
        for pId, _ in pairs(currentSquad) do table.insert(squadList, pId) end
        table.sort(squadList)

        for _, pId in ipairs(squadList) do
            local pStats = currentPlayerStats[pId]
            local prevStats = (state and state.playerStats and state.playerStats[pId]) or {goals=0, assists=0, apps=0, rating=7.0}
            local appsDiff = (pStats and pStats.apps or 0) - prevStats.apps
            local goalsDiff = (pStats and pStats.goals or 0) - prevStats.goals
            local assistsDiff = (pStats and pStats.assists or 0) - prevStats.assists

            if appsDiff > 0 or goalsDiff > 0 or assistsDiff > 0 or (perfIndex < 11 and #squadList <= 18) then
                perfIndex = perfIndex + 1
                local pName = get_player_name(pId)
                local pRating = math.min(9.9, math.max(6.0, 7.0 + goalsDiff * 0.8 + assistsDiff * 0.5 + (perfIndex % 3) * 0.2))
                
                local pScore = goalsDiff * 3 + assistsDiff * 2 + pRating
                if pScore > topScore then
                    topScore = pScore
                    topPlayerName = pName
                end

                local perfBlock = string.format(
                    '{"playerId":"%d","playerName":"%s","goals":%d,"assists":%d,"rating":%.1f,"passes":%d,"passAccuracy":%d,"shots":%d,"shotAccuracy":%d,"saves":0,"penaltySaves":0,"tackles":%d,"tacklePercentage":80,"interceptions":1,"cleanSheet":%s,"yellowCard":false,"redCard":false,"isMotm":false}',
                    pId, pName, goalsDiff, assistsDiff, pRating, 35 + (perfIndex * 3) % 20, 85 + (perfIndex % 8), goalsDiff + (perfIndex % 2), (goalsDiff > 0) and 75 or 0, 2 + (perfIndex % 3), (ga == 0) and "true" or "false"
                )
                if addedPerfs then matchPerfsStr = matchPerfsStr .. "," else addedPerfs = true end
                matchPerfsStr = matchPerfsStr .. perfBlock
            end
        end

        for g = 1, gf do
            local scorerName = topPlayerName ~= "" and topPlayerName or userClubName .. " Striker"
            if squadList[g] then scorerName = get_player_name(squadList[g]) end
            local min = 15 + ((g * 28) % 70)
            local gBlock = string.format('{"playerName":"%s","minute":%d,"isOpponent":false}', scorerName, min)
            if addedGoals then matchGoalsStr = matchGoalsStr .. "," else addedGoals = true end
            matchGoalsStr = matchGoalsStr .. gBlock
        end
        for g = 1, ga do
            local min = 20 + ((g * 31) % 65)
            local gBlock = string.format('{"playerName":"%s (Opponent)","minute":%d,"isOpponent":true}', oppName, min)
            if addedGoals then matchGoalsStr = matchGoalsStr .. "," else addedGoals = true end
            matchGoalsStr = matchGoalsStr .. gBlock
        end

        local shotsF = math.max(gf + 3, math.floor(gf * 3.2 + 4))
        local shotsA = math.max(ga + 2, math.floor(ga * 3.0 + 3))
        local xGF = math.floor((gf * 0.82 + 0.35) * 100) / 100
        local xGA = math.floor((ga * 0.78 + 0.28) * 100) / 100
        local poss = isHome and (gf >= ga and 56 or 50) or (gf >= ga and 52 or 46)

        local locMsg = isHome and "vs" or "@"
        local title = isCup and "Cup Match Final" or "Live Match Final"
        local msg = string.format("Full Time: %s %d - %d %s (%s)", locMsg, gf, ga, oppName, competitionName)

        local payload = string.format(
            '{"dataType":"match","title":"%s","message":"%s","opponentName":"%s","isHome":%s,"goalsFor":%d,"goalsAgainst":%d,"resultBadge":"%s","date":"%s","competition":"%s","stage":"%s","isExtraTime":%s,"homePenalties":%d,"awayPenalties":%d,"possession":%d,"passingAccuracy":88,"shotsFor":%d,"shotsAgainst":%d,"expectedGoals":%.2f,"expectedGoalsAgainst":%.2f,"leaguePosition":%d,"motmName":"%s","playerPerformances":[%s],"goalDetails":[%s]}',
            title, msg, oppName, isHome and "true" or "false", gf, ga, badge, todayDateStr, competitionName, stageName, isET and "true" or "false", homePens, awayPens, poss, shotsF, shotsA, xGF, xGA, 1, topPlayerName, matchPerfsStr, matchGoalsStr
        )

        send_to_bridge(payload, "Match: " .. msg)
        my_log(">>> SENT MATCH RESULT: " .. msg)
        pcall(print, string.format("[TelemetrySync ⚡ MATCH SYNCED] %s", msg))
    else
        if not isAuto then
            my_log("No new matches detected since last sync.")
            my_log("TIP: Click 'Advance' after a match so stats save, or run 'send_latest_match.lua' to beam latest match instantly!")
        end
    end

    -- A failed or partial read of the squad must never be treated as "everyone left" (and must not overwrite the saved state).
    local prevSquadSize = 0
    for _ in pairs((state and state.squad) or {}) do prevSquadSize = prevSquadSize + 1 end
    if squadCount == 0 or (prevSquadSize >= 12 and squadCount < math.floor(prevSquadSize / 2)) then
        if not isAuto then my_log("Squad read looks incomplete (" .. tostring(squadCount) .. " players) - transfer check skipped this pass") end
        return
    end

    local loansIn, loansOut = {}, {}
    for _, lo in ipairs(safe_get_rows("playerloans")) do
        local lpid = tonumber(val(lo, "playerid", 0)) or 0
        local lfrom = tonumber(val(lo, "teamidloanedfrom", 0)) or 0
        if lpid > 0 then
            -- FC 26 has no "loaned to" column in playerloans: a loaned-out player is simply linked to his loan club now
            if lfrom == userTeamId then
                local lto = playerToTeamMap[lpid] or 0
                loansOut[lpid] = (lto ~= userTeamId) and lto or 0
            elseif lfrom > 0 and currentSquad[lpid] then
                loansIn[lpid] = lfrom
            end
        end
    end

    -- 8. PRECISION TRANSFER LOGIC
    for pId, _ in pairs(currentSquad) do
        if state and state.squad and not state.squad[pId] then
            local pName = get_player_name(pId)
            local feeM = 0.0
            local transferType = loansIn[pId] and "In (Loan)" or "In (Permanent)"
            local fromClub = ""
            
            -- Step A: Did this player come from a specific team?
            local prevTid = loansIn[pId] or (state.allPlayerTeams and state.allPlayerTeams[pId]) or 0
            if prevTid > 0 and prevTid ~= userTeamId then
                fromClub = get_team_name(prevTid)
            end

            -- Step B: If player came from another club, preserve that club name!
            local cameFromAnotherClub = (fromClub ~= "" and fromClub ~= "External Club" and not tostring(fromClub):find("^Team ") and fromClub ~= userClubName)

            if cameFromAnotherClub then
                -- Check if this player matches bigbuyplayername in career_managerhistory
                for bNameLower, bFee in pairs(bigBuysMap) do
                    if pName:lower():find(bNameLower, 1, true) or bNameLower:find(pName:lower(), 1, true) then
                        feeM = bFee
                        break
                    end
                end
                -- If no fee is found, set to 0.0 as requested
            else
                -- Not from an external club: check if Youth Academy
                -- FC 26 has no academy flag and player ids do not tell a signing from a promotion (bought players
                -- also have ids above 300000), so only an explicit flag counts. FC26_Auto_Capture.lua uses the
                -- academy list and the contract instead; this older script cannot tell the two apart.
                local p = playersMap[pId]
                local isAcad = false
                if p then
                    local isAcadField = tonumber(val(p, "isacademy", 0)) or 0
                    if isAcadField == 1 then isAcad = true end
                end

                if isAcad then
                    fromClub = "Youth Academy"
                    transferType = "Promoted In"
                    feeM = 0.0
                else
                    fromClub = "Unknown club"
                    feeM = 0.0
                end
            end

            -- Step C: Resolve Exact Transfer Date from playerjointeamdate
            local transferDateStr = todayDateStr
            local p = playersMap[pId]
            if p then
                local joinDays = tonumber(val(p, "playerjointeamdate", 0)) or 0
                if joinDays > 0 then
                    transferDateStr = decode_fifa_date(joinDays, cYear)
                end
            end

            local msg = string.format("%s joined from %s!", pName, fromClub)
            local payload = string.format(
                '{"dataType":"transfer","title":"Transfer: %s","message":"%s","playerName":"%s","type":"%s","feeM":%.2f,"fromToClub":"%s","date":"%s"}',
                pName, msg, pName, transferType, feeM, fromClub, transferDateStr
            )
            send_to_bridge(payload, "Transfer In: " .. pName)
            my_log(string.format("Sent Transfer In: %s (From: %s | Fee: £%.2fM | Date: %s)", pName, fromClub, feeM, transferDateStr))
            pcall(print, string.format("[TelemetrySync ⚡ TRANSFER IN] %s (From: %s | £%.2fM | %s)", pName, fromClub, feeM, transferDateStr))
        end
    end

    for pId, _ in pairs(state and state.squad or {}) do
        if not currentSquad[pId] then
            local pName = get_player_name(pId)
            local toClub = "External Club"
            local newTid = loansOut[pId] or playerToTeamMap[pId]
            if newTid and newTid > 0 and newTid ~= userTeamId then
                toClub = get_team_name(newTid)
            elseif loansOut[pId] then
                toClub = "Unknown club" -- on loan, but the loan club could not be read
            else
                toClub = "Free Agent / Released"
            end
            local outType = loansOut[pId] and "Out (Loan)" or "Out (Permanent)"

            local msg = string.format("%s transferred to %s.", pName, toClub)
            local payload = string.format(
                '{"dataType":"transfer","title":"Transfer: %s","message":"%s","playerName":"%s","type":"%s","feeM":0.0,"fromToClub":"%s","date":"%s"}',
                pName, msg, pName, outType, toClub, todayDateStr
            )
            send_to_bridge(payload, "Transfer Out: " .. pName)
            my_log(string.format("Sent Transfer Out: %s (To: %s)", pName, toClub))
            pcall(print, string.format("[TelemetrySync ⚡ TRANSFER OUT] %s", msg))
        end
    end

    -- 9. Save New State
    local squadDump = "{"
    for pId, _ in pairs(currentSquad) do squadDump = squadDump .. "[" .. pId .. "]=true," end
    squadDump = squadDump .. "}"

    local pStatsDump = "{"
    for pId, ps in pairs(currentPlayerStats) do
        pStatsDump = pStatsDump .. string.format("[%d]={goals=%d, assists=%d, apps=%d, yellows=%d, reds=%d},", pId, ps.goals, ps.assists, ps.apps, ps.yellows or 0, ps.reds or 0)
    end
    pStatsDump = pStatsDump .. "}"

    local allTeamsDump = "{"
    for pId, tId in pairs(playerToTeamMap) do allTeamsDump = allTeamsDump .. string.format("[%d]=%d,", pId, tId) end
    allTeamsDump = allTeamsDump .. "}"

    local outf = io.open(statePath, "w")
    if outf then
        outf:write(string.format([[
return {
    pld = %d, hw = %d, aw = %d, hd = %d, ad = %d, hl = %d, al = %d,
    hgf = %d, agf = %d, hga = %d, aga = %d,
    currdate = %d,
    currManagerMatches = %d,
    currManagerWins = %d,
    currManagerDraws = %d,
    currManagerLosses = %d,
    currManagerGF = %d,
    currManagerGA = %d,
    allTimeManagerMatches = %d,
    latestSeasonNum = %d,
    squad = %s,
    playerStats = %s,
    allPlayerTeams = %s
}
]], currentStats.pld, currentStats.hw, currentStats.aw, currentStats.hd, currentStats.ad, currentStats.hl, currentStats.al,
    currentStats.hgf, currentStats.agf, currentStats.hga, currentStats.aga,
    currdate,
    currManagerMatches,
    currManagerWins,
    currManagerDraws,
    currManagerLosses,
    currManagerGF,
    currManagerGA,
    allTimeManagerMatches,
    latestSeasonNum,
    squadDump, pStatsDump, allTeamsDump))
        outf:close()
        if not isAuto then
            my_log("State saved successfully (" .. statePath .. ").")
        end
    else
        my_log("WARNING: could not write " .. statePath .. " - changes cannot be detected without it")
    end
end

-- Deliver anything an earlier session could not hand to the bridge, then run a sync immediately
pcall(adopt_outbox_leftovers)
local ok, err = pcall(function() run_telemetry_sync(false) end)
if not ok then
    my_log("CRITICAL ERROR: " .. tostring(err))
    print("[TelemetrySync ERROR] " .. tostring(err))
end

-- Background timer registration if available in this Live Editor build
if AUTO_SYNC then
    if _G.FIFA_TELEMETRY_TIMER then
        pcall(function()
            if type(_G.FIFA_TELEMETRY_TIMER.destroy) == "function" then
                _G.FIFA_TELEMETRY_TIMER.destroy()
            elseif type(_G.FIFA_TELEMETRY_TIMER.setEnabled) == "function" then
                _G.FIFA_TELEMETRY_TIMER.setEnabled(false)
            end
        end)
        _G.FIFA_TELEMETRY_TIMER = nil
    end

    local timerCreated = false
    if type(createTimer) == "function" then
        local okTimer, t = pcall(function()
            local timer = createTimer(nil)
            timer.Interval = AUTO_SYNC_INTERVAL_SEC * 1000
            timer.OnTimer = function()
                local okRun, errRun = pcall(function() run_telemetry_sync(true) end)
                if not okRun then
                    local now = os.time()
                    if now - (_G["FC26_SYNC_LAST_ERR"] or 0) >= 60 then
                        _G["FC26_SYNC_LAST_ERR"] = now
                        my_log("ERROR in background sync: " .. tostring(errRun))
                    end
                end
            end
            return timer
        end)
        if okTimer and t then
            _G.FIFA_TELEMETRY_TIMER = t
            timerCreated = true
        end
    end

    if timerCreated then
        print("[TelemetrySync] Background polling active (every " .. AUTO_SYNC_INTERVAL_SEC .. "s).")
    else
        print("=========================================================================")
        print(" [READY] EA FC LIVE TELEMETRY SYNC ACTIVE (v4.9)!")
        print(" Whenever you finish a match or make transfers:")
        print(" 1. Click 'Advance' on the post-match screen to return to Central Hub.")
        print(" 2. In Live Editor, tap 'Run' on telemetry_sync.lua (or hotkey F9)!")
        print(" 3. TIP: Run 'send_latest_match.lua' to beam your latest match immediately!")
        print("=========================================================================")
    end
end
