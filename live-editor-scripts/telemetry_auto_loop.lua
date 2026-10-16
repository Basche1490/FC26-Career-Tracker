-- =========================================================================
-- EA SPORTS FC TELEMETRY AUTO-LOOP LAUNCHER (OPTION B - FULLY AUTOMATIC)
-- Starts continuous background telemetry polling in Live Editor.
-- Zero hotkeys or manual clicks needed while playing!
-- =========================================================================

local function my_log(msg)
    local s = "[TelemetryAutoLoop] " .. tostring(msg)
    pcall(print, s)
end

my_log("==========================================================")
my_log("EA FC LIVE TELEMETRY: OPTION B (FULLY AUTOMATIC)")
my_log("==========================================================")

-- Helper: find one of our scripts across the usual folders
local function get_script_path(fileName)
    local userProfile = os.getenv("USERPROFILE") or ""
    local candidatePaths = {
        userProfile .. "\\Desktop\\FC26-LE\\FC 26 LE v26.3.6\\lua\\scripts\\" .. fileName,
        userProfile .. "\\Desktop\\FC26-LE\\" .. fileName,
        fileName,
        "FC 26 LE v26.3.6\\lua\\scripts\\" .. fileName,
        "lua\\scripts\\" .. fileName,
        userProfile .. "\\Desktop\\" .. fileName,
        userProfile .. "\\Downloads\\" .. fileName
    }
    for _, p in ipairs(candidatePaths) do
        local f = io.open(p, "r")
        if f then
            f:close()
            return p
        end
    end
    return nil
end

-- FC26_Auto_Capture.lua reads the real fixtures, lineups and stats: start it first.
-- telemetry_sync.lua (database-delta fallback) is only used when Auto Capture cannot start.
local ok, err = false, "FC26_Auto_Capture.lua not found"
local autoPath = get_script_path("FC26_Auto_Capture.lua")
if autoPath then
    my_log("Loading real-data capture: " .. autoPath)
    ok, err = pcall(dofile, autoPath)
    if not ok then my_log("FC26_Auto_Capture.lua could not start: " .. tostring(err)) end
end
if not ok then
    local syncPath = get_script_path("telemetry_sync.lua") or "telemetry_sync.lua"
    my_log("Falling back to telemetry script: " .. syncPath)
    ok, err = pcall(dofile, syncPath)
end

if not ok then
    my_log("Error starting automatic telemetry sync: " .. tostring(err))
    print("[TelemetryAutoLoop ERROR] " .. tostring(err))
else
    my_log("🟢 OPTION B IS RUNNING IN BACKGROUND!")
    print("==========================================================")
    print(" [OPTION B ACTIVE] FULLY AUTOMATIC MODE ENGAGED")
    print(" 1. Career Mode updates will be detected automatically.")
    print(" 2. Played matches will beam to your phone on Full Time.")
    print(" 3. Transfers and signings will beam when completed.")
    print(" No hotkeys or manual button clicks are needed!")
    print("==========================================================")
end
