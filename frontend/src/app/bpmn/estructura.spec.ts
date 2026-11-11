import { describe, expect, it } from 'vitest';

import { ElementoPlano, armarArbol, filasVisibles, huecoEnLane, pasoDeTecla } from './estructura';

const ELEMENTOS: ElementoPlano[] = [
  { id: 'Pool', tipo: 'bpmn:Participant', nombre: 'Store', padre: null, salidas: [] },
  { id: 'Lane_a', tipo: 'bpmn:Lane', nombre: 'Sales', padre: 'Pool', salidas: [] },
  { id: 'Lane_b', tipo: 'bpmn:Lane', nombre: 'Warehouse', padre: 'Pool', salidas: [] },
  {
    id: 'Inicio',
    tipo: 'bpmn:StartEvent',
    nombre: 'Order',
    padre: 'Lane_a',
    salidas: [{ id: 'Flujo', tipo: 'bpmn:SequenceFlow', nombre: '', destino: 'Tarea' }],
  },
  { id: 'Tarea', tipo: 'bpmn:UserTask', nombre: 'Pack', padre: 'Lane_b', salidas: [] },
];

const arbol = armarArbol(ELEMENTOS);
const nadie = new Set<string>();

describe('armarArbol', () => {
  it('pone pools, lanes, nodos y salidas uno dentro de otro', () => {
    expect(arbol.map((nodo) => nodo.clase)).toEqual(['pool']);
    expect(arbol[0].hijos.map((nodo) => [nodo.id, nodo.clase])).toEqual([
      ['Lane_a', 'lane'],
      ['Lane_b', 'lane'],
    ]);
    expect(arbol[0].hijos[0].hijos[0].id).toBe('Inicio');
    expect(arbol[0].hijos[0].hijos[0].hijos[0]).toMatchObject({ id: 'Flujo', clase: 'salida', destino: 'Tarea' });
  });

  it('un elemento cuyo padre no esta queda en la raiz en vez de perderse', () => {
    const suelto = armarArbol([{ id: 'X', tipo: 'bpmn:Task', nombre: '', padre: 'Falta', salidas: [] }]);

    expect(suelto.map((nodo) => nodo.id)).toEqual(['X']);
  });
});

describe('filasVisibles', () => {
  it('dice nivel, posicion y tamano de cada fila', () => {
    const filas = filasVisibles(arbol, nadie);

    expect(filas.map((fila) => fila.nodo.id)).toEqual(['Pool', 'Lane_a', 'Inicio', 'Flujo', 'Lane_b', 'Tarea']);
    expect(filas[4]).toMatchObject({ nivel: 2, posicion: 2, tamano: 2, padre: 'Pool' });
  });

  it('una rama cerrada esconde lo de adentro', () => {
    const filas = filasVisibles(arbol, new Set(['Lane_a']));

    expect(filas.map((fila) => fila.nodo.id)).toEqual(['Pool', 'Lane_a', 'Lane_b', 'Tarea']);
  });
});

describe('pasoDeTecla', () => {
  const filas = filasVisibles(arbol, nadie);

  it('las flechas verticales recorren lo visible y se detienen en los extremos', () => {
    expect(pasoDeTecla(filas, 'Pool', 'ArrowDown', nadie)).toEqual({ foco: 'Lane_a' });
    expect(pasoDeTecla(filas, 'Lane_a', 'ArrowUp', nadie)).toEqual({ foco: 'Pool' });
    expect(pasoDeTecla(filas, 'Pool', 'ArrowUp', nadie)).toBeNull();
    expect(pasoDeTecla(filas, 'Tarea', 'ArrowDown', nadie)).toBeNull();
  });

  it('Inicio y Fin van a los extremos', () => {
    expect(pasoDeTecla(filas, 'Lane_b', 'Home', nadie)).toEqual({ foco: 'Pool' });
    expect(pasoDeTecla(filas, 'Lane_a', 'End', nadie)).toEqual({ foco: 'Tarea' });
  });

  it('derecha abre una rama cerrada y, si ya esta abierta, entra', () => {
    const cerrada = new Set(['Lane_a']);

    expect(pasoDeTecla(filasVisibles(arbol, cerrada), 'Lane_a', 'ArrowRight', cerrada)).toEqual({
      foco: 'Lane_a',
      abrir: 'Lane_a',
    });
    expect(pasoDeTecla(filas, 'Lane_a', 'ArrowRight', nadie)).toEqual({ foco: 'Inicio' });
  });

  it('izquierda cierra una rama abierta y, si no hay que cerrar, sube', () => {
    expect(pasoDeTecla(filas, 'Lane_a', 'ArrowLeft', nadie)).toEqual({ foco: 'Lane_a', cerrar: 'Lane_a' });
    expect(pasoDeTecla(filas, 'Tarea', 'ArrowLeft', nadie)).toEqual({ foco: 'Lane_b' });
    expect(pasoDeTecla(filas, 'Pool', 'ArrowLeft', new Set(['Pool']))).toBeNull();
  });

  it('una tecla cualquiera, o un foco que ya no esta, no hace nada', () => {
    expect(pasoDeTecla(filas, 'Pool', 'a', nadie)).toBeNull();
    expect(pasoDeTecla(filas, 'Borrado', 'ArrowDown', nadie)).toBeNull();
  });
});

describe('huecoEnLane', () => {
  const lane = { x: 100, y: 0, width: 600, height: 120 };

  it('en una lane vacia empieza por la izquierda, centrado', () => {
    expect(huecoEnLane(lane, [])).toEqual({ x: 190, y: 60 });
  });

  it('salta lo ocupado', () => {
    const hueco = huecoEnLane(lane, [{ x: 140, y: 20, width: 100, height: 80 }]);

    expect(hueco.x).toBeGreaterThan(240);
    expect(hueco.y).toBe(60);
  });

  it('si no cabe, devuelve el ultimo hueco dentro de la lane', () => {
    const hueco = huecoEnLane(lane, [{ x: 0, y: -100, width: 1000, height: 400 }]);

    expect(hueco.x).toBeLessThan(lane.x + lane.width);
    expect(hueco.y).toBeLessThan(lane.y + lane.height);
  });
});
