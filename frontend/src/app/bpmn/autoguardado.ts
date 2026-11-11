import { HttpErrorResponse } from '@angular/common/http';
import { BehaviorSubject, Observable, firstValueFrom } from 'rxjs';

import { ModeloGuardado } from '../models/modelo.model';

export type EstadoGuardado = 'guardado' | 'pendiente' | 'guardando' | 'sin-conexion' | 'conflicto' | 'rechazado';

/** Lo que el autoguardado necesita de afuera: asi se prueba sin navegador ni red. */
export interface PuertosDelGuardado {
  leerXml(): Promise<string>;
  enviar(xml: string, huella: string | null, clave: string): Observable<ModeloGuardado>;
  guardarCopia(xml: string, huella: string | null): void;
  borrarCopia(): void;
  nuevaClave(): string;
  guardado?(respuesta: ModeloGuardado): void;
}

/** Cuanto se espera tras un cambio, y tras un corte de la red. */
export const ESPERA_MS = 2000;
export const REINTENTO_MS = 5000;

/**
 * El autoguardado: dos segundos despues del ultimo cambio manda el modelo entero. Uno a la vez: lo que cambie mientras
 * tanto se manda despues. Un corte de la red se reintenta con la misma clave, para que no se aplique dos veces; un
 * conflicto o un archivo rechazado paran hasta que alguien decida.
 */
export class Autoguardado {
  private readonly estadoActual = new BehaviorSubject<EstadoGuardado>('guardado');
  readonly estado$ = this.estadoActual.asObservable();
  private temporizador: ReturnType<typeof setTimeout> | null = null;
  private enCurso = false;
  private otroCambio = false;
  private clavePendiente: string | null = null;
  ultimoError: string | null = null;

  constructor(
    private readonly puertos: PuertosDelGuardado,
    private huella: string | null,
  ) {}

  get estado(): EstadoGuardado {
    return this.estadoActual.value;
  }

  get huellaActual(): string | null {
    return this.huella;
  }

  /** Se cambio algo: se agenda el guardado y se deja una copia local por si se cierra todo. */
  async marcarCambio(): Promise<void> {
    if (this.estado === 'conflicto' || this.estado === 'rechazado') {
      return;
    }
    this.clavePendiente = null;
    if (this.enCurso) {
      this.otroCambio = true;
    } else {
      this.estadoActual.next('pendiente');
      this.agendar(ESPERA_MS);
    }
    this.puertos.guardarCopia(await this.puertos.leerXml(), this.huella);
  }

  /** Sin esperar, como al cerrar. Devuelve cuando termina. */
  async guardarAhora(): Promise<void> {
    this.cancelar();
    await this.guardar();
  }

  /** Tras recargar lo del servidor: se sigue desde esa huella. */
  reiniciar(huella: string | null): void {
    this.cancelar();
    this.huella = huella;
    this.otroCambio = false;
    this.clavePendiente = null;
    this.ultimoError = null;
    this.puertos.borrarCopia();
    this.estadoActual.next('guardado');
  }

  detener(): void {
    this.cancelar();
  }

  private agendar(espera: number): void {
    this.cancelar();
    this.temporizador = setTimeout(() => void this.guardar(), espera);
  }

  private cancelar(): void {
    if (this.temporizador !== null) {
      clearTimeout(this.temporizador);
      this.temporizador = null;
    }
  }

  private async guardar(): Promise<void> {
    if (this.enCurso) {
      this.otroCambio = true;
      return;
    }
    this.enCurso = true;
    this.estadoActual.next('guardando');
    try {
      const xml = await this.puertos.leerXml();
      this.clavePendiente ??= this.puertos.nuevaClave();
      const respuesta = await firstValueFrom(this.puertos.enviar(xml, this.huella, this.clavePendiente));
      this.huella = respuesta.huella;
      this.clavePendiente = null;
      this.enCurso = false;
      this.puertos.guardado?.(respuesta);
      if (this.otroCambio) {
        this.otroCambio = false;
        this.estadoActual.next('pendiente');
        this.agendar(ESPERA_MS);
      } else {
        this.puertos.borrarCopia();
        this.estadoActual.next('guardado');
      }
    } catch (error) {
      this.enCurso = false;
      this.alFallar(error);
    }
  }

  private alFallar(error: unknown): void {
    const estado = error instanceof HttpErrorResponse ? error.status : 0;
    if (estado === 412) {
      this.estadoActual.next('conflicto');
    } else if (estado >= 400 && estado < 500 && estado !== 408 && estado !== 429) {
      this.ultimoError = error instanceof HttpErrorResponse ? (error.error?.detail ?? error.message) : String(error);
      this.estadoActual.next('rechazado');
    } else {
      this.estadoActual.next('sin-conexion');
      this.agendar(REINTENTO_MS);
    }
  }
}
