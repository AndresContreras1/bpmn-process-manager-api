import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { of, throwError } from 'rxjs';

import { Preferencias } from '../../models/preferencias.model';
import { AuthService } from '../../service/auth.service';
import { AvisosService } from '../../service/avisos.service';
import { BusquedaGlobalService } from '../../service/busqueda-global.service';
import { IdiomaService } from '../../service/idioma.service';
import { PreferenciasService } from '../../service/preferencias.service';
import { EstructuraComponent } from './estructura.component';

describe('EstructuraComponent: cambiar el idioma', () => {
  const preferencias: Preferencias = {
    idioma: 'es-CO',
    zonaHoraria: 'America/Bogota',
    idiomaPropio: null,
    zonaHorariaPropia: 'America/Bogota',
    idiomaDeLaOrganizacion: 'es-CO',
    zonaHorariaDeLaOrganizacion: 'America/Bogota',
  };
  const idiomas = { actual: 'es-CO', ir: vi.fn() };
  const guardar = vi.fn();
  let avisos: AvisosService;
  let estructura: EstructuraComponent;

  beforeEach(() => {
    vi.clearAllMocks();
    TestBed.configureTestingModule({
      providers: [
        provideRouter([]),
        { provide: AuthService, useValue: { usuario$: of(null), usuarioActual: () => ({ id: 1 }) } },
        { provide: IdiomaService, useValue: idiomas },
        { provide: PreferenciasService, useValue: { actuales: () => preferencias, cambiarLasPropias: guardar } },
      ],
    });
    avisos = TestBed.inject(AvisosService);
    estructura = TestBed.runInInjectionContext(() => new EstructuraComponent());
  });

  it('guarda el idioma con la zona propia y abre la web en el', () => {
    guardar.mockReturnValue(of({ ...preferencias, idioma: 'en-US', idiomaPropio: 'en-US' }));

    estructura.cambiarIdioma('en-US');

    expect(guardar).toHaveBeenCalledWith({ idioma: 'en-US', zonaHoraria: 'America/Bogota' });
    expect(idiomas.ir).toHaveBeenCalledWith('en-US');
    expect(avisos.avisos()).toEqual([]);
  });

  it('si la API no lo guarda, se queda en el idioma que tenia y lo dice', () => {
    guardar.mockReturnValue(throwError(() => new Error('403')));

    estructura.cambiarIdioma('en-US');

    expect(idiomas.ir).not.toHaveBeenCalled();
    expect(avisos.avisos()).toEqual([
      expect.objectContaining({ tipo: 'error', texto: expect.stringContaining('could not be saved') }),
    ]);
  });
});

describe('EstructuraComponent: Ctrl+K', () => {
  let usuario: { id: number } | null;
  let busqueda: BusquedaGlobalService;
  let estructura: EstructuraComponent;

  beforeEach(() => {
    usuario = { id: 1 };
    TestBed.configureTestingModule({
      providers: [
        provideRouter([]),
        { provide: AuthService, useValue: { usuario$: of(null), usuarioActual: () => usuario } },
      ],
    });
    busqueda = TestBed.inject(BusquedaGlobalService);
    estructura = TestBed.runInInjectionContext(() => new EstructuraComponent());
  });

  function pulsar(cambios: Partial<KeyboardEventInit>): KeyboardEvent {
    const evento = new KeyboardEvent('keydown', { key: 'k', cancelable: true, ...cambios });
    estructura.atajo(evento);
    return evento;
  }

  it('con sesion abre la busqueda global y le quita el atajo al navegador', () => {
    const evento = pulsar({ ctrlKey: true });

    expect(busqueda.abierta()).toBe(true);
    expect(evento.defaultPrevented).toBe(true);
  });

  it('sin sesion no abre nada y el navegador conserva su atajo', () => {
    usuario = null;
    const evento = pulsar({ metaKey: true });

    expect(busqueda.abierta()).toBe(false);
    expect(evento.defaultPrevented).toBe(false);
  });

  it('otra tecla no la abre', () => {
    pulsar({ key: 'j', ctrlKey: true });

    expect(busqueda.abierta()).toBe(false);
  });
});
