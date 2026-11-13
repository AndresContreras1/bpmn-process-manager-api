import { describe, expect, it } from 'vitest';

import { Comparacion } from '../models/modelo.model';
import { filasDeDiferencia, resumenDeDiferencia } from './diferencia';

const comparacion: Comparacion = {
  de: '2',
  a: 'solicitud',
  agregados: [
    { bpmnId: 'Tarea_9', tipo: 'ACTIVIDAD', nombre: 'Check fraud' },
    { bpmnId: 'Gateway_1', tipo: 'GATEWAY', nombre: null },
  ],
  quitados: [{ bpmnId: 'Tarea_3', tipo: 'ACTIVIDAD', nombre: 'Call the customer' }],
  cambiados: [
    {
      bpmnId: 'Tarea_2',
      tipo: 'ACTIVIDAD',
      nombre: '  Pack the order ',
      campos: [{ campo: 'nombre', antes: 'Pack', despues: 'Pack the order' }],
    },
  ],
  movidos: [{ bpmnId: 'Evento_1', tipo: 'EVENTO', nombre: 'Start' }],
};

describe('filasDeDiferencia', () => {
  it('pone lo agregado, lo cambiado, lo movido y lo quitado, y dentro de cada grupo por nombre', () => {
    expect(filasDeDiferencia(comparacion).map((fila) => [fila.tipo, fila.bpmnId])).toEqual([
      ['agregado', 'Tarea_9'],
      ['agregado', 'Gateway_1'],
      ['cambiado', 'Tarea_2'],
      ['movido', 'Evento_1'],
      ['quitado', 'Tarea_3'],
    ]);
  });

  it('un elemento sin nombre se lee por su bpmnId, y el nombre va sin espacios de sobra', () => {
    const filas = filasDeDiferencia(comparacion);

    expect(filas.find((fila) => fila.bpmnId === 'Gateway_1')?.nombre).toBe('Gateway_1');
    expect(filas.find((fila) => fila.bpmnId === 'Tarea_2')?.nombre).toBe('Pack the order');
  });

  it('solo lo cambiado trae campos, y lo quitado no esta en el dibujo', () => {
    const filas = filasDeDiferencia(comparacion);

    expect(filas.map((fila) => fila.campos.length)).toEqual([0, 0, 1, 0, 0]);
    expect(filas.map((fila) => fila.enElDibujo)).toEqual([true, true, true, true, false]);
    expect(filas.map((fila) => fila.elemento)).toEqual(['ACTIVIDAD', 'GATEWAY', 'ACTIVIDAD', 'EVENTO', 'ACTIVIDAD']);
  });

  it('dos con el mismo nombre se ordenan por su bpmnId', () => {
    const iguales: Comparacion = {
      ...comparacion,
      agregados: [
        { bpmnId: 'Tarea_b', tipo: 'ACTIVIDAD', nombre: 'Pack' },
        { bpmnId: 'Tarea_a', tipo: 'ACTIVIDAD', nombre: 'Pack' },
      ],
    };

    expect(
      filasDeDiferencia(iguales)
        .slice(0, 2)
        .map((fila) => fila.bpmnId),
    ).toEqual(['Tarea_a', 'Tarea_b']);
  });

  it('sin comparacion no hay filas', () => {
    expect(filasDeDiferencia(null)).toEqual([]);
  });
});

describe('resumenDeDiferencia', () => {
  it('cuenta cuantos hay de cada tipo de cambio', () => {
    expect(resumenDeDiferencia(filasDeDiferencia(comparacion))).toEqual({
      agregado: 2,
      cambiado: 1,
      movido: 1,
      quitado: 1,
    });
  });

  it('todo en cero cuando no hay cambios', () => {
    expect(resumenDeDiferencia([])).toEqual({ agregado: 0, cambiado: 0, movido: 0, quitado: 0 });
  });
});
