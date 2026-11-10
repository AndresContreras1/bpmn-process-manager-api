import { Injectable, signal } from '@angular/core';

export type TipoDeAviso = 'exito' | 'error' | 'info';

export interface Aviso {
  id: number;
  tipo: TipoDeAviso;
  texto: string;
}

/** Lo que dura un aviso a la vista antes de irse solo. Uno de error se queda hasta que la persona lo cierra. */
export const DURACION_DE_UN_AVISO: number = 6000;

/**
 * Los avisos pasajeros de la web: algo salio bien, algo fallo. Viven en una region aria-live de la estructura, asi que un
 * lector de pantalla los lee sin que el foco se mueva.
 */
@Injectable({ providedIn: 'root' })
export class AvisosService {
  private siguiente: number = 1;

  readonly avisos = signal<Aviso[]>([]);

  exito(texto: string): void {
    this.agregar('exito', texto);
  }

  error(texto: string): void {
    this.agregar('error', texto);
  }

  info(texto: string): void {
    this.agregar('info', texto);
  }

  cerrar(id: number): void {
    this.avisos.update((avisos: Aviso[]) => avisos.filter((aviso: Aviso) => aviso.id !== id));
  }

  private agregar(tipo: TipoDeAviso, texto: string): void {
    const aviso: Aviso = { id: this.siguiente++, tipo, texto };
    this.avisos.update((avisos: Aviso[]) => [...avisos, aviso]);
    if (tipo !== 'error') {
      setTimeout(() => this.cerrar(aviso.id), DURACION_DE_UN_AVISO);
    }
  }
}
