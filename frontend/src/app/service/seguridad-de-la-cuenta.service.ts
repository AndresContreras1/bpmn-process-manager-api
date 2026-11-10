import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { environment } from '../../environments/environment';
import {
  Dominio,
  FiltrosDeBitacora,
  IdentidadExterna,
  PaginaDeBitacora,
  SesionAbierta,
  VerificacionDeBitacora,
} from '../models/seguridad.model';
import { Empresa } from '../models/empresa.model';
import { RolAcceso } from '../models/usuario.model';

/**
 * F14: lo demas de la seguridad: las sesiones abiertas y los proveedores vinculados de cada persona, y lo del
 * administrador: los dominios de la organizacion, su bitacora, su baja y anonimizar a una persona.
 */
@Injectable({ providedIn: 'root' })
export class SeguridadDeLaCuentaService {
  private readonly http: HttpClient = inject(HttpClient);
  private readonly api: string = `${environment.apiUrl}/api/v1`;

  sesiones(): Observable<SesionAbierta[]> {
    return this.http.get<SesionAbierta[]>(`${this.api}/auth/sesiones`);
  }

  cerrarSesion(id: number): Observable<void> {
    return this.http.delete<void>(`${this.api}/auth/sesiones/${id}`);
  }

  cerrarLasDemas(): Observable<void> {
    return this.http.delete<void>(`${this.api}/auth/sesiones`);
  }

  identidades(): Observable<IdentidadExterna[]> {
    return this.http.get<IdentidadExterna[]>(`${this.api}/auth/identidades`);
  }

  desvincular(id: number): Observable<void> {
    return this.http.delete<void>(`${this.api}/auth/identidades/${id}`);
  }

  dominios(): Observable<Dominio[]> {
    return this.http.get<Dominio[]>(`${this.api}/empresas/actual/dominios`);
  }

  agregarDominio(dominio: string, rolPorDefecto: RolAcceso): Observable<Dominio> {
    return this.http.post<Dominio>(`${this.api}/empresas/actual/dominios`, { dominio, rolPorDefecto });
  }

  verificarDominio(id: number): Observable<Dominio> {
    return this.http.post<Dominio>(`${this.api}/empresas/actual/dominios/${id}/verificacion`, null);
  }

  quitarDominio(id: number): Observable<void> {
    return this.http.delete<void>(`${this.api}/empresas/actual/dominios/${id}`);
  }

  bitacora(filtros: FiltrosDeBitacora): Observable<PaginaDeBitacora> {
    return this.http.get<PaginaDeBitacora>(`${this.api}/auditoria`, { params: parametros(filtros) });
  }

  verificarBitacora(): Observable<VerificacionDeBitacora> {
    return this.http.get<VerificacionDeBitacora>(`${this.api}/auditoria/verificacion`);
  }

  /** Un enlace de descarga con los mismos filtros: el navegador lleva las cookies, que es lo que la autoriza. */
  exportacion(formato: 'csv' | 'json', filtros: FiltrosDeBitacora): string {
    const consulta: string = parametros({ ...filtros, antes: null })
      .set('formato', formato)
      .toString();
    return `${this.api}/auditoria/exportacion?${consulta}`;
  }

  /** La confirmacion es el nombre de la organizacion, escrito otra vez. */
  pedirBaja(confirmacion: string): Observable<Empresa> {
    return this.http.post<Empresa>(`${this.api}/empresas/actual/baja`, { confirmacion });
  }

  cancelarBaja(): Observable<Empresa> {
    return this.http.delete<Empresa>(`${this.api}/empresas/actual/baja`);
  }

  anonimizar(usuarioId: number): Observable<void> {
    return this.http.post<void>(`${this.api}/usuarios/${usuarioId}/anonimizar`, null);
  }
}

function parametros(filtros: FiltrosDeBitacora): HttpParams {
  let params = new HttpParams();
  Object.entries(filtros).forEach(([clave, valor]) => {
    if (valor !== null && valor !== undefined && valor !== '') {
      params = params.set(clave, String(valor));
    }
  });
  return params;
}
