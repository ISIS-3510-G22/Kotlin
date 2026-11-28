-- BQ Type 2 — At which step of the create-plan flow do users most frequently abandon?
-- Owner: Maria
--
-- Source table: Firestore BigQuery Export extension
--   Project:  plansync-bc8f6
--   Dataset:  firestore_export
--   Table:    events_raw_latest  (same extension as popular_days.sql)
--
-- The analytics events are written by AnalyticsRepository.logEvent() with
-- type = "create_plan_abandoned" and a "step" field (1 = named plan,
-- 2 = date selected, 3 = activities added).
--
-- How to run:
--   1. Open BigQuery console → plansync-bc8f6
--   2. Paste this query and run once to verify the results.
--   3. Schedule as a daily query that overwrites the output table:
--      (Scheduled queries > Create new)
--      Destination dataset:  firestore_export
--      Destination table:    abandonment_result
--      Write preference:     Overwrite table
--   4. Deploy a Cloud Function (Node.js) triggered by the BigQuery job
--      completion (or by a Cloud Scheduler calling it daily) that:
--        a. Reads the first row of firestore_export.abandonment_result
--        b. Writes to Firestore:
--             db.collection("insights")
--               .document("create_plan_abandonment")
--               .set({ topStep: row.topStep, abandonCount: row.abandonCount })
--      This is the same pattern as the Cloud Function for popular_days.
--
-- Firestore document shape (what InsightsRepository.getTopAbandonmentStep() reads):
--   insights/create_plan_abandonment
--     topStep     : Number   — step with the highest abandonment count
--     abandonCount: Number   — how many sessions abandoned at that step
--
-- Column aliases below match the Firestore field names so the Cloud Function
-- can write row.topStep / row.abandonCount directly without mapping.

SELECT
    SAFE_CAST(JSON_VALUE(data, '$.step') AS INT64) AS topStep,
    COUNT(*)                                        AS abandonCount
FROM `plansync-bc8f6.firestore_export.events_raw_latest`
WHERE JSON_VALUE(data, '$.type') = 'create_plan_abandoned'
  AND JSON_VALUE(data, '$.step') IS NOT NULL
  AND timestamp >= TIMESTAMP_SUB(CURRENT_TIMESTAMP(), INTERVAL 90 DAY)
GROUP BY topStep
ORDER BY abandonCount DESC
LIMIT 1
