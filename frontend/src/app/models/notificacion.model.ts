/** PR 63 y PR 64: los avisos de cada persona, que solo ella ve, y como le llegan por correo. */

export type TipoDeNotificacion =
  | 'MENCION'
  | 'RESPUESTA'
  | 'HILO_RESUELTO'
  | 'APROBACION_PEDIDA'
  | 'SOLICITUD_APROBADA'
  | 'SOLICITUD_PUBLICADA'
  | 'SOLICITUD_SIN_PUBLICAR'
  | 'CAMBIOS_PEDIDOS'
  | 'SOLICITUD_RECHAZADA'
  | 'RECERTIFICACION_PENDIENTE'
  | 'RECERTIFICACION_VENCIDA';

export type EnvioPorCorreo = 'INMEDIATO' | 'RESUMEN_DIARIO' | 'NUNCA';

/** Un aviso (NotificacionResponse). No trae el texto de ningun comentario: la web abre aquello a lo que apunta. */
export interface Notificacion {
  id: number;
  tipo: TipoDeNotificacion;
  procesoId: number;
  proceso: string;
  /** El comentario, el hilo o la solicitud, segun el tipo; null para una fecha de revision. */
  referenciaId: number | null;
  autorId: number | null;
  autor: string | null;
  creadaEn: string;
  leidaEn: string | null;
}

export interface ConteoDeNotificaciones {
  noLeidas: number;
}

/** Como llega por correo un tipo de aviso; en la web llega siempre. */
export interface PreferenciaDeNotificacion {
  tipo: TipoDeNotificacion;
  envio: EnvioPorCorreo;
  porDefecto: boolean;
}

/** Lo que trae el flujo de avisos, y cuando se corta o se repone. */
export type EventoDeNotificaciones =
  | { tipo: 'conectado' }
  | { tipo: 'desconectado' }
  | { tipo: 'conteo'; datos: ConteoDeNotificaciones }
  | { tipo: 'notificacion'; datos: Notificacion };

/** Los tipos en el orden en que se leen en las preferencias: comentarios, revisiones y fechas de revision. */
export const TIPOS_DE_NOTIFICACION: readonly TipoDeNotificacion[] = [
  'MENCION',
  'RESPUESTA',
  'HILO_RESUELTO',
  'APROBACION_PEDIDA',
  'SOLICITUD_APROBADA',
  'SOLICITUD_PUBLICADA',
  'SOLICITUD_SIN_PUBLICAR',
  'CAMBIOS_PEDIDOS',
  'SOLICITUD_RECHAZADA',
  'RECERTIFICACION_PENDIENTE',
  'RECERTIFICACION_VENCIDA',
];

export const ENVIOS: readonly EnvioPorCorreo[] = ['INMEDIATO', 'RESUMEN_DIARIO', 'NUNCA'];

export const NOMBRE_ENVIO: Record<EnvioPorCorreo, string> = {
  INMEDIATO: $localize`At once`,
  RESUMEN_DIARIO: $localize`In the daily digest`,
  NUNCA: $localize`Never`,
};

/** El nombre de cada tipo en la pantalla de preferencias. */
export const NOMBRE_TIPO_DE_NOTIFICACION: Record<TipoDeNotificacion, string> = {
  MENCION: $localize`Someone mentions me`,
  RESPUESTA: $localize`Someone replies in a thread I wrote in`,
  HILO_RESUELTO: $localize`A thread I wrote in is resolved`,
  APROBACION_PEDIDA: $localize`Someone asks me to approve a change`,
  SOLICITUD_APROBADA: $localize`Someone approves my request`,
  SOLICITUD_PUBLICADA: $localize`My request is published`,
  SOLICITUD_SIN_PUBLICAR: $localize`My request is approved but could not be published`,
  CAMBIOS_PEDIDOS: $localize`Someone asks for changes to my request`,
  SOLICITUD_RECHAZADA: $localize`Someone rejects my request`,
  RECERTIFICACION_PENDIENTE: $localize`A process I own is due for review`,
  RECERTIFICACION_VENCIDA: $localize`The review of a process I own is overdue`,
};

