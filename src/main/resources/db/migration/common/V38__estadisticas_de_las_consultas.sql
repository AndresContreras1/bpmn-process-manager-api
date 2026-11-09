-- PR 36: pg_stat_statements cuenta, de cada consulta, cuantas veces corrio, cuanto tardo y cuantas filas toco. Es
-- donde se mira que consulta se puso lenta (docs/runbook.md).
--
-- Leerla pide que el servidor cargue la biblioteca al arrancar (shared_preload_libraries), y eso lo pone el
-- compose; crear la extension pide un superusuario. Un servidor que no la trae, o un dueno de la base que no puede
-- crearla, como en algunas bases administradas, migra igual: la extension se deja para que la cree quien administre
-- el servidor, y la API no la usa para nada.
do $$
begin
    if exists (select from pg_available_extensions where name = 'pg_stat_statements') then
        create extension if not exists pg_stat_statements;
    end if;
exception
    when insufficient_privilege then
        raise notice 'pg_stat_statements no se creo: hace falta un superusuario';
end
$$;
