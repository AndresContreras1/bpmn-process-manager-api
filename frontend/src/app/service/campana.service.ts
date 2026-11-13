import { Injectable, inject, signal } from '@angular/core';
import { Subscription } from 'rxjs';

import {
  AVISOS_EN_LA_CAMPANA,
  EventoDeNotificaciones,
  Notificacion,
  conAvisoLeido,
  conAvisoNuevo,
  conTodosLeidos,
  textoDeNotificacion,
} from '../models/notificacion.model';
import { NotificacionService } from './notificacion.service';

/**
 * PR F21: lo que muestra la campana del encabezado, para quien entro: cuantos avisos faltan por leer, los ultimos y
 * si el flujo en vivo esta abierto. El conteo lo manda la API (al abrir el flujo y cada vez que cambia), asi que la
 * web no lo calcula: solo lo baja ella misma al marcar uno leido cuando el flujo esta cerrado, que si no lo dice el.
 */
@Injectable({ providedIn: 'root' })
export class CampanaService {
  private readonly notificaciones: NotificacionService = inject(NotificacionService);
  private flujo: Subscription | null = null;

  readonly noLeidas = signal<number>(0);
  readonly avisos = signal<Notificacion[]>([]);
  readonly conectada = signal<boolean>(false);
  readonly cargando = signal<boolean>(false);
  readonly error = signal<boolean>(false);
  /** El ultimo aviso que llego en vivo, dicho en palabras para la region aria-live. */
  readonly anuncio = signal<string>('');

  /** Abre el flujo; abrirlo dos veces no abre dos conexiones (la API admite cinco por persona). */
  iniciar(): void {
    if (this.flujo) {
      return;
    }
    this.flujo = this.notificaciones.flujo().subscribe((evento) => this.alEvento(evento));
  }

  /** Cierra el flujo y olvida lo de esta persona: la siguiente sesion puede ser de otra. */
  detener(): void {
    this.flujo?.unsubscribe();
    this.flujo = null;
    this.conectada.set(false);
    this.noLeidas.set(0);
    this.avisos.set([]);
    this.anuncio.set('');
  }

  /** Los ultimos avisos, leidos o no: lo que se ve al abrir la campana. */
  cargar(): void {
    this.cargando.set(true);
    this.error.set(false);
    this.notificaciones.listar(false, 0, AVISOS_EN_LA_CAMPANA).subscribe({
      next: (pagina) => {
        this.avisos.set(pagina.content);
        this.cargando.set(false);
      },
      error: () => {
        this.error.set(true);
        this.cargando.set(false);
      },
    });
  }

  /** Marca uno leido; uno que ya lo estaba no se vuelve a mandar. */
  marcarLeida(aviso: Notificacion): void {
    if (aviso.leidaEn) {
      return;
    }
    this.notificaciones.marcarLeida(aviso.id).subscribe({
      next: (leida) => {
        this.avisos.update((lista) => conAvisoLeido(lista, leida));
        // Con el flujo abierto el conteo nuevo llega por el, a veces antes que esta respuesta: restar otra vez lo
        // dejaria uno por debajo
        if (!this.conectada()) {
          this.noLeidas.update((cuantas) => Math.max(0, cuantas - 1));
        }
      },
      error: () => this.error.set(true),
    });
  }

  marcarTodas(): void {
    this.notificaciones.marcarTodas().subscribe({
      next: (conteo) => {
        this.noLeidas.set(conteo.noLeidas);
        this.avisos.update((lista) => conTodosLeidos(lista, new Date().toISOString()));
      },
      error: () => this.error.set(true),
    });
  }

  private alEvento(evento: EventoDeNotificaciones): void {
    switch (evento.tipo) {
      case 'conectado':
        this.conectada.set(true);
        break;
      case 'desconectado':
        this.conectada.set(false);
        break;
      case 'conteo':
        this.noLeidas.set(evento.datos.noLeidas);
        break;
      case 'notificacion':
        this.avisos.update((lista) => conAvisoNuevo(lista, evento.datos));
        this.anuncio.set($localize`New notification: ${textoDeNotificacion(evento.datos)}:aviso:`);
        break;
    }
  }
}
