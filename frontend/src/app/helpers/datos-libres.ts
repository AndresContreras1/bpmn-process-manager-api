import { Datos } from '../models/caso.model';

/**
 * Las variables de un caso y los datos con los que se completa una tarea son objetos libres, asi que se editan
 * como lo que son: una lista de claves y valores escritos a mano.
 */
export interface ParClaveValor {
  clave: string;
  valor: string;
}

/**
 * De lo escrito a lo que se manda. El valor se escribe como texto pero no siempre es texto: las condiciones de los
 * gateways comparan numeros y booleanos, y un mensaje puede traer un objeto dentro. Asi que se interpreta lo que
 * parece —un numero, true, false, null, un objeto o una lista en JSON— y lo demas viaja como la cadena que es.
 */
export function aDatos(pares: ParClaveValor[]): Datos {
  const datos: Datos = {};
  for (const par of pares) {
    const clave: string = par.clave.trim();
    if (clave !== '') {
      datos[clave] = valorDeTexto(par.valor);
    }
  }
  return datos;
}

/** De lo que devolvio la API a filas para el formulario. */
export function aPares(datos: Datos | null): ParClaveValor[] {
  return Object.entries(datos ?? {}).map(([clave, valor]) => ({ clave, valor: comoTexto(valor) }));
}

/** Como se ensena un valor cualquiera: el texto tal cual, y lo demas en JSON. */
export function comoTexto(valor: unknown): string {
  return typeof valor === 'string' ? valor : JSON.stringify(valor) ?? '';
}

/** Lo que parece un numero es un numero; lo que parece JSON se lee como JSON; el resto es texto. */
function valorDeTexto(texto: string): unknown {
  const limpio: string = texto.trim();
  if (limpio === '') {
    return '';
  }
  if (limpio === 'true' || limpio === 'false') {
    return limpio === 'true';
  }
  if (limpio === 'null') {
    return null;
  }
  if (/^-?\d+(\.\d+)?$/.test(limpio)) {
    return Number(limpio);
  }
  if (limpio.startsWith('{') || limpio.startsWith('[')) {
    try {
      return JSON.parse(limpio) as unknown;
    } catch {
      // No era JSON valido: se manda como texto y la API dira lo que opina
      return texto;
    }
  }
  return texto;
}
