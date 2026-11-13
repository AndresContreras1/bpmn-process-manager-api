-- PR 65: la busqueda de texto completo, en PostgreSQL y sin otro servicio. Cada cosa que se busca guarda su vector
-- (tsvector) con la configuracion del idioma de su tienda, y un indice GIN lo recorre: los procesos (nombre;
-- descripcion, categoria y etiquetas; su documentacion), los elementos del borrador y de la version publicada (nombre;
-- descripcion, condicion o contenido; su documentacion por bpmn_id), los documentos (el nombre de su ultima version y
-- el texto que la cola le saco) y los comentarios que no se borraron. Pesos: nombres y titulos A, descripciones B y
-- cuerpos C.
--
-- Los vectores los escriben triggers, en la misma transaccion que el cambio, y no columnas generadas: el idioma vive en
-- configuracion_tienda y la documentacion en su propia tabla, y una columna generada solo ve su fila.
--
-- Los datos que ya existen se indexan aqui mismo: la API entra como procesos_app, pero esto corre con el dueno de las
-- tablas, que tambien pasa por la seguridad por filas (FORCE); app.sistema la abre solo dentro de esta migracion.
select set_config('app.sistema', 'on', true);

-- unaccent es una extension de confianza: la crea el dueno de la base sin ser superusuario.
create extension if not exists unaccent;

-- Una configuracion por idioma, copia de la de PostgreSQL con unaccent delante del lematizador en las palabras con
-- letras que no son ASCII: "credito" encuentra "credito" con tilde, y ts_headline resalta la palabra tal como esta
-- escrita, porque normaliza el texto original con la misma configuracion que el vector. Por eso no hace falta envolver
-- unaccent() en una funcion IMMUTABLE: los vectores los calculan triggers, no columnas generadas ni indices sobre
-- expresiones, y to_tsvector con una configuracion fija ya es IMMUTABLE.
create text search configuration busqueda_es (copy = pg_catalog.spanish);
alter text search configuration busqueda_es
    alter mapping for hword, hword_part, word with unaccent, spanish_stem;
create text search configuration busqueda_en (copy = pg_catalog.english);
alter text search configuration busqueda_en
    alter mapping for hword, hword_part, word with unaccent, english_stem;
create text search configuration busqueda_fr (copy = pg_catalog.french);
alter text search configuration busqueda_fr
    alter mapping for hword, hword_part, word with unaccent, french_stem;

-- La configuracion de una tienda, por el idioma con que trabaja su gente (V40). Sin configuracion, la de fabrica.
create function configuracion_de_busqueda(la_empresa bigint) returns regconfig
    language sql stable parallel safe as $$
    select case left(coalesce((select c.idioma from configuracion_tienda c where c.empresa_id = la_empresa), 'es'), 2)
        when 'en' then 'busqueda_en'::regconfig
        when 'fr' then 'busqueda_fr'::regconfig
        else 'busqueda_es'::regconfig
    end
$$;

-- La documentacion (PR 62) de un proceso, con bpmn_id vacio, o de uno de sus elementos.
create function documentacion_de(el_proceso bigint, el_bpmn_id varchar) returns text
    language sql stable parallel safe as $$
    select d.markdown from documentacion d
    where d.proceso_id = el_proceso and d.bpmn_id is not distinct from el_bpmn_id
$$;

-- El vector de un proceso: su nombre, lo que lo describe y su documentacion.
create function vector_de_proceso(p procesos) returns tsvector
    language sql stable parallel safe as $$
    select setweight(to_tsvector(c.configuracion, coalesce(p.nombre, '')), 'A')
        || setweight(to_tsvector(c.configuracion, concat_ws(' ', p.descripcion, p.categoria,
                                                            array_to_string(p.etiquetas, ' '))), 'B')
        || setweight(to_tsvector(c.configuracion, coalesce(documentacion_de(p.id, null), '')), 'C')
    from (select configuracion_de_busqueda(p.empresa_id) as configuracion) c
$$;

