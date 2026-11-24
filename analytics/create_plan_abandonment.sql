-- Type 2 BQ: At which step of the create-plan flow do users most frequently abandon?
-- Scheduled query. Cloud Function reads top row and writes to insights/create_plan_abandonment
-- with fields: topStep (int), abandonCount (int).
SELECT
    SAFE_CAST(JSON_VALUE(data, '$.step') AS INT64) AS step,
    COUNT(*)                                        AS abandon_count
FROM `plansync-bc8f6.firestore_export.events_raw_latest`
WHERE JSON_VALUE(data, '$.type') = 'create_plan_abandoned'
  AND JSON_VALUE(data, '$.step') IS NOT NULL
  AND timestamp >= TIMESTAMP_SUB(CURRENT_TIMESTAMP(), INTERVAL 90 DAY)
GROUP BY step
ORDER BY abandon_count DESC
