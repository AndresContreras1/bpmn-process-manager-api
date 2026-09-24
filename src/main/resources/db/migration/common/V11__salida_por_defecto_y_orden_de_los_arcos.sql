-- Un gateway que decide evalua sus salidas en orden y toma la primera verdadera; si ninguna lo es, toma la salida
-- por defecto. Los arcos que ya existen no son la salida por defecto y comparten el orden 0: entre iguales decide
-- el id, que es como se evaluaban hasta ahora.
alter table arcos add column por_defecto boolean default false not null;
alter table arcos add column orden integer default 0 not null;

-- La salida por defecto es justo la que se toma cuando ninguna condicion se cumple: llevar condicion la contradice.
alter table arcos add constraint ck_arcos_por_defecto_sin_condicion
    check (not por_defecto or condicion is null);
