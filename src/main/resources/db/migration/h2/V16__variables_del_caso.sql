-- Las variables de un caso y los datos con que se completa una tarea son documentos JSON sin tope practico: en H2
-- son clob y en PostgreSQL text, asi que las columnas se agregan por motor. Las tablas, iguales en los dos, se
-- crean en la migracion comun.
alter table casos add column variables clob not null;
alter table actividades_caso add column datos_salida clob;
