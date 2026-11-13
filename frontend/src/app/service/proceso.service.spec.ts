import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { firstValueFrom } from 'rxjs';

import { environment } from '../../environments/environment';
import { filtrosIniciales } from '../helpers/filtros-en-url';
import { FichaRequest } from '../models/proceso.model';
import { ProcesoService } from './proceso.service';

describe('ProcesoService', () => {
  const url = `${environment.apiUrl}/api/v1/procesos`;
  const vacia = { content: [], page: 0, size: 10, totalElements: 0, totalPages: 0 };
  let http: HttpTestingController;
  let procesos: ProcesoService;

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
    http = TestBed.inject(HttpTestingController);
    procesos = TestBed.inject(ProcesoService);
  });

  afterEach(() => http.verify());

  it('sin filtros manda solo la pagina y el orden', async () => {
    const pagina = firstValueFrom(procesos.listar(filtrosIniciales()));

    const peticion = http.expectOne((req) => req.url === url);
    expect(peticion.request.params.keys().sort()).toEqual(['orden', 'pagina']);
    expect(peticion.request.params.get('orden')).toBe('fechaModificacion,desc');
    peticion.flush(vacia);
    await pagina;
  });

  it('cada filtro del repositorio viaja con el nombre que espera la API', async () => {
    const pagina = firstValueFrom(
      procesos.listar({
        ...filtrosIniciales(),
        nombre: ' credit ',
        carpetaId: 3,
        duenoId: 8,
        etiqueta: ' gdpr ',
        clasificacion: 'RESTRINGIDA',
        revisionAntesDe: '2027-03-01',
        orden: 'clasificacion',
        direccion: 'asc',
        pagina: 1,
      }),
    );

    const peticion = http.expectOne((req) => req.url === url);
    const params = peticion.request.params;
    expect(params.get('nombre')).toBe('credit');
    expect(params.get('carpetaId')).toBe('3');
    expect(params.get('duenoId')).toBe('8');
    expect(params.get('etiqueta')).toBe('gdpr');
    expect(params.get('clasificacion')).toBe('RESTRINGIDA');
    expect(params.get('revisionAntesDe')).toBe('2027-03-01');
    expect(params.get('orden')).toBe('clasificacion,asc');
    expect(params.get('pagina')).toBe('1');
    peticion.flush(vacia);
    await pagina;
  });

  it('mover un proceso es guardar su ficha', async () => {
    const ficha: FichaRequest = {
      carpetaId: 2,
      duenoId: null,
      proximaRevision: null,
      clasificacion: 'INTERNA',
      etiquetas: [],
      version: 3,
    };
    const guardado = firstValueFrom(procesos.editarFicha(7, ficha));

    const peticion = http.expectOne(`${url}/7/ficha`);
    expect(peticion.request.method).toBe('PUT');
    expect(peticion.request.body).toEqual(ficha);
    peticion.flush({ id: 7 });
    await guardado;
  });
});
