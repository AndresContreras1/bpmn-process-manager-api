import { LOCALE_ID, signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';

import { PreferenciasService } from '../service/preferencias.service';
import { FechaPipe } from './fecha.pipe';

describe('FechaPipe', () => {
  const zona = signal('America/Bogota');

  function pipe(locale: string): FechaPipe {
    TestBed.resetTestingModule();
    TestBed.configureTestingModule({
      providers: [
        FechaPipe,
        { provide: LOCALE_ID, useValue: locale },
        { provide: PreferenciasService, useValue: { zonaHoraria: zona } },
      ],
    });
    return TestBed.inject(FechaPipe);
  }

  it('lee la hora de la API como UTC y la escribe en la zona de quien la mira', () => {
    zona.set('America/Bogota');
    // 15:30 en UTC son las 10:30 en Bogota
    expect(pipe('en-US').transform('2026-09-21T15:30:00', 'fechaHora')).toContain('10:30');
    zona.set('Australia/Sydney');
    // ... y la 1:30 del dia siguiente en Sidney, que en septiembre va diez horas adelante
    expect(pipe('en-US').transform('2026-09-21T15:30:00', 'fechaHora')).toMatch(/Sep 22, 2026.*1:30/);
  });

  it('un dia sin hora es ese dia en cualquier zona', () => {
    zona.set('Pacific/Honolulu');
    expect(pipe('en-US').transform('2026-09-21', 'fechaLarga')).toBe('September 21, 2026');
  });

  it('respeta una fecha que ya trae su zona', () => {
    zona.set('UTC');
    expect(pipe('en-US').transform('2026-09-21T15:30:00-05:00', 'fechaHora')).toContain('8:30');
  });

  it('escribe la fecha en el idioma de la compilacion', () => {
    zona.set('Europe/Paris');
    expect(pipe('fr-FR').transform('2026-09-21', 'fechaLarga')).toBe('21 septembre 2026');
    expect(pipe('es-CO').transform('2026-09-21', 'fechaLarga')).toBe('21 de septiembre de 2026');
  });

  it('nada no escribe nada, y algo que no es una fecha se deja como llego', () => {
    expect(pipe('en-US').transform(null)).toBe('');
    expect(pipe('en-US').transform('')).toBe('');
    expect(pipe('en-US').transform('ayer')).toBe('ayer');
  });
});
