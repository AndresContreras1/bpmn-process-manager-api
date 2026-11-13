/** Lo que le puede pasar a un caso. ERROR es un gateway sin camino: se arregla y se reintenta. */
import { Tono } from './tono.model';

export type EstadoCaso = 'ABIERTO' | 'TERMINADO' | 'CANCELADO' | 'FALLIDO' | 'ERROR';

/** Cada linea de la linea de tiempo es uno de estos. */
export type TipoEventoCaso =
  | 'CASO_ABIERTO'
  | 'NODO_ACTIVADO'
  | 'TAREA_CREADA'
  | 'TAREA_COMPLETADA'
  | 'GATEWAY_DECIDIO'
  | 'MENSAJE_ENVIADO'
  | 'MENSAJE_RECIBIDO'
  | 'ENVIO_FALLIDO'
  | 'SIN_CAMINO'
  | 'VARIABLE_AUSENTE'
  | 'CASO_TERMINADO'
  | 'CASO_CANCELADO'
  | 'TAREA_ASIGNADA'
  | 'TAREA_RECLAMADA'
  | 'TAREA_LIBERADA'
  | 'TAREA_REASIGNADA'
  | 'TAREA_DELEGADA'
  | 'PLAZO_POR_VENCER'
  | 'PLAZO_VENCIDO'
  | 'TAREA_ESCALADA';

/** En que va un paso del caso. PENDIENTE y EN_ESPERA son los dos que tienen el token vivo. */
export type EstadoPaso = 'PENDIENTE' | 'EN_ESPERA' | 'COMPLETADA' | 'FALLIDA' | 'OMITIDA';

export type TipoNodoCaso = 'ACTIVIDAD' | 'GATEWAY' | 'EVENTO';

/**
 * Las variables del caso, el cuerpo de un mensaje y los datos de una tarea son objetos libres: la API los guarda
 * tal como llegan y las condiciones de los gateways los leen. Aqui se tratan como lo que son, datos sin forma
 * conocida, y cada pantalla decide como mostrarlos.
 */
export type Datos = Record<string, unknown>;

/** Una ejecucion de una version publicada: un pedido (CasoResponse). */
export interface Caso {
  id: number;
  procesoId: number;
  procesoNombre: string;
  /** Numero de la version publicada sobre la que corre; el diagrama que se dibuja es el de esa version. */
  versionNumero: number;
  referencia: string;
  estado: EstadoCaso;
  tickInicio: number;
  tickFin: number | null;
  fechaInicio: string;
  fechaFin: string | null;
  version: number;
  /** Quien lo abrio; vacio cuando lo abrio un mensaje. */
  creadoPor: number | null;
}

/** Un paso del caso por un nodo de su version (PasoDelCasoResponse). */
export interface PasoDelCaso {
  id: number;
  /** Id del nodo dentro de la version publicada: el mismo que trae el diagrama congelado. */
  nodoId: number;
  nodoNombre: string;
  tipoNodo: TipoNodoCaso;
  /** El tipo de la actividad, del gateway o del evento, como lo tenia la version. */
  subtipo: string;
  rolProcesoId: number | null;
  estado: EstadoPaso;
  llegadas: number;
  asignadoA: number | null;
  tickInicio: number;
  tickFin: number | null;
  datosSalida: Datos | null;
  version: number;
  fechaCreacion: string;
  modificadoPor: number | null;
  fechaModificacion: string;
}

/** El caso con lo que ha recorrido y las variables con las que deciden sus gateways. */
export interface CasoDetalle {
  caso: Caso;
  pasos: PasoDelCaso[];
  variables: Datos;
}

/** Una linea de la linea de tiempo (EventoCasoResponse). */
export interface EventoCaso {
  id: number;
  tick: number;
  fecha: string;
  tipo: TipoEventoCaso;
  detalle: string;
  /** Quien lo provoco; vacio cuando fue el motor. */
  autorId: number | null;
}

/** Abrir un caso a mano: la referencia por la que se correlacionan sus mensajes y sus variables iniciales. */
export interface AbrirCasoRequest {
  referencia: string;
  variables: Datos;
}

/** Las variables se reemplazan enteras, y sobre la version que se leyo. */
export interface VariablesRequest {
  variables: Datos;
  version: number;
}

/** Filtros de la lista de casos; los vacios no se envian. */
export interface FiltrosCaso {
  procesoId: number | '';
  estado: EstadoCaso | '';
  referencia: string;
  orden: 'id' | 'referencia' | 'estado';
  direccion: 'asc' | 'desc';
  pagina: number;
}

export const NOMBRE_ESTADO_CASO: Record<EstadoCaso, string> = {
  // El mismo Open que el boton de abrir uno, pero adjetivo: el significado los separa al traducir (Abierto, Abrir)
  ABIERTO: $localize`:estado de un caso|El caso sigue corriendo:Open`,
  TERMINADO: $localize`Finished`,
  CANCELADO: $localize`Cancelled`,
  FALLIDO: $localize`Failed`,
  ERROR: $localize`Stuck`,
};

/** El tono de la insignia de cada estado, con su color y su icono, para que la lista se lea de un vistazo. */
export const TONO_ESTADO_CASO: Record<EstadoCaso, Tono> = {
  ABIERTO: 'primario',
  TERMINADO: 'exito',
  CANCELADO: 'neutro',
  FALLIDO: 'peligro',
  ERROR: 'aviso',
};

export const NOMBRE_ESTADO_PASO: Record<EstadoPaso, string> = {
  PENDIENTE: $localize`Waiting for more tokens`,
  EN_ESPERA: $localize`Waiting for someone`,
  COMPLETADA: $localize`Done`,
  FALLIDA: $localize`Failed`,
  OMITIDA: $localize`Skipped`,
};

/** Que paso, en una palabra, para la linea de tiempo. */
export const NOMBRE_EVENTO_CASO: Record<TipoEventoCaso, string> = {
  CASO_ABIERTO: $localize`Case opened`,
  NODO_ACTIVADO: $localize`Node reached`,
  TAREA_CREADA: $localize`Task created`,
  TAREA_COMPLETADA: $localize`Task completed`,
  GATEWAY_DECIDIO: $localize`Gateway decided`,
  MENSAJE_ENVIADO: $localize`Message sent`,
  MENSAJE_RECIBIDO: $localize`Message received`,
  ENVIO_FALLIDO: $localize`Delivery failed`,
  SIN_CAMINO: $localize`No path`,
  VARIABLE_AUSENTE: $localize`Variable missing`,
  CASO_TERMINADO: $localize`Case finished`,
  CASO_CANCELADO: $localize`Case cancelled`,
  TAREA_ASIGNADA: $localize`Task assigned`,
  TAREA_RECLAMADA: $localize`Task taken`,
  TAREA_LIBERADA: $localize`Task released`,
  TAREA_REASIGNADA: $localize`Task reassigned`,
  TAREA_DELEGADA: $localize`Task delegated`,
  PLAZO_POR_VENCER: $localize`Due soon`,
  PLAZO_VENCIDO: $localize`Overdue`,
  TAREA_ESCALADA: $localize`Task escalated`,
};

/** Los que cuentan algo que no salio bien: se pintan en rojo. */
export const EVENTOS_MALOS: TipoEventoCaso[] = ['ENVIO_FALLIDO', 'SIN_CAMINO', 'VARIABLE_AUSENTE', 'PLAZO_VENCIDO'];

/** Los dos estados en los que el token sigue vivo dentro del nodo. */
export function pasoVivo(paso: PasoDelCaso): boolean {
  return paso.estado === 'PENDIENTE' || paso.estado === 'EN_ESPERA';
}
