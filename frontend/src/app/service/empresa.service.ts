import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { environment } from '../../environments/environment';
import { Empresa, RegistroEmpresaRequest } from '../models/empresa.model';

@Injectable({ providedIn: 'root' })
export class EmpresaService {
  private readonly http: HttpClient = inject(HttpClient);
  private readonly url: string = `${environment.apiUrl}/api/v1/empresas`;

  /** Registra una tienda con su primer administrador. Es publico: no necesita token. */
  registrar(solicitud: RegistroEmpresaRequest): Observable<Empresa> {
    return this.http.post<Empresa>(this.url, solicitud);
  }
}
