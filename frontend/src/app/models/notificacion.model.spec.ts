import { describe, expect, it } from 'vitest';

import {
  AVISOS_EN_LA_CAMPANA,
  Notificacion,
  TIPOS_DE_NOTIFICACION,
  TipoDeNotificacion,
  conAvisoLeido,
  conAvisoNuevo,
  conTodosLeidos,
  enlaceDeNotificacion,
  textoDeNotificacion,
} from './notificacion.model';

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

describe('enlaceDeNotificacion', () => {
  it('un aviso de un comentario abre su proceso en ese comentario, como el enlace del correo', () => {
    for (const tipo of ['MENCION', 'RESPUESTA', 'HILO_RESUELTO'] as TipoDeNotificacion[]) {
      expect(enlaceDeNotificacion(aviso(1, { tipo }))).toEqual({
        ruta: ['/procesos', 7],
        consulta: { comentario: 12 },
      });
    }
  });

  it('un aviso de una revision abre la pagina de su solicitud', () => {
    const deSolicitud: TipoDeNotificacion[] = [
      'APROBACION_PEDIDA',
      'SOLICITUD_APROBADA',
      'SOLICITUD_PUBLICADA',
      'SOLICITUD_SIN_PUBLICAR',
      'CAMBIOS_PEDIDOS',
      'SOLICITUD_RECHAZADA',
    ];
    for (const tipo of deSolicitud) {
      expect(enlaceDeNotificacion(aviso(1, { tipo, referenciaId: 4 }))).toEqual({
        ruta: ['/solicitudes', 4],
        consulta: null,
      });
    }
  });

  it('una fecha de revision, o un aviso sin referencia, abre el proceso', () => {
    expect(enlaceDeNotificacion(aviso(1, { tipo: 'RECERTIFICACION_VENCIDA', referenciaId: 3 }))).toEqual({
      ruta: ['/procesos', 7],
      consulta: null,
    });
    expect(enlaceDeNotificacion(aviso(1, { tipo: 'MENCION', referenciaId: null }))).toEqual({
      ruta: ['/procesos', 7],
      consulta: null,
    });
    expect(enlaceDeNotificacion(aviso(1, { tipo: 'APROBACION_PEDIDA', referenciaId: null }))).toEqual({
      ruta: ['/procesos', 7],
      consulta: null,
    });
  });
});

describe('textoDeNotificacion', () => {
  it('dice quien hizo que y en que proceso, para cada tipo', () => {
    const textos = TIPOS_DE_NOTIFICACION.map((tipo) => textoDeNotificacion(aviso(1, { tipo })));

    expect(textos).toEqual([
      'Ana mentioned you in Order fulfillment',
      'Ana replied in a thread of Order fulfillment',
      'Ana resolved a thread of Order fulfillment',
      'Ana asks you to approve a change to Order fulfillment',
      'Ana approved your request for Order fulfillment',
      'Your request for Order fulfillment was approved and published',
      'Your request for Order fulfillment was approved but could not be published',
      'Ana asked for changes to your request for Order fulfillment',
      'Ana rejected your request for Order fulfillment',
      'Order fulfillment is due for review',
      'The review of Order fulfillment is overdue',
    ]);
  });

  it('sin autor, alguien', () => {
    expect(textoDeNotificacion(aviso(1, { autor: null }))).toBe('Someone mentioned you in Order fulfillment');
  });
});

describe('la lista de la campana', () => {
  it('un aviso que llega va arriba, sin repetirse y sin pasar del tope', () => {
    const lista = [aviso(3), aviso(2), aviso(1)];

    expect(conAvisoNuevo(lista, aviso(4)).map((uno) => uno.id)).toEqual([4, 3, 2, 1]);
    expect(conAvisoNuevo(lista, aviso(2, { autor: 'Beto' })).map((uno) => [uno.id, uno.autor])).toEqual([
      [2, 'Beto'],
      [3, 'Ana'],
      [1, 'Ana'],
    ]);
    expect(conAvisoNuevo(lista, aviso(4), 2).map((uno) => uno.id)).toEqual([4, 3]);
  });

  it('por defecto conserva veinte', () => {
    const llena = Array.from({ length: AVISOS_EN_LA_CAMPANA }, (_, indice) => aviso(indice + 1));

    expect(AVISOS_EN_LA_CAMPANA).toBe(20);
    expect(conAvisoNuevo(llena, aviso(100))).toHaveLength(20);
    expect(conAvisoNuevo(llena, aviso(100))[19].id).toBe(19);
  });

  it('marcar uno leido cambia solo ese', () => {
    const leida = aviso(2, { leidaEn: '2026-11-12T11:00:00' });

    expect(conAvisoLeido([aviso(1), aviso(2)], leida)).toEqual([aviso(1), leida]);
  });

  it('marcar todos leidos respeta la hora de los que ya lo estaban', () => {
    const lista = [aviso(1), aviso(2, { leidaEn: '2026-11-11T09:00:00' })];

    expect(conTodosLeidos(lista, '2026-11-12T12:00:00').map((uno) => uno.leidaEn)).toEqual([
      '2026-11-12T12:00:00',
      '2026-11-11T09:00:00',
    ]);
  });
});
