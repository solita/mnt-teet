## Analytics

See Datomic cloud analytics documentation: https://docs.datomic.com/cloud/analytics/analytics-concepts.html

## Connecting

Connect using datomic cli: (leave tunnel open)
```datomic analytics access teet-datomic```

Use presto CLI to query:
```
% ./presto --server localhost:8989 --catalog teet-datomic --schema teetdev
presto:teetdev> show tables;
                 Table
---------------------------------------
 activity
 activity_x_meetings
 activity_x_tasks
 code
 comment
 ...more tables...
```

Optionally install metabase and use that.

## Example

Example query to fetch all tasks in all activities from every project's lifecycles:

```SQL
SELECT coalesce(p.project_name,p.name) as "Project",
       lc.type__code as "Lifecycle type",
       a.name__code as "Activity type",
       t.type__code as "Task type",
       t.estimated_start_date,
       concat(t.assignee__given_name,' ', t.assignee__family_name) as "Assignee"
  FROM project_x_lifecycles p
  JOIN lifecycle_x_activities lc ON p.lifecycles = lc.db__id
  JOIN activity_x_tasks a ON lc.activities = a.db__id
  JOIN task t ON a.tasks = t.db__id
 WHERE p.id='12345';
```
