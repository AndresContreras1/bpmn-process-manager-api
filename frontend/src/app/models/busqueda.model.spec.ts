import { describe, expect, it } from 'vitest';

import { ResultadoDeBusqueda, TipoDeResultado, agruparResultados, destinoDe, enOrdenDeGrupos } from './busqueda.model';

function resultado(tipo: TipoDeResultado, titulo: string): ResultadoDeBusqueda {
  return {
    tipo,
    procesoId: 1,
    proceso: 'Order fulfillment',
    titulo,
    bpmnId: null,
    tipoDeElemento: null,
    version: null,
    documentoId: null,
    comentarioId: null,
    hiloId: null,
    fragmento: [],
  };
}

describe('agruparResultados', () => {
  it('agrupa por tipo en el orden fijo, y dentro de cada grupo en el de la API', () => {
    const grupos = agruparResultados([
      resultado('COMENTARIO', 'Ana'),
      resultado('ELEMENTO', 'Pack'),
      resultado('PROCESO', 'Returns'),
      resultado('ELEMENTO', 'Ship'),
      resultado('DOCUMENTO', 'policy.pdf'),
    ]);

    expect(grupos.map((grupo) => grupo.tipo)).toEqual(['PROCESO', 'ELEMENTO', 'DOCUMENTO', 'COMENTARIO']);
    expect(grupos[1].resultados.map((uno) => uno.resultado.titulo)).toEqual(['Pack', 'Ship']);
    expect(grupos[0].nombre).toBe('Processes');
  });

  it('numera de corrido a traves de los grupos, que es como se recorren con las flechas', () => {
    const grupos = agruparResultados([resultado('ELEMENTO', 'Pack'), resultado('PROCESO', 'Returns')]);

    expect(grupos.flatMap((grupo) => grupo.resultados.map((uno) => [uno.resultado.titulo, uno.indice]))).toEqual([
      ['Returns', 0],
      ['Pack', 1],
    ]);
    expect(enOrdenDeGrupos(grupos).map((uno) => uno.titulo)).toEqual(['Returns', 'Pack']);
  });

  it('sin resultados no hay grupos, ni vacios', () => {
    expect(agruparResultados([])).toEqual([]);
    expect(agruparResultados([resultado('DOCUMENTO', 'a.pdf')]).map((grupo) => grupo.tipo)).toEqual(['DOCUMENTO']);
  });
});

describe('destinoDe', () => {
  it('un elemento abre su proceso con el elemento elegido', () => {
    expect(destinoDe({ ...resultado('ELEMENTO', 'Pack'), procesoId: 7, bpmnId: 'Activity_pack' })).toEqual({
      ruta: ['/procesos', 7],
      consulta: { elemento: 'Activity_pack' },
    });
  });

  it('un proceso, un documento, un comentario o un elemento sin bpmnId abren el proceso a secas', () => {
    for (const tipo of ['PROCESO', 'DOCUMENTO', 'COMENTARIO', 'ELEMENTO'] as TipoDeResultado[]) {
      expect(destinoDe({ ...resultado(tipo, 'x'), procesoId: 3 })).toEqual({ ruta: ['/procesos', 3], consulta: {} });
    }
    expect(destinoDe({ ...resultado('DOCUMENTO', 'policy.pdf'), bpmnId: 'Activity_pack' }).consulta).toEqual({});
  });
});
