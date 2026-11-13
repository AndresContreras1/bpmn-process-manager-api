import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { LOCALE_ID, signal } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';

import { environment } from '../../../environments/environment';
import { Documentacion } from '../../models/documentacion.model';
import { PreferenciasService } from '../../service/preferencias.service';
import { EditorDocumentacionComponent } from './editor-documentacion.component';

describe('EditorDocumentacionComponent', () => {
  const api = `${environment.apiUrl}/api/v1`;
  let http: HttpTestingController;
  let fixture: ComponentFixture<EditorDocumentacionComponent>;

  const delProceso: Documentacion = {
    bpmnId: null,
    markdown: 'Wrap the order **twice**.',
    html: '<p>Wrap the order <strong>twice</strong>.</p>',
    version: 3,
    modificadoPor: 2,
    fechaModificacion: '2026-11-12T10:00:00',
  };
  const delElemento: Documentacion = {
    ...delProceso,
    bpmnId: 'Activity_1',
    markdown: 'Check the address.',
    html: '<p>Check the address.</p>',
    version: 0,
  };

  function abrir(entradas: { elemento?: string | null; soloLectura?: boolean } = {}): void {
    TestBed.configureTestingModule({
      imports: [EditorDocumentacionComponent],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: LOCALE_ID, useValue: 'en-US' },
        { provide: PreferenciasService, useValue: { zonaHoraria: signal('UTC') } },
      ],
    });
    http = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(EditorDocumentacionComponent);
    fixture.componentRef.setInput('procesoId', 4);
    fixture.componentRef.setInput('elemento', entradas.elemento ?? null);
    fixture.componentRef.setInput('soloLectura', entradas.soloLectura ?? false);
    fixture.detectChanges();
    http.expectOne(`${api}/procesos/4/documentacion`).flush([delProceso, delElemento]);
    fixture.detectChanges();
  }

  afterEach(() => http.verify());

  const buscar = (selector: string): HTMLElement | null => fixture.nativeElement.querySelector(selector);
  const texto = (): HTMLTextAreaElement => buscar('#documentacion-texto') as HTMLTextAreaElement;

  function escribir(valor: string): void {
    texto().value = valor;
    texto().dispatchEvent(new Event('input'));
    fixture.detectChanges();
  }

  it('carga el texto del proceso entero, no el de sus elementos', () => {
    abrir();

    expect(texto().value).toBe('Wrap the order **twice**.');
  });

  it('con un elemento, carga el de ese elemento', () => {
    abrir({ elemento: 'Activity_1' });

    expect(texto().value).toBe('Check the address.');
  });

  it('la vista previa muestra el HTML que devuelve la API, pasado otra vez por el saneador de Angular', () => {
    abrir();
    escribir('## Packing');
    (buscar('#documentacion-ver-previa') as HTMLButtonElement).click();
    fixture.detectChanges();

    const peticion = http.expectOne(`${api}/documentacion/vista-previa`);
    expect(peticion.request.body).toEqual({ markdown: '## Packing' });
    peticion.flush({ html: '<h2>Packing</h2><img src="x" onerror="alert(1)">' });
    fixture.detectChanges();

    const previa = buscar('#documentacion-previa-html') as HTMLElement;
    expect(previa.querySelector('h2')?.textContent).toBe('Packing');
    expect(previa.innerHTML).not.toContain('onerror');
    expect(buscar('#documentacion-texto')).toBeNull();
    expect(buscar('#documentacion-ver-previa')?.getAttribute('aria-pressed')).toBe('true');
  });

  it('guarda con la version leida, y del elemento si lo hay', () => {
    abrir({ elemento: 'Activity_1' });
    escribir('Check the address **twice**.');
    (buscar('#documentacion-guardar') as HTMLButtonElement).click();

    const peticion = http.expectOne((req) => req.method === 'PUT');
    expect(peticion.request.url).toBe(`${api}/procesos/4/documentacion`);
    expect(peticion.request.body).toEqual({
      elemento: 'Activity_1',
      markdown: 'Check the address **twice**.',
      version: 0,
    });
    peticion.flush({ ...delElemento, markdown: 'Check the address **twice**.', version: 1 });
    fixture.detectChanges();

    expect(buscar('#documentacion-aviso')?.textContent).toContain('Documentation saved.');
    // La siguiente vez viaja la version nueva
    (buscar('#documentacion-guardar') as HTMLButtonElement).click();
    const otra = http.expectOne((req) => req.method === 'PUT');
    expect(otra.request.body.version).toBe(1);
    otra.flush({ ...delElemento, version: 2 });
  });

  it('un texto vacio lo borra, y despues se guarda como nuevo, sin version', () => {
    abrir();
    escribir('');
    (buscar('#documentacion-guardar') as HTMLButtonElement).click();
    http
      .expectOne((req) => req.method === 'PUT')
      .flush({ bpmnId: null, markdown: '', html: '', version: null, modificadoPor: null, fechaModificacion: null });
    fixture.detectChanges();

    expect(buscar('#documentacion-aviso')?.textContent).toContain('Documentation removed.');
    escribir('New text');
    (buscar('#documentacion-guardar') as HTMLButtonElement).click();
    const nuevo = http.expectOne((req) => req.method === 'PUT');
    expect(nuevo.request.body.version).toBeNull();
    nuevo.flush({ ...delProceso, version: 0 });
  });

  it('con una version vieja dice que otra persona guardo, conserva lo escrito y recargar trae lo de ella', () => {
    abrir();
    escribir('My version');
    (buscar('#documentacion-guardar') as HTMLButtonElement).click();
    http
      .expectOne((req) => req.method === 'PUT')
      .flush(
        { title: 'Conflicto de versión', detail: 'Otra persona guardo.' },
        { status: 409, statusText: 'Conflict' },
      );
    fixture.detectChanges();

    expect(buscar('#documentacion-conflicto')).not.toBeNull();
    expect(texto().value).toBe('My version');
    expect((buscar('#documentacion-guardar') as HTMLButtonElement).disabled).toBe(true);

    (buscar('#documentacion-recargar') as HTMLButtonElement).click();
    http.expectOne(`${api}/procesos/4/documentacion`).flush([{ ...delProceso, markdown: 'Theirs', version: 4 }]);
    fixture.detectChanges();

    expect(buscar('#documentacion-conflicto')).toBeNull();
    expect(texto().value).toBe('Theirs');
  });

  it('no deja guardar un texto de mas de 100 000 caracteres', () => {
    abrir();
    escribir('x'.repeat(100_001));

    expect(buscar('#documentacion-largo')).not.toBeNull();
    expect((buscar('#documentacion-guardar') as HTMLButtonElement).disabled).toBe(true);
  });

  it('quien solo lee ve el HTML guardado, sin editor ni vista previa', () => {
    abrir({ soloLectura: true });

    expect(buscar('#documentacion-html')?.querySelector('strong')?.textContent).toBe('twice');
    expect(buscar('#documentacion-texto')).toBeNull();
    expect(buscar('#documentacion-guardar')).toBeNull();
    expect(buscar('#documentacion-ver-previa')).toBeNull();
  });

  it('quien solo lee un elemento sin texto lo ve dicho', () => {
    abrir({ elemento: 'Gateway_9', soloLectura: true });

    expect(buscar('#documentacion-vacia')).not.toBeNull();
  });
});
