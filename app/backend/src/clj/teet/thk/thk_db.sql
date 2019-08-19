-- name: upsert-thk-project!
INSERT INTO teet.thk_project
       (id, plan_group_fk, road_nr, bridge_nr, km_range, carriageway,
        name, oper_method, object_type_fk, region_fk, county_fk,
        customer_unit, updated, procurement_no, procurement_id,
        activity_id, activity_type_fk, estimated_duration)
VALUES (:id, :plan_group_fk, :road_nr, :bridge_nr,
        ('[' || :km_start || ',' || :km_end || ']')::numrange,
        :carriageway,
        :name, :oper_method, :object_type_fk, :region_fk, :county_fk,
        :customer_unit,
        to_timestamp(:updated,'DD.MM.YYYY HH24\:MI'),
        :procurement_no, :procurement_id,
        :activity_id, :activity_type_fk,
        daterange(to_date(:estimated_start,'DD.MM.YYYY'),
                  to_date(:estimated_end,'DD.MM.YYYY') + 1))
ON CONFLICT DO NOTHING;
