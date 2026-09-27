/** Lo grave que es un hallazgo. La API la usa igual en el diagnostico y en la revision con IA. */
export type Severidad = 'ALTA' | 'MEDIA' | 'BAJA';

/** Un problema del diagrama, con el elemento al que se refiere para poder saltar a el. */
export interface Hallazgo {
  /** Codigo del catalogo, como E-05 o A-02: las E son errores y las A advertencias. */
  codigo: string;
  severidad: Severidad;
  /** Como se llama el elemento; vacio cuando el hallazgo es del proceso entero. */
  elemento: string | null;
  elementoId: number | null;
  problema: string;
  sugerencia: string;
}

/**
 * El diagnostico de un diagrama. Los errores impiden publicar y las advertencias no. El mismo diagrama siempre
 * responde los mismos hallazgos, en el mismo orden.
 */
export interface Diagnostico {
  procesoId: number;
  /** Cuando se pidio simulando un borrado, el elemento que se quito, escrito TIPO:id. */
  sinElemento: string | null;
  errores: number;
  advertencias: number;
  hallazgos: Hallazgo[];
}

/** Un hallazgo de la revision con IA: no tiene codigo ni id, porque no sale de un catalogo. */
export interface HallazgoIA {
  severidad: Severidad;
  elemento: string | null;
  problema: string;
  sugerencia: string;
}

/** La segunda opinion sobre el diagrama. Es consejo: no cambia nada. */
export interface Revision {
  procesoId: number;
  resumen: string;
  hallazgos: HallazgoIA[];
  /** True cuando la API devolvio una revision que ya tenia, porque el diagrama no cambio. */
  reutilizada: boolean;
  fecha: string;
}

/** Los tipos de elemento que acepta el parametro sinElemento del diagnostico. */
export type TipoElemento = 'POOL' | 'LANE' | 'ACTIVIDAD' | 'GATEWAY' | 'EVENTO' | 'ARCO' | 'MENSAJE';

/** Un error del catalogo empieza por E y una advertencia por A. */
export function esError(hallazgo: Hallazgo): boolean {
  return hallazgo.codigo.startsWith('E');
}
