-- Read-only Perfetto query. FrameTimeline records are distinct from FrameTimingMetric samples.
-- Keep Buffer Stuffing and Dropped Frame classifications visible, without equating them.
-- Scope: the whole capture, including records outside the benchmark's measured window.
SELECT
    a.jank_type,
    a.present_type,
    count(*) AS frames,
    round(avg(a.dur) / 1e6, 2) AS average_ms,
    round(max(a.dur) / 1e6, 2) AS maximum_ms
FROM actual_frame_timeline_slice a
JOIN process p USING (upid)
WHERE p.name = 'com.sujal.sleeptimer.benchmark'
GROUP BY a.jank_type, a.present_type;
