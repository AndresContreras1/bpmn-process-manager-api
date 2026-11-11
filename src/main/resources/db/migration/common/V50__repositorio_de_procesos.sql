-- PR 59: el repositorio de procesos. Carpetas en arbol, y de cada proceso su carpeta, su dueno, su proxima revision,
-- su clasificacion y sus etiquetas. Las etiquetas van en un arreglo de la fila: se leen con el proceso, sin otra consulta.
create table carpetas (
    id                 bigserial     primary key,
    empresa_id         bigint        not null,
    padre_id           bigint,
    nombre             varchar(120)  not null,
    version            bigint        not null default 0,
    creado_por         bigint,
    fecha_creacion     timestamp(6)  not null,
    modificado_por     bigint,
    fecha_modificacion timestamp(6)  not null,
    constraint fk_carpetas_empresa foreign key (empresa_id) references empresas (id),
    constraint fk_carpetas_padre foreign key (padre_id) references carpetas (id),
    constraint fk_carpetas_creado_por foreign key (creado_por) references usuarios (id),
    constraint fk_carpetas_modificado_por foreign key (modificado_por) references usuarios (id)
);
create index idx_carpetas_empresa on carpetas (empresa_id);
create index idx_carpetas_padre on carpetas (padre_id);
create index idx_carpetas_creado_por on carpetas (creado_por);
create index idx_carpetas_modificado_por on carpetas (modificado_por);
-- Dos hermanas no se llaman igual; las de la raiz tampoco, y para eso el padre vacio cuenta como un valor
create unique index uq_carpetas_nombre on carpetas (empresa_id, coalesce(padre_id, 0), lower(nombre));

-- PR 51: como toda tabla de una tienda, solo sus filas.
alter table carpetas enable row level security;
alter table carpetas force row level security;
create policy aislamiento_por_tienda on carpetas
    using (current_setting('app.sistema', true) = 'on'
           or empresa_id = nullif(current_setting('app.empresa_id', true), '')::bigint)
    with check (current_setting('app.sistema', true) = 'on'
                or empresa_id = nullif(current_setting('app.empresa_id', true), '')::bigint);

alter table procesos add column carpeta_id bigint;
alter table procesos add column dueno_id bigint;
alter table procesos add column proxima_revision date;
alter table procesos add column clasificacion varchar(20) not null default 'INTERNA';
alter table procesos add column etiquetas varchar(40)[] not null default '{}';
-- Un proceso eliminado sigue en la tabla: no puede impedir borrar la carpeta donde estaba
alter table procesos add constraint fk_procesos_carpeta foreign key (carpeta_id) references carpetas (id)
    on delete set null;
alter table procesos add constraint fk_procesos_dueno foreign key (dueno_id) references usuarios (id);
alter table procesos add constraint ck_procesos_clasificacion
    check (clasificacion in ('INTERNA', 'CONFIDENCIAL', 'RESTRINGIDA'));
create index idx_procesos_carpeta on procesos (carpeta_id);
create index idx_procesos_dueno on procesos (dueno_id);
create index idx_procesos_proxima_revision on procesos (proxima_revision);
create index idx_procesos_etiquetas on procesos using gin (etiquetas);
