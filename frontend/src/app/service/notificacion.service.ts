import { HttpClient } from '@angular/common/http';
import { Injectable, InjectionToken, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { environment } from '../../environments/environment';
import { ConexionEnVivo, FuenteDeEventos } from '../bpmn/conexion-en-vivo';
import {
  ConteoDeNotificaciones,
  EnvioPorCorreo,
  EventoDeNotificaciones,
  Notificacion,
  PreferenciaDeNotificacion,
  TipoDeNotificacion,
} from '../models/notificacion.model';
import { PageResponse } from '../models/page-response.model';

/** Como se abre un flujo de eventos del servidor: en el navegador, un EventSource con las cookies de la sesion. */
export const ABRIR_FUENTE_DE_EVENTOS = new InjectionToken<(url: string) => FuenteDeEventos>('abrir-fuente-de-eventos', {
  providedIn: 'root',
  factory: () => (url: string) => new EventSource(url, { withCredentials: true }) as FuenteDeEventos,
});

/** Los tipos de evento del flujo de avisos: el conteo al abrir y al cambiar, y cada aviso nuevo. */
const EVENTOS_DE_AVISOS: readonly string[] = ['conteo', 'notificacion'];

/**
 * PR F21: los avisos de quien entro (PR 63 y PR 64). Solo los suyos: el de otra persona responde 404. Llegan en una
 * lista paginada, y en vivo por un flujo SSE que dice el conteo al abrirse, de modo que una reconexion se pone al dia
 * sola.
 */
@Injectable({ providedIn: 'root' })
export class NotificacionService {
  private readonly http: HttpClient = inject(HttpClient);
  private readonly abrirFuente = inject(ABRIR_FUENTE_DE_EVENTOS);
  private readonly url: string = `${environment.apiUrl}/api/v1/notificaciones`;

  /** Una pagina, la mas nueva primero; con noLeidas, solo las que faltan por leer. */
  listar(noLeidas: boolean, pagina: number = 0, tamano: number = 20): Observable<PageResponse<Notificacion>> {
    return this.http.get<PageResponse<Notificacion>>(this.url, { params: { noLeidas, pagina, tamano } });
  }

  conteo(): Observable<ConteoDeNotificaciones> {
    return this.http.get<ConteoDeNotificaciones>(`${this.url}/conteo`);
  }

  marcarLeida(id: number): Observable<Notificacion> {
    return this.http.post<Notificacion>(`${this.url}/${id}/leida`, null);
  }

  /** Todas leidas de una vez; responde el conteo, que queda en cero. */
  marcarTodas(): Observable<ConteoDeNotificaciones> {
    return this.http.post<ConteoDeNotificaciones>(`${this.url}/leidas`, null);
  }

  /**
   * El flujo en vivo mientras alguien este suscrito. Se repone solo tras un corte o un 429, esperando el doble cada
   * vez y con algo de azar, y al soltarlo se cierra.
   */
  flujo(): Observable<EventoDeNotificaciones> {
    return new Observable<EventoDeNotificaciones>((suscriptor) => {
      const conexion = new ConexionEnVivo<EventoDeNotificaciones>(
        {
          abrir: () => this.abrirFuente(`${this.url}/flujo`),
          esperar: (accion, ms) => setTimeout(accion, ms),
          cancelar: (marca) => clearTimeout(marca as ReturnType<typeof setTimeout>),
          azar: () => Math.random(),
        },
        (evento) => suscriptor.next(evento),
        EVENTOS_DE_AVISOS,
      );
      conexion.iniciar();
      return () => conexion.detener();
    });
  }

  preferencias(): Observable<PreferenciaDeNotificacion[]> {
    return this.http.get<PreferenciaDeNotificacion[]>(`${this.url}/preferencias`);
  }

  /** Solo los tipos que se mandan cambian; responde cada tipo como queda. */
  guardarPreferencias(
    preferencias: { tipo: TipoDeNotificacion; envio: EnvioPorCorreo }[],
  ): Observable<PreferenciaDeNotificacion[]> {
    return this.http.put<PreferenciaDeNotificacion[]>(`${this.url}/preferencias`, { preferencias });
  }
}
