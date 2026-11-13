import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';

import { environment } from '../../environments/environment';
import { ManualService } from './manual.service';

describe('ManualService', () => {
  const api = `${environment.apiUrl}/api/v1`;
  let http: HttpTestingController;
  let manuales: ManualService;

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
    http = TestBed.inject(HttpTestingController);
    manuales = TestBed.inject(ManualService);
  });

  afterEach(() => http.verify());

  it('pide el manual de la version vigente sin cuerpo, o el de una version con version', () => {
    manuales.pedir(4).subscribe();
    manuales.pedir(4, 2).subscribe();

    const [vigente, segunda] = http.match((req) => req.url === `${api}/procesos/4/manuales`);
    expect(vigente.request.method).toBe('POST');
    expect(vigente.request.body).toBeNull();
    expect(vigente.request.params.has('version')).toBe(false);
    expect(segunda.request.params.get('version')).toBe('2');
    vigente.flush({ id: 5, estado: 'PENDIENTE' }, { status: 202, statusText: 'Accepted' });
    segunda.flush({ id: 6, estado: 'PENDIENTE' }, { status: 202, statusText: 'Accepted' });
  });

  it('lista los manuales del proceso, lee uno y pide su enlace en el formato elegido', () => {
    manuales.listar(4).subscribe();
    manuales.obtener(5).subscribe();
    manuales.enlace(5, 'html').subscribe();

    expect(http.expectOne(`${api}/procesos/4/manuales`).request.method).toBe('GET');
    expect(http.expectOne(`${api}/manuales/5`).request.method).toBe('GET');
    const enlace = http.expectOne((req) => req.url === `${api}/manuales/5/enlace`);
    expect(enlace.request.params.get('formato')).toBe('html');
    enlace.flush({ url: '/api/v1/descargas/manuales/5/html?firma=x', venceEn: '' });
  });
});
