import { describe, expect, it } from 'vitest';

import {
  Proceso,
  avisosAlAbrir,
  faltaEnEtiquetas,
  fichaConCarpeta,
  fichaInicial,
  leerEtiquetas,
} from './proceso.model';

const proceso: Proceso = {
  id: 7,
  nombre: 'Returns',
  descripcion: 'Take an order back.',
  categoria: 'Fulfillment',
  estado: 'BORRADOR',
  activo: true,
  fechaCreacion: '2026-11-01T10:00:00',
  fechaModificacion: '2026-11-02T10:00:00',
  version: 4,
  versionPublicada: null,
  borradorPendiente: null,
  carpetaId: 1,
  duenoId: 3,
  proximaRevision: '2027-03-01',
  clasificacion: 'RESTRINGIDA',
  etiquetas: ['gdpr', 'logistics'],
};

describe('fichaConCarpeta', () => {
  it('cambia la carpeta y reenvia lo demas de la ficha como estaba, con la version leida', () => {
    expect(fichaConCarpeta(proceso, 9)).toEqual({
      carpetaId: 9,
      duenoId: 3,
      proximaRevision: '2027-03-01',
      clasificacion: 'RESTRINGIDA',
      etiquetas: ['gdpr', 'logistics'],
      version: 4,
    });
  });

  it('sacarlo de cualquier carpeta es la carpeta vacia', () => {
    expect(fichaConCarpeta(proceso, null).carpetaId).toBeNull();
  });

  it('un proceso que llego sin ficha va interno, sin dueno, sin fecha y sin etiquetas', () => {
    const sinFicha: Proceso = {
      ...proceso,
      duenoId: undefined,
      proximaRevision: undefined,
      clasificacion: undefined,
      etiquetas: undefined,
    };

    expect(fichaConCarpeta(sinFicha, 2)).toEqual({
      carpetaId: 2,
      duenoId: null,
      proximaRevision: null,
      clasificacion: 'INTERNA',
      etiquetas: [],
      version: 4,
    });
  });
});

describe('leerEtiquetas', () => {
  it('separa por comas, recorta y quita las vacias y las repetidas', () => {
    expect(leerEtiquetas(' gdpr, ,logistics,gdpr ,')).toEqual(['gdpr', 'logistics']);
    expect(leerEtiquetas('')).toEqual([]);
  });
});

describe('fichaInicial', () => {
  const nuevo: Proceso = { ...proceso, carpetaId: null, duenoId: 2, version: 0 };
  const vacio = {
    carpetaId: null,
    duenoId: null,
    proximaRevision: '',
    clasificacion: 'INTERNA' as const,
    etiquetas: '',
  };

  it('sin carpeta, sin dueno, sin fecha, interno y sin etiquetas no hay nada que archivar', () => {
    expect(fichaInicial({ ...vacio, etiquetas: ' , ', proximaRevision: ' ' }, nuevo)).toBeNull();
  });

  it('con cualquiera de las cinco se archiva, con la version que devolvio el alta', () => {
    expect(fichaInicial({ ...vacio, carpetaId: 5 }, nuevo)).toEqual({
      carpetaId: 5,
      duenoId: 2,
      proximaRevision: null,
      clasificacion: 'INTERNA',
      etiquetas: [],
      version: 0,
    });
    expect(fichaInicial({ ...vacio, clasificacion: 'CONFIDENCIAL' }, nuevo)?.clasificacion).toBe('CONFIDENCIAL');
    expect(fichaInicial({ ...vacio, etiquetas: 'gdpr' }, nuevo)?.etiquetas).toEqual(['gdpr']);
    expect(fichaInicial({ ...vacio, proximaRevision: '2027-03-01' }, nuevo)?.proximaRevision).toBe('2027-03-01');
    expect(fichaInicial({ ...vacio, duenoId: 9 }, nuevo)?.duenoId).toBe(9);
  });

  it('sin dueno elegido se queda el que devolvio el alta', () => {
    expect(fichaInicial({ ...vacio, carpetaId: 5 }, { ...nuevo, duenoId: null })?.duenoId).toBeNull();
    expect(fichaInicial({ ...vacio, carpetaId: 5 }, nuevo)?.duenoId).toBe(2);
  });
});

describe('faltaEnEtiquetas', () => {
  it('diez etiquetas de cuarenta caracteres pasan, como en la API', () => {
    const diez = Array.from({ length: 10 }, (_, i) => `${i}`.padEnd(40, 'x')).join(',');
    expect(faltaEnEtiquetas(diez)).toBeNull();
    expect(faltaEnEtiquetas('')).toBeNull();
  });

  it('la undecima sobra, y las repetidas no cuentan', () => {
    const once = Array.from({ length: 11 }, (_, i) => `t${i}`).join(',');
    expect(faltaEnEtiquetas(once)).toBe('demasiadas');
    expect(faltaEnEtiquetas(Array.from({ length: 11 }, () => 'igual').join(','))).toBeNull();
  });

  it('una de cuarenta y un caracteres es larga, contada sin los espacios de los bordes', () => {
    expect(faltaEnEtiquetas(`gdpr, ${'x'.repeat(41)}`)).toBe('larga');
    expect(faltaEnEtiquetas(`  ${'x'.repeat(40)}  `)).toBeNull();
  });
});

describe('avisosAlAbrir', () => {
  const consulta = (...nombres: string[]) => ({ has: (nombre: string) => nombres.includes(nombre) });

  it('creado avisa del borrador', () => {
    expect(avisosAlAbrir(consulta('creado'))).toEqual({ aviso: 'Process created as a draft.', error: null });
  });

  it('creado sin archivar avisa del borrador y dice que la ficha quedo por hacer', () => {
    const avisos = avisosAlAbrir(consulta('creado', 'sinArchivar'));
    expect(avisos.aviso).toBe('Process created as a draft.');
    expect(avisos.error).toContain('filing could not be saved');
  });

  it('editado dice que se guardo, y sin nada no dice nada', () => {
    expect(avisosAlAbrir(consulta('editado'))).toEqual({ aviso: 'Changes saved.', error: null });
    expect(avisosAlAbrir(consulta())).toEqual({ aviso: null, error: null });
  });
});
