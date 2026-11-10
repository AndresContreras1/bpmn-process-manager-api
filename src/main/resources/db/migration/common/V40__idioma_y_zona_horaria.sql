-- F12: el idioma y la zona horaria. La organizacion tiene los suyos, con los que trabaja su gente; cada persona puede
-- elegir otros, y vacios sigue los de su organizacion. El idioma es uno de los cuatro de la web: espanol de Colombia,
-- ingles de Estados Unidos o de Australia y frances de Francia, que tambien dicen como se escriben las fechas, los
-- numeros y la moneda. La zona horaria es un nombre de la base de IANA, como America/Bogota.
alter table configuracion_tienda add column idioma varchar(5) default 'es-CO' not null;
alter table configuracion_tienda add column zona_horaria varchar(40) default 'America/Bogota' not null;
alter table configuracion_tienda add constraint ck_configuracion_tienda_idioma
    check (idioma in ('es-CO', 'en-US', 'en-AU', 'fr-FR'));

alter table usuarios add column idioma varchar(5);
alter table usuarios add column zona_horaria varchar(40);
alter table usuarios add constraint ck_usuarios_idioma
    check (idioma is null or idioma in ('es-CO', 'en-US', 'en-AU', 'fr-FR'));
