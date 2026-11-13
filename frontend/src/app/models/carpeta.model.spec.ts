import { describe, expect, it } from 'vitest';

import { Carpeta, aplanarCarpetas, destinosDeCarpeta } from './carpeta.model';

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

describe('destinosDeCarpeta', () => {
  const carpetas = [
    carpeta(1, 'Alfa', null),
    carpeta(2, 'Hija de alfa', 1),
    carpeta(3, 'Nieta', 2),
    carpeta(4, 'Beta', null),
    carpeta(5, 'Hija de beta', 4),
  ];

  it('una carpeta no se mueve a si misma ni a nada que tenga dentro', () => {
    expect(destinosDeCarpeta(carpetas, 2).map((fila) => fila.carpeta.id)).toEqual([1, 4, 5]);
  });

  it('una raiz puede ir dentro de cualquier otra rama', () => {
    expect(destinosDeCarpeta(carpetas, 1).map((fila) => [fila.carpeta.id, fila.nivel])).toEqual([
      [4, 0],
      [5, 1],
    ]);
  });

  it('una hoja puede ir a cualquier otra carpeta, la suya incluida', () => {
    expect(destinosDeCarpeta(carpetas, 3).map((fila) => fila.carpeta.id)).toEqual([1, 2, 4, 5]);
  });
});
