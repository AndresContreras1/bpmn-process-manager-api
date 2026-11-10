import { DOCUMENT, Injectable, inject, signal } from '@angular/core';

export type Tema = 'light' | 'dark';

const CLAVE: string = 'bpm-tema';

/**
 * El tema claro u oscuro de la web, en el atributo data-bs-theme de la pagina, que es lo que Bootstrap lee. Sale de la
 * eleccion que la persona guardo en este navegador o, si no eligio, de la preferencia de su sistema.
 */
@Injectable({ providedIn: 'root' })
export class TemaService {
  private readonly documento: Document = inject(DOCUMENT);

  readonly tema = signal<Tema>(this.inicial());

  constructor() {
    this.aplicar(this.tema());
  }

  alternar(): void {
    const otro: Tema = this.tema() === 'dark' ? 'light' : 'dark';
    this.tema.set(otro);
    this.aplicar(otro);
    try {
      this.documento.defaultView?.localStorage.setItem(CLAVE, otro);
    } catch {
      // Sin almacenamiento (una ventana privada, por ejemplo) el tema dura lo que dure la pestana
    }
  }

  private aplicar(tema: Tema): void {
    this.documento.documentElement.setAttribute('data-bs-theme', tema);
  }

  private inicial(): Tema {
    try {
      const guardado: string | null | undefined = this.documento.defaultView?.localStorage.getItem(CLAVE);
      if (guardado === 'dark' || guardado === 'light') {
        return guardado;
      }
    } catch {
      // Igual que al guardar: sin almacenamiento se sigue la preferencia del sistema
    }
    return this.documento.defaultView?.matchMedia?.('(prefers-color-scheme: dark)').matches ? 'dark' : 'light';
  }
}
