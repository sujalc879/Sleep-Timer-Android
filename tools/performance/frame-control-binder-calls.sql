-- Read-only diagnostic for the first 450 ms after TimerJourney's eight-second wave.
-- Binder transaction codes are platform-specific; retain arguments before interpreting them.
WITH measured AS (
    SELECT min(ts) AS start_ts, count(*) AS sections
    FROM slice WHERE name = 'measureBlock' AND dur >= 0
)
SELECT
    s.id,
    round((s.ts - m.start_ts) / 1e6, 3) AS offset_ms,
    round(s.dur / 1e6, 3) AS duration_ms,
    s.name,
    (SELECT group_concat(key || '=' || display_value, '; ') FROM args WHERE arg_set_id = s.arg_set_id) AS args
FROM slice s
JOIN thread_track tt ON s.track_id = tt.id
JOIN thread t USING (utid)
JOIN process p USING (upid)
JOIN measured m ON m.sections = 1
WHERE p.name = 'com.sujal.sleeptimer.benchmark' AND t.tid = p.pid
    AND s.ts >= m.start_ts + 8000000000 AND s.ts < m.start_ts + 8450000000
    AND (s.name LIKE '%binder%' OR s.name LIKE '%AIDL%' OR s.name LIKE '%Input%' OR s.name LIKE '%motion%')
ORDER BY s.ts;
