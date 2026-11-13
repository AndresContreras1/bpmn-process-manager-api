import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, TestRequest, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { firstValueFrom } from 'rxjs';

import { environment } from '../../environments/environment';
import { ProcesoDelPortal } from '../models/portal.model';
import { PortalService } from './portal.service';

describe('PortalService', () => {
  const api = `${environment.apiUrl}/api/v1`;
  let http: HttpTestingController;
  let portal: PortalService;

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
    http = TestBed.inject(HttpTestingController);
    portal = TestBed.inject(PortalService);
  });

  afterEach(() => http.verify());

  function detalle(versionPublicada: number | null): object {
    return { proceso: { id: 7, nombre: 'Order fulfillment', versionPublicada }, historial: [], duenoNombre: 'Ana' };
  }

  /** Las seis lecturas que abren un proceso, en el orden en que se piden. */
  function lecturas(): Record<string, TestRequest> {
    return {
      detalle: http.expectOne({ method: 'GET', url: `${api}/procesos/7` }),
      permisos: http.expectOne({ method: 'GET', url: `${api}/procesos/7/permisos` }),
      caracterizacion: http.expectOne({ method: 'GET', url: `${api}/procesos/7/caracterizacion` }),
      textos: http.expectOne({ method: 'GET', url: `${api}/procesos/7/documentacion` }),
      documentos: http.expectOne((req) => req.method === 'GET' && req.url === `${api}/procesos/7/documentos`),
      manuales: http.expectOne({ method: 'GET', url: `${api}/procesos/7/manuales` }),
    };
  }

  it('lista lo publicado de una carpeta por nombre, en la pagina pedida', () => {
    portal.publicados(4, 2).subscribe();

    const peticion = http.expectOne((req) => req.url === `${api}/procesos`);
    expect(peticion.request.method).toBe('GET');
    expect(peticion.request.params.get('estado')).toBe('PUBLICADO');
    expect(peticion.request.params.get('carpetaId')).toBe('4');
    expect(peticion.request.params.get('orden')).toBe('nombre,asc');
    expect(peticion.request.params.get('pagina')).toBe('2');
    peticion.flush({ content: [], page: 2, size: 10, totalElements: 0, totalPages: 0 });
  });

  it('sin carpeta lista lo publicado de la organizacion entera', () => {
    portal.publicados(null, 0).subscribe();

    const peticion = http.expectOne((req) => req.url === `${api}/procesos`);
    expect(peticion.request.params.has('carpetaId')).toBe(false);
    expect(peticion.request.params.get('estado')).toBe('PUBLICADO');
    peticion.flush({ content: [], page: 0, size: 10, totalElements: 0, totalPages: 0 });
  });

  it('busca con la API del PR 65 y quita lo que no es del portal', async () => {
    const encontrados = firstValueFrom(portal.buscar('packing'));

    const peticion = http.expectOne((req) => req.url === `${api}/busqueda`);
    expect(peticion.request.params.get('q')).toBe('packing');
    expect(peticion.request.params.has('tipo')).toBe(false);
    peticion.flush({
      content: [
        { tipo: 'PROCESO', procesoId: 7, titulo: 'Order fulfillment', fragmento: [] },
        { tipo: 'COMENTARIO', procesoId: 7, titulo: 'Ana', fragmento: [] },
      ],
      page: 0,
      size: 20,
      totalElements: 2,
      totalPages: 1,
    });
    await expect(encontrados).resolves.toEqual([
      { tipo: 'PROCESO', procesoId: 7, titulo: 'Order fulfillment', fragmento: [] },
    ]);
  });

  it('abre un proceso publicado con todo lo suyo y el dibujo de la version vigente, pedido para dibujarlo', async () => {
    const abierto = firstValueFrom(portal.abrir(7));

    const pedidas = lecturas();
    pedidas['detalle'].flush(detalle(3));
    pedidas['permisos'].flush({ acciones: ['VER', 'EXPORTAR'] });
    pedidas['caracterizacion'].flush({ objetivo: 'Deliver' });
    pedidas['textos'].flush([{ bpmnId: 'Task_1', html: '<p>Pack</p>' }]);
    expect(pedidas['documentos'].request.params.has('elemento')).toBe(false);
    pedidas['documentos'].flush([{ id: 1 }]);
    pedidas['manuales'].flush([{ id: 5 }]);
    const modelo = http.expectOne((req) => req.url === `${api}/procesos/7/versiones/3/modelo`);
    expect(modelo.request.params.get('para')).toBe('dibujar');
    expect(modelo.request.responseType).toBe('text');
    modelo.flush('<bpmn:definitions/>');

    await expect(abierto).resolves.toEqual({
      detalle: detalle(3),
      acciones: ['VER', 'EXPORTAR'],
      caracterizacion: { objetivo: 'Deliver' },
      textos: [{ bpmnId: 'Task_1', html: '<p>Pack</p>' }],
      documentos: [{ id: 1 }],
      manuales: [{ id: 5 }],
      xml: '<bpmn:definitions/>',
    } as unknown as ProcesoDelPortal);
  });

  it('sin version publicada no pide dibujo', async () => {
    const abierto = firstValueFrom(portal.abrir(7));

    const pedidas = lecturas();
    pedidas['detalle'].flush(detalle(null));
    pedidas['permisos'].flush({ acciones: ['VER'] });
    pedidas['caracterizacion'].flush({});
    pedidas['textos'].flush([]);
    pedidas['documentos'].flush([]);
    pedidas['manuales'].flush([]);
    http.expectNone((req) => req.url.includes('/versiones/'));

    await expect(abierto).resolves.toMatchObject({ xml: null });
  });

  it('si fallan los complementos, el proceso se abre igual y sin permisos se queda en lectura', async () => {
    const abierto = firstValueFrom(portal.abrir(7));
    const caido = { status: 500, statusText: 'Server Error' };

    const pedidas = lecturas();
    pedidas['detalle'].flush(detalle(1));
    pedidas['permisos'].flush(null, caido);
    pedidas['caracterizacion'].flush(null, caido);
    pedidas['textos'].flush(null, caido);
    pedidas['documentos'].flush(null, caido);
    pedidas['manuales'].flush(null, caido);
    http.expectOne((req) => req.url === `${api}/procesos/7/versiones/1/modelo`).flush('<xml/>');

    await expect(abierto).resolves.toEqual({
      detalle: detalle(1),
      acciones: null,
      caracterizacion: null,
      textos: [],
      documentos: [],
      manuales: [],
      xml: '<xml/>',
    } as unknown as ProcesoDelPortal);
  });

  it('si el detalle falla, el error sigue: un 404 es que el proceso no esta para esta persona', async () => {
    const abierto = firstValueFrom(portal.abrir(7));

    const pedidas = lecturas();
    pedidas['detalle'].flush(null, { status: 404, statusText: 'Not Found' });
    for (const otra of ['permisos', 'caracterizacion', 'textos', 'documentos', 'manuales']) {
      if (!pedidas[otra].cancelled) {
        pedidas[otra].flush([]);
      }
    }

    await expect(abierto).rejects.toMatchObject({ status: 404 });
  });

  it('si el dibujo de la version falla, el error sigue', async () => {
    const abierto = firstValueFrom(portal.abrir(7));

    const pedidas = lecturas();
    pedidas['detalle'].flush(detalle(2));
    pedidas['permisos'].flush({ acciones: ['VER'] });
    pedidas['caracterizacion'].flush({});
    pedidas['textos'].flush([]);
    pedidas['documentos'].flush([]);
    pedidas['manuales'].flush([]);
    http
      .expectOne((req) => req.url === `${api}/procesos/7/versiones/2/modelo`)
      .flush('', { status: 500, statusText: 'Server Error' });

    await expect(abierto).rejects.toMatchObject({ status: 500 });
  });
});
