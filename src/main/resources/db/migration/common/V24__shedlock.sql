-- D34: el candado de cada trabajo programado. Con varias instancias de la API, cada trabajo corre en una sola a la vez:
-- la que lo toma deja aqui hasta cuando lo tiene y quien es. Los nombres de las columnas son los que usa ShedLock.
create table shedlock (
    name       varchar(64)  not null primary key,
    lock_until timestamp(3) not null,
    locked_at  timestamp(3) not null,
    locked_by  varchar(255) not null
);
