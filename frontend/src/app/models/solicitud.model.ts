import { Persona } from './comentario.model';
import { Comparacion } from './modelo.model';
import { Tono } from './tono.model';

/** PR 64: la revision y la aprobacion antes de publicar, y la politica de cada organizacion. */

export type EstadoSolicitud =
  'ABIERTA' | 'PUBLICADA' | 'APROBADA_SIN_PUBLICAR' | 'CAMBIOS_PEDIDOS' | 'RECHAZADA' | 'RETIRADA';

export type TipoDeDecision = 'APROBADA' | 'CAMBIOS_PEDIDOS' | 'RECHAZADA';

/** Lo que decidio un aprobador (DecisionResponse). */
export interface Decision {
  id: number;
  aprobador: Persona;
  decision: TipoDeDecision;
  markdown: string | null;
  html: string | null;
  fecha: string;
}

/** Una solicitud de revision (SolicitudResponse). La diferencia solo viene al pedir una, no en las listas. */
export interface Solicitud {
  id: number;
  procesoId: number;
  proceso: string;
  estado: EstadoSolicitud;
  solicitante: Persona;
  markdown: string | null;
  html: string | null;
  /** La version que publica. */
  numero: number;
  /** La vigente cuando se pidio; null si no habia. */
  versionBase: number | null;
  /** La revision del borrador congelada al pedirla; null cuando las revisiones viejas ya se limpiaron. */
  revisionId: number | null;
  aprobacionesNecesarias: number;
  aprobaciones: number;
  aprobadores: Persona[];
  decisiones: Decision[];
  motivo: string | null;
  cerradaPor: Persona | null;
  cerradaEn: string | null;
  versionPublicada: number | null;
  fechaCreacion: string;
  diferencia: Comparacion | null;
}

export const ESTADOS_SOLICITUD: readonly EstadoSolicitud[] = [
  'ABIERTA',
  'PUBLICADA',
  'APROBADA_SIN_PUBLICAR',
  'CAMBIOS_PEDIDOS',
  'RECHAZADA',
  'RETIRADA',
];

export const NOMBRE_ESTADO_SOLICITUD: Record<EstadoSolicitud, string> = {
  ABIERTA: $localize`Waiting for approval`,
  PUBLICADA: $localize`Published`,
  APROBADA_SIN_PUBLICAR: $localize`Approved, not published`,
  CAMBIOS_PEDIDOS: $localize`Changes asked`,
  RECHAZADA: $localize`Rejected`,
  RETIRADA: $localize`Withdrawn`,
};

export const TONO_ESTADO_SOLICITUD: Record<EstadoSolicitud, Tono> = {
  ABIERTA: 'aviso',
  PUBLICADA: 'exito',
  APROBADA_SIN_PUBLICAR: 'peligro',
  CAMBIOS_PEDIDOS: 'info',
  RECHAZADA: 'peligro',
  RETIRADA: 'neutro',
};

export const NOMBRE_DECISION: Record<TipoDeDecision, string> = {
  APROBADA: $localize`Approved`,
  CAMBIOS_PEDIDOS: $localize`Asked for changes`,
  RECHAZADA: $localize`Rejected`,
};

/**
 * Si la persona puede decidir sobre la solicitud: abierta, es uno de sus aprobadores y todavia no decidio. Quien la
 * pidio nunca esta entre ellos, pero se comprueba igual: la API le responderia 403.
 */
export function puedeDecidir(solicitud: Solicitud, usuarioId: number | null): boolean {
  return (
    usuarioId !== null &&
    solicitud.estado === 'ABIERTA' &&
    solicitud.solicitante.id !== usuarioId &&
    solicitud.aprobadores.some((aprobador) => aprobador.id === usuarioId) &&
    !solicitud.decisiones.some((decision) => decision.aprobador.id === usuarioId)
  );
}

/** La retira, mientras siga abierta, quien la pidio o quien administra el proceso. */
export function puedeRetirar(solicitud: Solicitud, usuarioId: number | null, administraElProceso: boolean): boolean {
  return (
    solicitud.estado === 'ABIERTA' &&
    (administraElProceso || (usuarioId !== null && solicitud.solicitante.id === usuarioId))
  );
}

/** Pedir cambios y rechazar tienen que decir por que; aprobar no. */
export function decisionPideTexto(decision: TipoDeDecision): boolean {
  return decision !== 'APROBADA';
}

export type ModoDePublicacion = 'DIRECTA' | 'CON_APROBACION';

/** Un aprobador de la lista, con si sigue activo (y por lo tanto si todavia aprueba). */
export interface Aprobador {
  id: number;
  nombre: string;
  activo: boolean;
}

