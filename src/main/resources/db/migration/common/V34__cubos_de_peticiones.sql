-- PR 32: el estado de cada cubo del limite de peticiones (Bucket4j), compartido por todas las instancias de la API.
-- id es la clase del limite y a quien cuenta (una IP, un usuario); estado, las fichas que le quedan, como las
-- serializa Bucket4j; vence_en, en milisegundos desde la epoca, cuando el cubo se habra llenado otra vez y ya no
-- hace falta guardarlo. La purga nocturna se lleva los vencidos.
create table cubos_de_peticiones (
    id      varchar(255) primary key,
    estado  bytea,
    vence_en bigint
);
create index idx_cubos_de_peticiones_vence on cubos_de_peticiones (vence_en);
