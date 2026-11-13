import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { LOCALE_ID, signal } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';

import { environment } from '../../../environments/environment';
import { EstadoManual, Manual } from '../../models/documentacion.model';
import { PreferenciasService } from '../../service/preferencias.service';
import { INTERVALO_DEL_MANUAL, ManualProcesoComponent } from './manual-proceso.component';

describe('ManualProcesoComponent', () => {
  const api = `${environment.apiUrl}/api/v1`;
  const lista = `${api}/procesos/4/manuales`;
  let http: HttpTestingController;
  let fixture: ComponentFixture<ManualProcesoComponent>;

  function manual(id: number, estado: EstadoManual, extra: Partial<Manual> = {}): Manual {
    return {
      id,
      procesoId: 4,
      version: 2,
      idioma: 'es-CO',
      estado,
      motivo: null,
      pedidoEn: '2026-11-12T10:00:00',
      listoEn: estado === 'PENDIENTE' ? null : '2026-11-12T10:00:04',
      ...extra,
    };
  }

  function abrir(manuales: Manual[], entradas: { version?: number | null; puedePedir?: boolean } = {}): void {
    TestBed.configureTestingModule({
      imports: [ManualProcesoComponent],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: LOCALE_ID, useValue: 'en-US' },
        { provide: PreferenciasService, useValue: { zonaHoraria: signal('UTC') } },
      ],
    });
    http = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(ManualProcesoComponent);
    fixture.componentRef.setInput('procesoId', 4);
    fixture.componentRef.setInput('versionPublicada', entradas.version === undefined ? 2 : entradas.version);
    fixture.componentRef.setInput('puedePedir', entradas.puedePedir ?? true);
    fixture.detectChanges();
    http.expectOne(lista).flush(manuales);
    fixture.detectChanges();
  }

  afterEach(() => {
    http.verify();
    vi.useRealTimers();
    vi.restoreAllMocks();
  });

  const buscar = (selector: string): HTMLElement | null => fixture.nativeElement.querySelector(selector);
  const filas = (): HTMLElement[] => Array.from(fixture.nativeElement.querySelectorAll('[data-testid="manual"]'));

  it('genera el manual de la version vigente y pregunta cada dos segundos hasta que esta listo', () => {
    vi.useFakeTimers();
    abrir([]);
    expect(buscar('#manual-generar')?.textContent).toContain('version 2');

    (buscar('#manual-generar') as HTMLButtonElement).click();
    const pedido = http.expectOne((req) => req.method === 'POST');
    expect(pedido.request.url).toBe(lista);
    pedido.flush(manual(5, 'PENDIENTE'), { status: 202, statusText: 'Accepted' });
    fixture.detectChanges();

    expect(filas()[0].dataset['estado']).toBe('PENDIENTE');
    expect(filas()[0].querySelector('.spinner-border')).not.toBeNull();
    expect(filas()[0].querySelector('[data-testid="manual-pdf"]')).toBeNull();

    vi.advanceTimersByTime(INTERVALO_DEL_MANUAL);
    http.expectOne(lista).flush([manual(5, 'PENDIENTE')]);
    vi.advanceTimersByTime(INTERVALO_DEL_MANUAL);
    http.expectOne(lista).flush([manual(5, 'DISPONIBLE')]);
    fixture.detectChanges();

    expect(filas()[0].dataset['estado']).toBe('DISPONIBLE');
    expect(buscar('#manual-anuncio')?.textContent).toContain('The manual of version 2 is ready to download.');
    vi.advanceTimersByTime(INTERVALO_DEL_MANUAL * 3);
    http.expectNone(lista);
  });

  it('un manual que fallo dice por que y no se baja', () => {
    abrir([manual(5, 'FALLIDO', { motivo: 'La versión 2 no tiene su dibujo.' })]);

    expect(filas()[0].querySelector('[data-testid="manual-motivo"]')?.textContent).toContain('no tiene su dibujo');
    expect(filas()[0].querySelector('[data-testid="manual-pdf"]')).toBeNull();
  });

  it('baja el PDF o el HTML con un enlace firmado que pide justo antes', () => {
    abrir([manual(5, 'DISPONIBLE')]);
    const pulsado = vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(() => undefined);

    (filas()[0].querySelector('[data-testid="manual-pdf"]') as HTMLButtonElement).click();
    const pdf = http.expectOne((req) => req.url === `${api}/manuales/5/enlace`);
    expect(pdf.request.params.get('formato')).toBe('pdf');
    pdf.flush({ url: '/api/v1/descargas/manuales/5/pdf?firma=x', venceEn: '' });

    (filas()[0].querySelector('[data-testid="manual-html"]') as HTMLButtonElement).click();
    const html = http.expectOne((req) => req.url === `${api}/manuales/5/enlace`);
    expect(html.request.params.get('formato')).toBe('html');
    html.flush({ url: '/api/v1/descargas/manuales/5/html?firma=x', venceEn: '' });

    expect(pulsado).toHaveBeenCalledTimes(2);
  });

  it('muestra el 409 de la API cuando no se puede pedir', () => {
    abrir([]);
    (buscar('#manual-generar') as HTMLButtonElement).click();
    http
      .expectOne((req) => req.method === 'POST')
      .flush(
        { title: 'Regla de negocio', detail: 'El proceso no tiene una versión vigente: publícalo primero.' },
        { status: 409, statusText: 'Conflict' },
      );
    fixture.detectChanges();

    expect(buscar('#manual-error')?.textContent).toContain('publícalo primero');
  });

  it('sin version publicada no hay manual que pedir, y lo dice', () => {
    abrir([], { version: null });

    expect(buscar('#manual-generar')).toBeNull();
    expect(buscar('#manual-sin-version')).not.toBeNull();
  });

  it('quien solo lee baja los manuales que hay, pero no pide uno', () => {
    abrir([manual(5, 'DISPONIBLE')], { puedePedir: false });

    expect(buscar('#manual-generar')).toBeNull();
    expect(filas()[0].querySelector('[data-testid="manual-pdf"]')).not.toBeNull();
  });
});
