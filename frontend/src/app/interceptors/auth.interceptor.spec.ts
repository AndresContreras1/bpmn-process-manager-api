import { HttpClient, provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { of } from 'rxjs';

import { AuthService } from '../service/auth.service';
import { authInterceptor } from './auth.interceptor';

describe('authInterceptor', () => {
  const sesion = {
    estaAutenticado: vi.fn(() => true),
    renovar: vi.fn(() => of(undefined)),
    cerrarSesionLocal: vi.fn(),
  };
  let http: HttpClient;
  let api: HttpTestingController;

  beforeEach(() => {
    vi.clearAllMocks();
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(withInterceptors([authInterceptor])),
        provideHttpClientTesting(),
        provideRouter([]),
        { provide: AuthService, useValue: sesion },
      ],
    });
    http = TestBed.inject(HttpClient);
    api = TestBed.inject(HttpTestingController);
  });

  afterEach(() => api.verify());

  it('el 401 de entrar es la respuesta: no se renueva nada', () => {
    let error: number | undefined;
    http.post('/api/v1/auth/login', {}).subscribe({ error: (e) => (error = e.status) });
    api.expectOne('/api/v1/auth/login').flush(null, { status: 401, statusText: 'Unauthorized' });

    expect(error).toBe(401);
    expect(sesion.renovar).not.toHaveBeenCalled();
  });

  it('el 401 de las preferencias es un acceso vencido: se renueva y se repite la peticion', () => {
    let respuesta: unknown;
    http.get('/api/v1/auth/preferencias').subscribe((r) => (respuesta = r));
    api.expectOne('/api/v1/auth/preferencias').flush(null, { status: 401, statusText: 'Unauthorized' });
    api.expectOne('/api/v1/auth/preferencias').flush({ idioma: 'es-CO' });

    expect(sesion.renovar).toHaveBeenCalledTimes(1);
    expect(respuesta).toEqual({ idioma: 'es-CO' });
  });
});
