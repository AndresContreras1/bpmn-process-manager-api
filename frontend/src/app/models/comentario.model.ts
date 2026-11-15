/** PR 63: los comentarios en hilos sobre un proceso, uno de sus elementos o una version publicada. */

/** Una persona de la tienda, como sale junto a un comentario o en el selector de menciones (PersonaResponse). */
export interface Persona {
  id: number;
  nombre: string;
}

export type EstadoHilo = 'ABIERTO' | 'RESUELTO';

/** Un comentario (ComentarioResponse). La web nunca convierte el Markdown: muestra el html que limpio la API. */
export interface Comentario {
  id: number;
  procesoId: number;
  /** El primer comentario de su hilo; null si es el primero. */
  padreId: number | null;
  bpmnId: string | null;
  versionDelProceso: number | null;
  autor: Persona | null;
  markdown: string;
  html: string;
  menciones: Persona[];
  /** Solo en el primer comentario de un hilo. */
  estado: EstadoHilo | null;
  resueltoPor: Persona | null;
  resueltoEn: string | null;
  editadoEn: string | null;
  eliminado: boolean;
  fechaCreacion: string;
  version: number;
}

/** Un hilo: su primer comentario y sus respuestas, de la mas vieja a la mas nueva. */
export interface Hilo {
  comentario: Comentario;
  respuestas: Comentario[];
}

/** Cuerpo de POST /procesos/{id}/comentarios. */
export interface NuevoComentario {
  markdown: string;
  bpmnId: string | null;
  versionDelProceso: number | null;
  menciones: number[];
}

/** Cuerpo de POST /comentarios/{id}/respuestas. */
export interface NuevaRespuesta {
  markdown: string;
  menciones: number[];
}

/** Cuerpo de PUT /comentarios/{id}: el texto nuevo con la version leida. */
export interface EdicionDeComentario extends NuevaRespuesta {
  version: number;
}

/** Los topes de la API, dichos antes de enviar. */
export const LARGO_MAXIMO_COMENTARIO: number = 10_000;
export const MENCIONES_MAXIMAS: number = 20;

/**
 * Los hilos de un elemento (o del proceso entero, con null) en lo que se esta mirando: el borrador, con version null,
 * o una version publicada. Los abiertos van primero; dentro de cada grupo, el orden de la API, el mas nuevo arriba.
 */
export function hilosDe(hilos: readonly Hilo[], bpmnId: string | null, version: number | null): Hilo[] {
  const delLugar = hilos.filter(
    (hilo) => hilo.comentario.bpmnId === bpmnId && hilo.comentario.versionDelProceso === version,
  );
  return [
    ...delLugar.filter((hilo) => hilo.comentario.estado !== 'RESUELTO'),
    ...delLugar.filter((hilo) => hilo.comentario.estado === 'RESUELTO'),
  ];
}

/**
 * Cuantos hilos abiertos tiene cada elemento en lo que se esta mirando: el numero que el lienzo pone sobre el. Lo
 * resuelto ya no pide atencion, y lo del proceso entero no tiene elemento donde ir.
 */
export function contadoresDeHilos(hilos: readonly Hilo[], version: number | null): Record<string, number> {
  const contadores: Record<string, number> = {};
  for (const { comentario } of hilos) {
    if (comentario.bpmnId && comentario.versionDelProceso === version && comentario.estado === 'ABIERTO') {
      contadores[comentario.bpmnId] = (contadores[comentario.bpmnId] ?? 0) + 1;
    }
  }
  return contadores;
}

/** El hilo al que pertenece un comentario, sea el primero o una respuesta: a donde lleva el enlace de un aviso. */
export function hiloDelComentario(hilos: readonly Hilo[], comentarioId: number): Hilo | null {
  return (
    hilos.find(
      (hilo) => hilo.comentario.id === comentarioId || hilo.respuestas.some((una) => una.id === comentarioId),
    ) ?? null
  );
}

/** Solo su autor edita un comentario, y uno borrado ya no tiene texto que editar. */
export function puedeEditarComentario(comentario: Comentario, usuarioId: number | null): boolean {
  return !comentario.eliminado && usuarioId !== null && comentario.autor?.id === usuarioId;
}

/** Lo borra su autor o un administrador; borrado una vez, no hay nada mas que borrar. */
export function puedeEliminarComentario(
  comentario: Comentario,
  usuarioId: number | null,
  esAdministrador: boolean,
): boolean {
  return !comentario.eliminado && (esAdministrador || (usuarioId !== null && comentario.autor?.id === usuarioId));
}
