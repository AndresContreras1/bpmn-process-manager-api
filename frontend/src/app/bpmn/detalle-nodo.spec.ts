import { describe, expect, it } from 'vitest';

import { Diagrama } from '../models/diagrama.model';
import { bpmnIdsDeNodos, detallarNodo, numerarMensajes } from './detalle-nodo';

function diagrama(parcial: Partial<Diagrama>): Diagrama {
  return {
    pools: [],
    lanes: [],
    actividades: [],
    gateways: [],
    eventos: [],
    arcos: [],
    mensajes: [],
    correlaciones: [],
    ...parcial,
  } as unknown as Diagrama;
}

describe('bpmnIdsDeNodos', () => {
  it('usa el id del dibujo de cada nodo', () => {
    const ids = bpmnIdsDeNodos(
      diagrama({
        actividades: [{ id: 1, bpmnId: 'Activity_abc' }],
        gateways: [{ id: 2, bpmnId: 'Gateway_abc' }],
        eventos: [{ id: 3, bpmnId: 'Event_abc' }],
      } as unknown as Partial<Diagrama>),
    );

    expect([...ids]).toEqual([
      [1, 'Activity_abc'],
      [2, 'Gateway_abc'],
      [3, 'Event_abc'],
    ]);
  });

  it('una version anterior a los bpmnId se escribio con su tipo y su id', () => {
    const ids = bpmnIdsDeNodos(
      diagrama({
        actividades: [{ id: 42 }],
        gateways: [{ id: 43 }],
        eventos: [{ id: 44 }],
      } as unknown as Partial<Diagrama>),
    );

    expect(ids.get(42)).toBe('Activity_42');
    expect(ids.get(43)).toBe('Gateway_43');
    expect(ids.get(44)).toBe('Event_44');
  });
});

describe('numerarMensajes', () => {
  const conMensajes = diagrama({
    pools: [
      { id: 1, nombre: 'Store' },
      { id: 2, nombre: 'Customer' },
    ],
    lanes: [{ id: 5, nombre: 'Sales', poolId: 1, rol: { nombre: 'Clerk' } }],
    actividades: [{ id: 10, nombre: 'Send invoice', laneId: 5, tipo: 'ENVIO' }],
    mensajes: [
      {
        id: 9,
        nombre: 'Second',
        contenido: 'b',
        poolOrigenId: 2,
        poolDestinoId: 1,
        nodoOrigenId: null,
        nodoDestinoId: null,
      },
      {
        id: 4,
        nombre: 'First',
        contenido: 'a',
        poolOrigenId: 1,
        poolDestinoId: 2,
        nodoOrigenId: 10,
        nodoDestinoId: null,
      },
    ],
    correlaciones: [{ id: 1, mensajeId: 4, campo: 'orderId', criterio: 'order' }],
  } as unknown as Partial<Diagrama>);

  it('numera por id, sin importar el orden en que llegan', () => {
    expect(numerarMensajes(conMensajes).map((uno) => [uno.numero, uno.nombre])).toEqual([
      [1, 'First'],
      [2, 'Second'],
    ]);
  });

  it('dice entre quienes va y con que clave se correlaciona', () => {
    const [primero, segundo] = numerarMensajes(conMensajes);

    expect(primero).toMatchObject({ origen: 'Store', destino: 'Customer', desde: 'Send invoice', hasta: null });
    expect(primero.claves).toEqual(['orderId']);
    expect(segundo.claves).toEqual([]);
  });

  it('el panel del nodo usa el mismo numero que la lista', () => {
    const detalle = detallarNodo(conMensajes, 10, numerarMensajes(conMensajes));

    expect(detalle?.envia.map((uno) => uno.numero)).toEqual([1]);
  });
});
