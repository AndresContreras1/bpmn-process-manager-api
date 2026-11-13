import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { LOCALE_ID, signal } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';

import { environment } from '../../../environments/environment';
import { Caracterizacion } from '../../models/documentacion.model';
import { PreferenciasService } from '../../service/preferencias.service';
import { FichaCaracterizacionComponent } from './ficha-caracterizacion.component';

describe('FichaCaracterizacionComponent', () => {
  const url = `${environment.apiUrl}/api/v1/procesos/4/caracterizacion`;
  let http: HttpTestingController;
  let fixture: ComponentFixture<FichaCaracterizacionComponent>;

  const vacia: Caracterizacion = {
    objetivo: null,
    alcance: null,
    entradas: null,
    salidas: null,
    proveedores: null,
    clientes: null,
    indicadores: null,
    riesgos: null,
    normativa: null,
    version: null,
    fechaModificacion: null,
  };
  const escrita: Caracterizacion = {
    ...vacia,
    objetivo: 'Deliver every order complete and on time',
    riesgos: 'Stock out',
    version: 2,
    fechaModificacion: '2026-11-12T10:00:00',
  };

  function abrir(ficha: Caracterizacion, soloLectura: boolean = false): void {
    TestBed.configureTestingModule({
      imports: [FichaCaracterizacionComponent],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: LOCALE_ID, useValue: 'en-US' },
        { provide: PreferenciasService, useValue: { zonaHoraria: signal('UTC') } },
      ],
    });
    http = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(FichaCaracterizacionComponent);
    fixture.componentRef.setInput('procesoId', 4);
    fixture.componentRef.setInput('soloLectura', soloLectura);
    fixture.detectChanges();
    http.expectOne(url).flush(ficha);
    fixture.detectChanges();
  }

  afterEach(() => http.verify());

  const buscar = (selector: string): HTMLElement | null => fixture.nativeElement.querySelector(selector);
  const campo = (nombre: string): HTMLTextAreaElement => buscar(`#caracterizacion-${nombre}`) as HTMLTextAreaElement;

  function escribir(nombre: string, valor: string): void {
    campo(nombre).value = valor;
    campo(nombre).dispatchEvent(new Event('input'));
    fixture.detectChanges();
  }

  function guardar(): void {
    (buscar('#caracterizacion-formulario') as HTMLFormElement).dispatchEvent(new Event('submit'));
    fixture.detectChanges();
  }

  it('muestra los nueve campos con lo que trae la API', () => {
    abrir(escrita);

    expect(fixture.nativeElement.querySelectorAll('textarea').length).toBe(9);
    expect(campo('objetivo').value).toBe('Deliver every order complete and on time');
    expect(campo('alcance').value).toBe('');
  });

  it('la primera vez guarda la ficha entera sin version, y la siguiente con la que devolvio la API', () => {
    abrir(vacia);
    escribir('objetivo', 'Ship it');
    escribir('normativa', 'Consumer statute');
    guardar();

    const primera = http.expectOne((req) => req.method === 'PUT');
    expect(primera.request.url).toBe(url);
    expect(primera.request.body).toEqual({
      objetivo: 'Ship it',
      alcance: '',
      entradas: '',
      salidas: '',
      proveedores: '',
      clientes: '',
      indicadores: '',
      riesgos: '',
      normativa: 'Consumer statute',
      version: null,
    });
    primera.flush({ ...vacia, objetivo: 'Ship it', normativa: 'Consumer statute', version: 0 });
    fixture.detectChanges();
    expect(buscar('#caracterizacion-aviso')?.textContent).toContain('Characterization sheet saved.');

    guardar();
    const segunda = http.expectOne((req) => req.method === 'PUT');
    expect(segunda.request.body.version).toBe(0);
    segunda.flush({ ...escrita, version: 1 });
  });

  it('con una version vieja avisa del conflicto sin borrar lo escrito, y recargar trae la otra', () => {
    abrir(escrita);
    escribir('objetivo', 'Mine');
    guardar();
    http
      .expectOne((req) => req.method === 'PUT')
      .flush(
        { title: 'Conflicto de versión', detail: 'Otra persona guardo.' },
        { status: 409, statusText: 'Conflict' },
      );
    fixture.detectChanges();

    expect(buscar('#caracterizacion-conflicto')).not.toBeNull();
    expect(campo('objetivo').value).toBe('Mine');
    expect((buscar('#caracterizacion-guardar') as HTMLButtonElement).disabled).toBe(true);

    (buscar('#caracterizacion-recargar') as HTMLButtonElement).click();
    http.expectOne(url).flush({ ...escrita, objetivo: 'Theirs', version: 3 });
    fixture.detectChanges();

    expect(buscar('#caracterizacion-conflicto')).toBeNull();
    expect(campo('objetivo').value).toBe('Theirs');
  });

  it('marca el campo que la API rechaza en un 400', () => {
    abrir(escrita);
    guardar();
    http
      .expectOne((req) => req.method === 'PUT')
      .flush(
        { title: 'Solicitud inválida', detail: 'Datos inválidos.', errors: { riesgos: 'Demasiado largo.' } },
        { status: 400, statusText: 'Bad Request' },
      );
    fixture.detectChanges();

    expect(campo('riesgos').classList).toContain('is-invalid');
    expect(buscar('#caracterizacion-error')?.textContent).toContain('Datos inválidos.');
  });

  it('quien solo lee ve la ficha escrita, sin formulario', () => {
    abrir(escrita, true);

    expect(buscar('#caracterizacion-formulario')).toBeNull();
    expect(buscar('#caracterizacion-ver-objetivo')?.textContent).toContain('Deliver every order');
    expect(buscar('#caracterizacion-ver-alcance')?.textContent).toContain('Not written');
  });

  it('y si nadie la escribio, lo dice', () => {
    abrir(vacia, true);

    expect(buscar('#caracterizacion-vacia')).not.toBeNull();
  });
});
