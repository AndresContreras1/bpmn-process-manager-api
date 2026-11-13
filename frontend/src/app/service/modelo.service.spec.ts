import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { firstValueFrom } from 'rxjs';
import { afterEach, beforeEach, describe, expect, it } from 'vitest';

import { environment } from '../../environments/environment';
import { ModeloService } from './modelo.service';

describe('ModeloService', () => {
  const api = `${environment.apiUrl}/api/v1/procesos`;
  let http: HttpTestingController;
  let modelos: ModeloService;

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
    http = TestBed.inject(HttpTestingController);
    modelos = TestBed.inject(ModeloService);
  });

  afterEach(() => http.verify());

  it('F21: lee el XML de una revision del borrador como texto, que es lo que congela una solicitud', async () => {
    const xml = firstValueFrom(modelos.modeloDeRevision(7, 31));

    const peticion = http.expectOne((p) => p.url === `${api}/7/revisiones/31/modelo`);
    expect(peticion.request.method).toBe('GET');
    expect(peticion.request.responseType).toBe('text');
    // Sin para=dibujar la ruta pide EXPORTAR, y quien solo puede aprobar no veria el diagrama
    expect(peticion.request.params.get('para')).toBe('dibujar');
    peticion.flush('<definitions/>');
    await expect(xml).resolves.toBe('<definitions/>');
  });
});
