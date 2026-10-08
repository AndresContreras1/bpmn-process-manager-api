-- D34: el outbox de los eventos que cruzan de un modulo a otro. Spring Modulith anota aqui cada evento con cada
-- receptor @ApplicationModuleListener que lo espera, dentro de la misma transaccion que lo publica, y lo borra cuando
-- ese receptor termina: lo que queda es lo que un receptor no termino, y un reinicio lo vuelve a entregar. Es el
-- esquema de Spring Modulith 2 para PostgreSQL; los nombres de la tabla y de las columnas son los suyos.
create table event_publication (
    id                     uuid                     not null primary key,
    listener_id            text                     not null,
    event_type             text                     not null,
    serialized_event       text                     not null,
    publication_date       timestamp with time zone not null,
    completion_date        timestamp with time zone,
    status                 text,
    completion_attempts    integer,
    last_resubmission_date timestamp with time zone
);
create index event_publication_serialized_event_hash_idx on event_publication using hash (serialized_event);
create index event_publication_by_completion_date_idx on event_publication (completion_date);