-- El vector de un elemento: su nombre (o etiqueta, o texto de la anotacion), su detalle (descripcion, condicion o
-- contenido) y su documentacion por bpmn_id.
create function vector_de_elemento(la_empresa bigint, el_proceso bigint, el_bpmn_id varchar, el_nombre text,
                                   el_detalle text) returns tsvector
    language sql stable parallel safe as $$
    select setweight(to_tsvector(c.configuracion, coalesce(el_nombre, '')), 'A')
        || setweight(to_tsvector(c.configuracion, coalesce(el_detalle, '')), 'B')
        || setweight(to_tsvector(c.configuracion, coalesce(documentacion_de(el_proceso, el_bpmn_id), '')), 'C')
    from (select configuracion_de_busqueda(la_empresa) as configuracion) c
$$;

-- El vector de un comentario; uno borrado no tiene: no se encuentra.
create function vector_de_comentario(configuracion regconfig, el_markdown text, eliminado timestamp)
    returns tsvector language sql immutable parallel safe as $$
    select case when eliminado is null then setweight(to_tsvector(configuracion, coalesce(el_markdown, '')), 'C') end
$$;

-- El vector de un archivo: su nombre, con puntos y guiones bajos como espacios (si no, "empaque.pdf" seria una sola
-- palabra), y el texto que la cola le saco. Un texto de palabras sueltas y distintas puede pasar del megabyte que cabe
-- en un tsvector aunque el texto este dentro de su tope: entonces cuentan sus primeros cien mil caracteres.
create function vector_de_archivo(configuracion regconfig, el_nombre text, el_texto text) returns tsvector
    language plpgsql immutable parallel safe as $$
declare
    titulo tsvector := setweight(to_tsvector(configuracion, translate(coalesce(el_nombre, ''), '._', '  ')), 'A');
begin
    return titulo || setweight(to_tsvector(configuracion, coalesce(el_texto, '')), 'C');
exception when program_limit_exceeded then
    return titulo || setweight(to_tsvector(configuracion, left(el_texto, 100000)), 'C');
end
$$;

-- Procesos ------------------------------------------------------------------------------------------------------------

alter table procesos add column busqueda tsvector;
create index idx_procesos_busqueda on procesos using gin (busqueda);

create function busqueda_del_proceso() returns trigger language plpgsql as $$
begin
    new.busqueda := vector_de_proceso(new);
    return new;
end
$$;
create trigger busqueda_al_crear_proceso before insert on procesos
    for each row execute function busqueda_del_proceso();
create trigger busqueda_al_cambiar_proceso before update on procesos
    for each row when (old.nombre is distinct from new.nombre or old.descripcion is distinct from new.descripcion
                       or old.categoria is distinct from new.categoria or old.etiquetas is distinct from new.etiquetas)
    execute function busqueda_del_proceso();

-- Elementos -----------------------------------------------------------------------------------------------------------

-- Lo que se busca de cada elemento, del borrador (version_numero vacio; origen y origen_id dicen de que tabla y fila
-- viene) y de la version publicada del proceso (sacado del diagrama que guarda la version). Es un indice: se rehace
-- solo, desde las tablas del modelo y las versiones, y nada lo escribe a mano.
create table elementos_buscables (
    id             bigint        generated by default as identity primary key,
    empresa_id     bigint        not null,
    proceso_id     bigint        not null,
    version_numero integer,
    origen         varchar(30)   not null,
    origen_id      bigint,
    bpmn_id        varchar(64)   not null,
    tipo           varchar(20)   not null,
    nombre         varchar(1000),
    detalle        varchar(2000),
    busqueda       tsvector      not null,
    constraint fk_elementos_buscables_empresa foreign key (empresa_id) references empresas (id),
    constraint fk_elementos_buscables_proceso foreign key (proceso_id) references procesos (id),
    constraint ck_elementos_buscables_origen check ((version_numero is null) = (origen_id is not null))
);
create index idx_elementos_buscables_empresa on elementos_buscables (empresa_id);
create index idx_elementos_buscables_proceso on elementos_buscables (proceso_id, bpmn_id);
create unique index uq_elementos_buscables_borrador on elementos_buscables (origen, origen_id)
    where origen_id is not null;
