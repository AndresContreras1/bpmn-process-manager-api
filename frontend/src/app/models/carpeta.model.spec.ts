import { describe, expect, it } from 'vitest';

import { Carpeta, aplanarCarpetas } from './carpeta.model';

function carpeta(id: number, nombre: string, padreId: number | null): Carpeta {
  return { id, nombre, padreId, procesos: 0, version: 0 };
}

describe('aplanarCarpetas', () => {
  it('pone cada carpeta seguida de las suyas, por nombre, con su nivel', () => {
    const filas = aplanarCarpetas([
      carpeta(1, 'Zeta', null),
      carpeta(2, 'Alfa', null),
      carpeta(3, 'Hija de alfa', 2),
      carpeta(4, 'Nieta', 3),
    ]);

    expect(filas.map((fila) => [fila.carpeta.nombre, fila.nivel])).toEqual([
      ['Alfa', 0],
      ['Hija de alfa', 1],
      ['Nieta', 2],
      ['Zeta', 0],
    ]);
  });

  it('una carpeta cuyo padre no llego se muestra en la raiz en vez de perderse', () => {
    expect(aplanarCarpetas([carpeta(5, 'Suelta', 99)]).map((fila) => fila.nivel)).toEqual([0]);
  });

  it('sin carpetas no hay filas', () => {
    expect(aplanarCarpetas([])).toEqual([]);
  });
});
