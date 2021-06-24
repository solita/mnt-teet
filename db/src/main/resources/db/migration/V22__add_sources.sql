-- Add municipalities and counties

INSERT INTO teet.datasource (name,description,url,content_type,id_pattern,label_pattern) VALUES
('counties', 'Maa-amet Estonian counties', 'https://geoportaal.maaamet.ee/docs/haldus_asustus/maakond_shp.zip', 'SHP', '{MKOOD}','{MNIMI}'),
('municipalities','Maa-amet Estonian municipalities', 'https://geoportaal.maaamet.ee/docs/haldus_asustus/omavalitsus_shp.zip', 'SHP', '{OKOOD}','{ONIMI}');
