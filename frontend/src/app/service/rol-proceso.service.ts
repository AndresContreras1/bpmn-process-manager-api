import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable, map } from 'rxjs';

import { environment } from '../../environments/environment';
import { PageResponse } from '../models/page-response.model';
import { RolProceso } from '../models/rol-proceso.model';

/** Los roles de proceso de la tienda. Una lane va ligada a uno, y es el que atiende lo que hay dentro. */
@Injectable({ providedIn: 'root' })
export class RolProcesoService {
  private readonly http: HttpClient = inject(HttpClient);
  private readonly url: string = `${environment.apiUrl}/api/v1/roles`;

  /**
   * Todos los roles en una sola pagina. Son pocos por tienda —uno por cada trabajo que hace— y el editor los
   * necesita enteros para poder elegir cualquiera en el formulario de una lane.
   */
  listar(): Observable<RolProceso[]> {
    return this.http
      .get<PageResponse<RolProceso>>(this.url, { params: { pagina: 0, tamano: 200, orden: 'nombre,asc' } })
      .pipe(map((pagina: PageResponse<RolProceso>) => pagina.content));
  }
}
