-- PR 35: la baja de una tienda espera 30 dias en solo lectura y luego borra todo lo suyo; de su fila queda una
-- lapida con el id y las fechas, sin nada que diga cual era. Una persona anonimizada conserva su fila, porque la
-- nombran la autoria de lo que hizo y el historial, pero sin ningun dato que la identifique.
alter table empresas add column baja_solicitada_en timestamp(6);
alter table empresas add column borrada_en timestamp(6);
alter table usuarios add column anonimizado_en timestamp(6);
