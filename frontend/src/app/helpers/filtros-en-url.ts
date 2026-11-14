import {
  CAMPOS_ORDEN,
  CLASIFICACIONES,
  CampoOrden,
  Direccion,
  EstadoProceso,
  FiltrosProceso,
} from '../models/proceso.model';

/** Lo que se lee de una direccion: basta con el get de ParamMap, y asi se prueba sin el router. */
export interface ParametrosLeidos {
  get(nombre: string): string | null;
}

/** La lista sin filtros: lo que se ve al entrar, lo mas reciente primero. */
export function filtrosIniciales(): FiltrosProceso {
  return {
    nombre: '',
    estado: '',
    categoria: '',
    carpetaId: null,
    duenoId: null,
    etiqueta: '',
    clasificacion: '',
    revisionAntesDe: '',
    orden: 'fechaModificacion',
    direccion: 'desc',
    pagina: 0,
  };
}

const ESTADOS: EstadoProceso[] = ['BORRADOR', 'PUBLICADO'];
/** Un dia como lo escribe un input date: 2027-03-01. */
const DIA = /^[0-9]{4}-[0-9]{2}-[0-9]{2}$/;
const ENTERO_POSITIVO = /^[1-9][0-9]{0,15}$/;
/** La pagina es un int en la API: nueve cifras caben siempre, y una mas ya podria pasarse de 2^31 - 1. */
const PAGINA = /^[1-9][0-9]{0,8}$/;

/**
 * Los filtros de la lista de procesos que trae la direccion, para que una lista filtrada se pueda compartir y
 * sobreviva a recargar la pagina. La direccion la escribe cualquiera, asi que nada se cree: un estado, una
 * clasificacion o un orden que no existen, un id que no es un numero o un dia mal escrito valen lo de siempre, en
 * vez de llegar a la API como un 400. La pagina va contada desde 1, como la lee una persona.
 */
export function filtrosDesdeUrl(parametros: ParametrosLeidos): FiltrosProceso {
  const filtros = filtrosIniciales();
  filtros.nombre = parametros.get('nombre') ?? '';
  filtros.categoria = parametros.get('categoria') ?? '';
  filtros.etiqueta = parametros.get('etiqueta') ?? '';
  filtros.estado = deLista(parametros.get('estado'), ESTADOS) ?? '';
  filtros.clasificacion = deLista(parametros.get('clasificacion'), CLASIFICACIONES) ?? '';
  filtros.carpetaId = entero(parametros.get('carpeta'));
  filtros.duenoId = entero(parametros.get('dueno'));
  const revision = parametros.get('revision') ?? '';
  filtros.revisionAntesDe = esDia(revision) ? revision : '';
  const [campo, direccion] = (parametros.get('orden') ?? '').split(',');
  const orden = deLista(campo, CAMPOS_ORDEN);
  if (orden) {
    filtros.orden = orden;
    filtros.direccion = deLista(direccion, ['asc', 'desc'] as Direccion[]) ?? direccionPorDefecto(orden);
  }
  const pagina = parametros.get('pagina') ?? '';
  filtros.pagina = PAGINA.test(pagina) ? Number(pagina) - 1 : 0;
  return filtros;
}

/**
 * La direccion de unos filtros: solo lleva lo que no es lo de siempre, para que la lista sin filtros sea /procesos a
 * secas y un enlace diga solo lo que filtra. El nombre va como se escribio, sin recortar, porque la direccion se
 * vuelve a leer mientras se escribe y un espacio al final no puede desaparecer bajo el cursor.
 */
export function filtrosEnUrl(filtros: FiltrosProceso): Record<string, string> {
  const parametros: Record<string, string> = {};
  const poner = (nombre: string, valor: string | number | null | undefined): void => {
    if (valor !== null && valor !== undefined && String(valor).trim() !== '') {
      parametros[nombre] = String(valor);
    }
  };
  poner('nombre', filtros.nombre);
  poner('estado', filtros.estado);
  poner('categoria', filtros.categoria);
  poner('carpeta', filtros.carpetaId);
  poner('dueno', filtros.duenoId);
  poner('etiqueta', filtros.etiqueta);
  poner('clasificacion', filtros.clasificacion);
  poner('revision', filtros.revisionAntesDe);
  const iniciales = filtrosIniciales();
  if (filtros.orden !== iniciales.orden || filtros.direccion !== iniciales.direccion) {
    parametros['orden'] = `${filtros.orden},${filtros.direccion}`;
  }
  if (filtros.pagina > 0) {
    parametros['pagina'] = String(filtros.pagina + 1);
  }
  return parametros;
}

/** El primer clic ordena de la A a la Z, y las fechas, de la mas reciente. */
export function direccionPorDefecto(campo: CampoOrden): Direccion {
  return campo === 'fechaModificacion' ? 'desc' : 'asc';
}

function deLista<T extends string>(valor: string | null | undefined, lista: readonly T[]): T | null {
  return lista.find((uno) => uno === valor) ?? null;
}

/** Un dia que existe: 2027-02-30 tiene la forma de uno, pero la API lo rechaza con un 400. */
function esDia(valor: string): boolean {
  if (!DIA.test(valor)) {
    return false;
  }
  const [anio, mes, dia] = valor.split('-').map(Number);
  const fecha = new Date(Date.UTC(anio, mes - 1, dia));
  return fecha.getUTCFullYear() === anio && fecha.getUTCMonth() === mes - 1 && fecha.getUTCDate() === dia;
}

function entero(valor: string | null): number | null {
  return valor !== null && ENTERO_POSITIVO.test(valor) ? Number(valor) : null;
}
