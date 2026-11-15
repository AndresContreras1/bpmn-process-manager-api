import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { afterEach, beforeEach, describe, expect, it } from 'vitest';

import { environment } from '../../environments/environment';
import { SolicitudService } from './solicitud.service';

describe('SolicitudService', () => {
  const api = `${environment.apiUrl}/api/v1`;
  let http: HttpTestingController;
  let servicio: SolicitudService;

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
    http = TestBed.inject(HttpTestingController);
    servicio = TestBed.inject(SolicitudService);
  });

  afterEach(() => http.verify());

  it('pide la revision con su texto, o sin texto si va en blanco', () => {
    servicio.pedir(7, '  Adds the fraud check ').subscribe();
    servicio.pedir(7, '   ').subscribe();

    const [conTexto, sinTexto] = http.match(`${api}/procesos/7/solicitudes`);
    expect(conTexto.request.method).toBe('POST');
    expect(conTexto.request.body).toEqual({ markdown: 'Adds the fraud check' });
    expect(sinTexto.request.body).toEqual({});
  });

  it('lista las de un proceso, filtradas por estado si se dice', () => {
    servicio.delProceso(7, 'ABIERTA').subscribe();
    servicio.delProceso(7).subscribe();

    const [abiertas, todas] = http.match((req) => req.url === `${api}/procesos/7/solicitudes`);
    expect(abiertas.request.method).toBe('GET');
    expect(abiertas.request.params.get('estado')).toBe('ABIERTA');
    expect(todas.request.params.has('estado')).toBe(false);
  });

  it('la bandeja pide lo que espera mi decision, o todas con su estado', () => {
    servicio.listar(true, null, 0).subscribe();
    servicio.listar(false, 'RECHAZADA', 2, 10).subscribe();

    const [mias, otras] = http.match((req) => req.url === `${api}/solicitudes`);
    expect(mias.request.params.get('pendientesDeMi')).toBe('true');
    expect(mias.request.params.has('estado')).toBe(false);
    expect(mias.request.params.get('tamano')).toBe('20');
    expect(otras.request.params.has('pendientesDeMi')).toBe(false);
    expect(otras.request.params.get('estado')).toBe('RECHAZADA');
    expect(otras.request.params.get('pagina')).toBe('2');
    expect(otras.request.params.get('tamano')).toBe('10');
  });

  it('cada decision va a su ruta, con el texto o sin el', () => {
    servicio.decidir(4, 'APROBADA', '').subscribe();
    servicio.decidir(4, 'CAMBIOS_PEDIDOS', 'Add a timeout').subscribe();
    servicio.decidir(4, 'RECHAZADA', ' No ').subscribe();
    servicio.retirar(4).subscribe();
    servicio.obtener(4).subscribe();

    const aprobada = http.expectOne(`${api}/solicitudes/4/aprobar`);
    expect(aprobada.request.method).toBe('POST');
    expect(aprobada.request.body).toEqual({});
    expect(http.expectOne(`${api}/solicitudes/4/pedir-cambios`).request.body).toEqual({ markdown: 'Add a timeout' });
    expect(http.expectOne(`${api}/solicitudes/4/rechazar`).request.body).toEqual({ markdown: 'No' });
    expect(http.expectOne(`${api}/solicitudes/4/retirar`).request.method).toBe('POST');
    expect(http.expectOne(`${api}/solicitudes/4`).request.method).toBe('GET');
  });

  it('lee la politica y la guarda con PUT', () => {
    const politica = {
      modo: 'DIRECTA' as const,
      aprobacionesNecesarias: 1,
      rolProcesoId: null,
      aprobadores: [],
      version: 0,
    };
    servicio.politica().subscribe();
    servicio.guardarPolitica(politica).subscribe();

    const [leida, guardada] = http.match(`${api}/empresas/actual/politica-de-publicacion`);
    expect(leida.request.method).toBe('GET');
    expect(guardada.request.method).toBe('PUT');
    expect(guardada.request.body).toEqual(politica);
  });
});
