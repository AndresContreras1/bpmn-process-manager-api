import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { firstValueFrom } from 'rxjs';

import { environment } from '../../environments/environment';
import { Concesion, ConcesionRequest } from '../models/concesion.model';
import { ConcesionService } from './concesion.service';

describe('ConcesionService', () => {
  const api = `${environment.apiUrl}/api/v1`;
  const concesion: Concesion = {
    id: 5,
    carpetaId: null,
    procesoId: 4,
    sujeto: 'PERSONA',
    usuarioId: 2,
    rolProcesoId: null,
    accion: 'VER',
  };
  let http: HttpTestingController;
  let concesiones: ConcesionService;

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
    http = TestBed.inject(HttpTestingController);
    concesiones = TestBed.inject(ConcesionService);
  });

  afterEach(() => http.verify());

  it('pregunta lo que la persona puede hacer con un proceso y devuelve solo las acciones', async () => {
    const acciones = firstValueFrom(concesiones.permisos(4));

    const peticion = http.expectOne(`${api}/procesos/4/permisos`);
    expect(peticion.request.method).toBe('GET');
    peticion.flush({ acciones: ['VER', 'MODELAR'] });
    await expect(acciones).resolves.toEqual(['VER', 'MODELAR']);
  });

  it('lista las concesiones de un proceso y las de una carpeta, cada una en su ruta', async () => {
    const delProceso = firstValueFrom(concesiones.listar('procesos', 4));
    const deCarpeta = firstValueFrom(concesiones.listar('carpetas', 9));

    http.expectOne({ method: 'GET', url: `${api}/procesos/4/concesiones` }).flush([concesion]);
    http.expectOne({ method: 'GET', url: `${api}/carpetas/9/concesiones` }).flush([]);
    await expect(delProceso).resolves.toEqual([concesion]);
    await expect(deCarpeta).resolves.toEqual([]);
  });

  it('concede con POST al recurso y el cuerpo tal cual', async () => {
    const solicitud: ConcesionRequest = { accion: 'VER', sujeto: 'PERSONA', usuarioId: 2, rolProcesoId: null };
    const creada = firstValueFrom(concesiones.conceder('carpetas', 9, solicitud));

    const peticion = http.expectOne(`${api}/carpetas/9/concesiones`);
    expect(peticion.request.method).toBe('POST');
    expect(peticion.request.body).toEqual(solicitud);
    peticion.flush(concesion, { status: 201, statusText: 'Created' });
    await expect(creada).resolves.toEqual(concesion);
  });

  it('retira con DELETE a la concesion, este donde este', async () => {
    const retirada = firstValueFrom(concesiones.retirar(5));

    const peticion = http.expectOne(`${api}/concesiones/5`);
    expect(peticion.request.method).toBe('DELETE');
    peticion.flush(null, { status: 204, statusText: 'No Content' });
    await expect(retirada).resolves.toBeNull();
  });
});
