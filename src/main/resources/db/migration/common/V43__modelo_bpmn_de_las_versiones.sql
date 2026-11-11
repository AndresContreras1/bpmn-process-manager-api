-- PR 53: cada version guarda tambien su diagrama en BPMN 2.0.2, escrito al publicarla y que no se vuelve a calcular.
-- La V44 se lo escribe a las versiones que ya existian y despues deja la columna obligatoria.
alter table versiones_proceso add column modelo_bpmn text;
