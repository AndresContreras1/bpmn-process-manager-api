-- PR 51: el aislamiento entre tiendas tambien en la base. Hasta aqui lo hacia solo el codigo, consulta por consulta;
-- desde aqui, PostgreSQL no devuelve ni deja escribir una fila de otra tienda aunque una consulta se olvide de filtrar.
--
-- La API trabaja con el rol procesos_app, que no es dueno de ninguna tabla ni tiene BYPASSRLS: cada conexion que
-- toma del pool lo asume y fija la tienda de quien pide, en app.empresa_id. Flyway sigue migrando con el dueno.
-- Lo que todavia no tiene tienda (el login por correo, el registro, los enlaces del correo, la vuelta de un
-- proveedor) y lo que cruza tiendas a proposito (la purga, la cola de trabajos, el reloj de la simulacion) lo
-- declara el codigo, por su nombre, poniendo app.sistema en on solo para eso.

do $$
begin
    if not exists (select from pg_roles where rolname = 'procesos_app') then
        create role procesos_app nologin;
    end if;
end
$$;

do $$
declare
    esquema text := current_schema();
begin
    execute format('grant usage on schema %I to procesos_app', esquema);
    execute format('grant select, insert, update, delete on all tables in schema %I to procesos_app', esquema);
    execute format('grant usage, select on all sequences in schema %I to procesos_app', esquema);
    -- Lo que creen las migraciones que vengan, con el mismo dueno, tambien.
    execute format('alter default privileges in schema %I grant select, insert, update, delete on tables '
                   'to procesos_app', esquema);
    execute format('alter default privileges in schema %I grant usage, select on sequences to procesos_app', esquema);
end
$$;

-- La bitacora (PR 31) solo crece: la API puede agregarle y leerla, nada mas.
revoke update, delete, truncate on bitacora from procesos_app;

-- Cada tabla con empresa_id: solo las filas de la tienda de quien pide, para leer y para escribir. FORCE hace que la
-- politica valga tambien para el dueno de la tabla.
do $$
declare
    tabla text;
begin
    for tabla in
        select table_name from information_schema.columns
        where table_schema = current_schema() and column_name = 'empresa_id'
    loop
        execute format('alter table %I enable row level security', tabla);
        execute format('alter table %I force row level security', tabla);
        execute format('create policy aislamiento_por_tienda on %I '
                       'using (current_setting(''app.sistema'', true) = ''on'' '
                       'or empresa_id = nullif(current_setting(''app.empresa_id'', true), '''')::bigint) '
                       'with check (current_setting(''app.sistema'', true) = ''on'' '
                       'or empresa_id = nullif(current_setting(''app.empresa_id'', true), '''')::bigint)', tabla);
    end loop;
end
$$;

-- HU-23: la tienda invitada a un proceso lee su diagrama, en solo lectura. Lo que se comparte es el proceso y lo que
-- cuelga de el; la funcion corre con los permisos de su dueno para leer procesos_compartidos sin depender de su
-- propia politica, y solo devuelve lo compartido con la tienda de quien pide.
create function procesos_compartidos_conmigo() returns setof bigint
    language sql stable security definer set search_path = pg_catalog, public as $$
    select proceso_id from procesos_compartidos
    where empresa_invitada_id = nullif(current_setting('app.empresa_id', true), '')::bigint
$$;
revoke all on function procesos_compartidos_conmigo() from public;
grant execute on function procesos_compartidos_conmigo() to procesos_app;

create policy lectura_de_la_invitada on procesos_compartidos for select
    using (empresa_invitada_id = nullif(current_setting('app.empresa_id', true), '')::bigint);
create policy lectura_compartida on procesos for select
    using (id in (select procesos_compartidos_conmigo()));
-- D2: lo que la invitada ve de un proceso es su version publicada.
create policy lectura_compartida on versiones_proceso for select
    using (proceso_id in (select procesos_compartidos_conmigo()));
create policy lectura_compartida on pools for select
    using (proceso_id in (select procesos_compartidos_conmigo()));
create policy lectura_compartida on mensajes for select
    using (proceso_id in (select procesos_compartidos_conmigo()));
create policy lectura_compartida on lanes for select
    using (pool_id in (select id from pools where proceso_id in (select procesos_compartidos_conmigo())));
create policy lectura_compartida on arcos for select
    using (pool_id in (select id from pools where proceso_id in (select procesos_compartidos_conmigo())));
create policy lectura_compartida on nodos_flujo for select
    using (lane_id in (select l.id from lanes l join pools p on p.id = l.pool_id
                       where p.proceso_id in (select procesos_compartidos_conmigo())));
create policy lectura_compartida on correlaciones for select
    using (mensaje_id in (select id from mensajes where proceso_id in (select procesos_compartidos_conmigo())));
create policy lectura_compartida on roles_proceso for select
    using (id in (select l.rol_proceso_id from lanes l join pools p on p.id = l.pool_id
                  where p.proceso_id in (select procesos_compartidos_conmigo())));
