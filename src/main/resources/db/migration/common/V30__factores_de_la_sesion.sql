-- PR 49: con que probo la persona quien es al abrir cada sesion, y cuando lo confirmo por ultima vez. Lo que pide
-- una verificacion reciente, como la baja de la tienda, mira esa fecha. Las sesiones de hoy entraron con su clave.
alter table sesiones add column factores varchar(100) default 'PASSWORD' not null;
alter table sesiones add column verificada_en timestamp(6);
update sesiones set verificada_en = fecha_inicio;
alter table sesiones alter column verificada_en set not null;