/** Lo que dice un aviso en la campana: quien hizo que, y en que proceso. */
export function textoDeNotificacion(notificacion: Notificacion): string {
  const autor = notificacion.autor ?? $localize`Someone`;
  const proceso = notificacion.proceso;
  switch (notificacion.tipo) {
    case 'MENCION':
      return $localize`${autor}:autor: mentioned you in ${proceso}:proceso:`;
    case 'RESPUESTA':
      return $localize`${autor}:autor: replied in a thread of ${proceso}:proceso:`;
    case 'HILO_RESUELTO':
      return $localize`${autor}:autor: resolved a thread of ${proceso}:proceso:`;
    case 'APROBACION_PEDIDA':
      return $localize`${autor}:autor: asks you to approve a change to ${proceso}:proceso:`;
    case 'SOLICITUD_APROBADA':
      return $localize`${autor}:autor: approved your request for ${proceso}:proceso:`;
    case 'SOLICITUD_PUBLICADA':
      return $localize`Your request for ${proceso}:proceso: was approved and published`;
    case 'SOLICITUD_SIN_PUBLICAR':
      return $localize`Your request for ${proceso}:proceso: was approved but could not be published`;
    case 'CAMBIOS_PEDIDOS':
      return $localize`${autor}:autor: asked for changes to your request for ${proceso}:proceso:`;
    case 'SOLICITUD_RECHAZADA':
      return $localize`${autor}:autor: rejected your request for ${proceso}:proceso:`;
    case 'RECERTIFICACION_PENDIENTE':
      return $localize`${proceso}:proceso: is due for review`;
    case 'RECERTIFICACION_VENCIDA':
      return $localize`The review of ${proceso}:proceso: is overdue`;
  }
}

/** A donde lleva un aviso: la ruta y su consulta, como las recibe routerLink. */
export interface EnlaceDeNotificacion {
  ruta: (string | number)[];
  consulta: Record<string, number> | null;
}

const DE_COMENTARIO: readonly TipoDeNotificacion[] = ['MENCION', 'RESPUESTA', 'HILO_RESUELTO'];
const DE_SOLICITUD: readonly TipoDeNotificacion[] = [
  'APROBACION_PEDIDA',
  'SOLICITUD_APROBADA',
  'SOLICITUD_PUBLICADA',
  'SOLICITUD_SIN_PUBLICAR',
  'CAMBIOS_PEDIDOS',
  'SOLICITUD_RECHAZADA',
];

/**
 * Lo que abre un aviso: un comentario abre su proceso en ese hilo (?comentario=, lo mismo que el enlace del correo),
 * una solicitud abre su pagina, y una fecha de revision, el proceso. Sin referencia, siempre el proceso.
 */
export function enlaceDeNotificacion(notificacion: Notificacion): EnlaceDeNotificacion {
  const referencia = notificacion.referenciaId;
  if (referencia !== null && DE_SOLICITUD.includes(notificacion.tipo)) {
    return { ruta: ['/solicitudes', referencia], consulta: null };
  }
  if (referencia !== null && DE_COMENTARIO.includes(notificacion.tipo)) {
    return { ruta: ['/procesos', notificacion.procesoId], consulta: { comentario: referencia } };
  }
  return { ruta: ['/procesos', notificacion.procesoId], consulta: null };
}

/** Los avisos que la campana conserva en su lista. */
export const AVISOS_EN_LA_CAMPANA: number = 20;

/**
 * La lista de la campana con un aviso que llega en vivo: arriba, sin repetirlo si ya estaba (la lista y el flujo
 * pueden traer el mismo), y sin pasar del tope.
 */
export function conAvisoNuevo(
  lista: readonly Notificacion[],
  nueva: Notificacion,
  tope: number = AVISOS_EN_LA_CAMPANA,
): Notificacion[] {
  return [nueva, ...lista.filter((una) => una.id !== nueva.id)].slice(0, tope);
}

/** La lista con un aviso ya leido, sin tocar los demas. */
export function conAvisoLeido(lista: readonly Notificacion[], leida: Notificacion): Notificacion[] {
  return lista.map((una) => (una.id === leida.id ? leida : una));
}

/** La lista con todos leidos a la vez, con la hora de este navegador: la API no devuelve la suya. */
export function conTodosLeidos(lista: readonly Notificacion[], ahora: string): Notificacion[] {
  return lista.map((una) => (una.leidaEn ? una : { ...una, leidaEn: ahora }));
}
