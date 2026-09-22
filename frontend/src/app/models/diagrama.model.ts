import { Proceso } from './proceso.model';

export type TipoParticipante = 'EMPRESA' | 'CLIENTE' | 'PROVEEDOR' | 'SISTEMA_EXTERNO';
export type TipoGateway = 'EXCLUSIVO' | 'PARALELO' | 'INCLUSIVO';

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

/** Flujo de secuencia entre dos nodos, dentro de un pool (ArcoResponse). */
export interface Arco {
  id: number;
  etiqueta: string | null;
  condicion: string | null;
  origenId: number;
  destinoId: number;
  poolId: number;
}

/** Flujo de mensaje entre dos pools (MensajeResponse). */
export interface Mensaje {
  id: number;
  nombre: string;
  contenido: string;
  poolOrigenId: number;
  poolDestinoId: number;
  procesoId: number;
}

/** Clave con la que se relacionan los mensajes de una misma instancia, como el numero de pedido. */
export interface Correlacion {
  id: number;
  criterio: string;
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
