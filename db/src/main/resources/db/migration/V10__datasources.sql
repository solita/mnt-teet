-- Add cadastral units and restrictions as data sources

INSERT INTO teet.datasource (name, description, url, content_type, id_pattern, label_pattern)
VALUES ('cadastral-units',
        'Maa-amet cadastral units data',
        'https://geoportaal.maaamet.ee/docs/katastripiirid/paev/KATASTER_EESTI_SHP.zip',
        'SHP',
        '{TUNNUS}', '{TUNNUS}');

INSERT INTO teet.datasource (name,description,url,content_type,id_pattern,label_pattern)
VALUES ('restrictions:admin',
        'Maa-amet admin restrictions data',
        'https://geoportaal.maaamet.ee/docs/KPO/KITSENDUSED_ADMIN_SHP.zip',
        'SHP','{i}-{KPO_VID}','{VOOND}');

INSERT INTO teet.datasource (name,description,url,content_type,id_pattern,label_pattern)
VALUES ('restrictions:keskkond',
        'Maa-amet keskkond restrictions data',
        'https://geoportaal.maaamet.ee/docs/KPO/KITSENDUSED_KESKKOND_SHP.zip',
        'SHP','{i}-{KPO_VID}','{VOOND}');

INSERT INTO teet.datasource (name,description,url,content_type,id_pattern,label_pattern)
VALUES ('restrictions:tehno',
        'Maa-amet tehno restrictions data',
        'https://geoportaal.maaamet.ee/docs/KPO/KITSENDUSED_TEHNO_SHP.zip',
        'SHP','{i}-{KPO_VID}','{VOOND}');

INSERT INTO teet.datasource (name,description,url,content_type,id_pattern,label_pattern)
VALUES ('restrictions:transport',
        'Maa-amet transport restrictions data',
        'https://geoportaal.maaamet.ee/docs/KPO/KITSENDUSED_TRANSPORT_SHP.zip',
        'SHP','{i}-{KPO_VID}','{VOOND}');

INSERT INTO teet.datasource (name,description,url,content_type,id_pattern,label_pattern)
VALUES ('restrictions:vesi',
        'Maa-amet vesi restrictions data',
        'https://geoportaal.maaamet.ee/docs/KPO/KITSENDUSED_VESI_SHP.zip',
        'SHP','{i}-{KPO_VID}','{VOOND}');
