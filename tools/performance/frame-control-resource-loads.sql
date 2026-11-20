-- Read-only main-thread XML opens during the entire post-wave control phase.
-- An empty result means zero opens only when the separate capture audit verifies one measureBlock.
WITH measured AS (
    SELECT min(ts) AS start_ts, max(ts + dur) AS end_ts, count(*) AS sections
    FROM slice WHERE name = 'measureBlock' AND dur >= 0
)
SELECT s.name, round((s.ts - m.start_ts) / 1e6, 3) AS offset_ms,
    round(s.dur / 1e6, 3) AS duration_ms
FROM slice s JOIN thread_track tt ON s.track_id = tt.id
JOIN thread t USING (utid) JOIN process p USING (upid)
JOIN measured m ON m.sections = 1
WHERE p.name = 'com.sujal.sleeptimer.benchmark' AND t.tid = p.pid
    AND s.name LIKE 'AssetManager::OpenXmlAsset(%' AND s.dur >= 0
    AND s.ts >= m.start_ts + 8000000000 AND s.ts < m.end_ts
ORDER BY s.ts;
