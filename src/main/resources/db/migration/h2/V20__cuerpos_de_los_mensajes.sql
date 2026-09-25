-- El cuerpo de un mensaje es un documento JSON sin tope practico, como las variables de un caso: en H2 es clob y
-- en PostgreSQL text, asi que la columna se agrega por motor. Las tablas, iguales en los dos, estan en la comun.
alter table mensajes_salientes add column cuerpo clob not null;
alter table mensajes_entrantes add column cuerpo clob not null;
