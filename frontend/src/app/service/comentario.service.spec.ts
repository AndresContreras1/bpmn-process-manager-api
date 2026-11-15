import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { firstValueFrom } from 'rxjs';
import { afterEach, beforeEach, describe, expect, it } from 'vitest';

import { environment } from '../../environments/environment';
import { Hilo } from '../models/comentario.model';
import { ComentarioService } from './comentario.service';

function hilo(id: number): Hilo {
  return { comentario: { id } as Hilo['comentario'], respuestas: [] };
}

describe('ComentarioService', () => {
  const api = `${environment.apiUrl}/api/v1`;
  let http: HttpTestingController;
  let servicio: ComentarioService;

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
    http = TestBed.inject(HttpTestingController);
    servicio = TestBed.inject(ComentarioService);
  });

  afterEach(() => http.verify());

  it('lee los hilos de todas las paginas, de cincuenta en cincuenta, y los junta', async () => {
    const hilos = firstValueFrom(servicio.hilos(7));
    const ruta = `${api}/procesos/7/comentarios`;

    const primera = http.expectOne((req) => req.url === ruta && req.params.get('pagina') === '0');
    expect(primera.request.params.get('tamano')).toBe('50');
    expect(primera.request.params.has('version')).toBe(false);
    primera.flush({ content: [hilo(1), hilo(2)], page: 0, size: 50, totalElements: 3, totalPages: 2 });
    http
      .expectOne((req) => req.url === ruta && req.params.get('pagina') === '1')
      .flush({ content: [hilo(3)], page: 1, size: 50, totalElements: 3, totalPages: 2 });

    await expect(hilos.then((todos) => todos.map((uno) => uno.comentario.id))).resolves.toEqual([1, 2, 3]);
  });

  it('con una version pide solo los de esa version, y una sola pagina si no hay mas', async () => {
    const hilos = firstValueFrom(servicio.hilos(7, 2));

    const pagina = http.expectOne((req) => req.url === `${api}/procesos/7/comentarios`);
    expect(pagina.request.params.get('version')).toBe('2');
    pagina.flush({ content: [], page: 0, size: 50, totalElements: 0, totalPages: 0 });

    await expect(hilos).resolves.toEqual([]);
  });

  it('comenta, responde y edita con los cuerpos de la API', () => {
    const nuevo = { markdown: 'Who checks?', bpmnId: 'Tarea_1', versionDelProceso: null, menciones: [2] };
    servicio.comentar(7, nuevo).subscribe();
    servicio.responder(12, { markdown: 'The lead', menciones: [] }).subscribe();
    servicio.editar(12, { markdown: 'The lead, daily', menciones: [3], version: 1 }).subscribe();

    const comentado = http.expectOne(`${api}/procesos/7/comentarios`);
    expect(comentado.request.method).toBe('POST');
    expect(comentado.request.body).toEqual(nuevo);
    const respondido = http.expectOne(`${api}/comentarios/12/respuestas`);
    expect(respondido.request.method).toBe('POST');
    expect(respondido.request.body).toEqual({ markdown: 'The lead', menciones: [] });
    const editado = http.expectOne((req) => req.method === 'PUT');
    expect(editado.request.url).toBe(`${api}/comentarios/12`);
    expect(editado.request.body).toEqual({ markdown: 'The lead, daily', menciones: [3], version: 1 });
  });

  it('resuelve, reabre y borra un comentario', () => {
    servicio.resolver(11).subscribe();
    servicio.reabrir(11).subscribe();
    servicio.eliminar(11).subscribe();

    expect(http.expectOne(`${api}/comentarios/11/resolver`).request.method).toBe('POST');
    expect(http.expectOne(`${api}/comentarios/11/reabrir`).request.method).toBe('POST');
    expect(http.expectOne(`${api}/comentarios/11`).request.method).toBe('DELETE');
  });

  it('pregunta a quien mencionar con el texto sin espacios, y sin texto no lo manda', () => {
    servicio.mencionables(7, '  an ').subscribe();
    servicio.mencionables(7, '   ').subscribe();

    const [conTexto, sinTexto] = http.match((req) => req.url === `${api}/procesos/7/mencionables`);
    expect(conTexto.request.params.get('q')).toBe('an');
    expect(sinTexto.request.params.has('q')).toBe(false);
    conTexto.flush([]);
    sinTexto.flush([]);
  });
});
