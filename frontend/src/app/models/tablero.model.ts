import { EstadoCaso } from './caso.model';
import { EstadoMensajeSaliente, ResultadoCorrelacion } from './mensajeria.model';

export interface CasosPorEstado {
  estado: EstadoCaso;
  cantidad: number;
}

/** Cuanto tarda un pedido de punta a punta, en ticks del reloj de la tienda. */
export interface CicloDeCaso {
  /** Los casos con los que esta hecho el numero: solo cuentan los terminados. */
  terminados: number;
  medio: number;
  /** Los ticks por debajo de los que se queda diecinueve de cada veinte. */
  p95: number;
}

export interface TareasPorRol {
  rolProcesoId: number | null;
  rolNombre: string;
  tareas: number;
}

export interface MensajesPorEstado {
  estado: EstadoMensajeSaliente;
  cantidad: number;
}

export interface MensajesPorResultado {
  resultado: ResultadoCorrelacion;
  cantidad: number;
}

/** Lo que no salio como se esperaba, contado desde las lineas de tiempo. */
export interface LoQueSalioMal {
  enviosFallidos: number;
  sinCamino: number;
  variablesAusentes: number;
}

/** PR 71: cuantos se hicieron a tiempo y cuantos tarde (o siguen esperando pasado su plazo). */
export interface ATiempoYTarde {
  aTiempo: number;
  tarde: number;
}

export interface CumplimientoPorProceso extends ATiempoYTarde {
  procesoId: number;
  procesoNombre: string;
}

export interface CumplimientoPorActividad extends CumplimientoPorProceso {
  nodoId: number;
  nodoNombre: string;
}

/** Quien la completo, o quien la tiene si sigue esperando pasado su plazo. */
export interface CumplimientoPorPersona extends ATiempoYTarde {
  usuarioId: number;
  nombre: string;
}

/**
 * PR 71: como se cumplieron los plazos. Solo cuenta lo que ya se sabe como le fue, de las versiones publicadas y de
 * los procesos que quien pregunta puede ver (CumplimientoResponse).
 */
export interface Cumplimiento {
  tareasATiempo: number;
  tareasTarde: number;
  casos: ATiempoYTarde;
  porProceso: CumplimientoPorProceso[];
  porActividad: CumplimientoPorActividad[];
  porPersona: CumplimientoPorPersona[];
}

/** Como va la operacion, de un proceso o de la tienda entera (TableroResponse). */
export interface Tablero {
  /** El proceso del que habla; vacio cuando es la tienda entera. */
  procesoId: number | null;
  casos: number;
  casosPorEstado: CasosPorEstado[];
  ciclo: CicloDeCaso;
  tareasPorRol: TareasPorRol[];
  salientes: MensajesPorEstado[];
  entrantes: MensajesPorResultado[];
  loQueSalioMal: LoQueSalioMal;
  /** PR 71: el cumplimiento de los plazos. */
  cumplimiento?: Cumplimiento;
}
