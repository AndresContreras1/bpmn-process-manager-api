import { beforeEach, describe, expect, it } from 'vitest';

import { Persona } from '../models/comentario.model';
import { SelectorDeMenciones } from './menciones';

const ana: Persona = { id: 1, nombre: 'Ana' };
const beto: Persona = { id: 2, nombre: 'Beto' };
const carla: Persona = { id: 3, nombre: 'Carla' };

describe('SelectorDeMenciones', () => {
  let selector: SelectorDeMenciones;

  beforeEach(() => {
    selector = new SelectorDeMenciones();
  });

  it('empieza cerrado, sin nadie elegido y sin opcion activa', () => {
    expect(selector.abierto).toBe(false);
    expect(selector.elegidas).toEqual([]);
    expect(selector.activa).toBe(-1);
    expect(selector.ids).toEqual([]);
  });

  it('ofrece lo que respondio la API con la primera opcion activa', () => {
    selector.ofrecer([ana, beto]);

    expect(selector.abierto).toBe(true);
    expect(selector.opciones).toEqual([ana, beto]);
    expect(selector.activa).toBe(0);
  });

  it('sin resultados queda cerrado y sin opcion activa', () => {
    selector.ofrecer([]);

    expect(selector.abierto).toBe(false);
    expect(selector.activa).toBe(-1);
  });

  it('las flechas recorren las opciones y dan la vuelta; Inicio y Fin van a los extremos', () => {
    selector.ofrecer([ana, beto, carla]);

    selector.mover('ArrowDown');
    expect(selector.activa).toBe(1);
    selector.mover('ArrowDown');
    selector.mover('ArrowDown');
    expect(selector.activa).toBe(0);
    selector.mover('ArrowUp');
    expect(selector.activa).toBe(2);
    selector.mover('Home');
    expect(selector.activa).toBe(0);
    selector.mover('End');
    expect(selector.activa).toBe(2);
  });

  it('las flechas sin opciones no hacen nada', () => {
    selector.mover('ArrowDown');

    expect(selector.activa).toBe(-1);
  });

  it('Enter elige la activa, cierra la lista y deja su id para la API', () => {
    selector.ofrecer([ana, beto]);
    selector.mover('ArrowDown');

    expect(selector.elegir()).toEqual(beto);
    expect(selector.elegidas).toEqual([beto]);
    expect(selector.ids).toEqual([2]);
    expect(selector.abierto).toBe(false);
  });

  it('un clic elige la opcion de su indice', () => {
    selector.ofrecer([ana, beto, carla]);

    expect(selector.elegir(2)).toEqual(carla);
    expect(selector.ids).toEqual([3]);
  });

  it('elegir sin opciones, o fuera de la lista, no elige a nadie', () => {
    expect(selector.elegir()).toBeNull();
    selector.ofrecer([ana]);
    expect(selector.elegir(5)).toBeNull();
    expect(selector.elegidas).toEqual([]);
  });

  it('no vuelve a ofrecer a quien ya se eligio', () => {
    selector.ofrecer([ana, beto]);
    selector.elegir();
    selector.ofrecer([ana, beto, carla]);

    expect(selector.opciones).toEqual([beto, carla]);
  });

  it('quitar a alguien lo deja otra vez entre las opciones', () => {
    selector.ofrecer([ana, beto]);
    selector.elegir(0);
    selector.ofrecer([ana, beto]);
    selector.elegir(0);
    selector.quitar(1);

    expect(selector.ids).toEqual([2]);
    selector.ofrecer([ana, beto]);
    expect(selector.opciones).toEqual([ana]);
  });

  it('no pasa del tope de menciones, que la API rechazaria', () => {
    const corto = new SelectorDeMenciones(2);
    corto.ofrecer([ana, beto, carla]);
    corto.elegir(0);
    corto.ofrecer([beto, carla]);
    corto.elegir(0);

    expect(corto.lleno).toBe(true);
    corto.ofrecer([carla]);
    expect(corto.abierto).toBe(false);
    expect(corto.elegir(0)).toBeNull();
    expect(corto.ids).toEqual([1, 2]);
  });

  it('por defecto el tope son veinte', () => {
    const muchas = Array.from({ length: 25 }, (_, indice) => ({ id: indice + 1, nombre: `P${indice}` }));
    selector.reiniciar(muchas);

    expect(selector.elegidas).toHaveLength(20);
    expect(selector.lleno).toBe(true);
  });

  it('reiniciar deja las menciones de un comentario que se edita, y cierra la lista', () => {
    selector.ofrecer([ana]);
    selector.reiniciar([carla]);

    expect(selector.ids).toEqual([3]);
    expect(selector.abierto).toBe(false);
    selector.reiniciar();
    expect(selector.ids).toEqual([]);
  });

  it('Escape cierra la lista sin elegir', () => {
    selector.ofrecer([ana]);
    selector.cerrar();

    expect(selector.abierto).toBe(false);
    expect(selector.activa).toBe(-1);
    expect(selector.elegidas).toEqual([]);
  });
});
