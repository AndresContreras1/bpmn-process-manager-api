import { beforeEach, describe, expect, it } from 'vitest';

import { EventoEnVivo } from '../models/en-vivo.model';
import {
  ConexionEnVivo,
  ESPERA_INICIAL_MS,
  ESPERA_MAXIMA_MS,
  EventoDeConexion,
  FuenteDeEventos,
  TIPOS_EN_VIVO,
  esperaDeReconexion,
} from './conexion-en-vivo';

class FuenteFalsa implements FuenteDeEventos {
  onopen: (() => void) | null = null;
  onerror: (() => void) | null = null;
  cerrada = false;
  readonly oyentes = new Map<string, (evento: MessageEvent<string>) => void>();

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

describe('esperaDeReconexion', () => {
  it('se duplica con cada intento', () => {
    const siempre = () => 1;

    expect([0, 1, 2, 3].map((intentos) => esperaDeReconexion(intentos, siempre))).toEqual([1000, 2000, 4000, 8000]);
  });

  it('no pasa del tope', () => {
    expect(esperaDeReconexion(20, () => 1)).toBe(ESPERA_MAXIMA_MS);
  });

  it('el azar la deja entre la mitad y el total', () => {
    expect(esperaDeReconexion(0, () => 0)).toBe(ESPERA_INICIAL_MS / 2);
    expect(esperaDeReconexion(0, () => 0.5)).toBe(750);
  });
});

describe('ConexionEnVivo', () => {
  let fuentes: FuenteFalsa[];
  let esperas: { accion: () => void; ms: number; cancelada: boolean }[];
  let eventos: EventoEnVivo[];
  let conexion: ConexionEnVivo;

  beforeEach(() => {
    fuentes = [];
    esperas = [];
    eventos = [];
    conexion = new ConexionEnVivo(
      {
        abrir: () => {
          const fuente = new FuenteFalsa();
          fuentes.push(fuente);
          return fuente;
        },
        esperar: (accion, ms) => {
          const espera = { accion, ms, cancelada: false };
          esperas.push(espera);
          return espera;
        },
        cancelar: (marca) => ((marca as { cancelada: boolean }).cancelada = true),
        azar: () => 1,
      },
      (evento) => eventos.push(evento),
    );
    conexion.iniciar();
  });

  it('avisa al conectar y entrega los eventos ya leidos', () => {
    fuentes[0].onopen?.();
    fuentes[0].emitir('presencia', { personas: [{ usuarioId: 1, nombre: 'Ana' }] });

    expect(eventos).toEqual([
      { tipo: 'conectado' },
      { tipo: 'presencia', datos: { personas: [{ usuarioId: 1, nombre: 'Ana' }] } },
    ]);
  });

  it('un corte cierra la conexion, avisa y espera cada vez mas', () => {
    fuentes[0].onerror?.();
    expect(fuentes[0].cerrada).toBe(true);
    expect(eventos).toEqual([{ tipo: 'desconectado' }]);
    expect(esperas[0].ms).toBe(1000);

    esperas[0].accion();
    expect(fuentes).toHaveLength(2);
    fuentes[1].onerror?.();

    expect(esperas[1].ms).toBe(2000);
  });

  it('una conexion que abre reinicia la espera', () => {
    fuentes[0].onerror?.();
    esperas[0].accion();
    fuentes[1].onopen?.();
    fuentes[1].onerror?.();

    expect(esperas[1].ms).toBe(1000);
  });

  it('detenerla cierra lo abierto y cancela lo agendado', () => {
    fuentes[0].onerror?.();

    conexion.detener();

    expect(esperas[0].cancelada).toBe(true);
  });

  it('una conexion detenida no se repone', () => {
    conexion.detener();

    fuentes[0].onerror?.();

    expect(esperas).toHaveLength(0);
    expect(eventos).toHaveLength(0);
  });
});

describe('ConexionEnVivo con otros eventos', () => {
  it('F21: lee solo los tipos que se le dicen, como el conteo y los avisos de la campana', () => {
    const fuente = new FuenteFalsa();
    const eventos: EventoDeConexion[] = [];
    const conexion = new ConexionEnVivo<EventoDeConexion>(
      { abrir: () => fuente, esperar: () => null, cancelar: () => undefined, azar: () => 1 },
      (evento) => eventos.push(evento),
      ['conteo', 'notificacion'],
    );
    conexion.iniciar();

    fuente.emitir('conteo', { noLeidas: 1 });
    fuente.emitir('presencia', { personas: [] });

    expect([...fuente.oyentes.keys()]).toEqual(['conteo', 'notificacion']);
    expect(eventos).toEqual([{ tipo: 'conteo', datos: { noLeidas: 1 } }]);
  });

  it('por defecto lee los de un proceso en vivo', () => {
    const fuente = new FuenteFalsa();
    new ConexionEnVivo(
      { abrir: () => fuente, esperar: () => null, cancelar: () => undefined, azar: () => 1 },
      () => undefined,
    ).iniciar();

    expect([...fuente.oyentes.keys()]).toEqual([...TIPOS_EN_VIVO]);
  });
});
