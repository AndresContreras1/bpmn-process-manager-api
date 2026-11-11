/** Un elemento del dibujo sin bpmn-js de por medio: lo que hace falta para armar el arbol. */
export interface ElementoPlano {
  id: string;
  tipo: string;
  nombre: string;
  /** El pool de una lane, la lane o el subproceso de un nodo; null en un pool. */
  padre: string | null;
  salidas: SalidaPlana[];
}

export interface SalidaPlana {
  id: string;
  tipo: string;
  nombre: string;
  destino: string;
}

export type ClaseDeNodo = 'pool' | 'lane' | 'nodo' | 'salida';

export interface NodoDelArbol {
  id: string;
  clase: ClaseDeNodo;
  tipo: string;
  nombre: string;
  /** A donde lleva, en una salida. */
  destino: string | null;
  hijos: NodoDelArbol[];
}

/** Una fila de lo que se ve, con lo que un lector de pantalla necesita saber de su lugar. */
export interface Fila {
  nodo: NodoDelArbol;
  nivel: number;
  posicion: number;
  tamano: number;
  padre: string | null;
}

/** Lo que hace una tecla: a donde pasa el foco y que se abre o se cierra. */
export interface Paso {
  foco: string;
  abrir?: string;
  cerrar?: string;
}

function claseDe(tipo: string): ClaseDeNodo {
  if (tipo === 'bpmn:Participant') {
    return 'pool';
  }
  return tipo === 'bpmn:Lane' ? 'lane' : 'nodo';
}

/** Pools, sus lanes, los nodos de cada lane y lo que sale de cada nodo, en el orden del dibujo. */
export function armarArbol(elementos: ElementoPlano[]): NodoDelArbol[] {
  const nodos = new Map<string, NodoDelArbol>();
  elementos.forEach((elemento) => {
    const salidas = elemento.salidas.map((salida): NodoDelArbol => ({
      id: salida.id,
      clase: 'salida',
      tipo: salida.tipo,
      nombre: salida.nombre,
      destino: salida.destino,
      hijos: [],
    }));
    nodos.set(elemento.id, {
      id: elemento.id,
      clase: claseDe(elemento.tipo),
      tipo: elemento.tipo,
      nombre: elemento.nombre,
      destino: null,
      hijos: salidas,
    });
  });
  const raices: NodoDelArbol[] = [];
  elementos.forEach((elemento) => {
    const nodo = nodos.get(elemento.id) as NodoDelArbol;
    const padre = elemento.padre === null ? undefined : nodos.get(elemento.padre);
    if (padre) {
      padre.hijos.push(nodo);
    } else {
      raices.push(nodo);
    }
  });
  return raices;
}

/** Lo que se ve con esas ramas cerradas, de arriba abajo. */
export function filasVisibles(arbol: NodoDelArbol[], cerrados: ReadonlySet<string>): Fila[] {
  const filas: Fila[] = [];
  const recorrer = (nodos: NodoDelArbol[], nivel: number, padre: string | null): void => {
    nodos.forEach((nodo, indice) => {
      filas.push({ nodo, nivel, posicion: indice + 1, tamano: nodos.length, padre });
      if (!cerrados.has(nodo.id)) {
        recorrer(nodo.hijos, nivel + 1, nodo.id);
      }
    });
  };
  recorrer(arbol, 1, null);
  return filas;
}

/** El patron de arbol de WAI-ARIA: flechas para moverse y para abrir o cerrar, Inicio y Fin para los extremos. */
export function pasoDeTecla(filas: Fila[], actual: string, tecla: string, cerrados: ReadonlySet<string>): Paso | null {
  const indice = filas.findIndex((fila) => fila.nodo.id === actual);
  if (indice < 0) {
    return null;
  }
  const fila = filas[indice];
  const tieneHijos = fila.nodo.hijos.length > 0;
  switch (tecla) {
    case 'ArrowDown':
      return indice + 1 < filas.length ? { foco: filas[indice + 1].nodo.id } : null;
    case 'ArrowUp':
      return indice > 0 ? { foco: filas[indice - 1].nodo.id } : null;
    case 'Home':
      return { foco: filas[0].nodo.id };
    case 'End':
      return { foco: filas[filas.length - 1].nodo.id };
    case 'ArrowRight':
      if (tieneHijos && cerrados.has(actual)) {
        return { foco: actual, abrir: actual };
      }
      return tieneHijos ? { foco: fila.nodo.hijos[0].id } : null;
    case 'ArrowLeft':
      if (tieneHijos && !cerrados.has(actual)) {
        return { foco: actual, cerrar: actual };
      }
      return fila.padre === null ? null : { foco: fila.padre };
    default:
      return null;
  }
}

export interface Caja {
  x: number;
  y: number;
  width: number;
  height: number;
}

const COLUMNA = 130;
const FILA = 90;
const MARGEN = 90;

/**
 * El centro de un hueco libre en la lane, de izquierda a derecha y de arriba abajo. Si no cabe, el ultimo: la disposicion
 * de un diagrama se recalcula al volver a abrirlo, y un elemento encima de otro se puede mover.
 */
export function huecoEnLane(lane: Caja, ocupados: Caja[]): { x: number; y: number } {
  const columnas = Math.max(1, Math.floor((lane.width - MARGEN) / COLUMNA));
  const filas = Math.max(1, Math.floor(lane.height / FILA));
  const centro = (columna: number, fila: number): { x: number; y: number } => ({
    x: lane.x + MARGEN + columna * COLUMNA,
    y: lane.y + (filas === 1 ? lane.height / 2 : FILA / 2 + fila * FILA),
  });
  const libre = (punto: { x: number; y: number }): boolean =>
    !ocupados.some(
      (caja) =>
        punto.x > caja.x - COLUMNA / 2 &&
        punto.x < caja.x + caja.width + COLUMNA / 2 &&
        punto.y > caja.y - FILA / 2 &&
        punto.y < caja.y + caja.height + FILA / 2,
    );
  for (let fila = 0; fila < filas; fila++) {
    for (let columna = 0; columna < columnas; columna++) {
      const punto = centro(columna, fila);
      if (libre(punto)) {
        return punto;
      }
    }
  }
  return centro(columnas - 1, filas - 1);
}
