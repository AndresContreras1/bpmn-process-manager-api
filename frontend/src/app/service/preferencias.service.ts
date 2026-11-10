import { HttpClient } from '@angular/common/http';
import { Injectable, computed, inject, signal } from '@angular/core';
import { Observable, catchError, distinctUntilChanged, filter, map, of, switchMap, tap } from 'rxjs';

import { environment } from '../../environments/environment';
import { Preferencias, PreferenciasRequest } from '../models/preferencias.model';
import { Usuario } from '../models/usuario.model';
import { AuthService } from './auth.service';
import { IdiomaService } from './idioma.service';

/** La zona del navegador: la que vale mientras no haya sesion, o si la API no contesta. */
export function zonaDelNavegador(): string {
  return Intl.DateTimeFormat().resolvedOptions().timeZone || 'UTC';
}

/**
 * F12: el idioma y la zona horaria de quien entro y de su organizacion. Lo que vale es lo de la persona si eligio, y
 * si no lo de su organizacion; la API hace esa cuenta y la web solo la usa: las fechas se leen en esa zona, y si el
 * idioma que vale no es el de esta compilacion, la web se abre en el suyo.
 */
@Injectable({ providedIn: 'root' })
export class PreferenciasService {
  private readonly http: HttpClient = inject(HttpClient);
  private readonly authService: AuthService = inject(AuthService);
  private readonly idiomaService: IdiomaService = inject(IdiomaService);
  private readonly propias: string = `${environment.apiUrl}/api/v1/auth/preferencias`;
  private readonly deLaOrganizacion: string = `${environment.apiUrl}/api/v1/empresas/actual/preferencias`;

  private readonly preferencias = signal<Preferencias | null>(null);
  readonly actuales = this.preferencias.asReadonly();
  /** La zona en que se leen las fechas. */
  readonly zonaHoraria = computed(() => this.preferencias()?.zonaHoraria ?? zonaDelNavegador());

  /**
   * Cada vez que entra alguien, sus preferencias; al salir se olvidan. Con la clave temporal la API no deja pedir
   * nada mas que cambiarla, asi que se esperan a la sesion nueva.
   */
  seguirLaSesion(): void {
    this.authService.usuario$
      .pipe(
        map((usuario: Usuario | null) => (usuario && !usuario.debeCambiarClave ? usuario.id : null)),
        distinctUntilChanged(),
        tap((id: number | null) => id === null && this.preferencias.set(null)),
        filter((id: number | null): id is number => id !== null),
        switchMap(() => this.cargar().pipe(catchError(() => of(null)))),
      )
      .subscribe((preferencias: Preferencias | null) => {
        if (preferencias) {
          this.idiomaService.ir(preferencias.idioma);
        }
      });
  }

  cargar(): Observable<Preferencias> {
    return this.http.get<Preferencias>(this.propias).pipe(tap((p: Preferencias) => this.preferencias.set(p)));
  }

  cambiarLasPropias(cambio: PreferenciasRequest): Observable<Preferencias> {
    return this.http.put<Preferencias>(this.propias, cambio).pipe(tap((p: Preferencias) => this.preferencias.set(p)));
  }

  /** Solo el administrador. Lo que devuelve son las de quien la cambio, que puede seguir a su organizacion. */
  cambiarLasDeLaOrganizacion(cambio: PreferenciasRequest): Observable<Preferencias> {
    return this.http
      .put<Preferencias>(this.deLaOrganizacion, cambio)
      .pipe(tap((p: Preferencias) => this.preferencias.set(p)));
  }
}
