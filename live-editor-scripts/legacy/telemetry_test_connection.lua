-- =========================================================================
-- TELEMETRY CONNECTION & BRIDGE TEST SCRIPT
-- Run this in Live Editor to verify that Live Editor can reach the Bridge!
-- =========================================================================

local function my_log(msg)
    local s = "[TelemetryTest] " .. tostring(msg)
    pcall(print, s)
end

my_log(">>> TESTING CONNECTION TO TELEMETRY BRIDGE (Port 5050) <<<")

local temp_dir = os.getenv("TEMP") or os.getenv("TMP") or "."
local temp_json = temp_dir .. "\\fifa_test_payload.json"

local today = os.date("%Y-%m-%d")
local payload = string.format([[{
    "dataType": "match",
    "title": "Live Match Final",
    "message": "Full Time: vs 3 - 1 Arsenal (Premier League)",
    "opponentName": "Arsenal",
    "isHome": true,
    "goalsFor": 3,
    "goalsAgainst": 1,
    "resultBadge": "W",
    "date": "%s",
    "competition": "Premier League",
    "stage": "Matchday 10",
    "shotsFor": 14,
    "shotsAgainst": 5,
    "possession": 58,
    "passingAccuracy": 89,
    "expectedGoals": 2.65,
    "expectedGoalsAgainst": 0.85,
    "leaguePosition": 1,
    "motmName": "Bukayo Saka",
    "playerPerformances": [
        {"playerName": "Bukayo Saka", "goals": 2, "assists": 1, "rating": 9.2, "passes": 42, "passAccuracy": 88, "shots": 4, "shotAccuracy": 75, "saves": 0, "penaltySaves": 0, "tackles": 1, "tacklePercentage": 100, "interceptions": 0, "cleanSheet": false, "yellowCard": false, "redCard": false, "isMotm": true},
        {"playerName": "Declan Rice", "goals": 1, "assists": 0, "rating": 8.1, "passes": 68, "passAccuracy": 92, "shots": 2, "shotAccuracy": 50, "saves": 0, "penaltySaves": 0, "tackles": 5, "tacklePercentage": 80, "interceptions": 3, "cleanSheet": false, "yellowCard": true, "redCard": false, "isMotm": false},
        {"playerName": "Martin Odegaard", "goals": 0, "assists": 2, "rating": 8.5, "passes": 55, "passAccuracy": 91, "shots": 1, "shotAccuracy": 100, "saves": 0, "penaltySaves": 0, "tackles": 2, "tacklePercentage": 50, "interceptions": 1, "cleanSheet": false, "yellowCard": false, "redCard": false, "isMotm": false}
    ],
    "goalDetails": [
        {"playerName": "Bukayo Saka", "minute": 24, "isOpponent": false},
        {"playerName": "Declan Rice", "minute": 58, "isOpponent": false},
        {"playerName": "Bukayo Saka", "minute": 76, "isOpponent": false},
        {"playerName": "Gabriel Martinelli (Opponent)", "minute": 83, "isOpponent": true}
    ]
}]], today)

local f = io.open(temp_json, "wb")
if f then
    f:write(payload)
    f:close()
    my_log("Wrote test payload (" .. tostring(#payload) .. " bytes)")
end

local out_path = temp_dir .. "\\fifa_test_result.txt"

local function http_code(args)
    os.remove(out_path)
    os.execute('curl.exe -s -o NUL -w "%{http_code}" -m 5 ' .. args .. ' > "' .. out_path .. '" 2>nul')
    local rf = io.open(out_path, "r")
    local code = rf and rf:read("*l") or ""
    if rf then rf:close() end
    return (code ~= "") and code or "no answer"
end

-- 1. Is the bridge reachable at all? (GET /events is what the phone uses)
local readCode = http_code('http://127.0.0.1:5050/events')
my_log("GET  /events  -> HTTP " .. readCode)

-- 2. Can this PC post a match to it? (this adds a sample "Arsenal 3-1" match to your app)
local postCode = http_code('-X POST -H "Content-Type: application/json; charset=utf-8" --data-binary @"' .. temp_json .. '" http://127.0.0.1:5050')
my_log("POST sample match -> HTTP " .. postCode)

print("=========================================================================")
if postCode == "200" then
    print(" [OK] The bridge accepted the test match. It should appear in your phone app within ~10 seconds.")
elseif readCode == "no answer" or readCode == "000" then
    print(" [FAIL] Nothing is listening on port 5050. Run FC26_Launcher.bat first.")
else
    print(" [FAIL] The bridge answered GET with HTTP " .. readCode .. " but POST with HTTP " .. postCode .. ".")
    print("        403 = request refused (writes are only accepted from this PC), 413 = too large, 400 = bad request.")
end
print("=========================================================================")
os.remove(temp_json)
