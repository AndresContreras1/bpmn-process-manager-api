/** Lo que hace falta de un evento de teclado para decidir; asi las reglas se prueban sin un navegador. */
export interface Tecla {
  key: string;
  ctrlKey: boolean;
  metaKey: boolean;
  altKey: boolean;
  shiftKey: boolean;
}

/**
 * Ctrl+K, o Cmd+K en un Mac, abre la busqueda global. Con Alt o Shift es otro atajo, del navegador o de quien lo
 * tenga, y no se toca.
 */
export function esAtajoDeBusqueda(tecla: Tecla): boolean {
  return (tecla.ctrlKey || tecla.metaKey) && !tecla.altKey && !tecla.shiftKey && tecla.key.toLowerCase() === 'k';
}

/** Como se escribe el atajo para quien lo lee: Cmd en un Mac, un iPhone o un iPad, y Ctrl en lo demas. */
export function atajoDeBusqueda(agente: string): string {
  return /Mac|iPhone|iPad/.test(agente) ? '⌘ K' : 'Ctrl K';
}

/**
 * Si hay abierto un dialogo modal que no es el de ese id: Ctrl+K no abre la busqueda encima, que dejaria dos dialogos
 * modales a la vez, cada uno reteniendo el foco.
 */
export function hayOtroDialogoAbierto(raiz: ParentNode, propio: string): boolean {
  return Array.from(raiz.querySelectorAll('[aria-modal="true"]')).some((dialogo: Element) => dialogo.id !== propio);
}

/**
 * La opcion activa despues de una flecha, en una lista de `total` opciones: baja y sube dando la vuelta, y la primera
 * flecha entra por la punta que toca. Cualquier otra tecla la deja donde estaba; sin opciones no hay ninguna activa.
 */
export function siguienteOpcion(actual: number, total: number, tecla: string): number {
  if (total <= 0) {
    return -1;
  }
  if (tecla === 'ArrowDown') {
    return actual < 0 || actual >= total - 1 ? 0 : actual + 1;
  }
  if (tecla === 'ArrowUp') {
    return actual <= 0 ? total - 1 : actual - 1;
  }
  return Math.min(actual, total - 1);
}

const ENFOCABLES =
  'a[href], button:not([disabled]), input:not([disabled]), select:not([disabled]), textarea:not([disabled]), ' +
  '[tabindex]:not([tabindex="-1"])';

/**
 * Tab y Shift+Tab no se salen de un dialogo modal: del ultimo control se vuelve al primero y al reves. Devuelve si
 * movio el foco, para que quien llama impida que el navegador lo mueva otra vez.
 */
export function retenerFoco(evento: Pick<KeyboardEvent, 'key' | 'shiftKey'>, dialogo: HTMLElement): boolean {
  if (evento.key !== 'Tab') {
    return false;
  }
  const controles = Array.from(dialogo.querySelectorAll<HTMLElement>(ENFOCABLES));
  if (controles.length === 0) {
    return false;
  }
  const primero = controles[0];
  const ultimo = controles[controles.length - 1];
  const activo = dialogo.ownerDocument.activeElement;
  if (evento.shiftKey && activo === primero) {
    ultimo.focus();
    return true;
  }
  if (!evento.shiftKey && activo === ultimo) {
    primero.focus();
    return true;
  }
  return false;
}
