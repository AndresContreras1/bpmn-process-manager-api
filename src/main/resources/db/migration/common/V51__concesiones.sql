-- PR 60: quien puede hacer que sobre una carpeta o un proceso, por encima de su rol de acceso.
create table concesiones (
    id             bigserial    primary key,
    empresa_id     bigint       not null,
    carpeta_id     bigint,
    proceso_id     bigint,
    sujeto         varchar(20)  not null,
    usuario_id     bigint,
    rol_proceso_id bigint,
    accion         varchar(20)  not null,
    constraint fk_concesiones_empresa foreign key (empresa_id) references empresas (id),
    constraint fk_concesiones_carpeta foreign key (carpeta_id) references carpetas (id) on delete cascade,
    constraint fk_concesiones_proceso foreign key (proceso_id) references procesos (id) on delete cascade,
    constraint fk_concesiones_usuario foreign key (usuario_id) references usuarios (id),
    constraint fk_concesiones_rol foreign key (rol_proceso_id) references roles_proceso (id),
    constraint ck_concesiones_recurso check ((carpeta_id is null) <> (proceso_id is null)),
    constraint ck_concesiones_accion check (accion in ('VER', 'COMENTAR', 'MODELAR', 'REVISAR', 'APROBAR', 'PUBLICAR',
                                                       'EXPORTAR', 'ADMINISTRAR')),
    constraint ck_concesiones_sujeto check (
        (sujeto = 'PERSONA' and usuario_id is not null and rol_proceso_id is null)
        or (sujeto = 'ROL' and rol_proceso_id is not null and usuario_id is null)
        or (sujeto = 'ORGANIZACION' and usuario_id is null and rol_proceso_id is null))
);
create index idx_concesiones_empresa on concesiones (empresa_id);
create index idx_concesiones_carpeta on concesiones (carpeta_id);
create index idx_concesiones_proceso on concesiones (proceso_id);
create index idx_concesiones_usuario on concesiones (usuario_id);
create index idx_concesiones_rol on concesiones (rol_proceso_id);
create unique index uq_concesiones on concesiones (empresa_id, coalesce(carpeta_id, 0), coalesce(proceso_id, 0), sujeto,
                                                   coalesce(usuario_id, 0), coalesce(rol_proceso_id, 0), accion);

-- PR 51: como toda tabla de una tienda, solo sus filas.
alter table concesiones enable row level security;
alter table concesiones force row level security;
create policy aislamiento_por_tienda on concesiones
    using (current_setting('app.sistema', true) = 'on'
           or empresa_id = nullif(current_setting('app.empresa_id', true), '')::bigint)
    with check (current_setting('app.sistema', true) = 'on'
                or empresa_id = nullif(current_setting('app.empresa_id', true), '')::bigint);
