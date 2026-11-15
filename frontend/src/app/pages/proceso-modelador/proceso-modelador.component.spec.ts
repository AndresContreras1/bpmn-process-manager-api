import { provideHttpClient } from '@angular/common/http';
import { ChangeDetectorRef } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';

import { Autoguardado, EstadoGuardado } from '../../bpmn/autoguardado';
import { ProcesoModeladorComponent } from './proceso-modelador.component';

/**
 * Pedir la revision desde el modelador, sin pintarlo: lo que se congela es lo ultimo guardado, asi que el dialogo
 * solo se abre si el guardado de antes salio bien. El autoguardado es un doble que termina en el estado que se le diga.
 */
describe('ProcesoModeladorComponent: pedir la revision', () => {
  function abrir(despuesDeGuardar: EstadoGuardado): { pagina: ProcesoModeladorComponent; guardarAhora: () => void } {
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideRouter([]),
        { provide: ChangeDetectorRef, useValue: { markForCheck: () => undefined } },
      ],
    });
    const pagina = TestBed.runInInjectionContext(() => new ProcesoModeladorComponent());
    const guardarAhora = vi.fn(async () => undefined);
    pagina.autoguardado = { guardarAhora, estado: despuesDeGuardar } as unknown as Autoguardado;
    return { pagina, guardarAhora };
  }

  afterEach(() => TestBed.resetTestingModule());

  it('guarda primero y, guardado, abre el dialogo', async () => {
    const { pagina, guardarAhora } = abrir('guardado');

    await pagina.pedirRevision();

    expect(guardarAhora).toHaveBeenCalled();
    expect(pagina.pidiendoRevision).toBe(true);
    expect(pagina.error).toBeNull();
  });

  it.each<EstadoGuardado>(['conflicto', 'rechazado', 'sin-conexion', 'pendiente'])(
    'si el guardado termina en %s no abre el dialogo y dice por que',
    async (estado) => {
      const { pagina } = abrir(estado);

      await pagina.pedirRevision();

      expect(pagina.pidiendoRevision).toBe(false);
      expect(pagina.error).toContain(pagina.textoDeEstado[estado]);
    },
  );
});
