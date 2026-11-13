import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { firstValueFrom } from 'rxjs';

import { environment } from '../../environments/environment';
import { RolProcesoService } from './rol-proceso.service';

describe('RolProcesoService', () => {
  const url = `${environment.apiUrl}/api/v1/roles`;
  let http: HttpTestingController;
  let roles: RolProcesoService;

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
    http = TestBed.inject(HttpTestingController);
    roles = TestBed.inject(RolProcesoService);
  });

  afterEach(() => http.verify());

  it('pide un rol por su id, con los procesos que lo usan', async () => {
    const rol = firstValueFrom(roles.obtener(3));

    const peticion = http.expectOne(`${url}/3`);
    expect(peticion.request.method).toBe('GET');
    peticion.flush({ id: 3, nombre: 'Warehouse', procesos: [{ id: 1, nombre: 'Order fulfillment' }] });
    await expect(rol).resolves.toEqual(expect.objectContaining({ procesos: [{ id: 1, nombre: 'Order fulfillment' }] }));
  });
});
