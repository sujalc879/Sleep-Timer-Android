-- Read-only Perfetto query restricted to frames starting within measureBlock.
-- Benchmark 1.5.0 MacrobenchmarkPhase.kt emits this instrumentation trace section.
-- Counts can differ from FrameTimingMetric, which also matches UI/render/expected frame IDs.
WITH measured AS (
    SELECT min(ts) AS start_ts, max(ts + dur) AS end_ts, count(*) AS sections
    FROM slice
    WHERE name = 'measureBlock' AND dur >= 0
)
SELECT
    a.jank_type,
    a.present_type,
    count(*) AS frames,
    round(avg(a.dur) / 1e6, 2) AS average_ms,
    round(max(a.dur) / 1e6, 2) AS maximum_ms
FROM actual_frame_timeline_slice a
JOIN process p USING (upid)
JOIN measured m ON m.sections = 1 AND a.ts >= m.start_ts AND a.ts < m.end_ts
WHERE p.name = 'com.sujal.sleeptimer.benchmark'
GROUP BY a.jank_type, a.present_type;
