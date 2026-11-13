import { describe, expect, it } from 'vitest';

import { Carpeta } from './carpeta.model';
import {
  ACCIONES,
  Accion,
  BotonesDelProceso,
  ValoresDeConcesion,
  botonesDelProceso,
  cadenaDeCarpetas,
  faltaEnConcesion,
  solicitudDeConcesion,
} from './concesion.model';

function valores(cambios: Partial<ValoresDeConcesion>): ValoresDeConcesion {
  return { accion: 'VER', sujeto: 'ORGANIZACION', usuarioId: null, rolProcesoId: null, ...cambios };
}

function carpeta(id: number, padreId: number | null): Carpeta {
  return { id, nombre: `Carpeta ${id}`, padreId, procesos: 0, version: 0 };
}

const NINGUNO: BotonesDelProceso = {
  editar: false,
  modelar: false,
  publicar: false,
  exportar: false,
  compartir: false,
  permisos: false,
  eliminar: false,
};

describe('faltaEnConcesion', () => {
  it('sin accion falta la accion, antes que cualquier otra cosa', () => {
    expect(faltaEnConcesion(valores({ accion: null, sujeto: null }))).toBe('accion');
  });

  it('con la accion y sin sujeto falta el sujeto', () => {
    expect(faltaEnConcesion(valores({ sujeto: null }))).toBe('sujeto');
  });

  it('a una persona le falta la persona hasta que se elige', () => {
    expect(faltaEnConcesion(valores({ sujeto: 'PERSONA' }))).toBe('usuario');
    expect(faltaEnConcesion(valores({ sujeto: 'PERSONA', usuarioId: 7 }))).toBeNull();
  });

  it('a un rol le falta el rol hasta que se elige, aunque haya una persona de antes', () => {
    expect(faltaEnConcesion(valores({ sujeto: 'ROL', usuarioId: 7 }))).toBe('rol');
    expect(faltaEnConcesion(valores({ sujeto: 'ROL', rolProcesoId: 3 }))).toBeNull();
  });

  it('a la organizacion no le falta nada', () => {
    expect(faltaEnConcesion(valores({}))).toBeNull();
  });
});

describe('solicitudDeConcesion', () => {
  it('a una persona va solo la persona, aunque quede un rol elegido antes', () => {
    expect(
      solicitudDeConcesion(valores({ accion: 'MODELAR', sujeto: 'PERSONA', usuarioId: 7, rolProcesoId: 3 })),
    ).toEqual({ accion: 'MODELAR', sujeto: 'PERSONA', usuarioId: 7, rolProcesoId: null });
  });

  it('a un rol va solo el rol', () => {
    expect(solicitudDeConcesion(valores({ sujeto: 'ROL', usuarioId: 7, rolProcesoId: 3 }))).toEqual({
      accion: 'VER',
      sujeto: 'ROL',
      usuarioId: null,
      rolProcesoId: 3,
    });
  });

  it('a la organizacion no va ni persona ni rol', () => {
    expect(solicitudDeConcesion(valores({ usuarioId: 7, rolProcesoId: 3 }))).toEqual({
      accion: 'VER',
      sujeto: 'ORGANIZACION',
      usuarioId: null,
      rolProcesoId: null,
    });
  });

  it('un formulario incompleto no se manda', () => {
    expect(solicitudDeConcesion(valores({ sujeto: 'PERSONA' }))).toBeNull();
  });
});

describe('cadenaDeCarpetas', () => {
  const carpetas = [carpeta(1, null), carpeta(2, 1), carpeta(3, 2), carpeta(4, null)];

  it('va de la carpeta a la raiz, ella incluida', () => {
    expect(cadenaDeCarpetas(3, carpetas).map((una) => una.id)).toEqual([3, 2, 1]);
  });

  it('un proceso sin carpeta no hereda nada', () => {
    expect(cadenaDeCarpetas(null, carpetas)).toEqual([]);
    expect(cadenaDeCarpetas(undefined, carpetas)).toEqual([]);
  });

  it('una carpeta que no llego corta la cadena donde falta', () => {
    expect(cadenaDeCarpetas(5, [carpeta(5, 99)]).map((una) => una.id)).toEqual([5]);
    expect(cadenaDeCarpetas(99, carpetas)).toEqual([]);
  });

  it('un ciclo no da vueltas para siempre', () => {
    expect(cadenaDeCarpetas(1, [carpeta(1, 2), carpeta(2, 1)]).map((una) => una.id)).toEqual([1, 2]);
  });
});

describe('botonesDelProceso', () => {
  it('con cada accion, el administrador ve cada boton', () => {
    expect(botonesDelProceso(ACCIONES, 'ADMINISTRADOR')).toEqual({
      editar: true,
      modelar: true,
      publicar: true,
      exportar: true,
      compartir: true,
      permisos: true,
      eliminar: true,
    });
  });

  it('un editor que administra el proceso concede, pero no lo comparte con otra tienda ni lo borra', () => {
    expect(botonesDelProceso(ACCIONES, 'EDITOR')).toEqual({
      editar: true,
      modelar: true,
      publicar: true,
      exportar: true,
      compartir: false,
      permisos: true,
      eliminar: false,
    });
  });

  it('cada boton sale de su accion', () => {
    const solo = (accion: Accion): BotonesDelProceso => botonesDelProceso([accion], 'ADMINISTRADOR');
    expect(solo('MODELAR')).toEqual({ ...NINGUNO, editar: true, modelar: true });
    expect(solo('PUBLICAR')).toEqual({ ...NINGUNO, publicar: true });
    expect(solo('EXPORTAR')).toEqual({ ...NINGUNO, exportar: true });
    expect(solo('ADMINISTRAR')).toEqual({ ...NINGUNO, compartir: true, permisos: true, eliminar: true });
    expect(solo('VER')).toEqual(NINGUNO);
  });

  it('quien solo lee no ve botones de cambio aunque tuviera la accion, y exporta si la tiene', () => {
    expect(botonesDelProceso(ACCIONES, 'SOLO_LECTURA')).toEqual({ ...NINGUNO, exportar: true });
  });

  it('sin la lista de la API se decide por el rol', () => {
    expect(botonesDelProceso(null, 'ADMINISTRADOR')).toEqual({
      editar: true,
      modelar: true,
      publicar: true,
      exportar: true,
      compartir: true,
      permisos: true,
      eliminar: true,
    });
    expect(botonesDelProceso(null, 'EDITOR')).toEqual({
      ...NINGUNO,
      editar: true,
      modelar: true,
      publicar: true,
      exportar: true,
    });
    expect(botonesDelProceso(null, 'SOLO_LECTURA')).toEqual({ ...NINGUNO, exportar: true });
    expect(botonesDelProceso(null, null)).toEqual(NINGUNO);
  });
});
