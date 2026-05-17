INSERT INTO organization_departments (organization_id, name, sort_order, created_at)
SELECT o.id, '회장단', -1, CURRENT_TIMESTAMP
FROM organizations o
WHERE NOT EXISTS (
    SELECT 1
    FROM organization_departments od
    WHERE od.organization_id = o.id
      AND od.name = '회장단'
);
