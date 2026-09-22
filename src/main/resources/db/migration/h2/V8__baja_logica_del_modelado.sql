-- Baja logica del modelado: un elemento BPMN eliminado se queda en la base con activo = false. Cada entidad
-- convierte su borrado en esa actualizacion (@SQLDelete) y ninguna consulta ve las filas inactivas
-- (@SQLRestriction).
alter table pools add column activo boolean default true not null;
alter table lanes add column activo boolean default true not null;
alter table nodos_flujo add column activo boolean default true not null;
alter table arcos add column activo boolean default true not null;
alter table mensajes add column activo boolean default true not null;
alter table correlaciones add column activo boolean default true not null;

-- Un arco dado de baja no ocupa su par de nodos: solo compiten los activos. Es la misma tecnica de la V2: H2 no tiene
-- indices parciales, asi que una columna calculada vale el origen si el arco esta activo y null si no, y el indice
-- unico admite varios null. La columna no esta mapeada en la entidad: la calcula la base.
alter table arcos drop constraint uk_arcos_origen_destino;
alter table arcos add column origen_activo bigint generated always as (case when activo then origen_id end);
create unique index uk_arcos_origen_destino_activo on arcos (origen_activo, destino_id);
