import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import { environment } from '../../environments/environment';
import { ESPERA_INICIAL_MS, FuenteDeEventos } from '../bpmn/conexion-en-vivo';
import { EventoDeNotificaciones } from '../models/notificacion.model';
import { ABRIR_FUENTE_DE_EVENTOS, NotificacionService } from './notificacion.service';

class FuenteFalsa implements FuenteDeEventos {
  onopen: (() => void) | null = null;
  onerror: (() => void) | null = null;
  cerrada = false;
  readonly oyentes = new Map<string, (evento: MessageEvent<string>) => void>();

  constructor(readonly url: string) {}

  addEventListener(tipo: string, oyente: (evento: MessageEvent<string>) => void): void {
    this.oyentes.set(tipo, oyente);
  }

  close(): void {
    this.cerrada = true;
  }

  emitir(tipo: string, datos: unknown): void {
    this.oyentes.get(tipo)?.({ data: JSON.stringify(datos) } as MessageEvent<string>);
  }
}

describe('NotificacionService', () => {
  const url = `${environment.apiUrl}/api/v1/notificaciones`;
  let http: HttpTestingController;
  let servicio: NotificacionService;
  let fuentes: FuenteFalsa[];

  beforeEach(() => {
    fuentes = [];
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        {
          provide: ABRIR_FUENTE_DE_EVENTOS,
          useValue: (direccion: string) => {
            const fuente = new FuenteFalsa(direccion);
            fuentes.push(fuente);
            return fuente;
          },
        },
      ],
    });
    http = TestBed.inject(HttpTestingController);
    servicio = TestBed.inject(NotificacionService);
  });

  afterEach(() => {
    http.verify();
    vi.useRealTimers();
  });

  it('lista una pagina, solo las no leidas si se pide, y cuenta las que faltan', () => {
    servicio.listar(true, 1, 10).subscribe();
    servicio.conteo().subscribe();

    const pagina = http.expectOne((req) => req.url === url);
    expect(pagina.request.method).toBe('GET');
    expect(pagina.request.params.get('noLeidas')).toBe('true');
    expect(pagina.request.params.get('pagina')).toBe('1');
    expect(pagina.request.params.get('tamano')).toBe('10');
    pagina.flush({ content: [], page: 1, size: 10, totalElements: 0, totalPages: 0 });
    http.expectOne({ method: 'GET', url: `${url}/conteo` }).flush({ noLeidas: 3 });
  });

  it('por defecto pide la primera pagina de veinte, leidas o no', () => {
    servicio.listar(false).subscribe();

    const pagina = http.expectOne((req) => req.url === url);
    expect(pagina.request.params.get('noLeidas')).toBe('false');
    expect(pagina.request.params.get('pagina')).toBe('0');
    expect(pagina.request.params.get('tamano')).toBe('20');
    pagina.flush({ content: [], page: 0, size: 20, totalElements: 0, totalPages: 0 });
  });

  it('marca una leida y todas leidas con POST', () => {
    servicio.marcarLeida(41).subscribe();
    servicio.marcarTodas().subscribe();

    expect(http.expectOne(`${url}/41/leida`).request.method).toBe('POST');
    expect(http.expectOne(`${url}/leidas`).request.method).toBe('POST');
  });

  it('lee las preferencias y guarda solo las que cambian', () => {
    servicio.preferencias().subscribe();
    servicio.guardarPreferencias([{ tipo: 'HILO_RESUELTO', envio: 'NUNCA' }]).subscribe();

    const [leidas, guardadas] = http.match(`${url}/preferencias`);
    expect(leidas.request.method).toBe('GET');
    expect(guardadas.request.method).toBe('PUT');
    expect(guardadas.request.body).toEqual({ preferencias: [{ tipo: 'HILO_RESUELTO', envio: 'NUNCA' }] });
    leidas.flush([]);
    guardadas.flush([]);
  });

  describe('el flujo en vivo', () => {
    it('abre el flujo de la persona y entrega el conteo y cada aviso nuevo', () => {
      const eventos: EventoDeNotificaciones[] = [];
      const suscripcion = servicio.flujo().subscribe((evento) => eventos.push(evento));

      expect(fuentes).toHaveLength(1);
      expect(fuentes[0].url).toBe(`${url}/flujo`);
      fuentes[0].onopen?.();
      fuentes[0].emitir('conteo', { noLeidas: 2 });
      fuentes[0].emitir('notificacion', { id: 41, tipo: 'MENCION' });
      // Un evento de otro flujo no se escucha
      fuentes[0].emitir('presencia', { personas: [] });

      expect(eventos).toEqual([
        { tipo: 'conectado' },
        { tipo: 'conteo', datos: { noLeidas: 2 } },
        { tipo: 'notificacion', datos: { id: 41, tipo: 'MENCION' } },
      ]);
      suscripcion.unsubscribe();
    });

    it('se repone solo tras un corte, esperando y abriendo otro flujo', () => {
      vi.useFakeTimers();
      const eventos: EventoDeNotificaciones[] = [];
      const suscripcion = servicio.flujo().subscribe((evento) => eventos.push(evento));

      fuentes[0].onerror?.();
      expect(fuentes[0].cerrada).toBe(true);
      expect(eventos).toEqual([{ tipo: 'desconectado' }]);
      expect(fuentes).toHaveLength(1);

      vi.advanceTimersByTime(ESPERA_INICIAL_MS);
      expect(fuentes).toHaveLength(2);
      fuentes[1].onopen?.();
      fuentes[1].emitir('conteo', { noLeidas: 5 });

      expect(eventos.slice(1)).toEqual([{ tipo: 'conectado' }, { tipo: 'conteo', datos: { noLeidas: 5 } }]);
      suscripcion.unsubscribe();
    });

    it('cada corte seguido espera el doble antes de volver a intentar', () => {
      vi.useFakeTimers();
      const suscripcion = servicio.flujo().subscribe();

      fuentes[0].onerror?.();
      vi.advanceTimersByTime(ESPERA_INICIAL_MS);
      fuentes[1].onerror?.();
      vi.advanceTimersByTime(ESPERA_INICIAL_MS / 2 - 1);
      expect(fuentes).toHaveLength(2);
      vi.advanceTimersByTime(ESPERA_INICIAL_MS * 2);
      expect(fuentes).toHaveLength(3);
      suscripcion.unsubscribe();
    });

    it('soltar el flujo lo cierra y cancela la reconexion pendiente', () => {
      vi.useFakeTimers();
      const suscripcion = servicio.flujo().subscribe();

      fuentes[0].onerror?.();
      suscripcion.unsubscribe();
      vi.advanceTimersByTime(60_000);

      expect(fuentes).toHaveLength(1);
    });

    it('soltar un flujo abierto cierra su conexion', () => {
      const suscripcion = servicio.flujo().subscribe();

      suscripcion.unsubscribe();

      expect(fuentes[0].cerrada).toBe(true);
    });
  });
});