create unique index uq_elementos_buscables_version on elementos_buscables (proceso_id, version_numero, bpmn_id)
    where version_numero is not null;
create index idx_elementos_buscables_busqueda on elementos_buscables using gin (busqueda);

alter table elementos_buscables enable row level security;
alter table elementos_buscables force row level security;
create policy aislamiento_por_tienda on elementos_buscables
    using (current_setting('app.sistema', true) = 'on'
           or empresa_id = nullif(current_setting('app.empresa_id', true), '')::bigint)
    with check (current_setting('app.sistema', true) = 'on'
                or empresa_id = nullif(current_setting('app.empresa_id', true), '')::bigint);

-- Un elemento del borrador, de cualquiera de sus seis tablas: se lee como JSON para tratarlas igual. Uno dado de baja
-- sale del indice. Pools, mensajes y artefactos dicen su proceso; lanes y arcos, su pool; los nodos, su lane.
create function indexar_elemento(la_tabla text, fila jsonb) returns void language plpgsql as $$
declare
    el_proceso bigint;
    el_nombre text := coalesce(fila->>'nombre', fila->>'etiqueta', fila->>'texto');
    el_detalle text := coalesce(fila->>'descripcion', fila->>'condicion', fila->>'contenido');
begin
    delete from elementos_buscables where origen = la_tabla and origen_id = (fila->>'id')::bigint;
    if not (fila->>'activo')::boolean then
        return;
    end if;
    el_proceso := coalesce((fila->>'proceso_id')::bigint,
        (select p.proceso_id from pools p where p.id = (fila->>'pool_id')::bigint),
        (select p.proceso_id from lanes l join pools p on p.id = l.pool_id where l.id = (fila->>'lane_id')::bigint));
    insert into elementos_buscables (empresa_id, proceso_id, origen, origen_id, bpmn_id, tipo, nombre, detalle,
                                     busqueda)
    values ((fila->>'empresa_id')::bigint, el_proceso, la_tabla, (fila->>'id')::bigint, fila->>'bpmn_id',
            case la_tabla
                when 'pools' then 'POOL'
                when 'lanes' then 'LANE'
                when 'nodos_flujo' then fila->>'tipo_nodo'
                when 'arcos' then 'ARCO'
                when 'mensajes' then 'MENSAJE'
                else 'ARTEFACTO'
            end,
            left(el_nombre, 1000), left(el_detalle, 2000),
            vector_de_elemento((fila->>'empresa_id')::bigint, el_proceso, fila->>'bpmn_id', el_nombre, el_detalle));
end
$$;

create function busqueda_del_elemento() returns trigger language plpgsql as $$
begin
    if tg_op = 'DELETE' then
        delete from elementos_buscables where origen = tg_table_name and origen_id = old.id;
    else
        perform indexar_elemento(tg_table_name, to_jsonb(new));
    end if;
    return null;
end
$$;

-- Mover un elemento no cambia lo que se busca de el: solo se rehace si cambia su texto, su bpmn_id o si sigue vivo.
create trigger busqueda_del_pool after insert or delete on pools
    for each row execute function busqueda_del_elemento();
create trigger busqueda_al_cambiar_pool after update on pools
    for each row when (old.nombre is distinct from new.nombre or old.bpmn_id is distinct from new.bpmn_id
                       or old.activo is distinct from new.activo)
    execute function busqueda_del_elemento();
create trigger busqueda_de_la_lane after insert or delete on lanes
    for each row execute function busqueda_del_elemento();
create trigger busqueda_al_cambiar_lane after update on lanes
    for each row when (old.nombre is distinct from new.nombre or old.bpmn_id is distinct from new.bpmn_id
                       or old.activo is distinct from new.activo)
    execute function busqueda_del_elemento();
create trigger busqueda_del_nodo after insert or delete on nodos_flujo
    for each row execute function busqueda_del_elemento();
