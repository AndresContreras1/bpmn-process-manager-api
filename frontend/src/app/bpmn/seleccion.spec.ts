import { esEleccion } from './seleccion';

describe('esEleccion', () => {
  it('de nada a nada no es elegir: es bpmn-js importando el diagrama', () => {
    expect(esEleccion([], [])).toBe(false);
    expect(esEleccion(undefined, [])).toBe(false);
    expect(esEleccion(null, [])).toBe(false);
  });

  it('de nada a un elemento es elegirlo', () => {
    expect(esEleccion([], [{ id: 'Tarea_1' }])).toBe(true);
    expect(esEleccion(undefined, [{ id: 'Tarea_1' }])).toBe(true);
  });

  it('de un elemento a nada es soltarlo, y la pagina lo tiene que saber', () => {
    expect(esEleccion([{ id: 'Tarea_1' }], [])).toBe(true);
  });

  it('de un elemento a otro es elegir el otro', () => {
    expect(esEleccion([{ id: 'Tarea_1' }], [{ id: 'Tarea_2' }])).toBe(true);
  });
});
