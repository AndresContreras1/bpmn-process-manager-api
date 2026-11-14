import { describe, expect, it } from 'vitest';

import {
  Tecla,
  atajoDeBusqueda,
  esAtajoDeBusqueda,
  hayOtroDialogoAbierto,
  retenerFoco,
  siguienteOpcion,
} from './teclado';

function tecla(cambios: Partial<Tecla>): Tecla {
  return { key: 'k', ctrlKey: false, metaKey: false, altKey: false, shiftKey: false, ...cambios };
}

describe('esAtajoDeBusqueda', () => {
  it('Ctrl+K y Cmd+K abren la busqueda, en mayuscula tambien', () => {
    expect(esAtajoDeBusqueda(tecla({ ctrlKey: true }))).toBe(true);
    expect(esAtajoDeBusqueda(tecla({ metaKey: true }))).toBe(true);
    expect(esAtajoDeBusqueda(tecla({ ctrlKey: true, key: 'K' }))).toBe(true);
  });

  it('la K sola, otra letra o el atajo con Alt o Shift no son el atajo', () => {
    expect(esAtajoDeBusqueda(tecla({}))).toBe(false);
    expect(esAtajoDeBusqueda(tecla({ ctrlKey: true, key: 'j' }))).toBe(false);
    expect(esAtajoDeBusqueda(tecla({ ctrlKey: true, altKey: true }))).toBe(false);
    expect(esAtajoDeBusqueda(tecla({ metaKey: true, shiftKey: true }))).toBe(false);
  });
});

describe('siguienteOpcion', () => {
  it('la flecha abajo entra por la primera y avanza', () => {
    expect(siguienteOpcion(-1, 3, 'ArrowDown')).toBe(0);
    expect(siguienteOpcion(0, 3, 'ArrowDown')).toBe(1);
    expect(siguienteOpcion(1, 3, 'ArrowDown')).toBe(2);
  });

  it('abajo desde la ultima vuelve a la primera, y arriba desde la primera va a la ultima', () => {
    expect(siguienteOpcion(2, 3, 'ArrowDown')).toBe(0);
    expect(siguienteOpcion(0, 3, 'ArrowUp')).toBe(2);
    expect(siguienteOpcion(-1, 3, 'ArrowUp')).toBe(2);
    expect(siguienteOpcion(2, 3, 'ArrowUp')).toBe(1);
  });

  it('otra tecla la deja donde estaba, dentro de la lista', () => {
    expect(siguienteOpcion(1, 3, 'a')).toBe(1);
    expect(siguienteOpcion(5, 3, 'a')).toBe(2);
    expect(siguienteOpcion(-1, 3, 'a')).toBe(-1);
  });

  it('sin opciones no hay ninguna activa', () => {
    expect(siguienteOpcion(0, 0, 'ArrowDown')).toBe(-1);
    expect(siguienteOpcion(2, 0, 'ArrowUp')).toBe(-1);
  });
});

describe('retenerFoco', () => {
  function dialogo(): { contenedor: HTMLElement; primero: HTMLElement; ultimo: HTMLElement } {
    const contenedor = document.createElement('div');
    contenedor.innerHTML = '<input id="primero" /><button id="medio" disabled>-</button><button id="ultimo">x</button>';
    document.body.appendChild(contenedor);
    return {
      contenedor,
      primero: contenedor.querySelector('#primero') as HTMLElement,
      ultimo: contenedor.querySelector('#ultimo') as HTMLElement,
    };
  }

  it('Tab en el ultimo control vuelve al primero', () => {
    const { contenedor, primero, ultimo } = dialogo();
    ultimo.focus();

    expect(retenerFoco({ key: 'Tab', shiftKey: false }, contenedor)).toBe(true);
    expect(document.activeElement).toBe(primero);
    contenedor.remove();
  });

  it('Shift+Tab en el primero va al ultimo', () => {
    const { contenedor, primero, ultimo } = dialogo();
    primero.focus();

    expect(retenerFoco({ key: 'Tab', shiftKey: true }, contenedor)).toBe(true);
    expect(document.activeElement).toBe(ultimo);
    contenedor.remove();
  });

  it('en medio, u otra tecla, deja hacer al navegador', () => {
    const { contenedor, primero } = dialogo();
    primero.focus();

    expect(retenerFoco({ key: 'Tab', shiftKey: false }, contenedor)).toBe(false);
    expect(retenerFoco({ key: 'Enter', shiftKey: false }, contenedor)).toBe(false);
    expect(document.activeElement).toBe(primero);
    contenedor.remove();
  });

  it('un dialogo sin controles no hace nada', () => {
    const vacio = document.createElement('div');
    expect(retenerFoco({ key: 'Tab', shiftKey: false }, vacio)).toBe(false);
  });
});

describe('atajoDeBusqueda', () => {
  it('en un Mac, un iPhone o un iPad dice Cmd, y en lo demas Ctrl', () => {
    expect(atajoDeBusqueda('Mozilla/5.0 (Macintosh; Intel Mac OS X 14_5) AppleWebKit/605.1.15')).toBe('⌘ K');
    expect(atajoDeBusqueda('Mozilla/5.0 (iPad; CPU OS 17_5 like Mac OS X)')).toBe('⌘ K');
    expect(atajoDeBusqueda('Mozilla/5.0 (Windows NT 10.0; Win64; x64) Chrome/131.0')).toBe('Ctrl K');
    expect(atajoDeBusqueda('Mozilla/5.0 (X11; Linux x86_64)')).toBe('Ctrl K');
  });
});

describe('hayOtroDialogoAbierto', () => {
  function raiz(id: string, modal: string | null): HTMLElement {
    const div = document.createElement('div');
    const dialogo = document.createElement('div');
    dialogo.id = id;
    if (modal !== null) {
      dialogo.setAttribute('aria-modal', modal);
    }
    div.appendChild(dialogo);
    return div;
  }

  it('un dialogo modal abierto que no es el propio cuenta', () => {
    expect(hayOtroDialogoAbierto(raiz('mover-dialogo', 'true'), 'busqueda-dialogo')).toBe(true);
  });

  it('el propio, uno que no es modal o ninguno no cuentan', () => {
    expect(hayOtroDialogoAbierto(raiz('busqueda-dialogo', 'true'), 'busqueda-dialogo')).toBe(false);
    expect(hayOtroDialogoAbierto(raiz('aviso', 'false'), 'busqueda-dialogo')).toBe(false);
    expect(hayOtroDialogoAbierto(raiz('nada', null), 'busqueda-dialogo')).toBe(false);
  });
});
