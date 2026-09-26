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
}
