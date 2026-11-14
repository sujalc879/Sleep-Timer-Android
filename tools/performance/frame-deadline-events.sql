-- FrameTimeline drop/deadline events relative to the single measured interval.
WITH measured AS (
    SELECT min(ts) AS start_ts, max(ts + dur) AS end_ts, count(*) AS sections
    FROM slice
    WHERE name = 'measureBlock' AND dur >= 0
)
SELECT
    a.jank_type,
    a.present_type,
    round((a.ts - m.start_ts) / 1e6, 2) AS offset_ms,
    round((m.end_ts - m.start_ts) / 1e6, 2) AS measured_duration_ms,
    round(a.dur / 1e6, 2) AS frame_duration_ms
FROM actual_frame_timeline_slice a
JOIN process p USING (upid)
JOIN measured m ON m.sections = 1 AND a.ts >= m.start_ts AND a.ts < m.end_ts
WHERE p.name = 'com.sujal.sleeptimer.benchmark'
    AND (a.present_type = 'Dropped Frame' OR a.jank_type LIKE '%Deadline Missed%')
ORDER BY a.ts;
