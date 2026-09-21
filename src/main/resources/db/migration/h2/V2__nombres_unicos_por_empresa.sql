-- La misma regla que la V2 de PostgreSQL: entre los registros activos de una empresa, el nombre de un proceso o de un
-- rol es unico sin distinguir mayusculas. H2 no tiene indices parciales ni sobre expresiones, asi que una columna
-- calculada vale lower(nombre) si el registro esta activo y null si no; el indice unico admite varios null, de modo
-- que solo compiten los activos. La columna no esta mapeada en las entidades: la calcula la base.
alter table procesos add column nombre_activo varchar(120)
    generated always as (case when activo then lower(nombre) end);
create unique index uk_procesos_empresa_nombre_activo on procesos (empresa_id, nombre_activo);

alter table roles_proceso add column nombre_activo varchar(80)
    generated always as (case when activo then lower(nombre) end);
create unique index uk_roles_proceso_empresa_nombre_activo on roles_proceso (empresa_id, nombre_activo);
