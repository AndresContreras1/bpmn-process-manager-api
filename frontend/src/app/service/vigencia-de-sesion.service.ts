import { Injectable, signal } from '@angular/core';

/**
 * F13: hasta cuando vale la sesion, segun lo ultimo que dijo la API. Dos fechas, en milisegundos desde 1970: cuando
 * se cierra si nadie la renueva (la inactividad de la organizacion, que cuenta desde la ultima renovacion) y cuando
 * termina aunque se use (su duracion desde el login). El aviso de inactividad las lee.
 */
@Injectable({ providedIn: 'root' })
export class VigenciaDeSesionService {
  private readonly inactiva = signal<number | null>(null);
  private readonly termina = signal<number | null>(null);

  readonly inactivaEn = this.inactiva.asReadonly();
  readonly terminaEn = this.termina.asReadonly();

  /**
   * Lo que acaba de decir la API, contado desde ahora. Una inactividad nula (una verificacion reciente no renueva
   * nada) deja la que habia.
   */
  registrar(inactividadSegundos: number | null | undefined, restanteSegundos: number | null | undefined): void {
    const ahora: number = Date.now();
    if (inactividadSegundos !== null && inactividadSegundos !== undefined) {
      this.inactiva.set(ahora + inactividadSegundos * 1000);
    }
    if (restanteSegundos !== null && restanteSegundos !== undefined) {
      this.termina.set(ahora + restanteSegundos * 1000);
    }
  }

  olvidar(): void {
    this.inactiva.set(null);
    this.termina.set(null);
  }
}
