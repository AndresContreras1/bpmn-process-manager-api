-- El diagrama publicado es un documento JSON sin tope practico: en H2 es un clob y en PostgreSQL un text, asi que
-- la columna se agrega por motor. La tabla, que es igual en los dos, se crea en la migracion comun.
alter table versiones_proceso add column definicion text not null;
