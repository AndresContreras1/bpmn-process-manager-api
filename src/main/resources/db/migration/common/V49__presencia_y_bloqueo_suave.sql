-- PR 58: quien esta mirando un proceso, y quien lo esta editando. Vive en la base para que lo vean todas las replicas.
create table presencias (
    id             bigserial     primary key,
    empresa_id     bigint        not null,
    proceso_id     bigint        not null,
    usuario_id     bigint        not null,
    nombre         varchar(120)  not null,
    conexion       varchar(36)   not null unique,
    desde          timestamp(6)  not null,
    ultimo_latido  timestamp(6)  not null,
    constraint fk_presencias_empresa foreign key (empresa_id) references empresas (id),
    constraint fk_presencias_proceso foreign key (proceso_id) references procesos (id),
    constraint fk_presencias_usuario foreign key (usuario_id) references usuarios (id)
);
create index idx_presencias_empresa on presencias (empresa_id);
create index idx_presencias_proceso on presencias (proceso_id);
create index idx_presencias_usuario on presencias (usuario_id);
create index idx_presencias_latido on presencias (ultimo_latido);

create table bloqueos_edicion (
    id             bigserial     primary key,
    empresa_id     bigint        not null,
    proceso_id     bigint        not null unique,
    usuario_id     bigint        not null,
    nombre         varchar(120)  not null,
    desde          timestamp(6)  not null,
    ultimo_latido  timestamp(6)  not null,
    constraint fk_bloqueos_edicion_empresa foreign key (empresa_id) references empresas (id),
    constraint fk_bloqueos_edicion_proceso foreign key (proceso_id) references procesos (id),
    constraint fk_bloqueos_edicion_usuario foreign key (usuario_id) references usuarios (id)
);
create index idx_bloqueos_edicion_empresa on bloqueos_edicion (empresa_id);
create index idx_bloqueos_edicion_usuario on bloqueos_edicion (usuario_id);

-- PR 51: como toda tabla de una tienda, solo sus filas.
alter table presencias enable row level security;
alter table presencias force row level security;
create policy aislamiento_por_tienda on presencias
    using (current_setting('app.sistema', true) = 'on'
           or empresa_id = nullif(current_setting('app.empresa_id', true), '')::bigint)
    with check (current_setting('app.sistema', true) = 'on'
                or empresa_id = nullif(current_setting('app.empresa_id', true), '')::bigint);

alter table bloqueos_edicion enable row level security;
alter table bloqueos_edicion force row level security;
create policy aislamiento_por_tienda on bloqueos_edicion
    using (current_setting('app.sistema', true) = 'on'
           or empresa_id = nullif(current_setting('app.empresa_id', true), '')::bigint)
    with check (current_setting('app.sistema', true) = 'on'
                or empresa_id = nullif(current_setting('app.empresa_id', true), '')::bigint);