/** Como publica la tienda (PoliticaDePublicacionResponse). Version null mientras nunca se guardo. */
export interface PoliticaDePublicacion {
  modo: ModoDePublicacion;
  aprobacionesNecesarias: number;
  rolProcesoId: number | null;
  rolProceso: string | null;
  aprobadores: Aprobador[];
  version: number | null;
  modificadoPor: number | null;
  fechaModificacion: string | null;
}

/** Cuerpo de PUT .../politica-de-publicacion: los aprobadores salen de una lista o de un rol, nunca de los dos. */
export interface PoliticaDePublicacionRequest {
  modo: ModoDePublicacion;
  aprobacionesNecesarias: number;
  rolProcesoId: number | null;
  aprobadores: number[];
  version: number | null;
}

/** De donde salen los aprobadores en el formulario. */
export type FuenteDeAprobadores = 'PERSONAS' | 'ROL';

/** Lo que tiene el formulario de la politica, tal cual. */
export interface ValoresDePolitica {
  modo: ModoDePublicacion;
  aprobacionesNecesarias: number | null;
  fuente: FuenteDeAprobadores;
  aprobadores: number[];
  rolProcesoId: number | null;
  version: number | null;
}

export const APROBACIONES_MINIMAS: number = 1;
export const APROBACIONES_MAXIMAS: number = 10;
export const APROBADORES_MAXIMOS: number = 50;

/** Que le falta a la politica para guardarse, en el orden en que se lee el formulario; null si nada. */
export type FaltaEnPolitica = 'aprobaciones' | 'rol' | 'aprobadores' | 'demasiados' | 'pocos';

/**
 * Las reglas de la API, dichas antes de enviar. Las aprobaciones van de 1 a 10 en los dos modos, aunque publicando
 * directo pueden quedar vacias (se manda 1). Publicar directo no pide nada mas: la API guarda lo demas para volver a
 * encenderla. Con aprobacion, un rol elegido o una lista de hasta 50 con al menos tantas personas como aprobaciones;
 * cuanta gente tiene un rol solo lo sabe la API.
 */
export function faltaEnPolitica(valores: ValoresDePolitica): FaltaEnPolitica | null {
  const vacias = valores.aprobacionesNecesarias === null;
  const necesarias = valores.aprobacionesNecesarias ?? 0;
  const validas =
    Number.isInteger(necesarias) && necesarias >= APROBACIONES_MINIMAS && necesarias <= APROBACIONES_MAXIMAS;
  if (!validas && (valores.modo === 'CON_APROBACION' || !vacias)) {
    return 'aprobaciones';
  }
  if (valores.modo === 'DIRECTA') {
    return null;
  }
  if (valores.fuente === 'ROL') {
    return valores.rolProcesoId === null ? 'rol' : null;
  }
  if (valores.aprobadores.length === 0) {
    return 'aprobadores';
  }
  if (valores.aprobadores.length > APROBADORES_MAXIMOS) {
    return 'demasiados';
  }
  return valores.aprobadores.length < necesarias ? 'pocos' : null;
}

/**
 * El cuerpo que se manda. La fuente que no se eligio va vacia, aunque el formulario la recuerde de antes, porque la
 * API responde 400 a una lista y un rol juntos. Publicando directo se mandan igual: es lo que se guarda para despues.
 */
export function solicitudDePolitica(valores: ValoresDePolitica): PoliticaDePublicacionRequest | null {
  if (faltaEnPolitica(valores) !== null) {
    return null;
  }
  const porRol = valores.fuente === 'ROL';
  return {
    modo: valores.modo,
    aprobacionesNecesarias: valores.aprobacionesNecesarias ?? APROBACIONES_MINIMAS,
    rolProcesoId: porRol ? valores.rolProcesoId : null,
    aprobadores: porRol ? [] : [...valores.aprobadores],
    version: valores.version,
  };
}

/** El formulario con lo que hay guardado; una politica que nunca se guardo arranca con una lista vacia. */
export function valoresDePolitica(politica: PoliticaDePublicacion): ValoresDePolitica {
  return {
    modo: politica.modo,
    aprobacionesNecesarias: politica.aprobacionesNecesarias || APROBACIONES_MINIMAS,
    fuente: politica.rolProcesoId === null ? 'PERSONAS' : 'ROL',
    aprobadores: politica.aprobadores.filter((aprobador) => aprobador.activo).map((aprobador) => aprobador.id),
    rolProcesoId: politica.rolProcesoId,
    version: politica.version,
  };
}
