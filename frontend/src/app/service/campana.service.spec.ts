import { TestBed } from '@angular/core/testing';
import { Observable, Subject, of, throwError } from 'rxjs';
import { beforeEach, describe, expect, it, vi } from 'vitest';

import { EventoDeNotificaciones, Notificacion } from '../models/notificacion.model';
import { CampanaService } from './campana.service';
import { NotificacionService } from './notificacion.service';

function aviso(id: number, cambios: Partial<Notificacion> = {}): Notificacion {
  return {
    id,
    tipo: 'MENCION',
    procesoId: 7,
    proceso: 'Order fulfillment',
    referenciaId: 12,
    autorId: 2,
    autor: 'Ana',
    creadaEn: '2026-11-12T10:00:00',
    leidaEn: null,
    ...cambios,
  };
}

describe('CampanaService', () => {
  let flujo: Subject<EventoDeNotificaciones>;
  let abiertos: number;
  let notificaciones: {
    flujo: () => Observable<EventoDeNotificaciones>;
    listar: ReturnType<typeof vi.fn>;
    marcarLeida: ReturnType<typeof vi.fn>;
    marcarTodas: ReturnType<typeof vi.fn>;
  };
  let campana: CampanaService;

  beforeEach(() => {
    flujo = new Subject<EventoDeNotificaciones>();
    abiertos = 0;
    notificaciones = {
      flujo: () => {
        abiertos++;
        return flujo;
      },
      listar: vi.fn(() => of({ content: [aviso(2), aviso(1, { leidaEn: '2026-11-11T09:00:00' })] })),
      marcarLeida: vi.fn((id: number) => of(aviso(id, { leidaEn: '2026-11-12T11:00:00' }))),
      marcarTodas: vi.fn(() => of({ noLeidas: 0 })),
    };
    TestBed.configureTestingModule({ providers: [{ provide: NotificacionService, useValue: notificaciones }] });
    campana = TestBed.inject(CampanaService);
  });

  it('el conteo es el que manda el flujo, al abrirse y cada vez que cambia', () => {
    campana.iniciar();
    flujo.next({ tipo: 'conectado' });
    flujo.next({ tipo: 'conteo', datos: { noLeidas: 3 } });

    expect(campana.conectada()).toBe(true);
    expect(campana.noLeidas()).toBe(3);
    flujo.next({ tipo: 'desconectado' });
    expect(campana.conectada()).toBe(false);
  });

  it('iniciar dos veces no abre dos flujos', () => {
    campana.iniciar();
    campana.iniciar();

    expect(abiertos).toBe(1);
  });

  it('un aviso en vivo va arriba de la lista y se anuncia en palabras', () => {
    campana.iniciar();
    campana.cargar();
    flujo.next({ tipo: 'notificacion', datos: aviso(3, { autor: 'Beto' }) });

    expect(campana.avisos().map((uno) => uno.id)).toEqual([3, 2, 1]);
    expect(campana.anuncio()).toBe('New notification: Beto mentioned you in Order fulfillment');
  });

  it('al abrirla pide los ultimos veinte, leidos o no', () => {
    campana.cargar();

    expect(notificaciones.listar).toHaveBeenCalledWith(false, 0, 20);
    expect(campana.avisos()).toHaveLength(2);
    expect(campana.cargando()).toBe(false);
    expect(campana.error()).toBe(false);
  });

  it('si la lista no llega lo dice, y deja de cargar', () => {
    notificaciones.listar.mockReturnValue(throwError(() => new Error('caida')));

    campana.cargar();

    expect(campana.error()).toBe(true);
    expect(campana.cargando()).toBe(false);
  });

  it('marcar uno leido lo cambia en la lista y adelanta el conteo, sin bajar de cero', () => {
    campana.iniciar();
    flujo.next({ tipo: 'conteo', datos: { noLeidas: 1 } });
    campana.cargar();

    campana.marcarLeida(campana.avisos()[0]);
    expect(notificaciones.marcarLeida).toHaveBeenCalledWith(2);
    expect(campana.avisos()[0].leidaEn).toBe('2026-11-12T11:00:00');
    expect(campana.noLeidas()).toBe(0);

    campana.marcarLeida(aviso(5));
    expect(campana.noLeidas()).toBe(0);
  });

  it('uno que ya estaba leido no se vuelve a mandar', () => {
    campana.marcarLeida(aviso(1, { leidaEn: '2026-11-11T09:00:00' }));

    expect(notificaciones.marcarLeida).not.toHaveBeenCalled();
  });

  it('si marcar leido falla lo dice', () => {
    notificaciones.marcarLeida.mockReturnValue(throwError(() => new Error('caida')));

    campana.marcarLeida(aviso(2));

    expect(campana.error()).toBe(true);
  });

  it('todos leidos deja el conteo de la API y cada aviso con su hora', () => {
    campana.iniciar();
    flujo.next({ tipo: 'conteo', datos: { noLeidas: 4 } });
    campana.cargar();

    campana.marcarTodas();

    expect(campana.noLeidas()).toBe(0);
    expect(campana.avisos().every((uno) => uno.leidaEn !== null)).toBe(true);
    expect(campana.avisos()[1].leidaEn).toBe('2026-11-11T09:00:00');
  });

  it('si marcar todos falla lo dice y el conteo sigue', () => {
    notificaciones.marcarTodas.mockReturnValue(throwError(() => new Error('caida')));
    campana.iniciar();
    flujo.next({ tipo: 'conteo', datos: { noLeidas: 4 } });

    campana.marcarTodas();

    expect(campana.error()).toBe(true);
    expect(campana.noLeidas()).toBe(4);
  });

  it('detener cierra el flujo y olvida lo de esta persona', () => {
    campana.iniciar();
    flujo.next({ tipo: 'conectado' });
    flujo.next({ tipo: 'conteo', datos: { noLeidas: 2 } });
    campana.cargar();
    flujo.next({ tipo: 'notificacion', datos: aviso(3) });

    campana.detener();

    expect(flujo.observed).toBe(false);
    expect(campana.noLeidas()).toBe(0);
    expect(campana.avisos()).toEqual([]);
    expect(campana.conectada()).toBe(false);
    expect(campana.anuncio()).toBe('');
    campana.iniciar();
    expect(abiertos).toBe(2);
  });
});
