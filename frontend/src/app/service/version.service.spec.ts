import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { firstValueFrom } from 'rxjs';

import { environment } from '../../environments/environment';
import { VersionService } from './version.service';

describe('VersionService', () => {
  const procesos = `${environment.apiUrl}/api/v1/procesos`;
  let api: HttpTestingController;
  let versiones: VersionService;

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
    api = TestBed.inject(HttpTestingController);
    versiones = TestBed.inject(VersionService);
  });

  afterEach(() => api.verify());

  it('manda el dibujo de una version como SVG, tal cual, con PUT a su ruta', async () => {
    const svg = '<svg xmlns="http://www.w3.org/2000/svg"><rect width="10" height="10"/></svg>';
    const enviado = firstValueFrom(versiones.guardarDibujo(4, 2, svg));

    const peticion = api.expectOne(`${procesos}/4/versiones/2/dibujo`);
    expect(peticion.request.method).toBe('PUT');
    expect(peticion.request.headers.get('Content-Type')).toBe('image/svg+xml');
    expect(peticion.request.body).toBe(svg);
    peticion.flush(null, { status: 204, statusText: 'No Content' });
    await expect(enviado).resolves.toBeNull();
  });

  it('pide el modelo de una version para dibujarlo, como texto', async () => {
    const modelo = firstValueFrom(versiones.modelo(4, 2));

    const peticion = api.expectOne((req) => req.url === `${procesos}/4/versiones/2/modelo`);
    expect(peticion.request.params.get('para')).toBe('dibujar');
    expect(peticion.request.responseType).toBe('text');
    peticion.flush('<definitions/>');
    await expect(modelo).resolves.toBe('<definitions/>');
  });
});
