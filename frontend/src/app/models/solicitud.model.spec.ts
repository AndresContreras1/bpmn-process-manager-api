import { describe, expect, it } from 'vitest';

import {
  APROBADORES_MAXIMOS,
  PoliticaDePublicacion,
  Solicitud,
  ValoresDePolitica,
  decisionPideTexto,
  faltaEnPolitica,
  puedeDecidir,
  puedeRetirar,
  solicitudDePolitica,
  valoresDePolitica,
} from './solicitud.model';

function solicitud(cambios: Partial<Solicitud> = {}): Solicitud {
  return {
    id: 4,
    procesoId: 7,
    proceso: 'Order fulfillment',
    estado: 'ABIERTA',
    solicitante: { id: 1, nombre: 'Ana' },
    markdown: null,
    html: null,
    numero: 3,
    versionBase: 2,
    revisionId: 31,
    aprobacionesNecesarias: 2,
    aprobaciones: 0,
    aprobadores: [
      { id: 2, nombre: 'Beto' },
      { id: 3, nombre: 'Carla' },
    ],
    decisiones: [],
    motivo: null,
    cerradaPor: null,
    cerradaEn: null,
    versionPublicada: null,
    fechaCreacion: '2026-11-12T10:00:00',
    diferencia: null,
    ...cambios,
  };
}

function valores(cambios: Partial<ValoresDePolitica> = {}): ValoresDePolitica {
  return {
    modo: 'CON_APROBACION',
    aprobacionesNecesarias: 2,
    fuente: 'PERSONAS',
    aprobadores: [4, 5],
    rolProcesoId: null,
    version: 0,
    ...cambios,
  };
}

describe('puedeDecidir', () => {
  it('decide un aprobador de una solicitud abierta que todavia no decidio', () => {
    expect(puedeDecidir(solicitud(), 2)).toBe(true);
  });

  it('no decide quien no es aprobador, quien la pidio, ni nadie sin sesion', () => {
    expect(puedeDecidir(solicitud(), 9)).toBe(false);
    expect(puedeDecidir(solicitud({ aprobadores: [{ id: 1, nombre: 'Ana' }] }), 1)).toBe(false);
    expect(puedeDecidir(solicitud(), null)).toBe(false);
  });

  it('no decide dos veces, ni sobre una cerrada', () => {
    const decidida = solicitud({
      decisiones: [
        { id: 9, aprobador: { id: 2, nombre: 'Beto' }, decision: 'APROBADA', markdown: null, html: null, fecha: '' },
      ],
    });

    expect(puedeDecidir(decidida, 2)).toBe(false);
    expect(puedeDecidir(decidida, 3)).toBe(true);
    expect(puedeDecidir(solicitud({ estado: 'RECHAZADA' }), 2)).toBe(false);
  });
});

describe('puedeRetirar', () => {
  it('la retira quien la pidio o quien administra el proceso, mientras siga abierta', () => {
    expect(puedeRetirar(solicitud(), 1, false)).toBe(true);
    expect(puedeRetirar(solicitud(), 2, false)).toBe(false);
    expect(puedeRetirar(solicitud(), 2, true)).toBe(true);
    expect(puedeRetirar(solicitud(), null, false)).toBe(false);
    expect(puedeRetirar(solicitud({ estado: 'PUBLICADA' }), 1, true)).toBe(false);
  });
});

describe('decisionPideTexto', () => {
  it('pedir cambios y rechazar dicen por que; aprobar no', () => {
    expect(decisionPideTexto('APROBADA')).toBe(false);
    expect(decisionPideTexto('CAMBIOS_PEDIDOS')).toBe(true);
    expect(decisionPideTexto('RECHAZADA')).toBe(true);
  });
});

