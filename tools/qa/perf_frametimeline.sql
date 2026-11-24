-- Tutti i tempi delle slice e dei contatori sono nel clock della traccia.
-- I token si associano in Python dopo la scelta del processo e del layer.
SELECT 'actual' AS kind, a.ts, a.dur, a.upid, p.pid, p.name AS name,
       a.layer_name AS layer, a.surface_frame_token AS token, a.display_frame_token AS display_token,
       a.present_type AS present, a.prediction_type AS prediction, a.jank_type AS jank
FROM actual_frame_timeline_slice a LEFT JOIN process p USING (upid)
UNION ALL
SELECT 'expected', e.ts, e.dur, e.upid, p.pid, p.name, e.layer_name,
       e.surface_frame_token, e.display_frame_token, NULL, NULL, NULL
FROM expected_frame_timeline_slice e LEFT JOIN process p USING (upid)
UNION ALL
SELECT 'clock', ts, clock_value, NULL, NULL, clock_name, NULL, NULL, NULL, NULL, NULL, NULL
FROM clock_snapshot WHERE clock_name = 'MONOTONIC'
UNION ALL
SELECT 'counter', c.ts, CAST(c.value AS INT), t.id, NULL, t.name, NULL, NULL, NULL, NULL, NULL, NULL
FROM counter c JOIN counter_track t ON c.track_id = t.id
WHERE t.name = 'VsyncPeriod' OR t.name GLOB 'HW_VSYNC_[0-9]*'
UNION ALL
SELECT 'loss', NULL, value, NULL, NULL, name, NULL, NULL, NULL, NULL, NULL, NULL
FROM stats WHERE value > 0 AND severity IN ('data_loss', 'error')
UNION ALL
SELECT 'log', l.ts, NULL, NULL, p.pid, l.msg, NULL, NULL, NULL, NULL, NULL, NULL
FROM android_logs l LEFT JOIN thread t USING (utid) LEFT JOIN process p USING (upid)
WHERE l.tag = 'FluidMisure' AND l.msg GLOB '*SCENARIO *'
