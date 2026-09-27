import { Proceso } from './proceso.model';

export type TipoParticipante = 'EMPRESA' | 'CLIENTE' | 'PROVEEDOR' | 'SISTEMA_EXTERNO';
export type TipoGateway = 'EXCLUSIVO' | 'PARALELO' | 'INCLUSIVO';

/** Los eventos que el modelo acepta. No hay temporizadores ni eventos de error: no estan en la API. */
export type TipoEvento = 'INICIO' | 'FIN' | 'MENSAJE_INICIO' | 'MENSAJE_INTERMEDIO' | 'MENSAJE_FIN';
export type TipoDestino = 'CORREO' | 'SERVICIO_WEB' | 'COLA';
export type AccionSiFalla = 'CONTINUAR' | 'MANEJAR_ERROR' | 'FINALIZAR';
export type PoliticaSinCaso = 'DESCARTAR' | 'INICIAR_CASO';
export type TipoDeDato = 'TEXTO' | 'NUMERO' | 'FECHA' | 'BOOLEANO';
export type TipoActividad = 'USUARIO' | 'SERVICIO' | 'ENVIO' | 'RECEPCION';
/** Con que socio simulado habla el participante cuando el proceso corre. */
export type Integracion = 'NINGUNA' | 'CLIENTE' | 'PAGOS' | 'TRANSPORTE' | 'NOTIFICACIONES';

/** Participante del proceso (PoolResponse). Una caja negra no muestra su interior. */
export interface Pool {
  id: number;
  nombre: string;
  tipoParticipante: TipoParticipante;
  cajaNegra: boolean;
  integracion: Integracion;
  orden: number;
  procesoId: number;
  version: number;
}

export interface PoolRequest {
  nombre: string;
  tipoParticipante: TipoParticipante;
  cajaNegra: boolean;
  integracion: Integracion;
}

export interface EditarPoolRequest extends PoolRequest {
  version: number;
}

/** Carril de un pool, ligado a un rol de proceso (LaneResponse). */
export interface Lane {
  id: number;
  nombre: string;
  orden: number;
  poolId: number;
  rolProcesoId: number;
  rolProcesoNombre: string;
  version: number;
}

export interface LaneRequest {
  nombre: string;
  rolProcesoId: number;
}

export interface EditarLaneRequest extends LaneRequest {
  version: number;
}

/** Tarea del diagrama (ActividadResponse); la posicion es el centro de la tarea en el lienzo. */
export interface Actividad {
  id: number;
  nombre: string;
  descripcion: string | null;
  tipoActividad: TipoActividad;
  posicionX: number;
  posicionY: number;
  laneId: number;
  version: number;
}

export interface ActividadRequest {
  nombre: string;
  descripcion: string | null;
  tipoActividad: TipoActividad;
  posicionX: number;
  posicionY: number;
}

/** Al editar se puede mover de lane, y eso cambia el rol que atiende la tarea. */
export interface EditarActividadRequest extends ActividadRequest {
  laneId: number;
  version: number;
}

/** Punto de decision (GatewayResponse). Comparte los ids con las actividades: los dos son nodos del flujo. */
export interface Gateway {
  id: number;
  nombre: string;
  tipoGateway: TipoGateway;
  posicionX: number;
  posicionY: number;
  laneId: number;
  version: number;
}

export interface GatewayRequest {
  nombre: string;
  tipoGateway: TipoGateway;
  posicionX: number;
  posicionY: number;
}

export interface EditarGatewayRequest extends GatewayRequest {
  laneId: number;
  version: number;
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
  version: number;
}

export interface EventoRequest {
  nombre: string;
  tipoEvento: TipoEvento;
  posicionX: number;
  posicionY: number;
}

export interface EditarEventoRequest extends EventoRequest {
  laneId: number;
  version: number;
}

/** Flujo de secuencia entre dos nodos, dentro de un pool (ArcoResponse). */
export interface Arco {
  id: number;
  etiqueta: string | null;
  condicion: string | null;
  /** La salida que se toma cuando ninguna condicion se cumple; solo una por gateway. */
  porDefecto: boolean;
  orden: number;
  origenId: number;
  destinoId: number;
  poolId: number;
  version: number;
}

export interface ArcoRequest {
  origenId: number;
  destinoId: number;
  etiqueta: string | null;
  condicion: string | null;
  porDefecto: boolean;
  orden: number;
}

export interface EditarArcoRequest extends ArcoRequest {
  version: number;
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
  version: number;
}

/** Los dos pools son obligatorios; los nodos de los extremos solo si ese lado no es una caja negra. */
export interface MensajeRequest {
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
}

/** Editar no mueve el mensaje de pools: para eso se borra y se crea otro. */
export type EditarMensajeRequest = Omit<MensajeRequest, 'poolOrigenId' | 'poolDestinoId'> & { version: number };

/** Clave con la que se relacionan los mensajes de una misma instancia, como el numero de pedido. */
export interface Correlacion {
  id: number;
  criterio: string;
  campo: string;
  sinCaso: PoliticaSinCaso;
  mensajeId: number;
  version: number;
}

/** La correlacion se pone entera con un PUT; la version solo va cuando ya habia una. */
export interface CorrelacionRequest {
  criterio: string;
  campo: string;
  sinCaso: PoliticaSinCaso;
  version: number | null;
}

/** El cuerpo de PUT /pools/{id}/lanes/orden y de PUT /procesos/{id}/pools/orden: los ids en el orden que toca. */
export interface OrdenRequest {
  ids: number[];
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

/** Nombre de cada tipo de actividad en la interfaz. */
export const NOMBRE_ACTIVIDAD: Record<TipoActividad, string> = {
  USUARIO: 'User task',
  SERVICIO: 'Service task',
  ENVIO: 'Send task',
  RECEPCION: 'Receive task',
};

/** Que socio simulado atiende a ese participante cuando el proceso corre. */
export const NOMBRE_INTEGRACION: Record<Integracion, string> = {
  NINGUNA: 'none',
  CLIENTE: 'customer',
  PAGOS: 'payments',
  TRANSPORTE: 'shipping',
  NOTIFICACIONES: 'notifications',
};

/** Solo una actividad de envio o de servicio manda mensajes, y solo una de recepcion los espera. */
export function actividadPuedeEnviar(tipo: TipoActividad): boolean {
  return tipo === 'ENVIO' || tipo === 'SERVICIO';
}

export function actividadPuedeRecibir(tipo: TipoActividad): boolean {
  return tipo === 'RECEPCION';
}

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
