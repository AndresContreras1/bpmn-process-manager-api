import { TestBed } from '@angular/core/testing';

import { TemaService } from './tema.service';

/** jsdom no trae matchMedia: cada prueba dice que prefiere el sistema. */
function preferirOscuro(oscuro: boolean): void {
  Object.defineProperty(window, 'matchMedia', {
    configurable: true,
    value: (consulta: string) => ({ matches: oscuro && consulta.includes('dark') }) as MediaQueryList,
  });
}

describe('TemaService', () => {
  beforeEach(() => {
    localStorage.clear();
    document.documentElement.removeAttribute('data-bs-theme');
  });

  it('sin eleccion guardada sigue la preferencia del sistema y la pone en data-bs-theme', () => {
    preferirOscuro(true);
    const tema = TestBed.inject(TemaService);
    expect(tema.tema()).toBe('dark');
    expect(document.documentElement.getAttribute('data-bs-theme')).toBe('dark');
  });

  it('alternar cambia el tema de la pagina y lo recuerda en este navegador', () => {
    preferirOscuro(false);
    const tema = TestBed.inject(TemaService);
    tema.alternar();
    expect(document.documentElement.getAttribute('data-bs-theme')).toBe('dark');
    expect(localStorage.getItem('bpm-tema')).toBe('dark');
  });

  it('la eleccion guardada gana a la preferencia del sistema', () => {
    preferirOscuro(true);
    localStorage.setItem('bpm-tema', 'light');
    expect(TestBed.inject(TemaService).tema()).toBe('light');
  });
});
