-- Auditoria de cada recurso editable: quien lo creo y quien guardo el ultimo cambio, y cuando. Los llena Spring Data
-- con el usuario del token; quedan vacios si los hizo el sistema, como el registro de una tienda o la semilla de dev.
-- Los procesos ya tenian sus fechas: solo les faltan los autores.
alter table procesos add column creado_por bigint;
alter table procesos add column modificado_por bigint;

alter table roles_proceso add column creado_por bigint;
alter table roles_proceso add column fecha_creacion timestamp(6) default localtimestamp not null;
alter table roles_proceso add column modificado_por bigint;
alter table roles_proceso add column fecha_modificacion timestamp(6) default localtimestamp not null;

alter table usuarios add column creado_por bigint;
alter table usuarios add column fecha_creacion timestamp(6) default localtimestamp not null;
alter table usuarios add column modificado_por bigint;
alter table usuarios add column fecha_modificacion timestamp(6) default localtimestamp not null;

alter table pools add column creado_por bigint;
alter table pools add column fecha_creacion timestamp(6) default localtimestamp not null;
alter table pools add column modificado_por bigint;
alter table pools add column fecha_modificacion timestamp(6) default localtimestamp not null;

alter table lanes add column creado_por bigint;
alter table lanes add column fecha_creacion timestamp(6) default localtimestamp not null;
alter table lanes add column modificado_por bigint;
alter table lanes add column fecha_modificacion timestamp(6) default localtimestamp not null;

alter table nodos_flujo add column creado_por bigint;
alter table nodos_flujo add column fecha_creacion timestamp(6) default localtimestamp not null;
alter table nodos_flujo add column modificado_por bigint;
alter table nodos_flujo add column fecha_modificacion timestamp(6) default localtimestamp not null;

alter table arcos add column creado_por bigint;
alter table arcos add column fecha_creacion timestamp(6) default localtimestamp not null;
alter table arcos add column modificado_por bigint;
alter table arcos add column fecha_modificacion timestamp(6) default localtimestamp not null;

alter table mensajes add column creado_por bigint;
alter table mensajes add column fecha_creacion timestamp(6) default localtimestamp not null;
alter table mensajes add column modificado_por bigint;
alter table mensajes add column fecha_modificacion timestamp(6) default localtimestamp not null;

alter table correlaciones add column creado_por bigint;
alter table correlaciones add column fecha_creacion timestamp(6) default localtimestamp not null;
alter table correlaciones add column modificado_por bigint;
alter table correlaciones add column fecha_modificacion timestamp(6) default localtimestamp not null;

-- Los autores son usuarios de verdad: los usuarios nunca se borran, solo se desactivan.
alter table procesos add constraint fk_procesos_creado_por foreign key (creado_por) references usuarios (id);
alter table procesos add constraint fk_procesos_modificado_por foreign key (modificado_por) references usuarios (id);
alter table roles_proceso add constraint fk_roles_proceso_creado_por foreign key (creado_por) references usuarios (id);
alter table roles_proceso add constraint fk_roles_proceso_modificado_por foreign key (modificado_por)
    references usuarios (id);
alter table usuarios add constraint fk_usuarios_creado_por foreign key (creado_por) references usuarios (id);
alter table usuarios add constraint fk_usuarios_modificado_por foreign key (modificado_por) references usuarios (id);
alter table pools add constraint fk_pools_creado_por foreign key (creado_por) references usuarios (id);
alter table pools add constraint fk_pools_modificado_por foreign key (modificado_por) references usuarios (id);
alter table lanes add constraint fk_lanes_creado_por foreign key (creado_por) references usuarios (id);
alter table lanes add constraint fk_lanes_modificado_por foreign key (modificado_por) references usuarios (id);
alter table nodos_flujo add constraint fk_nodos_flujo_creado_por foreign key (creado_por) references usuarios (id);
alter table nodos_flujo add constraint fk_nodos_flujo_modificado_por foreign key (modificado_por)
    references usuarios (id);
alter table arcos add constraint fk_arcos_creado_por foreign key (creado_por) references usuarios (id);
alter table arcos add constraint fk_arcos_modificado_por foreign key (modificado_por) references usuarios (id);
alter table mensajes add constraint fk_mensajes_creado_por foreign key (creado_por) references usuarios (id);
alter table mensajes add constraint fk_mensajes_modificado_por foreign key (modificado_por) references usuarios (id);
alter table correlaciones add constraint fk_correlaciones_creado_por foreign key (creado_por) references usuarios (id);
alter table correlaciones add constraint fk_correlaciones_modificado_por foreign key (modificado_por)
    references usuarios (id);