describe('faltaEnPolitica', () => {
  it('una politica con aprobacion completa no le falta nada', () => {
    expect(faltaEnPolitica(valores())).toBeNull();
    expect(faltaEnPolitica(valores({ fuente: 'ROL', rolProcesoId: 3, aprobadores: [] }))).toBeNull();
  });

  it('las aprobaciones van de 1 a 10 y son enteras', () => {
    expect(faltaEnPolitica(valores({ aprobacionesNecesarias: null }))).toBe('aprobaciones');
    expect(faltaEnPolitica(valores({ aprobacionesNecesarias: 0 }))).toBe('aprobaciones');
    expect(faltaEnPolitica(valores({ aprobacionesNecesarias: 11 }))).toBe('aprobaciones');
    expect(faltaEnPolitica(valores({ aprobacionesNecesarias: 1.5 }))).toBe('aprobaciones');
    expect(faltaEnPolitica(valores({ aprobacionesNecesarias: 1, aprobadores: [4] }))).toBeNull();
    expect(faltaEnPolitica(valores({ aprobacionesNecesarias: 10, fuente: 'ROL', rolProcesoId: 3 }))).toBeNull();
  });

  it('publicar directo no pide aprobadores, pero no deja mandar un numero fuera del rango', () => {
    expect(faltaEnPolitica(valores({ modo: 'DIRECTA', aprobadores: [] }))).toBeNull();
    expect(faltaEnPolitica(valores({ modo: 'DIRECTA', aprobacionesNecesarias: null }))).toBeNull();
    expect(faltaEnPolitica(valores({ modo: 'DIRECTA', aprobacionesNecesarias: 20 }))).toBe('aprobaciones');
  });

  it('por rol, el rol tiene que estar elegido', () => {
    expect(faltaEnPolitica(valores({ fuente: 'ROL', rolProcesoId: null }))).toBe('rol');
  });

  it('por personas, al menos tantas como aprobaciones y como mucho cincuenta', () => {
    expect(faltaEnPolitica(valores({ aprobadores: [] }))).toBe('aprobadores');
    expect(faltaEnPolitica(valores({ aprobadores: [4] }))).toBe('pocos');
    expect(faltaEnPolitica(valores({ aprobadores: [4, 5, 6] }))).toBeNull();
    const muchas = Array.from({ length: APROBADORES_MAXIMOS + 1 }, (_, indice) => indice + 1);
    expect(faltaEnPolitica(valores({ aprobadores: muchas }))).toBe('demasiados');
    expect(faltaEnPolitica(valores({ aprobadores: muchas.slice(1) }))).toBeNull();
  });
});

describe('solicitudDePolitica', () => {
  it('manda solo la fuente elegida, aunque el formulario recuerde la otra', () => {
    expect(solicitudDePolitica(valores({ rolProcesoId: 3 }))).toEqual({
      modo: 'CON_APROBACION',
      aprobacionesNecesarias: 2,
      rolProcesoId: null,
      aprobadores: [4, 5],
      version: 0,
    });
    expect(solicitudDePolitica(valores({ fuente: 'ROL', rolProcesoId: 3 }))).toEqual({
      modo: 'CON_APROBACION',
      aprobacionesNecesarias: 2,
      rolProcesoId: 3,
      aprobadores: [],
      version: 0,
    });
  });

  it('publicando directo sin numero manda una aprobacion, y no manda nada si falta algo', () => {
    expect(solicitudDePolitica(valores({ modo: 'DIRECTA', aprobacionesNecesarias: null, version: null }))).toEqual({
      modo: 'DIRECTA',
      aprobacionesNecesarias: 1,
      rolProcesoId: null,
      aprobadores: [4, 5],
      version: null,
    });
    expect(solicitudDePolitica(valores({ aprobadores: [] }))).toBeNull();
  });
});

describe('valoresDePolitica', () => {
  const guardada: PoliticaDePublicacion = {
    modo: 'CON_APROBACION',
    aprobacionesNecesarias: 2,
    rolProcesoId: null,
    rolProceso: null,
    aprobadores: [
      { id: 4, nombre: 'Beto', activo: true },
      { id: 5, nombre: 'Carla', activo: false },
      { id: 6, nombre: 'Dora', activo: true },
    ],
    version: 3,
    modificadoPor: 1,
    fechaModificacion: '2026-11-12T10:00:00',
  };

  it('el formulario arranca con lo guardado, sin quien ya no esta activo', () => {
    expect(valoresDePolitica(guardada)).toEqual({
      modo: 'CON_APROBACION',
      aprobacionesNecesarias: 2,
      fuente: 'PERSONAS',
      aprobadores: [4, 6],
      rolProcesoId: null,
      version: 3,
    });
  });

  it('con un rol, la fuente es el rol; una politica nunca guardada pide una aprobacion', () => {
    expect(valoresDePolitica({ ...guardada, rolProcesoId: 8, aprobadores: [] })).toMatchObject({
      fuente: 'ROL',
      rolProcesoId: 8,
      aprobadores: [],
    });
    expect(
      valoresDePolitica({ ...guardada, modo: 'DIRECTA', aprobacionesNecesarias: 0, aprobadores: [], version: null }),
    ).toMatchObject({ modo: 'DIRECTA', aprobacionesNecesarias: 1, version: null });
  });
});
