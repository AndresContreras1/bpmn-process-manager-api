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
  /** Quien la tomo; vacia mientras nadie la tome. Tomarla es una nota para el equipo, no un permiso. */
  asignadoA: number | null;
  tickInicio: number;
  tickFin: number | null;
  datosSalida: Datos | null;
  version: number;
  fechaCreacion: string;
}

/** Lo que entrega quien completa la tarea; va a las variables del caso bajo tarea.<nombreEnCamel>. */
export interface CompletarTareaRequest {
  datos: Datos;
}

/** Quien toma la tarea; vacio la deja libre otra vez. */
export interface AsignarTareaRequest {
  usuarioId: number | null;
}

/** Filtros de la bandeja. Por defecto, las que esperan a los roles de quien entro. */
export interface FiltrosTarea {
  mias: boolean;
  rolProcesoId: number | '';
  procesoId: number | '';
  estado: EstadoPaso | '';
  pagina: number;
}
