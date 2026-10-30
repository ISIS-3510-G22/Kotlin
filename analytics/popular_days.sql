-- BQ Type 4 — What days of the week, month and year do users go out for plans most?
-- Owner: Maria
-- Question: Which day-of-week, month and year see the most plan outings?
--
-- Source table: Firestore BigQuery Export extension
--   Project:    plansync-bc8f6
--   Dataset:    firestore_export
--   Table:      plans_raw_latest  (same pipeline as onboarding_steps.sql)
--
-- The `date` field is stored in the export JSON as a Firestore Timestamp:
--   { "_seconds": <int64>, "_nanoseconds": <int32> }
-- We convert via TIMESTAMP_SECONDS and format in America/Bogota.
--
-- How to run:
--   1. Open BigQuery console → plansync-bc8f6
--   2. Paste this query and run once to verify results.
--   3. Schedule as a daily query that overwrites the output table:
--      (Tools > Scheduled queries > Create new)
--      Destination dataset: firestore_export
--      Destination table:   popular_days_result
--      Write preference:    Overwrite table
--   4. A Cloud Function triggered by that table write pushes the result
--      to Firestore: db.collection("insights").document("popular_days")
--      It filters dimension = 'day_of_week' rows and writes:
--      days = { "Monday": n, "Saturday": n, ... }
--      which is the shape InsightsRepository.getPopularDays() already reads.
--
-- What must exist in Firebase / BigQuery for insights/popular_days to be written:
--   a) Firestore BigQuery Export extension installed in plansync-bc8f6
--      (Extensions > Firestore Export to BigQuery) — creates firestore_export dataset
--      and keeps plans_raw_latest updated automatically.
--   b) This scheduled query runs daily and writes to popular_days_result.
--   c) A Cloud Function (Node.js) triggered on BigQuery table write that reads
--      popular_days_result and writes the days map to Firestore
--      db.collection("insights").document("popular_days").set({ days: { ... } })
--
-- The Kotlin app reads that Firestore doc in InsightsRepository.getPopularDays()
-- and displays the top day in the Create Plan insight card.

SELECT
  'day_of_week' AS dimension,
  FORMAT_TIMESTAMP('%A',
    TIMESTAMP_SECONDS(CAST(JSON_VALUE(data, '$.date._seconds') AS INT64)),
    'America/Bogota') AS value,
  COUNT(*) AS plan_count
FROM `plansync-bc8f6.firestore_export.plans_raw_latest`
WHERE JSON_VALUE(data, '$.date._seconds') IS NOT NULL
GROUP BY value

UNION ALL

SELECT
  'month' AS dimension,
  FORMAT_TIMESTAMP('%B',
    TIMESTAMP_SECONDS(CAST(JSON_VALUE(data, '$.date._seconds') AS INT64)),
    'America/Bogota') AS value,
  COUNT(*) AS plan_count
FROM `plansync-bc8f6.firestore_export.plans_raw_latest`
WHERE JSON_VALUE(data, '$.date._seconds') IS NOT NULL
GROUP BY value

UNION ALL

SELECT
  'year' AS dimension,
  CAST(EXTRACT(YEAR FROM
    TIMESTAMP_SECONDS(CAST(JSON_VALUE(data, '$.date._seconds') AS INT64))
    AT TIME ZONE 'America/Bogota') AS STRING) AS value,
  COUNT(*) AS plan_count
FROM `plansync-bc8f6.firestore_export.plans_raw_latest`
WHERE JSON_VALUE(data, '$.date._seconds') IS NOT NULL
GROUP BY value

ORDER BY dimension, plan_count DESC
