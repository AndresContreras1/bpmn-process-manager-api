import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { firstValueFrom } from 'rxjs';

import { environment } from '../../environments/environment';
import { BusquedaService } from './busqueda.service';

describe('BusquedaService', () => {
  const url = `${environment.apiUrl}/api/v1/busqueda`;
  const vacia = { content: [], page: 0, size: 20, totalElements: 0, totalPages: 0 };
  let http: HttpTestingController;
  let busqueda: BusquedaService;

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
    http = TestBed.inject(HttpTestingController);
    busqueda = TestBed.inject(BusquedaService);
  });

  afterEach(() => http.verify());

  it('manda el texto con su sintaxis, la pagina y el tamano, sin tipo si no se pide', async () => {
    const pagina = firstValueFrom(busqueda.buscar('"credit approval" -card'));

    const peticion = http.expectOne((req) => req.url === url);
    expect(peticion.request.method).toBe('GET');
    expect(peticion.request.params.get('q')).toBe('"credit approval" -card');
    expect(peticion.request.params.get('pagina')).toBe('0');
    // Veinte, dentro del tope de la API: un numero escrito, para que cambiar la constante no pase sin verse
    expect(peticion.request.params.get('tamano')).toBe('20');
    expect(peticion.request.params.has('tipo')).toBe(false);
    expect(peticion.request.params.has('publicado')).toBe(false);
    peticion.flush(vacia);
    await expect(pagina).resolves.toEqual(vacia);
  });

  it('solo lo publicado, si se pide', async () => {
    const pagina = firstValueFrom(busqueda.buscar('pack', null, 0, true));

    const peticion = http.expectOne((req) => req.url === url);
    expect(peticion.request.params.get('publicado')).toBe('true');
    expect(peticion.request.params.has('tipo')).toBe(false);
    peticion.flush(vacia);
    await pagina;
  });

  it('con un tipo, solo ese tipo, y la pagina pedida', async () => {
    const pagina = firstValueFrom(busqueda.buscar('pack', 'ELEMENTO', 2));

    const peticion = http.expectOne((req) => req.url === url);
    expect(peticion.request.params.get('tipo')).toBe('ELEMENTO');
    expect(peticion.request.params.get('pagina')).toBe('2');
    peticion.flush(vacia);
    await pagina;
  });
});
