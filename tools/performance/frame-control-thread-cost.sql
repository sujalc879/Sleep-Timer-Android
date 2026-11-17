-- Read-only diagnostic around TimerJourney's extension/reduction/cancel controls.
-- Nested slice totals overlap and are not exclusive CPU time. Requires one measureBlock.
WITH measured AS (
    SELECT min(ts) AS start_ts, count(*) AS sections
    FROM slice WHERE name = 'measureBlock' AND dur >= 0
)
SELECT
    t.name AS thread,
    s.name,
    count(*) AS samples,
    round(sum(s.dur) / 1e6, 3) AS nested_total_ms,
    round(max(s.dur) / 1e6, 3) AS maximum_ms
FROM slice s
JOIN thread_track tt ON s.track_id = tt.id
JOIN thread t USING (utid)
JOIN process p USING (upid)
JOIN measured m ON m.sections = 1
WHERE p.name = 'com.sujal.sleeptimer.benchmark' AND s.dur > 0
    AND s.ts < m.start_ts + 8450000000 AND s.ts + s.dur > m.start_ts + 8000000000
    AND (t.tid = p.pid OR t.name = 'RenderThread')
GROUP BY t.name, s.name
ORDER BY nested_total_ms DESC
LIMIT 40;
