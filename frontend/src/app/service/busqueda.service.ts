import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { environment } from '../../environments/environment';
import { ResultadoDeBusqueda, TipoDeResultado } from '../models/busqueda.model';
import { PageResponse } from '../models/page-response.model';

/** Cuantos resultados trae la busqueda global: los que caben en el dialogo sin una pagina mas. */
export const RESULTADOS_POR_BUSQUEDA = 20;

/** PR 65: la busqueda de texto en lo que la persona puede ver de su organizacion. */
@Injectable({ providedIn: 'root' })
export class BusquedaService {
  private readonly http: HttpClient = inject(HttpClient);
  private readonly url: string = `${environment.apiUrl}/api/v1/busqueda`;

  buscar(
    texto: string,
    tipo: TipoDeResultado | null = null,
    pagina: number = 0,
  ): Observable<PageResponse<ResultadoDeBusqueda>> {
    // Con el objeto params Angular codifica el texto: las comillas de una frase y el menos de excluir viajan tal cual
    const params: Record<string, string | number> = { q: texto, pagina, tamano: RESULTADOS_POR_BUSQUEDA };
    if (tipo) {
      params['tipo'] = tipo;
    }
    return this.http.get<PageResponse<ResultadoDeBusqueda>>(this.url, { params });
  }
}