create trigger busqueda_al_cambiar_nodo after update on nodos_flujo
    for each row when (old.nombre is distinct from new.nombre or old.descripcion is distinct from new.descripcion
                       or old.tipo_nodo is distinct from new.tipo_nodo or old.bpmn_id is distinct from new.bpmn_id
                       or old.activo is distinct from new.activo)
    execute function busqueda_del_elemento();
create trigger busqueda_del_arco after insert or delete on arcos
    for each row execute function busqueda_del_elemento();
create trigger busqueda_al_cambiar_arco after update on arcos
    for each row when (old.etiqueta is distinct from new.etiqueta or old.condicion is distinct from new.condicion
                       or old.bpmn_id is distinct from new.bpmn_id or old.activo is distinct from new.activo)
    execute function busqueda_del_elemento();
create trigger busqueda_del_mensaje after insert or delete on mensajes
    for each row execute function busqueda_del_elemento();
create trigger busqueda_al_cambiar_mensaje after update on mensajes
    for each row when (old.nombre is distinct from new.nombre or old.contenido is distinct from new.contenido
                       or old.bpmn_id is distinct from new.bpmn_id or old.activo is distinct from new.activo)
    execute function busqueda_del_elemento();
create trigger busqueda_del_artefacto after insert or delete on artefactos
    for each row execute function busqueda_del_elemento();
create trigger busqueda_al_cambiar_artefacto after update on artefactos
    for each row when (old.texto is distinct from new.texto or old.bpmn_id is distinct from new.bpmn_id
                       or old.activo is distinct from new.activo)
    execute function busqueda_del_elemento();

-- Los elementos de la version publicada de un proceso, del diagrama que la version guarda (D2): cada lista de
-- elementos del JSON, y de cada uno su bpmnId, su nombre y su detalle. Las versiones de antes de los bpmnId (PR 52)
-- no tienen nada que indexar. Se rehacen cuando cambia la version publicada: al publicar y al retirar.
create function indexar_version_publicada(el_proceso bigint, el_numero integer) returns void language plpgsql as $$
begin
    delete from elementos_buscables where proceso_id = el_proceso and version_numero is not null;
    if el_numero is null then
        return;
    end if;
    insert into elementos_buscables (empresa_id, proceso_id, version_numero, origen, bpmn_id, tipo, nombre, detalle,
                                     busqueda)
    select e.empresa_id, el_proceso, el_numero, 'versiones_proceso', e.bpmn_id, e.tipo, left(e.nombre, 1000),
           left(e.detalle, 2000), vector_de_elemento(e.empresa_id, el_proceso, e.bpmn_id, e.nombre, e.detalle)
    from (
        select v.empresa_id, elemento->>'bpmnId' as bpmn_id,
               case lista.clave
                   when 'pools' then 'POOL'
                   when 'lanes' then 'LANE'
                   when 'actividades' then 'ACTIVIDAD'
                   when 'gateways' then 'GATEWAY'
                   when 'eventos' then 'EVENTO'
                   when 'arcos' then 'ARCO'
                   when 'mensajes' then 'MENSAJE'
                   else 'ARTEFACTO'
               end as tipo,
               coalesce(elemento->>'nombre', elemento->>'etiqueta', elemento->>'texto') as nombre,
               coalesce(elemento->>'descripcion', elemento->>'condicion', elemento->>'contenido') as detalle
        from versiones_proceso v
        cross join lateral jsonb_each(v.definicion::jsonb) as lista(clave, valor)
        cross join lateral jsonb_array_elements(case when jsonb_typeof(lista.valor) = 'array' then lista.valor
                                                     else '[]'::jsonb end) as elemento
        where v.proceso_id = el_proceso and v.numero = el_numero
          and lista.clave in ('pools', 'lanes', 'actividades', 'gateways', 'eventos', 'arcos', 'mensajes', 'artefactos')
          and elemento->>'bpmnId' is not null
    ) e
    on conflict do nothing;
end
$$;

create function busqueda_de_la_version() returns trigger language plpgsql as $$
begin
    perform indexar_version_publicada(new.id, new.version_publicada);
    return null;
