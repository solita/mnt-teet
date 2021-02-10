-- Create view for project (needs to be recreated if thk_project columns change)
DROP VIEW teet.thk_project_search;
CREATE VIEW teet.thk_project_search AS
SELECT *, (id||name||road_nr||procurement_no) AS searchable_text
  FROM teet.thk_project;
