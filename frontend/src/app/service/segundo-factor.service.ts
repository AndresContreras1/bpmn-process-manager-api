import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable, from, switchMap, tap } from 'rxjs';

import { environment } from '../../environments/environment';
import { crearCredencial, firmar } from '../helpers/webauthn';
import { LoginResponse } from '../models/login.model';
import {
  CodigosDeRecuperacion,
  EstadoSegundoFactor,
  InscripcionTotp,
  OpcionesWebAuthn,
  Passkey,
  PasskeyRegistrada,
  Reautenticacion,
} from '../models/seguridad.model';
import { AuthService } from './auth.service';

/**
 * F14: el segundo factor de quien entro y las passkeys: inscribir la aplicacion de autenticacion, los codigos de
 * recuperacion, registrar y quitar passkeys, terminar un login a medias, entrar sin clave y confirmar quien se es para
 * lo que pide una verificacion reciente. Lo que abre una sesion pasa por AuthService.abrir.
 */
@Injectable({ providedIn: 'root' })
export class SegundoFactorService {
  private readonly http: HttpClient = inject(HttpClient);
  private readonly authService: AuthService = inject(AuthService);
  private readonly auth: string = `${environment.apiUrl}/api/v1/auth`;
  private readonly url: string = `${this.auth}/segundo-factor`;

  estado(): Observable<EstadoSegundoFactor> {
    return this.http.get<EstadoSegundoFactor>(this.url);
  }

  inscribirTotp(): Observable<InscripcionTotp> {
    return this.http.post<InscripcionTotp>(`${this.url}/totp`, null);
  }

  /** El primer codigo de la aplicacion la confirma; la primera vez llegan los codigos de recuperacion. */
  confirmarTotp(codigo: string): Observable<CodigosDeRecuperacion> {
    return this.http.post<CodigosDeRecuperacion>(`${this.url}/totp/confirmacion`, { codigo });
  }

  quitarTotp(): Observable<void> {
    return this.http.delete<void>(`${this.url}/totp`);
  }

  /** Diez codigos nuevos; los de antes dejan de servir. */
  renovarCodigos(): Observable<CodigosDeRecuperacion> {
    return this.http.post<CodigosDeRecuperacion>(`${this.url}/codigos`, null);
  }

  passkeys(): Observable<Passkey[]> {
    return this.http.get<Passkey[]>(`${this.url}/passkeys`);
  }

  /** Pide las opciones, deja que el navegador cree la passkey y la registra con su nombre. */
  registrarPasskey(etiqueta: string): Observable<PasskeyRegistrada> {
    return this.http.post<OpcionesWebAuthn>(`${this.url}/passkeys/opciones`, null).pipe(
      switchMap((opciones: OpcionesWebAuthn) =>
        from(crearCredencial(opciones.opciones)).pipe(
          switchMap((credencial) =>
            this.http.post<PasskeyRegistrada>(`${this.url}/passkeys`, {
              desafio: opciones.desafio,
              etiqueta,
              credencial,
            }),
          ),
        ),
      ),
    );
  }

  renombrarPasskey(id: number, etiqueta: string): Observable<Passkey> {
    return this.http.patch<Passkey>(`${this.url}/passkeys/${id}`, { etiqueta });
  }

  quitarPasskey(id: number): Observable<void> {
    return this.http.delete<void>(`${this.url}/passkeys/${id}`);
  }

  /** Termina el login a medias con el codigo de la aplicacion o uno de recuperacion. */
  completarConCodigo(codigo: string): Observable<LoginResponse> {
    return this.http
      .post<LoginResponse>(`${this.url}/verificacion`, { codigo })
      .pipe(tap((respuesta: LoginResponse) => this.authService.abrir(respuesta)));
  }

  /** Termina el login a medias firmando con una passkey. */
  completarConPasskey(): Observable<LoginResponse> {
    return this.firmarYEnviar(`${this.url}/verificacion/passkey/opciones`, `${this.url}/verificacion/passkey`).pipe(
      tap((respuesta: LoginResponse) => this.authService.abrir(respuesta)),
    );
  }

  /** Entra sin clave: la passkey dice quien es. */
  entrarConPasskey(): Observable<LoginResponse> {
    return this.firmarYEnviar(`${this.auth}/passkeys/opciones`, `${this.auth}/passkeys/entrada`).pipe(
      tap((respuesta: LoginResponse) => this.authService.abrir(respuesta)),
    );
  }

  /** Confirma quien es con la clave o un codigo: lo que pide una verificacion reciente vuelve a estar a su alcance. */
  reautenticar(prueba: Reautenticacion): Observable<LoginResponse> {
    return this.http.post<LoginResponse>(`${this.auth}/reautenticacion`, prueba);
  }

  reautenticarConPasskey(): Observable<LoginResponse> {
    return this.firmarYEnviar(`${this.url}/verificacion/passkey/opciones`, `${this.auth}/reautenticacion/passkey`);
  }

  private firmarYEnviar(opcionesEn: string, firmaA: string): Observable<LoginResponse> {
    return this.http
      .post<OpcionesWebAuthn>(opcionesEn, null)
      .pipe(
        switchMap((opciones: OpcionesWebAuthn) =>
          from(firmar(opciones.opciones)).pipe(
            switchMap((credencial) => this.http.post<LoginResponse>(firmaA, { desafio: opciones.desafio, credencial })),
          ),
        ),
      );
  }
}