end
$$;
create trigger busqueda_al_publicar after update on procesos
    for each row when (old.version_publicada is distinct from new.version_publicada)
    execute function busqueda_de_la_version();

-- Documentacion -------------------------------------------------------------------------------------------------------

-- La documentacion entra en el vector de lo que documenta: con bpmn_id vacio, el proceso; si no, sus elementos.
create function refrescar_documentacion(el_proceso bigint, el_bpmn_id varchar) returns void language plpgsql as $$
begin
    if el_bpmn_id is null then
        update procesos p set busqueda = vector_de_proceso(p) where p.id = el_proceso;
    else
        update elementos_buscables i
        set busqueda = vector_de_elemento(i.empresa_id, i.proceso_id, i.bpmn_id, i.nombre, i.detalle)
        where i.proceso_id = el_proceso and i.bpmn_id = el_bpmn_id;
    end if;
end
$$;

create function busqueda_de_la_documentacion() returns trigger language plpgsql as $$
begin
    if tg_op <> 'INSERT' then
        perform refrescar_documentacion(old.proceso_id, old.bpmn_id);
    end if;
    if tg_op <> 'DELETE' then
        perform refrescar_documentacion(new.proceso_id, new.bpmn_id);
    end if;
    return null;
end
$$;
create trigger busqueda_de_la_documentacion after insert or delete on documentacion
    for each row execute function busqueda_de_la_documentacion();
create trigger busqueda_al_cambiar_documentacion after update on documentacion
    for each row when (old.markdown is distinct from new.markdown or old.bpmn_id is distinct from new.bpmn_id)
    execute function busqueda_de_la_documentacion();

-- Comentarios ---------------------------------------------------------------------------------------------------------

alter table comentarios add column busqueda tsvector;
create index idx_comentarios_busqueda on comentarios using gin (busqueda);

create function busqueda_del_comentario() returns trigger language plpgsql as $$
begin
    new.busqueda := vector_de_comentario(configuracion_de_busqueda(new.empresa_id), new.markdown, new.eliminado_en);
    return new;
end
$$;
create trigger busqueda_al_crear_comentario before insert on comentarios
    for each row execute function busqueda_del_comentario();
create trigger busqueda_al_cambiar_comentario before update on comentarios
    for each row when (old.markdown is distinct from new.markdown or old.eliminado_en is distinct from new.eliminado_en)
    execute function busqueda_del_comentario();

-- Documentos ----------------------------------------------------------------------------------------------------------

-- El texto que la cola le saca a un archivo disponible (con su tope), y cuando lo intento: con texto_extraido_en lleno
-- y texto vacio, el archivo no tenia texto o no se pudo leer, y se busca solo por su nombre.
alter table versiones_documento add column texto text;
alter table versiones_documento add column texto_extraido_en timestamp(6);
alter table versiones_documento add column busqueda tsvector;
create index idx_versiones_documento_busqueda on versiones_documento using gin (busqueda);

create function busqueda_del_archivo() returns trigger language plpgsql as $$
begin
    new.busqueda := vector_de_archivo(configuracion_de_busqueda(new.empresa_id), new.nombre, new.texto);
    return new;
end
$$;
create trigger busqueda_al_subir_archivo before insert on versiones_documento
    for each row execute function busqueda_del_archivo();
create trigger busqueda_al_leer_archivo before update on versiones_documento
    for each row when (old.nombre is distinct from new.nombre or old.texto is distinct from new.texto)
    execute function busqueda_del_archivo();

-- Idioma --------------------------------------------------------------------------------------------------------------

-- Un tramo de los vectores de una tienda, otra vez, con la configuracion que tiene ahora: las filas de esa tabla con id
-- mayor que desde, hasta cuantos. Devuelve el ultimo id que rehizo, de donde sigue el tramo siguiente; vacio, si ya no
-- quedan. Cuando cambia el idioma, la API encola un trabajo que la recorre tabla por tabla, cada tramo en su propia
-- transaccion: rehacer de una vez la tienda entera, con el texto de sus documentos, puede pasar del tope por sentencia.
create function rehacer_busqueda(la_empresa bigint, la_tabla text, desde bigint, cuantos integer) returns bigint
    language plpgsql as $$
