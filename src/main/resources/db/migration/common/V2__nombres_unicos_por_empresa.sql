-- Entre los registros activos de una empresa, el nombre de un proceso o de un rol es unico sin distinguir mayusculas.
-- Los eliminados (activo = false) no cuentan: su nombre queda libre. Indices unicos parciales y sobre lower(nombre).
create unique index uk_procesos_empresa_nombre_activo on procesos (empresa_id, lower(nombre)) where activo;
create unique index uk_roles_proceso_empresa_nombre_activo on roles_proceso (empresa_id, lower(nombre)) where activo;
