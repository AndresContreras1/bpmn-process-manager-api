-- PR 56: un subproceso contiene su propio flujo, y una actividad de llamada apunta a otro proceso de la tienda.
alter table nodos_flujo drop constraint ck_nodos_flujo_tipo_actividad;
alter table nodos_flujo add constraint ck_nodos_flujo_tipo_actividad
    check (tipo_actividad in ('USUARIO', 'SERVICIO', 'ENVIO', 'RECEPCION', 'SIN_TIPO', 'SUBPROCESO',
                              'SUBPROCESO_COLAPSADO', 'LLAMADA'));

alter table nodos_flujo add column padre_id bigint;
alter table nodos_flujo add column proceso_llamado_id bigint;
alter table nodos_flujo add constraint fk_nodos_flujo_padre foreign key (padre_id) references nodos_flujo (id);
alter table nodos_flujo add constraint fk_nodos_flujo_proceso_llamado
    foreign key (proceso_llamado_id) references procesos (id);
create index idx_nodos_flujo_padre on nodos_flujo (padre_id);
create index idx_nodos_flujo_proceso_llamado on nodos_flujo (proceso_llamado_id);
