import { describe, expect, it } from 'vitest';

import { Proceso, fichaConCarpeta, fichaInicial, leerEtiquetas } from './proceso.model';

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

  it('sin carpeta, interno y sin etiquetas no hay nada que archivar', () => {
    expect(fichaInicial({ carpetaId: null, clasificacion: 'INTERNA', etiquetas: ' , ' }, nuevo)).toBeNull();
  });

  it('con cualquiera de las tres se archiva, con la version que devolvio el alta', () => {
    expect(fichaInicial({ carpetaId: 5, clasificacion: 'INTERNA', etiquetas: '' }, nuevo)).toEqual({
      carpetaId: 5,
      duenoId: 2,
      proximaRevision: null,
      clasificacion: 'INTERNA',
      etiquetas: [],
      version: 0,
    });
    expect(fichaInicial({ carpetaId: null, clasificacion: 'CONFIDENCIAL', etiquetas: '' }, nuevo)?.clasificacion).toBe(
      'CONFIDENCIAL',
    );
    expect(fichaInicial({ carpetaId: null, clasificacion: 'INTERNA', etiquetas: 'gdpr' }, nuevo)?.etiquetas).toEqual([
      'gdpr',
    ]);
  });
});
