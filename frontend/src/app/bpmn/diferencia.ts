import { CampoCambiado, Comparacion } from '../models/modelo.model';

/** Que le paso a un elemento entre la version vigente y lo que se pide publicar. */
export type TipoDeCambio = 'agregado' | 'cambiado' | 'movido' | 'quitado';

/** Una fila de la lista de la diferencia. */
export interface FilaDeDiferencia {
  tipo: TipoDeCambio;
  bpmnId: string;
  /** El nombre, o el bpmnId si no tiene: una fila sin texto no se puede leer. */
  nombre: string;
  /** La clase de elemento, como la nombra la API (ACTIVIDAD, GATEWAY...). */
  elemento: string;
  campos: CampoCambiado[];
  /** Si esta en el dibujo de lo que se pide: lo quitado no, asi que no se puede llevar la vista hasta el. */
  enElDibujo: boolean;
}

/** Cuantos elementos hay de cada tipo de cambio. */
export type ResumenDeDiferencia = Record<TipoDeCambio, number>;

const ORDEN: readonly TipoDeCambio[] = ['agregado', 'cambiado', 'movido', 'quitado'];

/**
 * La diferencia en una sola lista, para leerla de arriba abajo: lo agregado, lo cambiado, lo que solo se movio y lo
 * quitado, y dentro de cada grupo por nombre. Es la misma informacion que el color del dibujo, que nunca va sola.
 */
export function filasDeDiferencia(comparacion: Comparacion | null): FilaDeDiferencia[] {
  if (!comparacion) {
    return [];
  }
  const grupos: Record<TipoDeCambio, FilaDeDiferencia[]> = {
    agregado: comparacion.agregados.map((uno) => fila('agregado', uno.bpmnId, uno.nombre, uno.tipo, [])),
    cambiado: comparacion.cambiados.map((uno) => fila('cambiado', uno.bpmnId, uno.nombre, uno.tipo, uno.campos)),
    movido: comparacion.movidos.map((uno) => fila('movido', uno.bpmnId, uno.nombre, uno.tipo, [])),
    quitado: comparacion.quitados.map((uno) => fila('quitado', uno.bpmnId, uno.nombre, uno.tipo, [])),
  };
  return ORDEN.flatMap((tipo) => [...grupos[tipo]].sort(porNombre));
}

/** Cuantos de cada tipo; todo en cero dice que no hay cambios. */
export function resumenDeDiferencia(filas: readonly FilaDeDiferencia[]): ResumenDeDiferencia {
  const resumen: ResumenDeDiferencia = { agregado: 0, cambiado: 0, movido: 0, quitado: 0 };
  filas.forEach((una) => resumen[una.tipo]++);
  return resumen;
}

function fila(
  tipo: TipoDeCambio,
  bpmnId: string,
  nombre: string | null,
  elemento: string,
  campos: CampoCambiado[],
): FilaDeDiferencia {
  return {
    tipo,
    bpmnId,
    nombre: nombre?.trim() || bpmnId,
    elemento,
    campos,
    enElDibujo: tipo !== 'quitado',
  };
}

function porNombre(una: FilaDeDiferencia, otra: FilaDeDiferencia): number {
  return una.nombre.localeCompare(otra.nombre) || una.bpmnId.localeCompare(otra.bpmnId);
}
