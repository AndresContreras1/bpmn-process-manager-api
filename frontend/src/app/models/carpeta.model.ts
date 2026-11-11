/** Una carpeta del repositorio de procesos (CarpetaResponse). */
export interface Carpeta {
  id: number;
  nombre: string;
  padreId: number | null;
  procesos: number;
  version: number;
}

/** Una carpeta en su lugar del arbol: las filas salen en orden para dibujarlo. */
export interface FilaDeCarpeta {
  carpeta: Carpeta;
  nivel: number;
}

/** El arbol aplanado, cada carpeta seguida de las suyas. Una cuyo padre no esta se trata como raiz. */
export function aplanarCarpetas(carpetas: Carpeta[]): FilaDeCarpeta[] {
  const ids = new Set(carpetas.map((carpeta) => carpeta.id));
  const hijas = new Map<number | null, Carpeta[]>();
  carpetas.forEach((carpeta) => {
    const padre = carpeta.padreId !== null && ids.has(carpeta.padreId) ? carpeta.padreId : null;
    hijas.set(padre, [...(hijas.get(padre) ?? []), carpeta]);
  });
  const filas: FilaDeCarpeta[] = [];
  const recorrer = (padre: number | null, nivel: number): void => {
    [...(hijas.get(padre) ?? [])]
      .sort((una, otra) => una.nombre.localeCompare(otra.nombre))
      .forEach((carpeta) => {
        filas.push({ carpeta, nivel });
        recorrer(carpeta.id, nivel + 1);
      });
  };
  recorrer(null, 0);
  return filas;
}
