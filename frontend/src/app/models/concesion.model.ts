import { Carpeta } from './carpeta.model';
import { Proceso } from './proceso.model';
import { RolAcceso } from './usuario.model';

/** PR 60: lo que se puede hacer con una carpeta o un proceso. */
export type Accion = 'VER' | 'COMENTAR' | 'MODELAR' | 'REVISAR' | 'APROBAR' | 'PUBLICAR' | 'EXPORTAR' | 'ADMINISTRAR';

/** A quien se le concede: una persona, la gente de un rol de proceso o la organizacion entera. */
export type SujetoConcesion = 'PERSONA' | 'ROL' | 'ORGANIZACION';

export const ACCIONES: Accion[] = [
  'VER',
  'COMENTAR',
  'MODELAR',
  'REVISAR',
  'APROBAR',
  'PUBLICAR',
  'EXPORTAR',
  'ADMINISTRAR',
];

export const SUJETOS: SujetoConcesion[] = ['PERSONA', 'ROL', 'ORGANIZACION'];

export const NOMBRE_ACCION: Record<Accion, string> = {
  VER: $localize`View`,
  COMENTAR: $localize`Comment`,
  MODELAR: $localize`Model`,
  REVISAR: $localize`Review`,
  APROBAR: $localize`Approve`,
  PUBLICAR: $localize`Publish`,
  EXPORTAR: $localize`Export`,
  ADMINISTRAR: $localize`Administer`,
};

/** Lo que trae cada accion, dicho para quien la concede. */
export const AYUDA_ACCION: Record<Accion, string> = {
  VER: $localize`Open it and find it in lists and searches.`,
  COMENTAR: $localize`Write and answer comments.`,
  MODELAR: $localize`Edit its data and its diagram.`,
  REVISAR: $localize`Ask for a review of the diagram.`,
  APROBAR: $localize`Approve a change made by someone else.`,
  PUBLICAR: $localize`Publish a new version.`,
  EXPORTAR: $localize`Download the BPMN file and build the manual.`,
  ADMINISTRAR: $localize`Everything, including granting and taking back.`,
};

export const NOMBRE_SUJETO: Record<SujetoConcesion, string> = {
  PERSONA: $localize`A person`,
  ROL: $localize`The people of a process role`,
  ORGANIZACION: $localize`Everyone in the organization`,
};

/** Una concesion sobre una carpeta o un proceso (ConcesionResponse). */
export interface Concesion {
  id: number;
  carpetaId: number | null;
  procesoId: number | null;
  sujeto: SujetoConcesion;
  usuarioId: number | null;
  rolProcesoId: number | null;
  accion: Accion;
}

/** Cuerpo de POST .../concesiones: la persona va solo con PERSONA y el rol solo con ROL. */
export interface ConcesionRequest {
  accion: Accion;
  sujeto: SujetoConcesion;
  usuarioId: number | null;
  rolProcesoId: number | null;
}

/** Lo que tiene el formulario de conceder, tal cual: cualquier campo puede venir vacio. */
export interface ValoresDeConcesion {
  accion: Accion | null;
  sujeto: SujetoConcesion | null;
  usuarioId: number | null;
  rolProcesoId: number | null;
}

/** Que le falta al formulario para mandarse; null si no le falta nada. */
export type FaltaEnConcesion = 'accion' | 'sujeto' | 'usuario' | 'rol';

/**
 * Lo primero que le falta al formulario, en el orden en que se lee. Es la misma regla de la API (el sujeto tiene que
 * coincidir con la persona o el rol), dicha antes de enviar.
 */
export function faltaEnConcesion(valores: ValoresDeConcesion): FaltaEnConcesion | null {
  if (!valores.accion) {
    return 'accion';
  }
  if (!valores.sujeto) {
    return 'sujeto';
  }
  if (valores.sujeto === 'PERSONA' && valores.usuarioId === null) {
    return 'usuario';
  }
  if (valores.sujeto === 'ROL' && valores.rolProcesoId === null) {
    return 'rol';
  }
  return null;
}

