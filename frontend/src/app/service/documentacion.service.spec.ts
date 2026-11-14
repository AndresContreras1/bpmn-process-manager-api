import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { firstValueFrom } from 'rxjs';

import { environment } from '../../environments/environment';
import { CaracterizacionRequest } from '../models/documentacion.model';
import { DocumentacionService } from './documentacion.service';

describe('DocumentacionService', () => {
  const api = `${environment.apiUrl}/api/v1`;
  let http: HttpTestingController;
  let documentacion: DocumentacionService;

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
    http = TestBed.inject(HttpTestingController);
    documentacion = TestBed.inject(DocumentacionService);
  });

  afterEach(() => http.verify());

  it('lista los textos del proceso y guarda uno con PUT, con su elemento y su version', () => {
    documentacion.listar(4).subscribe();
    documentacion.guardar(4, { elemento: 'Activity_1', markdown: '**Twice**', version: 2 }).subscribe();

    http.expectOne({ method: 'GET', url: `${api}/procesos/4/documentacion` }).flush([]);
    const guardado = http.expectOne((req) => req.method === 'PUT');
    expect(guardado.request.url).toBe(`${api}/procesos/4/documentacion`);
    expect(guardado.request.body).toEqual({ elemento: 'Activity_1', markdown: '**Twice**', version: 2 });
    guardado.flush({});
  });

  it('pide la vista previa con POST y devuelve solo el HTML que la API limpio', async () => {
    const html = firstValueFrom(documentacion.vistaPrevia('## Packing'));

    const peticion = http.expectOne(`${api}/documentacion/vista-previa`);
    expect(peticion.request.method).toBe('POST');
    expect(peticion.request.body).toEqual({ markdown: '## Packing' });
    peticion.flush({ html: '<h2>Packing</h2>' });
    await expect(html).resolves.toBe('<h2>Packing</h2>');
  });

  it('lee la ficha y la guarda entera, con la version leida', () => {
    const ficha: CaracterizacionRequest = {
      objetivo: 'Deliver every order',
      alcance: '',
      entradas: '',
      salidas: '',
      proveedores: '',
      clientes: '',
      indicadores: '',
      riesgos: '',
      normativa: '',
      version: 0,
    };
    documentacion.caracterizacion(4).subscribe();
    documentacion.guardarCaracterizacion(4, ficha).subscribe();

    const [leida, guardada] = http.match(`${api}/procesos/4/caracterizacion`);
    expect(leida.request.method).toBe('GET');
    expect(guardada.request.method).toBe('PUT');
    expect(guardada.request.body).toEqual(ficha);
    leida.flush({});
    guardada.flush({});
  });
});
