-- Read-only Perfetto query. Run separately: the bundled processor accepts one result set.
-- Nested slice totals overlap; these totals are not exclusive CPU time.
SELECT
    p.pid,
    t.name AS thread,
    s.name,
    count(*) AS samples,
    round(sum(s.dur) / 1e6, 2) AS total_ms,
    round(avg(s.dur) / 1e6, 3) AS average_ms,
    round(max(s.dur) / 1e6, 2) AS maximum_ms
FROM slice s
JOIN thread_track tt ON s.track_id = tt.id
JOIN thread t USING (utid)
JOIN process p USING (upid)
WHERE p.name = 'com.sujal.sleeptimer.benchmark' AND s.dur > 0
GROUP BY p.pid, t.name, s.name
ORDER BY total_ms DESC
LIMIT 35;
