-- BQ Type 4 — Most popular days for plan creation
-- Owner: Maria
-- Question: What days of the week do users create plans the most?
--
-- Source table: Firebase Analytics export in BigQuery
--   Project:    isis-3510-g22
--   Dataset:    analytics_<firebase_app_id>
--   Table:      events_* (wildcard over daily partitions)
--
-- How to run:
--   1. Open BigQuery console → ISIS-3510-G22 project
--   2. Paste this query, set the date range suffix as needed
--   3. Schedule as a daily query that overwrites the output table
--      (Tools > Scheduled queries > Create new)
--   4. A Cloud Function triggered by that table write pushes the result
--      to Firestore: db.collection("insights").document("popular_days")
--      with field: days = { "Monday": 12, "Saturday": 45, ... }
--
-- The Kotlin app reads that Firestore doc in InsightsRepository.getPopularDays()
-- and displays the top day in the Create Plan insight card.

SELECT
  FORMAT_TIMESTAMP('%A', TIMESTAMP_MICROS(event_timestamp), 'America/Bogota') AS day_of_week,
  COUNT(*)                                                                      AS plan_count
FROM
  `isis-3510-g22.analytics_<firebase_app_id>.events_*`
WHERE
  event_name = 'plan_created'
  AND _TABLE_SUFFIX BETWEEN
      FORMAT_DATE('%Y%m%d', DATE_SUB(CURRENT_DATE('America/Bogota'), INTERVAL 90 DAY))
      AND FORMAT_DATE('%Y%m%d', CURRENT_DATE('America/Bogota'))
GROUP BY
  day_of_week
ORDER BY
  plan_count DESC
