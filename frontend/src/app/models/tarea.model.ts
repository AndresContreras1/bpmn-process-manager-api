import { Datos, EstadoPaso } from './caso.model';

/** Una tarea esperando en la bandeja de un rol de proceso (TareaResponse). */
export interface Tarea {
  id: number;
  casoId: number;
  casoReferencia: string;
  procesoId: number;
  procesoNombre: string;
  /** Id del nodo dentro de la version publicada. */
  nodoId: number;
  nodoNombre: string;
  rolProcesoId: number | null;
  estado: EstadoPaso;
  /** Quien la tiene, el unico que la completa; vacia mientras espera en la bandeja de su rol. */
  asignadoA: number | null;
  /** Quien se la delego a quien la tiene; le vuelve si la suelta. */
  delegadaPor?: number | null;
  tickInicio: number;
  tickFin: number | null;
  datosSalida: Datos | null;
  version: number;
  fechaCreacion: string;
  /** PR 71: cuando vence, en UTC, si su actividad tiene plazo y el caso corre en produccion. */
  venceEn?: string | null;
  /** PR 71: el tick en que vence, si su actividad tiene plazo y el caso corre en prueba. */
  venceTick?: number | null;
  /** PR 71: como va su plazo; vacio si no tiene. */
  estadoPlazo?: EstadoPlazo | null;
}

/** PR 71: a tiempo, en el ultimo cuarto de su plazo, o pasado. */
export type EstadoPlazo = 'A_TIEMPO' | 'POR_VENCER' | 'VENCIDA';

/** Lo que entrega quien completa la tarea; va a las variables del caso bajo tarea.<nombreEnCamel>. */
export interface CompletarTareaRequest {
  datos: Datos;
}

/** Filtros de la bandeja. Por defecto, las que esperan a los roles de quien entro. */
export interface FiltrosTarea {
  mias: boolean;
  rolProcesoId: number | '';
  procesoId: number | '';
  estado: EstadoPaso | '';
  pagina: number;
}
