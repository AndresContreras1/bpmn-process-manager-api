import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, TestRequest, provideHttpClientTesting } from '@angular/common/http/testing';
import { LOCALE_ID, signal } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';

import { environment } from '../../../environments/environment';
import { Documento, EstadoDocumento } from '../../models/documento.model';
import { PreferenciasService } from '../../service/preferencias.service';
import { AdjuntosComponent, INTERVALO_DE_REVISION } from './adjuntos.component';

describe('AdjuntosComponent', () => {
  const api = `${environment.apiUrl}/api/v1`;
  const lista = (req: { url: string }): boolean => req.url === `${api}/procesos/4/documentos`;
  let http: HttpTestingController;
  let fixture: ComponentFixture<AdjuntosComponent>;

  function documento(id: number, nombre: string, estado: EstadoDocumento, extra: Partial<Documento> = {}): Documento {
    return {
      id,
      procesoId: 4,
      bpmnId: null,
      nombre,
      versionActual: 1,
      estado,
      motivo: null,
      revisado: false,
      tipo: 'text/plain',
      tamano: 2048,
      version: 0,
      fechaModificacion: '2026-11-12T10:00:00',
      ...extra,
    };
  }

  function abrir(documentos: Documento[], entradas: { elemento?: string; soloLectura?: boolean } = {}): TestRequest {
    TestBed.configureTestingModule({
      imports: [AdjuntosComponent],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: LOCALE_ID, useValue: 'en-US' },
        { provide: PreferenciasService, useValue: { zonaHoraria: signal('UTC') } },
      ],
    });
    http = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(AdjuntosComponent);
    fixture.componentRef.setInput('procesoId', 4);
    fixture.componentRef.setInput('elemento', entradas.elemento ?? null);
    fixture.componentRef.setInput('soloLectura', entradas.soloLectura ?? false);
    fixture.detectChanges();
    const peticion = http.expectOne(lista);
    peticion.flush(documentos);
    fixture.detectChanges();
    return peticion;
  }

  afterEach(() => {
    http.verify();
    vi.useRealTimers();
    vi.restoreAllMocks();
  });

  const buscar = (selector: string): HTMLElement | null => fixture.nativeElement.querySelector(selector);
  const filas = (): HTMLElement[] => Array.from(fixture.nativeElement.querySelectorAll('[data-testid="documento"]'));
  const fila = (id: number): HTMLElement => buscar(`[data-documento="${id}"]`) as HTMLElement;

  function elegir(selector: string, archivos: File[]): void {
    const entrada = buscar(selector) as HTMLInputElement;
    Object.defineProperty(entrada, 'files', { value: archivos, configurable: true });
    entrada.dispatchEvent(new Event('change'));
    fixture.detectChanges();
  }

  function grande(nombre: string): File {
    const archivo = new File(['x'], nombre);
    Object.defineProperty(archivo, 'size', { value: 25 * 1024 * 1024 + 1 });
    return archivo;
  }

  it('lista los documentos del proceso, con su estado y el elemento al que van pegados', () => {
    const peticion = abrir([
      documento(1, 'contract.pdf', 'DISPONIBLE', { bpmnId: 'Activity_1', revisado: true }),
      documento(2, 'virus.pdf', 'BLOQUEADO', { motivo: 'Win.Test.EICAR_HDB-1' }),
    ]);

    expect(peticion.request.params.has('elemento')).toBe(false);
    expect(filas().map((una) => una.dataset['estado'])).toEqual(['DISPONIBLE', 'BLOQUEADO']);
    expect(fila(1).textContent).toContain('attached to Activity_1');
    expect(fila(2).querySelector('[data-testid="documento-motivo"]')?.textContent).toContain('Win.Test.EICAR_HDB-1');
    expect((fila(2).querySelector('[data-testid="documento-bajar"]') as HTMLButtonElement).disabled).toBe(true);
  });

  it('en un elemento, pide solo los de ese elemento y les sube el archivo a el', () => {
    const peticion = abrir([], { elemento: 'Activity_1' });
    expect(peticion.request.params.get('elemento')).toBe('Activity_1');

    elegir('#adjuntos-archivo', [new File(['Wrap it.'], 'packing.txt')]);

    const subida = http.expectOne((req) => req.method === 'POST');
    expect(subida.request.params.get('elemento')).toBe('Activity_1');
    subida.flush(documento(3, 'packing.txt', 'DISPONIBLE', { bpmnId: 'Activity_1' }));
  });

  it('adjunta con el boton: manda el archivo como octet-stream y lo muestra pendiente, con un giro', () => {
    abrir([]);
    const archivo = new File(['Wrap it twice.'], 'Packing instructions.txt', { type: 'text/plain' });
    elegir('#adjuntos-archivo', [archivo]);

    expect(buscar('#adjuntos-subiendo')?.textContent).toContain('Packing instructions.txt');
    const subida = http.expectOne((req) => req.method === 'POST');
    expect(subida.request.url).toBe(`${api}/procesos/4/documentos`);
    expect(subida.request.body).toBe(archivo);
    expect(subida.request.headers.get('Content-Type')).toBe('application/octet-stream');
    expect(subida.request.params.get('nombre')).toBe('Packing instructions.txt');
    subida.flush(documento(7, 'Packing instructions.txt', 'PENDIENTE'), { status: 201, statusText: 'Created' });
    fixture.detectChanges();

    expect(buscar('#adjuntos-subiendo')).toBeNull();
    expect(fila(7).dataset['estado']).toBe('PENDIENTE');
    expect(fila(7).querySelector('.spinner-border')).not.toBeNull();
    expect(buscar('#adjuntos-aviso')?.textContent).toContain('The antivirus is checking it.');
    // Lo que sigue pendiente se vuelve a pedir: aqui no se espera, se descarta
    fixture.destroy();
  });

  it('adjunta lo que se suelta sobre la zona, uno detras de otro', () => {
    abrir([]);
    const zona = buscar('#adjuntos-zona') as HTMLElement;
    const encima = new Event('dragover', { cancelable: true });
    zona.dispatchEvent(encima);
    fixture.detectChanges();
    expect(encima.defaultPrevented).toBe(true);
    expect(zona.classList).toContain('arrastrando');

    const soltar = new Event('drop', { cancelable: true });
    Object.defineProperty(soltar, 'dataTransfer', {
      value: { files: [new File(['a'], 'a.txt'), new File(['b'], 'b.txt')] },
    });
    zona.dispatchEvent(soltar);
    fixture.detectChanges();

    expect(soltar.defaultPrevented).toBe(true);
    expect(zona.classList).not.toContain('arrastrando');
    const primera = http.expectOne((req) => req.method === 'POST');
    expect(primera.request.params.get('nombre')).toBe('a.txt');
    primera.flush(documento(1, 'a.txt', 'DISPONIBLE'));
    const segunda = http.expectOne((req) => req.method === 'POST');
    expect(segunda.request.params.get('nombre')).toBe('b.txt');
    segunda.flush(documento(2, 'b.txt', 'DISPONIBLE'));
    fixture.detectChanges();

    expect(filas().map((una) => una.dataset['nombre'])).toEqual(['a.txt', 'b.txt']);
  });

  it('no manda lo que pasa de 25 MB y dice cual era; lo demas si sube', () => {
    abrir([]);
    elegir('#adjuntos-archivo', [grande('video.mp4'), new File(['ok'], 'ok.txt')]);

    expect(buscar('#adjuntos-error')?.textContent).toContain('video.mp4');
    const subida = http.expectOne((req) => req.method === 'POST');
    expect(subida.request.params.get('nombre')).toBe('ok.txt');
    subida.flush(documento(1, 'ok.txt', 'DISPONIBLE'));
  });

  it('muestra lo que la API dice de un 413 y de un 415', () => {
    abrir([]);
    elegir('#adjuntos-archivo', [new File(['x'], 'app.pdf')]);
    http
      .expectOne((req) => req.method === 'POST')
      .flush(
        { title: 'Tipo no permitido', detail: 'Ese tipo de archivo no se acepta.' },
        { status: 415, statusText: 'Unsupported Media Type' },
      );
    fixture.detectChanges();
    expect(buscar('#adjuntos-error')?.textContent).toContain(
      'app.pdf was not uploaded: Ese tipo de archivo no se acepta.',
    );

    elegir('#adjuntos-archivo', [new File(['x'], 'big.pdf')]);
    http
      .expectOne((req) => req.method === 'POST')
      .flush('<html>413</html>', { status: 413, statusText: 'Payload Too Large' });
    fixture.detectChanges();
    expect(buscar('#adjuntos-error')?.textContent).toContain('larger than 25 MB');
  });

  it('mientras haya uno pendiente vuelve a preguntar cada dos segundos, y anuncia cuando el antivirus termina', () => {
    vi.useFakeTimers();
    abrir([documento(1, 'a.txt', 'PENDIENTE'), documento(2, 'b.txt', 'PENDIENTE')]);

    vi.advanceTimersByTime(INTERVALO_DE_REVISION - 1);
    http.expectNone(lista);
    vi.advanceTimersByTime(1);
    http.expectOne(lista).flush([documento(1, 'a.txt', 'DISPONIBLE'), documento(2, 'b.txt', 'PENDIENTE')]);
    fixture.detectChanges();

    expect(fila(1).dataset['estado']).toBe('DISPONIBLE');
    expect(buscar('#adjuntos-anuncio')?.getAttribute('aria-live')).toBe('polite');
    expect(buscar('#adjuntos-anuncio')?.textContent).toContain('“a.txt” is ready to download.');

    vi.advanceTimersByTime(INTERVALO_DE_REVISION);
    http
      .expectOne(lista)
      .flush([
        documento(1, 'a.txt', 'DISPONIBLE'),
        documento(2, 'b.txt', 'BLOQUEADO', { motivo: 'El archivo crece demasiado al abrirlo.' }),
      ]);
    fixture.detectChanges();

    expect(buscar('#adjuntos-anuncio')?.textContent).toContain('“b.txt” was blocked: El archivo crece demasiado');
    // Nada pendiente: deja de preguntar
    vi.advanceTimersByTime(INTERVALO_DE_REVISION * 3);
    http.expectNone(lista);
  });

  it('baja la ultima version con un enlace firmado que pide justo antes', () => {
    abrir([documento(1, 'a.txt', 'DISPONIBLE')]);
    const pulsado = vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(function (
      this: HTMLAnchorElement,
    ) {
      expect(this.getAttribute('href')).toBe('/api/v1/descargas/documentos/1/1?empresa=1&vence=9&firma=x');
      expect(this.hasAttribute('download')).toBe(true);
    });

    (fila(1).querySelector('[data-testid="documento-bajar"]') as HTMLButtonElement).click();
    const enlace = http.expectOne((req) => req.url === `${api}/documentos/1/enlace`);
    expect(enlace.request.params.has('version')).toBe(false);
    enlace.flush({ url: '/api/v1/descargas/documentos/1/1?empresa=1&vence=9&firma=x', venceEn: '' });

    expect(pulsado).toHaveBeenCalledTimes(1);
  });

  it('abre las versiones de un documento, baja una vieja y sube una nueva', () => {
    abrir([documento(1, 'a.txt', 'DISPONIBLE', { versionActual: 2 })]);
    vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(() => undefined);
    const versiones = fila(1).querySelector('[data-testid="documento-versiones"]') as HTMLButtonElement;
    versiones.click();
    http.expectOne(`${api}/documentos/1`).flush({
      documento: documento(1, 'a.txt', 'DISPONIBLE', { versionActual: 2 }),
      versiones: [
        { numero: 2, nombre: 'a.txt', tipo: 'text/plain', tamano: 10, sha256: 'b', estado: 'DISPONIBLE' },
        { numero: 1, nombre: 'a-old.txt', tipo: 'text/plain', tamano: 9, sha256: 'a', estado: 'DISPONIBLE' },
      ],
    });
    fixture.detectChanges();

    expect(versiones.getAttribute('aria-expanded')).toBe('true');
    const vieja = buscar('[data-testid="version-documento"][data-version="1"]') as HTMLElement;
    (vieja.querySelector('[data-testid="version-bajar"]') as HTMLButtonElement).click();
    const enlace = http.expectOne((req) => req.url === `${api}/documentos/1/enlace`);
    expect(enlace.request.params.get('version')).toBe('1');
    enlace.flush({ url: '/x', venceEn: '' });

    (fila(1).querySelector('[data-testid="documento-nueva-version"]') as HTMLButtonElement).click();
    const archivo = new File(['v3'], 'a-v3.txt');
    elegir('#adjuntos-version-archivo', [archivo]);
    const subida = http.expectOne(`${api}/documentos/1/versiones?nombre=a-v3.txt`);
    expect(subida.request.method).toBe('POST');
    expect(subida.request.body).toBe(archivo);
    subida.flush(documento(1, 'a-v3.txt', 'DISPONIBLE', { versionActual: 3 }));
    fixture.detectChanges();

    // Con la lista abierta, vuelve a pedir las versiones para mostrar la nueva
    http.expectOne(`${api}/documentos/1`).flush({ documento: documento(1, 'a-v3.txt', 'DISPONIBLE'), versiones: [] });
    expect(buscar('#adjuntos-aviso')?.textContent).toContain('Version 3 of “a-v3.txt” was uploaded.');
  });

  it('borra un documento despues de confirmarlo en el modal', () => {
    abrir([documento(1, 'a.txt', 'DISPONIBLE'), documento(2, 'b.txt', 'DISPONIBLE')]);
    (fila(1).querySelector('[data-testid="documento-borrar"]') as HTMLButtonElement).click();
    fixture.detectChanges();
    expect(buscar('#modal-borrar-documento-mensaje')?.textContent).toContain('“a.txt”');

    (buscar('#modal-borrar-documento-confirmar') as HTMLButtonElement).click();
    const borrado = http.expectOne((req) => req.method === 'DELETE');
    expect(borrado.request.url).toBe(`${api}/documentos/1`);
    borrado.flush(null, { status: 204, statusText: 'No Content' });
    fixture.detectChanges();

    expect(filas().map((una) => una.dataset['nombre'])).toEqual(['b.txt']);
  });

  it('quien solo lee ve y baja, pero no tiene zona para soltar, ni version nueva, ni borrar', () => {
    abrir([documento(1, 'a.txt', 'DISPONIBLE')], { soloLectura: true });

    expect(buscar('#adjuntos-zona')).toBeNull();
    expect(buscar('#adjuntos-archivo')).toBeNull();
    expect(buscar('[data-testid="documento-nueva-version"]')).toBeNull();
    expect(buscar('[data-testid="documento-borrar"]')).toBeNull();
    expect(buscar('#modal-borrar-documento')).toBeNull();
    expect((fila(1).querySelector('[data-testid="documento-bajar"]') as HTMLButtonElement).disabled).toBe(false);
  });
});
