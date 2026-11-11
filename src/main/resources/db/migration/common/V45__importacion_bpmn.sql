-- PR 54: lo que trae un archivo BPMN de otra herramienta y el modelo no tiene, guardado por elemento para que vuelva
-- intacto al exportar: los extensionElements y los atributos de otros espacios de nombres (Camunda, Signavio,
-- Bizagi...). Va ya saneado (sin comentarios ni instrucciones de proceso) y con tope de tamano.
create table extension_xml (
    id          bigserial    primary key,
    empresa_id  bigint       not null,
    proceso_id  bigint       not null,
    -- El bpmn_id del elemento, o #definitions, #collaboration y <pool>#process para lo que no es un elemento
    elemento    varchar(80)  not null,
    xml         text         not null,
    constraint fk_extension_xml_empresa foreign key (empresa_id) references empresas (id),
    constraint fk_extension_xml_proceso foreign key (proceso_id) references procesos (id),
    constraint uk_extension_xml_elemento unique (proceso_id, elemento)
);
create index idx_extension_xml_empresa on extension_xml (empresa_id);

-- Cada archivo que se importo: quien, cuando, que archivo (su huella) y el informe de lo que paso con el.
create table importaciones (
    id              bigserial     primary key,
    empresa_id      bigint        not null,
    proceso_id      bigint        not null,
    usuario_id      bigint,
    fecha           timestamp(6)  not null,
    bytes           integer       not null,
    huella_archivo  varchar(64)   not null,
    creados         integer       not null,
    editados        integer       not null,
    dados_de_baja   integer       not null,
    informe         text          not null,
    constraint fk_importaciones_empresa foreign key (empresa_id) references empresas (id),
    constraint fk_importaciones_proceso foreign key (proceso_id) references procesos (id),
    constraint fk_importaciones_usuario foreign key (usuario_id) references usuarios (id)
);
create index idx_importaciones_empresa on importaciones (empresa_id);
create index idx_importaciones_proceso on importaciones (proceso_id, fecha desc);
create index idx_importaciones_usuario on importaciones (usuario_id);

-- PR 51: como toda tabla de una tienda, solo sus filas.
alter table extension_xml enable row level security;
alter table extension_xml force row level security;
create policy aislamiento_por_tienda on extension_xml
    using (current_setting('app.sistema', true) = 'on'
           or empresa_id = nullif(current_setting('app.empresa_id', true), '')::bigint)
    with check (current_setting('app.sistema', true) = 'on'
                or empresa_id = nullif(current_setting('app.empresa_id', true), '')::bigint);

alter table importaciones enable row level security;
alter table importaciones force row level security;
create policy aislamiento_por_tienda on importaciones
    using (current_setting('app.sistema', true) = 'on'
           or empresa_id = nullif(current_setting('app.empresa_id', true), '')::bigint)
    with check (current_setting('app.sistema', true) = 'on'
                or empresa_id = nullif(current_setting('app.empresa_id', true), '')::bigint);
