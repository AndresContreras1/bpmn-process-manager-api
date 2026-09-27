import { Proceso } from './proceso.model';

export type TipoParticipante = 'EMPRESA' | 'CLIENTE' | 'PROVEEDOR' | 'SISTEMA_EXTERNO';
export type TipoGateway = 'EXCLUSIVO' | 'PARALELO' | 'INCLUSIVO';

/** Los eventos que el modelo acepta. No hay temporizadores ni eventos de error: no estan en la API. */
export type TipoEvento = 'INICIO' | 'FIN' | 'MENSAJE_INICIO' | 'MENSAJE_INTERMEDIO' | 'MENSAJE_FIN';
export type TipoDestino = 'CORREO' | 'SERVICIO_WEB' | 'COLA';
export type AccionSiFalla = 'CONTINUAR' | 'MANEJAR_ERROR' | 'FINALIZAR';
export type PoliticaSinCaso = 'DESCARTAR' | 'INICIAR_CASO';
export type TipoDeDato = 'TEXTO' | 'NUMERO' | 'FECHA' | 'BOOLEANO';

/** Participante del proceso (PoolResponse). Una caja negra no muestra su interior. */
export interface Pool {
  id: number;
  nombre: string;
  tipoParticipante: TipoParticipante;
  cajaNegra: boolean;
  orden: number;
  procesoId: number;
}

/** Carril de un pool, ligado a un rol de proceso (LaneResponse). */
export interface Lane {
  id: number;
  nombre: string;
  orden: number;
  poolId: number;
  rolProcesoId: number;
  rolProcesoNombre: string;
}

/** Tarea del diagrama (ActividadResponse); la posicion es el centro de la tarea en el lienzo. */
export interface Actividad {
  id: number;
  nombre: string;
  descripcion: string | null;
  posicionX: number;
  posicionY: number;
  laneId: number;
}

/** Punto de decision (GatewayResponse). Comparte los ids con las actividades: los dos son nodos del flujo. */
export interface Gateway {
  id: number;
  nombre: string;
  tipoGateway: TipoGateway;
  posicionX: number;
  posicionY: number;
  laneId: number;
}

/**
 * Evento del proceso (EventoResponse): por donde empieza, por donde termina y donde espera o manda un mensaje.
 * Comparte los ids con las actividades y los gateways: los tres son nodos del flujo.
 */
export interface Evento {
  id: number;
  nombre: string;
  tipoEvento: TipoEvento;
  posicionX: number;
  posicionY: number;
  laneId: number;
}

/** Flujo de secuencia entre dos nodos, dentro de un pool (ArcoResponse). */
export interface Arco {
  id: number;
  etiqueta: string | null;
  condicion: string | null;
  origenId: number;
  destinoId: number;
  poolId: number;
}

/** Un dato que viaja dentro de un mensaje (CampoDeMensaje). */
export interface CampoDeMensaje {
  nombre: string;
  tipo: TipoDeDato;
}

/**
 * Flujo de mensaje entre dos pools (MensajeResponse). Los nodos de los extremos pueden faltar: una caja negra no
 * muestra por donde sale ni por donde entra.
 */
export interface Mensaje {
  id: number;
  nombre: string;
  contenido: string;
  poolOrigenId: number;
  poolDestinoId: number;
  nodoOrigenId: number | null;
  nodoDestinoId: number | null;
  tipoDestino: TipoDestino | null;
  siFalla: AccionSiFalla | null;
  nodoManejoErrorId: number | null;
  origenExterno: boolean;
  campos: CampoDeMensaje[];
  usoDeLosDatos: string | null;
  variable: string | null;
  respuestaEsperadaId: number | null;
  procesoId: number;
}

/** Clave con la que se relacionan los mensajes de una misma instancia, como el numero de pedido. */
export interface Correlacion {
  id: number;
  criterio: string;
  campo: string;
  sinCaso: PoliticaSinCaso;
  mensajeId: number;
}

/** El diagrama completo en una respuesta (DiagramaResponse): listas planas que se enlazan por id. */
export interface Diagrama {
  proceso: Proceso;
  compartido: boolean;
  pools: Pool[];
  lanes: Lane[];
  actividades: Actividad[];
  gateways: Gateway[];
  eventos: Evento[];
  arcos: Arco[];
  mensajes: Mensaje[];
  correlaciones: Correlacion[];
}

/** Nombre de cada tipo de participante en la interfaz. */
export const NOMBRE_PARTICIPANTE: Record<TipoParticipante, string> = {
  EMPRESA: 'store',
  CLIENTE: 'customer',
  PROVEEDOR: 'supplier',
  SISTEMA_EXTERNO: 'external system',
};

/** Nombre de cada tipo de gateway en la interfaz. */
export const NOMBRE_GATEWAY: Record<TipoGateway, string> = {
  EXCLUSIVO: 'Exclusive gateway',
  PARALELO: 'Parallel gateway',
  INCLUSIVO: 'Inclusive gateway',
};

/** Nombre de cada tipo de evento en la interfaz. */
export const NOMBRE_EVENTO: Record<TipoEvento, string> = {
  INICIO: 'Start event',
  FIN: 'End event',
  MENSAJE_INICIO: 'Message start event',
  MENSAJE_INTERMEDIO: 'Message catch event',
  MENSAJE_FIN: 'Message end event',
};

/** Como sale el mensaje hacia el otro participante. */
export const NOMBRE_DESTINO: Record<TipoDestino, string> = {
  CORREO: 'email',
  SERVICIO_WEB: 'web service',
  COLA: 'queue',
};

/** Que hace el proceso si el mensaje no se puede entregar. */
export const NOMBRE_SI_FALLA: Record<AccionSiFalla, string> = {
  CONTINUAR: 'the process carries on',
  MANEJAR_ERROR: 'the process goes to a task that handles it',
  FINALIZAR: 'the process ends',
};

/** El tipo de un campo del mensaje. La API los nombra en espanol, como el historial de cambios. */
export const NOMBRE_TIPO_DATO: Record<TipoDeDato, string> = {
  TEXTO: 'text',
  NUMERO: 'number',
  FECHA: 'date',
  BOOLEANO: 'yes/no',
};

/** Que hace un mensaje que llega y no corresponde a ningun caso abierto. */
export const NOMBRE_SIN_CASO: Record<PoliticaSinCaso, string> = {
  DESCARTAR: 'it is discarded',
  INICIAR_CASO: 'it opens a new case',
};

/** Un evento de inicio no recibe flujo de secuencia y uno de fin no lo emite. */
export function empiezaElProceso(tipo: TipoEvento): boolean {
  return tipo === 'INICIO' || tipo === 'MENSAJE_INICIO';
}

export function terminaElProceso(tipo: TipoEvento): boolean {
  return tipo === 'FIN' || tipo === 'MENSAJE_FIN';
}
