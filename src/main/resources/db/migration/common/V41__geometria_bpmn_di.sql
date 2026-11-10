-- PR 52: lo que un modelo BPMN 2.0.2 con DI necesita y el modelo todavia no tenia.
--
-- Un bpmn_id estable por elemento: el id que llevara en el XML, el mismo de una exportacion a la siguiente, y por el que
-- una importacion reconocera lo que ya existe. Los que ya existian lo reciben aqui, con su tipo y su id de siempre;
-- los nuevos, al crearse.
--
-- Y la geometria del diagrama, en coordenadas absolutas: una forma por participante, lane y nodo (BPMNShape), y una
-- arista por flujo de secuencia y de mensaje (BPMNEdge), con su etiqueta. Va en tablas propias, y no en las de cada
-- elemento: se recalcula con cada cambio del proceso, y asi no sube la version de los elementos que no cambiaron, que
-- es lo que protege a quien edita a la vez. La migracion V42 la calcula para los diagramas que ya existian.

-- Las filas de todas las tiendas: la migracion corre como el sistema (PR 51)
select set_config('app.sistema', 'on', true);

alter table pools add column bpmn_id varchar(64);
alter table lanes add column bpmn_id varchar(64);
alter table nodos_flujo add column bpmn_id varchar(64);
alter table arcos add column bpmn_id varchar(64);
alter table mensajes add column bpmn_id varchar(64);

update pools set bpmn_id = 'Participant_' || id;
update lanes set bpmn_id = 'Lane_' || id;
update nodos_flujo set bpmn_id = case tipo_nodo
    when 'ACTIVIDAD' then 'Activity_'
    when 'GATEWAY' then 'Gateway_'
    else 'Event_' end || id;
update arcos set bpmn_id = 'Flow_' || id;
update mensajes set bpmn_id = 'MessageFlow_' || id;

alter table pools alter column bpmn_id set not null;
alter table lanes alter column bpmn_id set not null;
alter table nodos_flujo alter column bpmn_id set not null;
alter table arcos alter column bpmn_id set not null;
alter table mensajes alter column bpmn_id set not null;

create table formas_bpmn (
    id               bigserial        primary key,
    empresa_id       bigint           not null,
    proceso_id       bigint           not null,
    elemento         varchar(64)      not null,
    tipo             varchar(20)      not null,
    x                double precision not null,
    y                double precision not null,
    ancho            double precision not null,
    alto             double precision not null,
    etiqueta_x       double precision,
    etiqueta_y       double precision,
    etiqueta_ancho   double precision,
    etiqueta_alto    double precision,
    constraint fk_formas_bpmn_empresa foreign key (empresa_id) references empresas (id),
    constraint fk_formas_bpmn_proceso foreign key (proceso_id) references procesos (id),
    constraint uk_formas_bpmn_elemento unique (proceso_id, elemento),
    constraint ck_formas_bpmn_tipo check (tipo in ('PARTICIPANTE', 'LANE', 'TAREA', 'COMPUERTA', 'EVENTO'))
);
create index idx_formas_bpmn_empresa on formas_bpmn (empresa_id);

create table aristas_bpmn (
    id               bigserial        primary key,
    empresa_id       bigint           not null,
    proceso_id       bigint           not null,
    elemento         varchar(64)      not null,
    tipo             varchar(20)      not null,
    -- Los puntos de la arista, en orden, como [[x, y], ...]
    puntos           text             not null,
    etiqueta_x       double precision,
    etiqueta_y       double precision,
    etiqueta_ancho   double precision,
    etiqueta_alto    double precision,
    constraint fk_aristas_bpmn_empresa foreign key (empresa_id) references empresas (id),
    constraint fk_aristas_bpmn_proceso foreign key (proceso_id) references procesos (id),
    constraint uk_aristas_bpmn_elemento unique (proceso_id, elemento),
    constraint ck_aristas_bpmn_tipo check (tipo in ('SECUENCIA', 'MENSAJE'))
);
create index idx_aristas_bpmn_empresa on aristas_bpmn (empresa_id);

-- PR 51: como toda tabla de una tienda, solo sus filas.
alter table formas_bpmn enable row level security;
alter table formas_bpmn force row level security;
create policy aislamiento_por_tienda on formas_bpmn
    using (current_setting('app.sistema', true) = 'on'
           or empresa_id = nullif(current_setting('app.empresa_id', true), '')::bigint)
    with check (current_setting('app.sistema', true) = 'on'
                or empresa_id = nullif(current_setting('app.empresa_id', true), '')::bigint);

alter table aristas_bpmn enable row level security;
alter table aristas_bpmn force row level security;
create policy aislamiento_por_tienda on aristas_bpmn
    using (current_setting('app.sistema', true) = 'on'
           or empresa_id = nullif(current_setting('app.empresa_id', true), '')::bigint)
    with check (current_setting('app.sistema', true) = 'on'
                or empresa_id = nullif(current_setting('app.empresa_id', true), '')::bigint);
