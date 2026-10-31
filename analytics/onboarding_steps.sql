WITH users AS (
  SELECT
    document_id AS uid,
    IFNULL(JSON_VALUE(data, '$.photoUrl'), '') != '' AS has_photo,
    IFNULL(ARRAY_LENGTH(JSON_QUERY_ARRAY(data, '$.reimbursementMethods')), 0) > 0 AS has_payment_method
  FROM `plansync-bc8f6.firestore_export.users_raw_latest`
),
friends AS (
  SELECT DISTINCT uid
  FROM `plansync-bc8f6.firestore_export.friend_requests_raw_latest`,
    UNNEST([JSON_VALUE(data, '$.fromUserId'), JSON_VALUE(data, '$.toUserId')]) AS uid
  WHERE JSON_VALUE(data, '$.status') = 'accepted'
),
likers AS (
  SELECT DISTINCT uid
  FROM `plansync-bc8f6.firestore_export.activities_raw_latest`,
    UNNEST(JSON_VALUE_ARRAY(data, '$.likedBy')) AS uid
),
activity_creators AS (
  SELECT DISTINCT JSON_VALUE(data, '$.ownerId') AS uid
  FROM `plansync-bc8f6.firestore_export.activities_raw_latest`
),
plan_creators AS (
  SELECT DISTINCT JSON_VALUE(data, '$.creatorId') AS uid
  FROM `plansync-bc8f6.firestore_export.plans_raw_latest`
)
SELECT u.uid, step.step_order, step.step, step.completed
FROM users u
LEFT JOIN friends f ON f.uid = u.uid
LEFT JOIN likers l ON l.uid = u.uid
LEFT JOIN activity_creators a ON a.uid = u.uid
LEFT JOIN plan_creators p ON p.uid = u.uid
CROSS JOIN UNNEST([
  STRUCT(1 AS step_order, 'Add a profile photo' AS step, u.has_photo AS completed),
  STRUCT(2, 'Connect a payment method', u.has_payment_method),
  STRUCT(3, 'Add a first friend', f.uid IS NOT NULL),
  STRUCT(4, 'Like a first activity', l.uid IS NOT NULL),
  STRUCT(5, 'Create a first activity', a.uid IS NOT NULL),
  STRUCT(6, 'Create a first plan', p.uid IS NOT NULL)
]) AS step
