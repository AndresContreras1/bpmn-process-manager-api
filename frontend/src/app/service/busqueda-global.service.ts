import { Injectable, signal } from '@angular/core';

/**
 * Si el dialogo de la busqueda global esta abierto. Lo abren el atajo y el boton del encabezado, y el dialogo llega
 * aparte la primera vez que se abre: quien nunca busca no lo descarga.
 */
@Injectable({ providedIn: 'root' })
export class BusquedaGlobalService {
  private readonly estado = signal(false);
  readonly abierta = this.estado.asReadonly();

  abrir(): void {
    this.estado.set(true);
  }

  cerrar(): void {
    this.estado.set(false);
  }
}
