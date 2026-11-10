import { HttpClient, provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';

import { VerificacionService } from '../service/verificacion.service';
import { VERIFICACION_REQUERIDA, verificacionInterceptor } from './verificacion.interceptor';

describe('verificacionInterceptor', () => {
  let http: HttpClient;
  let api: HttpTestingController;
  let verificacion: VerificacionService;
  const pedida = { status: 403, statusText: 'Forbidden' };

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(withInterceptors([verificacionInterceptor])), provideHttpClientTesting()],
    });
    http = TestBed.inject(HttpClient);
    api = TestBed.inject(HttpTestingController);
    verificacion = TestBed.inject(VerificacionService);
  });

  afterEach(() => api.verify());

  it('pide confirmar la identidad y repite la peticion cuando la persona la da', () => {
    let hecho = false;
    http.delete('/api/v1/auth/segundo-factor/totp').subscribe(() => (hecho = true));
    api.expectOne('/api/v1/auth/segundo-factor/totp').flush({ title: VERIFICACION_REQUERIDA }, pedida);

    expect(verificacion.pidiendo()).toBe(true);
    verificacion.confirmada();

    expect(verificacion.pidiendo()).toBe(false);
    api.expectOne('/api/v1/auth/segundo-factor/totp').flush(null);
    expect(hecho).toBe(true);
  });

  it('si la persona cancela, la peticion falla con el 403 de siempre', () => {
    let estado: number | undefined;
    http.delete('/api/v1/empresas/actual/baja').subscribe({ error: (e) => (estado = e.status) });
    api.expectOne('/api/v1/empresas/actual/baja').flush({ title: VERIFICACION_REQUERIDA }, pedida);

    verificacion.cancelada();

    expect(estado).toBe(403);
  });

  it('un 403 de falta de permisos no abre nada', () => {
    let estado: number | undefined;
    http.get('/api/v1/usuarios').subscribe({ error: (e) => (estado = e.status) });
    api.expectOne('/api/v1/usuarios').flush({ title: 'Sin permisos' }, pedida);

    expect(verificacion.pidiendo()).toBe(false);
    expect(estado).toBe(403);
  });

  it('dos peticiones que la piden a la vez comparten un solo dialogo', () => {
    let hechas = 0;
    http.post('/api/v1/auth/segundo-factor/codigos', null).subscribe(() => hechas++);
    http.delete('/api/v1/auth/identidades/3').subscribe(() => hechas++);
    api.expectOne('/api/v1/auth/segundo-factor/codigos').flush({ title: VERIFICACION_REQUERIDA }, pedida);
    api.expectOne('/api/v1/auth/identidades/3').flush({ title: VERIFICACION_REQUERIDA }, pedida);

    verificacion.confirmada();

    api.expectOne('/api/v1/auth/segundo-factor/codigos').flush({ codigos: [] });
    api.expectOne('/api/v1/auth/identidades/3').flush(null);
    expect(hechas).toBe(2);
  });
});
