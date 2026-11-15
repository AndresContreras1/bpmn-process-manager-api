import { HttpErrorResponse, provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { LOCALE_ID, signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import { of, throwError } from 'rxjs';

import { Proceso } from '../../models/proceso.model';
import { AuthService } from '../../service/auth.service';
import { PreferenciasService } from '../../service/preferencias.service';
import { ProcesoService } from '../../service/proceso.service';
import { ProcesoDocumentacionComponent } from './proceso-documentacion.component';

describe('ProcesoDocumentacionComponent', () => {
  const proceso = { id: 4, nombre: 'Gift wrapping', versionPublicada: 2 } as Proceso;
  let harness: RouterTestingHarness;
  let http: HttpTestingController;

  async function abrir(
    url: string,
    opciones: { puedeEditar?: boolean; error?: HttpErrorResponse } = {},
  ): Promise<HTMLElement> {
    TestBed.configureTestingModule({
      providers: [
        provideRouter([{ path: 'procesos/:id/documentacion', component: ProcesoDocumentacionComponent }]),
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: LOCALE_ID, useValue: 'en-US' },
        { provide: PreferenciasService, useValue: { zonaHoraria: signal('UTC') } },
        { provide: AuthService, useValue: { puedeEditar: () => opciones.puedeEditar ?? true } },
        {
          provide: ProcesoService,
          useValue: {
            obtener: () => (opciones.error ? throwError(() => opciones.error) : of({ proceso, historial: [] })),
          },
        },
      ],
    });
    http = TestBed.inject(HttpTestingController);
    harness = await RouterTestingHarness.create();
    await harness.navigateByUrl(url, ProcesoDocumentacionComponent);
    // Lo que piden las cuatro pestanas al abrir: cada una tiene sus propias pruebas
    http.match(() => true).forEach((peticion) => peticion.flush([]));
    harness.detectChanges();
    return harness.routeNativeElement as HTMLElement;
  }

  const pestana = (pagina: HTMLElement, nombre: string): HTMLButtonElement =>
    pagina.querySelector(`#pestana-${nombre}`) as HTMLButtonElement;
  const panel = (pagina: HTMLElement, nombre: string): HTMLElement =>
    pagina.querySelector(`#panel-${nombre}`) as HTMLElement;

  function teclear(boton: HTMLElement, tecla: string): void {
    boton.dispatchEvent(new KeyboardEvent('keydown', { key: tecla, bubbles: true }));
    harness.detectChanges();
  }

  it('abre en la documentacion, con las otras tres pestanas fuera del orden del tabulador', async () => {
    const pagina = await abrir('/procesos/4/documentacion');

    expect(pagina.querySelector('#documentacion-proceso')?.textContent).toContain('Gift wrapping');
    expect(pestana(pagina, 'texto').getAttribute('aria-selected')).toBe('true');
    expect(pestana(pagina, 'texto').tabIndex).toBe(0);
    expect(pestana(pagina, 'ficha').tabIndex).toBe(-1);
    expect(panel(pagina, 'texto').hidden).toBe(false);
    expect(panel(pagina, 'adjuntos').hidden).toBe(true);
  });

  it('las flechas, Inicio y Fin se mueven entre las pestanas, dan la vuelta y dejan la elegida en la direccion', async () => {
    const pagina = await abrir('/procesos/4/documentacion');
    const navegar = vi.spyOn(TestBed.inject(Router), 'navigate');

    teclear(pestana(pagina, 'texto'), 'ArrowRight');
    expect(pestana(pagina, 'ficha').getAttribute('aria-selected')).toBe('true');
    expect(document.activeElement).toBe(pestana(pagina, 'ficha'));
    expect(panel(pagina, 'ficha').hidden).toBe(false);
    expect(panel(pagina, 'texto').hidden).toBe(true);
    expect(navegar).toHaveBeenCalledWith([], expect.objectContaining({ queryParams: { pestana: 'ficha' } }));

    teclear(pestana(pagina, 'ficha'), 'End');
    expect(pestana(pagina, 'manual').getAttribute('aria-selected')).toBe('true');
    teclear(pestana(pagina, 'manual'), 'ArrowRight');
    expect(pestana(pagina, 'texto').getAttribute('aria-selected')).toBe('true');
    teclear(pestana(pagina, 'texto'), 'ArrowLeft');
    expect(pestana(pagina, 'manual').getAttribute('aria-selected')).toBe('true');
    teclear(pestana(pagina, 'manual'), 'Home');
    expect(pestana(pagina, 'texto').getAttribute('aria-selected')).toBe('true');
  });

  it('abre en la pestana que diga la direccion', async () => {
    const pagina = await abrir('/procesos/4/documentacion?pestana=adjuntos');

    expect(pestana(pagina, 'adjuntos').getAttribute('aria-selected')).toBe('true');
    expect(panel(pagina, 'adjuntos').hidden).toBe(false);
  });

  it('a quien solo lee le dice que no puede cambiar nada, y las pestanas no le ofrecen editar', async () => {
    const pagina = await abrir('/procesos/4/documentacion?pestana=adjuntos', { puedeEditar: false });

    expect(pagina.querySelector('#documentacion-solo-lectura')).not.toBeNull();
    expect(pagina.querySelector('#adjuntos-zona')).toBeNull();
    expect(pagina.querySelector('#documentacion-texto')).toBeNull();
    expect(pagina.querySelector('#manual-generar')).toBeNull();
  });

  it('un proceso de otra tienda, o que no existe, no se encuentra', async () => {
    const pagina = await abrir('/procesos/99/documentacion', { error: new HttpErrorResponse({ status: 404 }) });

    expect(pagina.querySelector('#documentacion-no-encontrado')).not.toBeNull();
    expect(pagina.querySelector('[role="tablist"]')).toBeNull();
  });
});