/**
 * El cuerpo que se manda: la persona o el rol que no van con el sujeto elegido se quitan, aunque sigan en el
 * formulario de una eleccion anterior, porque la API responde 400 a un sujeto con datos de otro.
 */
export function solicitudDeConcesion(valores: ValoresDeConcesion): ConcesionRequest | null {
  if (faltaEnConcesion(valores) !== null) {
    return null;
  }
  const sujeto = valores.sujeto as SujetoConcesion;
  return {
    accion: valores.accion as Accion,
    sujeto,
    usuarioId: sujeto === 'PERSONA' ? valores.usuarioId : null,
    rolProcesoId: sujeto === 'ROL' ? valores.rolProcesoId : null,
  };
}

/**
 * Las carpetas cuyas concesiones alcanzan a algo que esta en esta: ella misma y cada una de las que la contienen,
 * de la mas cercana a la raiz. Una cadena rota o con un ciclo se corta donde se rompe en vez de dar vueltas.
 */
export function cadenaDeCarpetas(carpetaId: number | null | undefined, carpetas: Carpeta[]): Carpeta[] {
  const porId = new Map(carpetas.map((carpeta) => [carpeta.id, carpeta]));
  const cadena: Carpeta[] = [];
  const vistas = new Set<number>();
  let actual = carpetaId ?? null;
  while (actual !== null && !vistas.has(actual)) {
    const carpeta = porId.get(actual);
    if (!carpeta) {
      break;
    }
    vistas.add(actual);
    cadena.push(carpeta);
    actual = carpeta.padreId;
  }
  return cadena;
}

/** Lo que tiene concesiones: un proceso, que esta en una carpeta o en ninguna, o una carpeta. */
export type RecursoConConcesiones = { proceso: Pick<Proceso, 'carpetaId'> } | { carpeta: Carpeta };

/**
 * La carpeta desde la que se hereda: la del proceso, o la madre de la carpeta, porque lo de una carpeta no es heredado
 * para ella misma sino suyo. Null si no hay ninguna arriba.
 */
export function inicioDeLaHerencia(recurso: RecursoConConcesiones): number | null {
  return 'carpeta' in recurso ? recurso.carpeta.padreId : (recurso.proceso.carpetaId ?? null);
}

/** Los botones de un proceso que van a funcionar para quien lo mira. */
export interface BotonesDelProceso {
  editar: boolean;
  modelar: boolean;
  publicar: boolean;
  exportar: boolean;
  compartir: boolean;
  permisos: boolean;
  eliminar: boolean;
}

/**
 * Que botones se ensenan, de lo que la API dice que la persona puede hacer (GET /procesos/{id}/permisos) y de su
 * rol, porque la matriz de rutas tambien cuenta: compartir con otra tienda y borrar son del administrador, y quien
 * solo lee no cambia nada aunque tuviera una concesion. Sin la lista, porque no llego, se decide por el rol, como
 * antes de las concesiones; la API sigue siendo la que manda y responde 403 a lo que no toca.
 */
export function botonesDelProceso(acciones: readonly Accion[] | null, rol: RolAcceso | null): BotonesDelProceso {
  const edita = rol === 'ADMINISTRADOR' || rol === 'EDITOR';
  const administra = rol === 'ADMINISTRADOR';
  if (acciones === null) {
    return {
      editar: edita,
      modelar: edita,
      publicar: edita,
      exportar: rol !== null,
      compartir: administra,
      permisos: administra,
      eliminar: administra,
    };
  }
  const puede = (accion: Accion): boolean => acciones.includes(accion);
  return {
    editar: edita && puede('MODELAR'),
    modelar: edita && puede('MODELAR'),
    publicar: edita && puede('PUBLICAR'),
    exportar: puede('EXPORTAR'),
    compartir: administra && puede('ADMINISTRAR'),
    permisos: edita && puede('ADMINISTRAR'),
    eliminar: administra && puede('ADMINISTRAR'),
  };
}
