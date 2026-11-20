-- Read-only capture/window audit for Benchmark 1.5.0's whole-trace frame metric.
-- Requires exactly one measureBlock. Region is determined by each slice's start.
WITH measured AS (
    SELECT min(ts) AS start_ts, max(ts + dur) AS end_ts, count(*) AS sections
    FROM slice WHERE name = 'measureBlock' AND dur >= 0
), frames AS (
    SELECT 'actual' AS kind, a.ts, a.dur
    FROM actual_frame_timeline_slice a JOIN process p USING (upid)
    WHERE p.name = 'com.sujal.sleeptimer.benchmark' AND a.dur > 0
    UNION ALL
    SELECT 'expected', e.ts, e.dur
    FROM expected_frame_timeline_slice e JOIN process p USING (upid)
    WHERE p.name = 'com.sujal.sleeptimer.benchmark' AND e.dur > 0
    UNION ALL
    SELECT CASE WHEN t.tid = p.pid THEN 'ui' ELSE 'render' END, s.ts, s.dur
    FROM slice s JOIN thread_track tt ON s.track_id = tt.id
    JOIN thread t USING (utid) JOIN process p USING (upid)
    WHERE p.name = 'com.sujal.sleeptimer.benchmark' AND s.dur > 0 AND (
        (t.tid = p.pid AND s.name LIKE 'Choreographer#doFrame%'
            AND s.name NOT LIKE 'Choreographer#doFrame - resynced to%')
        OR (t.name = 'RenderThread' AND s.name LIKE 'DrawFrame%')
    )
), regions AS (
    SELECT f.*, m.start_ts, CASE
        WHEN f.ts < m.start_ts THEN 'before'
        WHEN f.ts >= m.end_ts THEN 'after'
        ELSE 'measured'
    END AS region
    FROM frames f JOIN measured m ON m.sections = 1
)
SELECT kind, region, count(*) AS records,
    round((min(ts) - start_ts) / 1e6, 3) AS first_offset_ms,
    round((max(ts + dur) - start_ts) / 1e6, 3) AS last_end_offset_ms
FROM regions GROUP BY kind, region, start_ts
UNION ALL
SELECT 'measureBlock', 'measured', 1, 0, round((end_ts - start_ts) / 1e6, 3)
FROM measured WHERE sections = 1
UNION ALL
SELECT 'capture', 'whole', 1, round((b.start_ts - m.start_ts) / 1e6, 3),
    round((b.end_ts - m.start_ts) / 1e6, 3)
FROM trace_bounds b JOIN measured m ON m.sections = 1
ORDER BY kind, region;
