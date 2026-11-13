import { HttpErrorResponse } from '@angular/common/http';
import { Observable, Subject, of, throwError } from 'rxjs';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import { ResultadoDeBusqueda } from '../models/busqueda.model';
import { PageResponse } from '../models/page-response.model';
import { EstadoDeBusqueda, buscarMientrasSeEscribe } from './buscador';

function pagina(...titulos: string[]): PageResponse<ResultadoDeBusqueda> {
  const content = titulos.map((titulo): ResultadoDeBusqueda => ({
    tipo: 'PROCESO',
    procesoId: 1,
    proceso: titulo,
    titulo,
    bpmnId: null,
    tipoDeElemento: null,
    version: null,
    documentoId: null,
    comentarioId: null,
    hiloId: null,
    fragmento: [],
  }));
  return { content, page: 0, size: 20, totalElements: content.length, totalPages: 1 };
}

describe('buscarMientrasSeEscribe', () => {
  let textos: Subject<string>;
  let estados: EstadoDeBusqueda[];

  beforeEach(() => {
    vi.useFakeTimers();
    textos = new Subject<string>();
    estados = [];
  });

  afterEach(() => vi.useRealTimers());

  function escuchar(buscar: (texto: string) => Observable<PageResponse<ResultadoDeBusqueda>>): void {
    buscarMientrasSeEscribe(textos, buscar, 250).subscribe((estado) => estados.push(estado));
  }

  it('pregunta una sola vez, con el ultimo texto, cuando se deja de escribir', () => {
    const buscar = vi.fn(() => of(pagina('Credit approval')));
    escuchar(buscar);

    textos.next('c');
    vi.advanceTimersByTime(100);
    textos.next('cr');
    vi.advanceTimersByTime(100);
    textos.next('credit ');
    expect(buscar).not.toHaveBeenCalled();
    vi.advanceTimersByTime(250);

    expect(buscar).toHaveBeenCalledTimes(1);
    expect(buscar).toHaveBeenCalledWith('credit');
    expect(estados.at(-1)).toEqual({
      texto: 'credit',
      buscando: false,
      resultados: pagina('Credit approval').content,
      total: 1,
      error: null,
    });
  });

  it('antes de la respuesta dice que esta buscando', () => {
    const respuesta = new Subject<PageResponse<ResultadoDeBusqueda>>();
    escuchar(() => respuesta);

    textos.next('credit');
    vi.advanceTimersByTime(250);

    expect(estados.at(-1)).toEqual(expect.objectContaining({ texto: 'credit', buscando: true, resultados: [] }));
    respuesta.next(pagina('Credit approval'));
    expect(estados.at(-1)?.buscando).toBe(false);
  });

  it('el mismo texto otra vez, con espacios o sin ellos, no vuelve a preguntar', () => {
    const buscar = vi.fn(() => of(pagina()));
    escuchar(buscar);

    textos.next('credit');
    vi.advanceTimersByTime(250);
    textos.next(' credit ');
    vi.advanceTimersByTime(250);

    expect(buscar).toHaveBeenCalledTimes(1);
  });

  it('en blanco no pregunta, y deja la lista vacia', () => {
    const buscar = vi.fn(() => of(pagina('x')));
    escuchar(buscar);

    textos.next('   ');
    vi.advanceTimersByTime(250);

    expect(buscar).not.toHaveBeenCalled();
    expect(estados).toEqual([{ texto: '', buscando: false, resultados: [], total: 0, error: null }]);
  });

  it('la respuesta de un texto que ya cambio se descarta', () => {
    const lenta = new Subject<PageResponse<ResultadoDeBusqueda>>();
    const buscar = vi.fn((texto: string) => (texto === 'old' ? lenta : of(pagina('New one'))));
    escuchar(buscar);

    textos.next('old');
    vi.advanceTimersByTime(250);
    textos.next('new');
    vi.advanceTimersByTime(250);
    lenta.next(pagina('Old one'));

    expect(estados.at(-1)?.resultados.map((uno) => uno.titulo)).toEqual(['New one']);
    expect(estados.some((estado) => estado.resultados.some((uno) => uno.titulo === 'Old one'))).toBe(false);
  });

  it('un error se cuenta sin cortar la busqueda: el texto siguiente vuelve a preguntar', () => {
    const error = new HttpErrorResponse({ status: 429 });
    const buscar = vi.fn((texto: string) => (texto === 'a' ? throwError(() => error) : of(pagina('B'))));
    escuchar(buscar);

    textos.next('a');
    vi.advanceTimersByTime(250);
    expect(estados.at(-1)).toEqual({ texto: 'a', buscando: false, resultados: [], total: 0, error });

    textos.next('b');
    vi.advanceTimersByTime(250);
    expect(estados.at(-1)?.error).toBeNull();
    expect(estados.at(-1)?.total).toBe(1);
  });
});
