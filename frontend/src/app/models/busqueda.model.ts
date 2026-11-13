/** PR 65: de que es lo que encontro la busqueda. */
export type TipoDeResultado = 'PROCESO' | 'ELEMENTO' | 'DOCUMENTO' | 'COMENTARIO';

/** El orden en que se agrupan: lo mas amplio primero. */
export const TIPOS_DE_RESULTADO: TipoDeResultado[] = ['PROCESO', 'ELEMENTO', 'DOCUMENTO', 'COMENTARIO'];

export const NOMBRE_TIPO_DE_RESULTADO: Record<TipoDeResultado, string> = {
  PROCESO: $localize`Processes`,
  ELEMENTO: $localize`Elements`,
  DOCUMENTO: $localize`Documents`,
  COMENTARIO: $localize`Comments`,
};

/** Un tramo del fragmento: texto plano, resaltado si es una de las palabras buscadas. Nunca HTML. */
export interface Segmento {
  texto: string;
  resaltado: boolean;
}

/** Algo que encontro la busqueda (ResultadoDeBusquedaResponse). */
export interface ResultadoDeBusqueda {
  tipo: TipoDeResultado;
  procesoId: number;
  proceso: string;
  titulo: string;
  bpmnId: string | null;
  tipoDeElemento: string | null;
  version: number | null;
  documentoId: number | null;
  comentarioId: number | null;
  hiloId: number | null;
  fragmento: Segmento[];
}

/** Un resultado en su grupo, con su posicion en la lista entera: las flechas recorren los grupos de corrido. */
export interface ResultadoEnLista {
  resultado: ResultadoDeBusqueda;
  indice: number;
}

export interface GrupoDeResultados {
  tipo: TipoDeResultado;
  nombre: string;
  resultados: ResultadoEnLista[];
}

/**
 * Los resultados agrupados por tipo, en el orden de TIPOS_DE_RESULTADO, y dentro de cada grupo en el que dio la API,
 * que es el de relevancia. Los grupos sin nada no salen. El indice de cada uno es su lugar en la lista que se recorre
 * con las flechas, grupo tras grupo, asi que lo que se ve y lo que se recorre van en el mismo orden.
 */
export function agruparResultados(resultados: ResultadoDeBusqueda[]): GrupoDeResultados[] {
  const grupos: GrupoDeResultados[] = [];
  let indice = 0;
  for (const tipo of TIPOS_DE_RESULTADO) {
    const delTipo = resultados.filter((resultado) => resultado.tipo === tipo);
    if (delTipo.length > 0) {
      grupos.push({
        tipo,
        nombre: NOMBRE_TIPO_DE_RESULTADO[tipo],
        resultados: delTipo.map((resultado) => ({ resultado, indice: indice++ })),
      });
    }
  }
  return grupos;
}

/** Los resultados en el orden en que se recorren, que es el de los grupos. */
export function enOrdenDeGrupos(grupos: GrupoDeResultados[]): ResultadoDeBusqueda[] {
  return grupos.flatMap((grupo) => grupo.resultados.map((uno) => uno.resultado));
}
