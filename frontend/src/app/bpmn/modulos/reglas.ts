/**
 * Las reglas duras del dominio y lo que cada modo de proceso deja dibujar. Son funciones sobre datos simples, sin
 * bpmn-js dentro: asi se prueban solas, y los modulos del lienzo solo las enchufan.
 */

export type ModoProceso = 'DOCUMENTAL' | 'EJECUTABLE';

/** Lo poco que las reglas necesitan saber de un elemento. */
export interface ElementoPlano {
  tipo: string;
  /** El id del participante que lo contiene; vacio si esta fuera de todos. */
  participante: string | null;
}

/** Que se puede unir con un flujo de secuencia: un inicio no recibe, un fin no emite y nada cruza un pool. */
export function puedeUnirConSecuencia(origen: ElementoPlano, destino: ElementoPlano): boolean {
  return (
    origen.tipo !== 'bpmn:EndEvent' &&
    destino.tipo !== 'bpmn:StartEvent' &&
    destino.tipo !== 'bpmn:BoundaryEvent' &&
    origen.participante === destino.participante
  );
}

/** Un mensaje solo va entre dos pools distintos. */
export function puedeUnirConMensaje(origen: ElementoPlano, destino: ElementoPlano): boolean {
  return origen.participante !== null && destino.participante !== null && origen.participante !== destino.participante;
}

/** Lo que la paleta no ofrece en un proceso que corre: el motor no ejecuta subprocesos. */
const PALETA_SOLO_DOCUMENTAL = ['create.subprocess-expanded'];

export function entradaDePaletaPermitida(entrada: string, modo: ModoProceso): boolean {
  return modo === 'DOCUMENTAL' || !PALETA_SOLO_DOCUMENTAL.includes(entrada);
}

/** Lo que el menu de cambiar de tipo ofrece: lo que el modelo tiene. */
const REEMPLAZOS = new Set([
  'replace-with-task',
  'replace-with-user-task',
  'replace-with-service-task',
  'replace-with-send-task',
  'replace-with-receive-task',
  'replace-with-call-activity',
  'replace-with-collapsed-subprocess',
  'replace-with-expanded-subprocess',
  'replace-with-exclusive-gateway',
  'replace-with-parallel-gateway',
  'replace-with-inclusive-gateway',
  'replace-with-none-start',
  'replace-with-message-start',
  'replace-with-timer-start',
  'replace-with-none-end',
  'replace-with-message-end',
  'replace-with-terminate-end',
  'replace-with-message-intermediate-catch',
  'replace-with-timer-intermediate-catch',
  'replace-with-timer-boundary',
  'replace-with-non-interrupting-timer-boundary',
  'replace-with-subprocess',
  'replace-with-data-object-reference',
  'replace-with-data-store-reference',
  'replace-with-collapsed-pool',
  'replace-with-expanded-pool',
  'replace-with-sequence-flow',
  'replace-with-default-flow',
  'replace-with-conditional-flow',
]);

/** En un proceso que corre, ni tareas sin tipo ni subprocesos ni temporizadores: el diagnostico no dejaria publicarlos. */
const REEMPLAZOS_SOLO_DOCUMENTAL = new Set([
  'replace-with-task',
  'replace-with-collapsed-subprocess',
  'replace-with-expanded-subprocess',
  'replace-with-timer-start',
  'replace-with-timer-intermediate-catch',
  'replace-with-timer-boundary',
  'replace-with-non-interrupting-timer-boundary',
]);

export function reemplazoPermitido(entrada: string, modo: ModoProceso): boolean {
  return REEMPLAZOS.has(entrada) && (modo === 'DOCUMENTAL' || !REEMPLAZOS_SOLO_DOCUMENTAL.has(entrada));
}
