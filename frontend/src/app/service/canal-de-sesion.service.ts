import { Injectable, OnDestroy } from '@angular/core';
import { Observable, Subject } from 'rxjs';

/** Lo que una pestaña le cuenta a las demas de la misma web. */
export type MensajeDeSesion =
  /** Alguien entro, o entro otra persona: las demas pestañas vuelven a preguntar quien es. */
  | { tipo: 'entrada' }
  /** Se salio, o la sesion dejo de valer: todas vuelven al login. */
  | { tipo: 'salida' }
  /** La sesion se renovo: las cookies ya son nuevas, y lo que le queda empieza a contar de nuevo. */
  | { tipo: 'renovada'; inactividadSegundos: number | null; restanteSegundos: number | null };

const NOMBRE = 'bpm-sesion';

/**
 * F13: las pestañas de la web hablan entre si con BroadcastChannel. Las cookies de la sesion son las mismas para
 * todas, pero cada una tiene su copia de quien entro: sin esto, salir en una dejaria las otras abiertas con una sesion
 * que ya no existe. Un navegador sin BroadcastChannel sigue funcionando, cada pestaña por su cuenta.
 */
@Injectable({ providedIn: 'root' })
export class CanalDeSesionService implements OnDestroy {
  private readonly recibidos = new Subject<MensajeDeSesion>();
  private readonly canal: BroadcastChannel | null =
    typeof BroadcastChannel === 'function' ? new BroadcastChannel(NOMBRE) : null;

  /** Lo que mandan las otras pestañas; lo que manda esta no vuelve. */
  readonly mensajes$: Observable<MensajeDeSesion> = this.recibidos.asObservable();

  constructor() {
    this.canal?.addEventListener('message', (evento: MessageEvent<MensajeDeSesion>) =>
      this.recibidos.next(evento.data),
    );
  }

  publicar(mensaje: MensajeDeSesion): void {
    this.canal?.postMessage(mensaje);
  }

  ngOnDestroy(): void {
    this.canal?.close();
  }
}
