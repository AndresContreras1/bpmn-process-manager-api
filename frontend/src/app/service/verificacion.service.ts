import { Injectable, signal } from '@angular/core';
import { Observable, Subject, finalize, share, take } from 'rxjs';

/**
 * F14: lo que pide haber confirmado la identidad hace poco (quitar un factor, dar de baja la organizacion) responde 403
 * "Verificación requerida". Este servicio abre el dialogo que la pide, una sola vez aunque varias peticiones la
 * necesiten a la vez, y dice si la persona la dio o lo cancelo.
 */
@Injectable({ providedIn: 'root' })
export class VerificacionService {
  private readonly abierto = signal<boolean>(false);
  private respuesta: Subject<boolean> | null = null;
  private pedido$: Observable<boolean> | null = null;

  /** Si el dialogo esta abierto; lo lee el dialogo de la estructura. */
  readonly pidiendo = this.abierto.asReadonly();

  /** Abre el dialogo y emite una vez: true si la persona confirmo quien es, false si lo cancelo. */
  pedir(): Observable<boolean> {
    if (!this.pedido$) {
      this.respuesta = new Subject<boolean>();
      this.abierto.set(true);
      this.pedido$ = this.respuesta.pipe(
        take(1),
        finalize(() => {
          this.abierto.set(false);
          this.respuesta = null;
          this.pedido$ = null;
        }),
        share(),
      );
    }
    return this.pedido$;
  }

  /** La llama el dialogo cuando la API acepto la confirmacion. */
  confirmada(): void {
    this.respuesta?.next(true);
  }

  cancelada(): void {
    this.respuesta?.next(false);
  }
}
