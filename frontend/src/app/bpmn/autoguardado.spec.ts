import { HttpErrorResponse } from '@angular/common/http';
import { Subject, of, throwError } from 'rxjs';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import { ModeloGuardado } from '../models/modelo.model';
import { Autoguardado, ESPERA_MS, PuertosDelGuardado, REINTENTO_MS } from './autoguardado';

const respuesta = (huella: string): ModeloGuardado => ({
  procesoId: 1,
  creados: 0,
  editados: 1,
  dadosDeBaja: 0,
  huella,
});

describe('Autoguardado', () => {
  let puertos: PuertosDelGuardado;
  let enviar: ReturnType<typeof vi.fn>;
  let claves: number;

  beforeEach(() => {
    vi.useFakeTimers();
    claves = 0;
    enviar = vi.fn(() => of(respuesta('h2')));
    puertos = {
      leerXml: vi.fn(async () => '<xml/>'),
      enviar: enviar as unknown as PuertosDelGuardado['enviar'],
      guardarCopia: vi.fn(),
      borrarCopia: vi.fn(),
      nuevaClave: () => `clave-${++claves}`,
    };
  });

  afterEach(() => vi.useRealTimers());

  it('espera dos segundos desde el ultimo cambio', async () => {
    const guardado = new Autoguardado(puertos, 'h1');
    await guardado.marcarCambio();
    await vi.advanceTimersByTimeAsync(ESPERA_MS - 1);
    await guardado.marcarCambio();
    await vi.advanceTimersByTimeAsync(ESPERA_MS - 1);
    expect(enviar).not.toHaveBeenCalled();
    await vi.advanceTimersByTimeAsync(1);
    expect(enviar).toHaveBeenCalledTimes(1);
    expect(enviar).toHaveBeenCalledWith('<xml/>', 'h1', 'clave-1');
  });

  it('sigue desde la huella que responde el servidor y borra la copia', async () => {
    const guardado = new Autoguardado(puertos, 'h1');
    await guardado.marcarCambio();
    await vi.advanceTimersByTimeAsync(ESPERA_MS);
    expect(guardado.huellaActual).toBe('h2');
    expect(guardado.estado).toBe('guardado');
    expect(puertos.borrarCopia).toHaveBeenCalled();
  });

  it('deja una copia local en cada cambio', async () => {
    const guardado = new Autoguardado(puertos, 'h1');
    await guardado.marcarCambio();
    expect(puertos.guardarCopia).toHaveBeenCalledWith('<xml/>', 'h1');
  });

  it('un guardado a la vez: lo que cambia mientras tanto se manda despues', async () => {
    const enCurso = new Subject<ModeloGuardado>();
    enviar.mockReturnValueOnce(enCurso);
    const guardado = new Autoguardado(puertos, 'h1');
    await guardado.marcarCambio();
    await vi.advanceTimersByTimeAsync(ESPERA_MS);
    await guardado.marcarCambio();
    await vi.advanceTimersByTimeAsync(ESPERA_MS);
    expect(enviar).toHaveBeenCalledTimes(1);

    enCurso.next(respuesta('h2'));
    enCurso.complete();
    await vi.advanceTimersByTimeAsync(ESPERA_MS);

    expect(enviar).toHaveBeenCalledTimes(2);
    expect(enviar.mock.calls[1][1]).toBe('h2');
  });

  it('un corte de la red se reintenta con la misma clave', async () => {
    enviar.mockReturnValueOnce(throwError(() => new HttpErrorResponse({ status: 0 })));
    const guardado = new Autoguardado(puertos, 'h1');
    await guardado.marcarCambio();
    await vi.advanceTimersByTimeAsync(ESPERA_MS);
    expect(guardado.estado).toBe('sin-conexion');

    await vi.advanceTimersByTimeAsync(REINTENTO_MS);

    expect(enviar).toHaveBeenCalledTimes(2);
    expect(enviar.mock.calls[1][2]).toBe(enviar.mock.calls[0][2]);
    expect(guardado.estado).toBe('guardado');
  });

  it('un cambio despues de un corte es otra peticion, con otra clave', async () => {
    enviar.mockReturnValueOnce(throwError(() => new HttpErrorResponse({ status: 503 })));
    const guardado = new Autoguardado(puertos, 'h1');
    await guardado.marcarCambio();
    await vi.advanceTimersByTimeAsync(ESPERA_MS);
    await guardado.marcarCambio();
    await vi.advanceTimersByTimeAsync(ESPERA_MS);
    expect(enviar.mock.calls[1][2]).not.toBe(enviar.mock.calls[0][2]);
  });

  it('un conflicto para hasta que alguien decida', async () => {
    enviar.mockReturnValueOnce(throwError(() => new HttpErrorResponse({ status: 412 })));
    const guardado = new Autoguardado(puertos, 'h1');
    await guardado.marcarCambio();
    await vi.advanceTimersByTimeAsync(ESPERA_MS);
    expect(guardado.estado).toBe('conflicto');

    await guardado.marcarCambio();
    await vi.advanceTimersByTimeAsync(REINTENTO_MS * 2);
    expect(enviar).toHaveBeenCalledTimes(1);

    guardado.reiniciar('h9');
    expect(guardado.estado).toBe('guardado');
    expect(guardado.huellaActual).toBe('h9');
  });

  it('un archivo rechazado dice por que y no reintenta', async () => {
    enviar.mockReturnValueOnce(
      throwError(() => new HttpErrorResponse({ status: 409, error: { detail: 'El flujo cruza un pool' } })),
    );
    const guardado = new Autoguardado(puertos, 'h1');
    await guardado.marcarCambio();
    await vi.advanceTimersByTimeAsync(ESPERA_MS);
    expect(guardado.estado).toBe('rechazado');
    expect(guardado.ultimoError).toBe('El flujo cruza un pool');
    await vi.advanceTimersByTimeAsync(REINTENTO_MS * 2);
    expect(enviar).toHaveBeenCalledTimes(1);
  });
});