declare
    configuracion regconfig := configuracion_de_busqueda(la_empresa);
    hasta bigint;
begin
    case la_tabla
        when 'procesos' then
            select max(t.id) into hasta from (
                select p.id from procesos p where p.empresa_id = la_empresa and p.id > desde order by p.id limit cuantos
            ) t;
            update procesos p set busqueda = vector_de_proceso(p)
            where p.empresa_id = la_empresa and p.id > desde and p.id <= hasta;
        when 'elementos_buscables' then
            select max(t.id) into hasta from (
                select i.id from elementos_buscables i where i.empresa_id = la_empresa and i.id > desde order by i.id
                limit cuantos
            ) t;
            update elementos_buscables i
            set busqueda = vector_de_elemento(i.empresa_id, i.proceso_id, i.bpmn_id, i.nombre, i.detalle)
            where i.empresa_id = la_empresa and i.id > desde and i.id <= hasta;
        when 'comentarios' then
            select max(t.id) into hasta from (
                select c.id from comentarios c where c.empresa_id = la_empresa and c.id > desde order by c.id
                limit cuantos
            ) t;
            update comentarios c set busqueda = vector_de_comentario(configuracion, c.markdown, c.eliminado_en)
            where c.empresa_id = la_empresa and c.id > desde and c.id <= hasta;
        when 'versiones_documento' then
            select max(t.id) into hasta from (
                select v.id from versiones_documento v where v.empresa_id = la_empresa and v.id > desde order by v.id
                limit cuantos
            ) t;
            update versiones_documento v set busqueda = vector_de_archivo(configuracion, v.nombre, v.texto)
            where v.empresa_id = la_empresa and v.id > desde and v.id <= hasta;
        else
            raise exception 'La tabla % no tiene vectores de busqueda', la_tabla;
    end case;
    return hasta;
end
$$;

-- Lo que ya existe ----------------------------------------------------------------------------------------------------

select indexar_elemento('pools', to_jsonb(t)) from pools t where t.activo;
select indexar_elemento('lanes', to_jsonb(t)) from lanes t where t.activo;
select indexar_elemento('nodos_flujo', to_jsonb(t)) from nodos_flujo t where t.activo;
select indexar_elemento('arcos', to_jsonb(t)) from arcos t where t.activo;
select indexar_elemento('mensajes', to_jsonb(t)) from mensajes t where t.activo;
select indexar_elemento('artefactos', to_jsonb(t)) from artefactos t where t.activo;
select indexar_version_publicada(p.id, p.version_publicada) from procesos p where p.version_publicada is not null;
select rehacer_busqueda(e.id, t.tabla, 0, 2147483647)
from empresas e
cross join (values ('procesos'), ('elementos_buscables'), ('comentarios'), ('versiones_documento')) as t(tabla);

-- El texto de los documentos que ya estaban disponibles lo saca la cola, como el de los que se suban desde ahora. La
-- API anota las horas de la cola en UTC, y sus cinco intentos son los de trabajos.maximo-intentos de fabrica.
insert into trabajos (empresa_id, tipo, datos, estado, intentos, maximo_intentos, disponible_desde, creado_en)
select v.empresa_id, 'documentos.leer-texto', v.id::text, 'PENDIENTE', 0, 5, now() at time zone 'UTC',
       now() at time zone 'UTC'
from versiones_documento v
where v.estado = 'DISPONIBLE'
  and v.tipo in ('application/pdf', 'text/plain', 'text/csv', 'text/markdown',
                 'application/vnd.openxmlformats-officedocument.wordprocessingml.document',
                 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet',
                 'application/vnd.openxmlformats-officedocument.presentationml.presentation',
                 'application/vnd.oasis.opendocument.text', 'application/vnd.oasis.opendocument.spreadsheet');
