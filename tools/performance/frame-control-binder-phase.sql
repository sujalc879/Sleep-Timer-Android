-- Read-only main-thread synchronous calls after TimerJourney’s eight-second wave.
-- Requires one measureBlock; includes all controls through measurement end. Codes are platform-specific.
WITH measured AS (
    SELECT min(ts) AS start_ts, max(ts + dur) AS end_ts, count(*) AS sections
    FROM slice WHERE name = 'measureBlock' AND dur >= 0
)
SELECT
    round((s.ts - m.start_ts) / 1e6, 3) AS offset_ms,
    round(s.dur / 1e6, 3) AS duration_ms,
    (SELECT group_concat(key || '=' || display_value, '; ') FROM args WHERE arg_set_id = s.arg_set_id) AS args
FROM slice s
JOIN thread_track tt ON s.track_id = tt.id
JOIN thread t USING (utid)
JOIN process p USING (upid)
JOIN measured m ON m.sections = 1
WHERE p.name = 'com.sujal.sleeptimer.benchmark' AND t.tid = p.pid AND s.name = 'binder transaction'
    AND s.ts >= m.start_ts + 8000000000 AND s.ts < m.end_ts
ORDER BY s.ts;
