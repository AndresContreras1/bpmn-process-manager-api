-- Baja logica del modelado: un elemento BPMN eliminado se queda en la base con activo = false. Cada entidad
-- convierte su borrado en esa actualizacion (@SQLDelete) y ninguna consulta ve las filas inactivas
-- (@SQLRestriction).
alter table pools add column activo boolean default true not null;
alter table lanes add column activo boolean default true not null;
alter table nodos_flujo add column activo boolean default true not null;
alter table arcos add column activo boolean default true not null;
alter table mensajes add column activo boolean default true not null;
alter table correlaciones add column activo boolean default true not null;

-- Un arco dado de baja no ocupa su par de nodos: solo compiten los activos, con un indice unico parcial.
alter table arcos drop constraint uk_arcos_origen_destino;
create unique index uk_arcos_origen_destino_activo on arcos (origen_id, destino_id) where activo;
