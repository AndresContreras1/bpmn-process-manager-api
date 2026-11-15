import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { firstValueFrom } from 'rxjs';

import { environment } from '../../environments/environment';
import { DocumentoService } from './documento.service';

describe('DocumentoService', () => {
  const api = `${environment.apiUrl}/api/v1`;
  let http: HttpTestingController;
  let documentos: DocumentoService;

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
    http = TestBed.inject(HttpTestingController);
    documentos = TestBed.inject(DocumentoService);
  });

  afterEach(() => http.verify());

  it('lista los del proceso sin filtro, y los de un elemento con elemento', () => {
    documentos.listar(4).subscribe();
    documentos.listar(4, 'Activity_1').subscribe();

    const [todos, delElemento] = http.match((req) => req.url === `${api}/procesos/4/documentos`);
    expect(todos.request.method).toBe('GET');
    expect(todos.request.params.has('elemento')).toBe(false);
    expect(delElemento.request.params.get('elemento')).toBe('Activity_1');
    todos.flush([]);
    delElemento.flush([]);
  });

  it('sube el archivo tal cual, como octet-stream, con su nombre y su elemento en la consulta', async () => {
    const archivo = new File(['Wrap it twice.'], 'Packing instructions.txt', { type: 'text/plain' });
    const subido = firstValueFrom(documentos.subir(4, archivo, 'Activity_1'));

    const peticion = http.expectOne((req) => req.url === `${api}/procesos/4/documentos`);
    expect(peticion.request.method).toBe('POST');
    expect(peticion.request.body).toBe(archivo);
    expect(peticion.request.headers.get('Content-Type')).toBe('application/octet-stream');
    expect(peticion.request.params.get('nombre')).toBe('Packing instructions.txt');
    expect(peticion.request.params.get('elemento')).toBe('Activity_1');
    peticion.flush({ id: 12, estado: 'PENDIENTE' }, { status: 201, statusText: 'Created' });
    await expect(subido).resolves.toMatchObject({ id: 12, estado: 'PENDIENTE' });
  });

  it('sin elemento, el documento es del proceso entero', () => {
    documentos.subir(4, new File(['x'], 'a.txt')).subscribe();

    const peticion = http.expectOne((req) => req.url === `${api}/procesos/4/documentos`);
    expect(peticion.request.params.has('elemento')).toBe(false);
    peticion.flush({});
  });

  it('una version nueva va a su documento, tambien como el archivo tal cual', () => {
    const archivo = new File(['v2'], 'packing-v2.txt');
    documentos.subirVersion(12, archivo).subscribe();

    const peticion = http.expectOne((req) => req.url === `${api}/documentos/12/versiones`);
    expect(peticion.request.method).toBe('POST');
    expect(peticion.request.body).toBe(archivo);
    expect(peticion.request.headers.get('Content-Type')).toBe('application/octet-stream');
    expect(peticion.request.params.get('nombre')).toBe('packing-v2.txt');
    peticion.flush({});
  });

  it('pide el documento con sus versiones, su enlace y lo borra', () => {
    documentos.obtener(12).subscribe();
    documentos.enlace(12).subscribe();
    documentos.enlace(12, 1).subscribe();
    documentos.eliminar(12).subscribe();

    const detalle = http.expectOne({ method: 'GET', url: `${api}/documentos/12` });
    expect(detalle.request.method).toBe('GET');
    detalle.flush({ documento: {}, versiones: [] });
    const [ultima, primera] = http.match((req) => req.url === `${api}/documentos/12/enlace`);
    expect(ultima.request.params.has('version')).toBe(false);
    expect(primera.request.params.get('version')).toBe('1');
    const borrado = http.expectOne((req) => req.method === 'DELETE');
    expect(borrado.request.url).toBe(`${api}/documentos/12`);
    [ultima, primera].forEach((peticion) => peticion.flush({ url: '/x', venceEn: '' }));
    borrado.flush(null, { status: 204, statusText: 'No Content' });
  });
});
