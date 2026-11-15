import { describe, expect, it } from 'vitest';

import {
  Comentario,
  Hilo,
  contadoresDeHilos,
  hiloDelComentario,
  hilosDe,
  puedeEditarComentario,
  puedeEliminarComentario,
} from './comentario.model';

function comentario(id: number, cambios: Partial<Comentario> = {}): Comentario {
  return {
    id,
    procesoId: 7,
    padreId: null,
    bpmnId: null,
    versionDelProceso: null,
    autor: { id: 2, nombre: 'Ana' },
    markdown: 'texto',
    html: '<p>texto</p>',
    menciones: [],
    estado: 'ABIERTO',
    resueltoPor: null,
    resueltoEn: null,
    editadoEn: null,
    eliminado: false,
    fechaCreacion: '2026-11-12T10:00:00',
    version: 0,
    ...cambios,
  };
}

function hilo(id: number, cambios: Partial<Comentario> = {}, respuestas: Comentario[] = []): Hilo {
  return { comentario: comentario(id, cambios), respuestas };
}

describe('hilosDe', () => {
  const hilos = [
    hilo(1, { bpmnId: 'Tarea_1', estado: 'RESUELTO' }),
    hilo(2, { bpmnId: 'Tarea_1' }),
    hilo(3, { bpmnId: 'Tarea_2' }),
    hilo(4, {}),
    hilo(5, { bpmnId: 'Tarea_1', versionDelProceso: 2 }),
    hilo(6, { bpmnId: 'Tarea_1' }),
  ];

  it('da los del elemento en el borrador, los abiertos antes que los resueltos y en el orden de la API', () => {
    expect(hilosDe(hilos, 'Tarea_1', null).map((uno) => uno.comentario.id)).toEqual([2, 6, 1]);
  });

  it('el proceso entero son los hilos sin elemento', () => {
    expect(hilosDe(hilos, null, null).map((uno) => uno.comentario.id)).toEqual([4]);
  });

  it('una version publicada tiene sus propios hilos, que no son los del borrador', () => {
    expect(hilosDe(hilos, 'Tarea_1', 2).map((uno) => uno.comentario.id)).toEqual([5]);
    expect(hilosDe(hilos, 'Tarea_2', 2)).toEqual([]);
  });
});

describe('contadoresDeHilos', () => {
  it('cuenta por elemento solo los hilos abiertos de lo que se mira', () => {
    const hilos = [
      hilo(1, { bpmnId: 'Tarea_1' }),
      hilo(2, { bpmnId: 'Tarea_1' }),
      hilo(3, { bpmnId: 'Tarea_1', estado: 'RESUELTO' }),
      hilo(4, { bpmnId: 'Tarea_2' }),
      hilo(5, { bpmnId: 'Tarea_2', versionDelProceso: 1 }),
      hilo(6, {}),
    ];

    expect(contadoresDeHilos(hilos, null)).toEqual({ Tarea_1: 2, Tarea_2: 1 });
    expect(contadoresDeHilos(hilos, 1)).toEqual({ Tarea_2: 1 });
  });

  it('sin hilos abiertos no hay nada que pintar', () => {
    expect(contadoresDeHilos([hilo(1, { bpmnId: 'Tarea_1', estado: 'RESUELTO' })], null)).toEqual({});
  });
});

describe('hiloDelComentario', () => {
  const hilos = [hilo(1, {}, [comentario(11, { padreId: 1, estado: null })]), hilo(2)];

  it('encuentra el hilo por su primer comentario o por una de sus respuestas', () => {
    expect(hiloDelComentario(hilos, 2)?.comentario.id).toBe(2);
    expect(hiloDelComentario(hilos, 11)?.comentario.id).toBe(1);
  });

  it('un comentario que no esta no lleva a ninguno', () => {
    expect(hiloDelComentario(hilos, 99)).toBeNull();
  });
});

describe('quien edita y quien borra', () => {
  it('solo su autor edita, y no un comentario borrado', () => {
    expect(puedeEditarComentario(comentario(1), 2)).toBe(true);
    expect(puedeEditarComentario(comentario(1), 3)).toBe(false);
    expect(puedeEditarComentario(comentario(1), null)).toBe(false);
    expect(puedeEditarComentario(comentario(1, { eliminado: true }), 2)).toBe(false);
    expect(puedeEditarComentario(comentario(1, { autor: null }), 2)).toBe(false);
  });

  it('lo borra su autor o un administrador, una sola vez', () => {
    expect(puedeEliminarComentario(comentario(1), 2, false)).toBe(true);
    expect(puedeEliminarComentario(comentario(1), 3, false)).toBe(false);
    expect(puedeEliminarComentario(comentario(1), 3, true)).toBe(true);
    expect(puedeEliminarComentario(comentario(1), null, false)).toBe(false);
    expect(puedeEliminarComentario(comentario(1, { eliminado: true }), 2, true)).toBe(false);
  });
});
