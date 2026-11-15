import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { EMPTY, Observable, expand, reduce } from 'rxjs';

import { environment } from '../../environments/environment';
import {
  Comentario,
  EdicionDeComentario,
  Hilo,
  NuevaRespuesta,
  NuevoComentario,
  Persona,
} from '../models/comentario.model';
import { PageResponse } from '../models/page-response.model';

/** El maximo de hilos por pagina que acepta la API. */
const HILOS_POR_PAGINA = 50;

/**
 * PR F21: los hilos de comentarios de un proceso (PR 63). Cualquier rol comenta, tambien quien solo lee; editar es de
 * su autor y borrar de su autor o de un administrador, y la API responde 403 o 409 a lo demas.
 */
@Injectable({ providedIn: 'root' })
export class ComentarioService {
  private readonly http: HttpClient = inject(HttpClient);
  private readonly url: string = `${environment.apiUrl}/api/v1`;

  /**
   * Cada hilo del proceso, de todas las paginas, de cincuenta en cincuenta: el lienzo cuenta los de cada elemento y
   * el panel muestra los del elegido sin volver a preguntar. Con una version, solo los de esa version publicada; sin
   * ella vienen tambien los del borrador, que son los de version vacia.
   */
  hilos(procesoId: number, version: number | null = null): Observable<Hilo[]> {
    const leer = (pagina: number): Observable<PageResponse<Hilo>> => {
      const params: Record<string, number> = { pagina, tamano: HILOS_POR_PAGINA };
      if (version !== null) {
        params['version'] = version;
      }
      return this.http.get<PageResponse<Hilo>>(`${this.url}/procesos/${procesoId}/comentarios`, { params });
    };
    return leer(0).pipe(
      expand((leida: PageResponse<Hilo>) => (leida.page + 1 < leida.totalPages ? leer(leida.page + 1) : EMPTY)),
      reduce((todos: Hilo[], leida: PageResponse<Hilo>) => [...todos, ...leida.content], []),
    );
  }

  comentar(procesoId: number, nuevo: NuevoComentario): Observable<Comentario> {
    return this.http.post<Comentario>(`${this.url}/procesos/${procesoId}/comentarios`, nuevo);
  }

  /** Contestar a una respuesta contesta en su hilo. */
  responder(comentarioId: number, respuesta: NuevaRespuesta): Observable<Comentario> {
    return this.http.post<Comentario>(`${this.url}/comentarios/${comentarioId}/respuestas`, respuesta);
  }

  editar(comentarioId: number, edicion: EdicionDeComentario): Observable<Comentario> {
    return this.http.put<Comentario>(`${this.url}/comentarios/${comentarioId}`, edicion);
  }

  resolver(comentarioId: number): Observable<Comentario> {
    return this.http.post<Comentario>(`${this.url}/comentarios/${comentarioId}/resolver`, null);
  }

  reabrir(comentarioId: number): Observable<Comentario> {
    return this.http.post<Comentario>(`${this.url}/comentarios/${comentarioId}/reabrir`, null);
  }

  eliminar(comentarioId: number): Observable<void> {
    return this.http.delete<void>(`${this.url}/comentarios/${comentarioId}`);
  }

  /** A quien se puede mencionar en el proceso: por nombre, hasta veinte. Un texto en blanco no se manda. */
  mencionables(procesoId: number, texto: string): Observable<Persona[]> {
    const q = texto.trim();
    return this.http.get<Persona[]>(`${this.url}/procesos/${procesoId}/mencionables`, {
      params: q ? { q } : {},
    });
  }
}
