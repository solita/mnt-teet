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
 SELECT p.name, -- project name
        lc.type__code, -- lifecycle type code
        a.name__code,  -- activity type code
        t.type__code,  -- task type code
        t.estimated_start_date, -- task start date
        t.assignee__given_name, t.assignee__family_name -- assignee name
   FROM project_x_lifecycles p
   JOIN lifecycle_x_activities lc ON p.lifecycles = lc.db__id
   JOIN activity_x_tasks a ON lc.activities = a.db__id
   JOIN task t ON a.tasks = t.db__id
  WHERE p.id = '12345'; -- some THK project id
