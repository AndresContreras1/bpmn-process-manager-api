-- PR 40: puntos de guardado del borrador, para volver atras. Una revision no es una version: no se publica ni abre casos.
create table revisiones_borrador (
    id          bigserial     primary key,
    empresa_id  bigint        not null,
    proceso_id  bigint        not null,
    usuario_id  bigint,
    motivo      varchar(20)   not null,
    huella      varchar(64)   not null,
    xml         bytea         not null,
    fecha       timestamp(6)  not null,
    constraint fk_revisiones_borrador_empresa foreign key (empresa_id) references empresas (id),
    constraint fk_revisiones_borrador_proceso foreign key (proceso_id) references procesos (id),
    constraint fk_revisiones_borrador_usuario foreign key (usuario_id) references usuarios (id),
    constraint ck_revisiones_borrador_motivo check (motivo in ('AUTOGUARDADO', 'CIERRE', 'ANTES_DE_RESTAURAR'))
);
create index idx_revisiones_borrador_empresa on revisiones_borrador (empresa_id);
create index idx_revisiones_borrador_proceso on revisiones_borrador (proceso_id, id desc);
create index idx_revisiones_borrador_usuario on revisiones_borrador (usuario_id);

-- PR 51: como toda tabla de una tienda, solo sus filas.
alter table revisiones_borrador enable row level security;
alter table revisiones_borrador force row level security;
create policy aislamiento_por_tienda on revisiones_borrador
    using (current_setting('app.sistema', true) = 'on'
           or empresa_id = nullif(current_setting('app.empresa_id', true), '')::bigint)
    with check (current_setting('app.sistema', true) = 'on'
                or empresa_id = nullif(current_setting('app.empresa_id', true), '')::bigint);
