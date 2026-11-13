import { HttpErrorResponse } from '@angular/common/http';
import { Observable, catchError, debounceTime, distinctUntilChanged, map, of, startWith, switchMap } from 'rxjs';

import { ResultadoDeBusqueda } from '../models/busqueda.model';
import { PageResponse } from '../models/page-response.model';

/** Lo que muestra la busqueda en cada momento. */
export interface EstadoDeBusqueda {
  texto: string;
  buscando: boolean;
  resultados: ResultadoDeBusqueda[];
  total: number;
  error: HttpErrorResponse | null;
}

/** Cuanto se espera a que la persona deje de escribir antes de preguntar. */
export const ESPERA_AL_ESCRIBIR_MS = 250;

/**
 * La busqueda mientras se escribe: pregunta cuando la persona deja de escribir, no por cada tecla, y no pregunta lo
 * mismo dos veces seguidas. Si llega otro texto con la respuesta en camino, esa respuesta se descarta (switchMap), asi
 * que nunca se ven los resultados de un texto que ya no esta. Un texto en blanco no pregunta: no hay nada que buscar.
 * Un error no corta el flujo: se cuenta en el estado y el siguiente texto vuelve a preguntar.
 */
export function buscarMientrasSeEscribe(
  textos: Observable<string>,
  buscar: (texto: string) => Observable<PageResponse<ResultadoDeBusqueda>>,
  espera: number = ESPERA_AL_ESCRIBIR_MS,
): Observable<EstadoDeBusqueda> {
  return textos.pipe(
    map((texto) => texto.trim()),
    debounceTime(espera),
    distinctUntilChanged(),
    switchMap((texto) => {
      if (!texto) {
        return of(estado(texto, false, [], 0, null));
      }
      return buscar(texto).pipe(
        map((pagina) => estado(texto, false, pagina.content, pagina.totalElements, null)),
        catchError((error: HttpErrorResponse) => of(estado(texto, false, [], 0, error))),
        startWith(estado(texto, true, [], 0, null)),
      );
    }),
  );
}

function estado(
  texto: string,
  buscando: boolean,
  resultados: ResultadoDeBusqueda[],
  total: number,
  error: HttpErrorResponse | null,
): EstadoDeBusqueda {
  return { texto, buscando, resultados, total, error };
}
