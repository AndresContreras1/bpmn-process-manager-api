import { Injectable } from '@angular/core';

/** Lo que queda guardado en el navegador de un borrador sin confirmar. */
export interface CopiaLocal {
  procesoId: number;
  xml: string;
  huella: string | null;
  fecha: number;
}

const BASE = 'bpmn-process-manager';
const ALMACEN = 'copias';

/**
 * Una copia en IndexedDB de lo que todavia no se confirmo guardado, para recuperarlo si se cierra el navegador o se
 * corta la red. Si IndexedDB no esta, como en una ventana privada estricta, simplemente no hay copia.
 */
@Injectable({ providedIn: 'root' })
export class CopiaLocalService {
  private base: Promise<IDBDatabase | null> | null = null;

  async guardar(copia: CopiaLocal): Promise<void> {
    const base = await this.abrir();
    if (base) {
      await completar(base.transaction(ALMACEN, 'readwrite').objectStore(ALMACEN).put(copia));
    }
  }

  async leer(procesoId: number): Promise<CopiaLocal | null> {
    const base = await this.abrir();
    if (!base) {
      return null;
    }
    return (await completar(base.transaction(ALMACEN).objectStore(ALMACEN).get(procesoId))) ?? null;
  }

  async borrar(procesoId: number): Promise<void> {
    const base = await this.abrir();
    if (base) {
      await completar(base.transaction(ALMACEN, 'readwrite').objectStore(ALMACEN).delete(procesoId));
    }
  }

  private abrir(): Promise<IDBDatabase | null> {
    this.base ??= new Promise((resolver) => {
      if (typeof indexedDB === 'undefined') {
        resolver(null);
        return;
      }
      const peticion = indexedDB.open(BASE, 1);
      peticion.onupgradeneeded = () => peticion.result.createObjectStore(ALMACEN, { keyPath: 'procesoId' });
      peticion.onsuccess = () => resolver(peticion.result);
      peticion.onerror = () => resolver(null);
    });
    return this.base;
  }
}

function completar<T>(peticion: IDBRequest<T>): Promise<T> {
  return new Promise((resolver, rechazar) => {
    peticion.onsuccess = () => resolver(peticion.result);
    peticion.onerror = () => rechazar(peticion.error);
  });
}
