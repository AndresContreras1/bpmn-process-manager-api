import { HttpClient, HttpContext, HttpErrorResponse } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Router } from '@angular/router';
import {
  BehaviorSubject,
  Observable,
  catchError,
  finalize,
  firstValueFrom,
  from,
  map,
  of,
  shareReplay,
  switchMap,
  tap,
  throwError,
} from 'rxjs';

import { environment } from '../../environments/environment';
import { SIN_RENOVAR } from '../interceptors/auth.interceptor';
import { LoginRequest, LoginResponse, SesionActual } from '../models/login.model';
import { RolAcceso, Usuario } from '../models/usuario.model';
import { CanalDeSesionService, MensajeDeSesion } from './canal-de-sesion.service';
import { VigenciaDeSesionService } from './vigencia-de-sesion.service';

/** Lo que guardaban las versiones anteriores, el perfil incluido: desde F13 nada de quien entro vive en el navegador. */
const CLAVES_ANTIGUAS: readonly string[] = ['usuario', 'token', 'token-refresco', 'token-vence'];

/**
 * Que hubo una sesion en este navegador, y nada mas: sin esto, cada visita sin sesion pediria la sesion y la
 * renovacion solo para oir dos 401. No dice quien, ni sirve para nada a quien lo lea.
 */
const HAY_SESION = 'bpm-hay-sesion';

/** Cuando renovo alguna pestaña por ultima vez, en milisegundos: lo leen las demas antes de renovar. */
const RENOVADA_EN = 'bpm-sesion-renovada-en';

/** Una renovacion mas nueva que esto ya sirve: las cookies que dejo son las que tienen todas las pestañas. */
const RECIENTE_MS = 15_000;

/** El cerrojo de Web Locks que deja renovar a una sola pestaña a la vez. */
const CERROJO = 'bpm-renovar-sesion';

/**
 * Inicia y cierra la sesion, y publica el usuario actual para toda la aplicacion.
 *
 * D29: la sesion son dos cookies HttpOnly que pone y quita la API; este servicio nunca ve un token. Lo que cambia algo
 * lleva ademas el token CSRF, que HttpClient copia solo de la cookie XSRF-TOKEN a la cabecera X-XSRF-TOKEN.
 *
 * F13: quien entro vive en memoria y se pide a la API al arrancar (GET /api/v1/auth/sesion). Las pestañas se avisan
 * por BroadcastChannel cuando alguien entra, sale o renueva, y renuevan de a una: el refresh token es de un solo uso,
 * y dos pestañas mandando el mismo cerrarian la sesion.
 */
@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly http: HttpClient = inject(HttpClient);
  private readonly router: Router = inject(Router);
  private readonly canal: CanalDeSesionService = inject(CanalDeSesionService);
  private readonly vigencia: VigenciaDeSesionService = inject(VigenciaDeSesionService);
  private readonly url: string = `${environment.apiUrl}/api/v1/auth`;

  // Guarda el usuario actual y se lo entrega de inmediato a cada componente que se suscribe
  private readonly usuarioSubject = new BehaviorSubject<Usuario | null>(null);
  readonly usuario$: Observable<Usuario | null> = this.usuarioSubject.asObservable();

  // La renovacion en curso, mientras dura: varias peticiones que fallan a la vez esperan esta misma
  private renovacion$: Observable<void> | null = null;

  constructor() {
    CLAVES_ANTIGUAS.forEach((clave: string) => borrar(clave));
    this.canal.mensajes$.subscribe((mensaje: MensajeDeSesion) => this.recibir(mensaje));
  }

  /**
   * Lo que la aplicacion hace antes de pintar nada: pedir la cookie XSRF-TOKEN, que el login y la renovacion
   * necesitan, y despues saber si hay alguien dentro. Si la API no contesta, arranca sin sesion.
   */
  iniciar(): Observable<void> {
    return this.prepararCsrf().pipe(switchMap(() => this.cargarSesion()));
  }

  /** Pide a la API la cookie XSRF-TOKEN. Si la API no contesta, el login mostrara el error. */
  prepararCsrf(): Observable<void> {
    return this.http.get<void>(`${this.url}/csrf`).pipe(catchError(() => of(undefined)));
  }

  /**
   * Quien entro, segun la API. Si el acceso vencio, se renueva: la sesion puede seguir viva aunque la pestaña haya
   * estado cerrada un rato. Sin sesion, o sin respuesta, nadie.
   */
  cargarSesion(): Observable<void> {
    if (!leer(HAY_SESION)) {
      return of(undefined);
    }
    const preguntar = (): Observable<void> =>
      this.http
        .get<SesionActual>(`${this.url}/sesion`, { context: new HttpContext().set(SIN_RENOVAR, true) })
        .pipe(map((sesion: SesionActual) => this.mostrar(sesion)));
    return preguntar().pipe(
      catchError((error: HttpErrorResponse) =>
        error.status === 401
          ? // Si otra pestaña acababa de renovar no hubo respuesta que diga quien es: se vuelve a preguntar
            this.renovar().pipe(switchMap(() => (this.usuarioSubject.value ? of(undefined) : preguntar())))
          : throwError(() => error),
      ),
      catchError(() => {
        this.olvidar();
        return of(undefined);
      }),
    );
  }

  login(credenciales: LoginRequest): Observable<LoginResponse> {
    return this.http.post<LoginResponse>(`${this.url}/login`, credenciales).pipe(
      tap((respuesta: LoginResponse) => {
        this.guardarSesion(respuesta);
        this.canal.publicar({ tipo: 'entrada' });
      }),
    );
  }

  /**
   * Renueva la sesion con la cookie de refresco, que el navegador manda solo a /api/v1/auth.
   *
   * Se hace una sola llamada aunque varias peticiones venzan al mismo tiempo: el refresh token es de un solo uso y
   * mandarlo dos veces cierra la sesion, asi que todas comparten el observable en curso y se olvida al terminar, para
   * que la proxima vez se renueve de nuevo. Entre pestañas, lo mismo con un cerrojo de Web Locks.
   */
  renovar(): Observable<void> {
    this.renovacion$ ??= from(this.renovarDeAUna()).pipe(
      finalize(() => (this.renovacion$ = null)),
      shareReplay({ bufferSize: 1, refCount: false }),
    );
    return this.renovacion$;
  }

  /**
   * Cambia la contrasena y abre una sesion nueva: la API cierra todas las de este usuario, esta incluida, y deja las
   * cookies de la nueva, que son las de todas las pestañas.
   */
  cambiarClave(actual: string, nueva: string): Observable<LoginResponse> {
    return this.http.post<LoginResponse>(`${this.url}/password`, { actual, nueva }).pipe(
      tap((respuesta: LoginResponse) => {
        this.guardarSesion(respuesta);
        this.canal.publicar({ tipo: 'entrada' });
      }),
    );
  }

  /**
   * Avisa a la API, que cierra la sesion y borra sus cookies, y olvida al usuario aunque la llamada falle. La API
   * responde lo mismo con el acceso vencido, asi que siempre se puede salir.
   */
  logout(): Observable<void> {
    return this.http.post<void>(`${this.url}/logout`, null).pipe(finalize(() => this.cerrarSesionLocal()));
  }

  /**
   * Olvida al usuario y se lo dice a las demas pestañas, que vuelven al login. La usan el logout, el interceptor
   * cuando la renovacion es rechazada y el aviso de inactividad.
   */
  cerrarSesionLocal(): void {
    this.olvidar();
    this.canal.publicar({ tipo: 'salida' });
  }

  estaAutenticado(): boolean {
    return this.usuarioSubject.value !== null;
  }

  /** El usuario de la sesion, tal como lo devolvio la API. Null cuando no hay sesion abierta. */
  usuarioActual(): Usuario | null {
    return this.usuarioSubject.value;
  }

  tieneRol(...roles: RolAcceso[]): boolean {
    const usuario: Usuario | null = this.usuarioSubject.value;
    return usuario !== null && roles.includes(usuario.rolAcceso);
  }

  /** Administradores y editores crean, editan y publican; el rol de solo lectura solo consulta. */
  puedeEditar(): boolean {
    return this.tieneRol('ADMINISTRADOR', 'EDITOR');
  }

  /** Solo el administrador borra procesos y elementos del diagrama. */
  esAdministrador(): boolean {
    return this.tieneRol('ADMINISTRADOR');
  }

  /**
   * Una pestaña a la vez, y solo si nadie acaba de hacerlo: la que espera el cerrojo encuentra al entrar que otra ya
   * renovo, y sus cookies, que son las mismas, ya sirven.
   */
  private renovarDeAUna(): Promise<void> {
    const renovar = async (): Promise<void> => {
      if (Date.now() - Number(leer(RENOVADA_EN) ?? 0) < RECIENTE_MS) {
        return;
      }
      const respuesta: LoginResponse = await firstValueFrom(this.http.post<LoginResponse>(`${this.url}/refresh`, null));
      escribir(RENOVADA_EN, String(Date.now()));
      this.guardarSesion(respuesta);
      this.canal.publicar({
        tipo: 'renovada',
        inactividadSegundos: respuesta.inactividadSegundos ?? null,
        restanteSegundos: respuesta.restanteSegundos ?? null,
      });
    };
    const cerrojos: LockManager | undefined = 'locks' in navigator ? navigator.locks : undefined;
    return cerrojos ? cerrojos.request(CERROJO, renovar) : renovar();
  }

  private recibir(mensaje: MensajeDeSesion): void {
    switch (mensaje.tipo) {
      case 'salida':
        if (this.usuarioSubject.value) {
          this.olvidar();
          this.router.navigate(['/login'], { queryParams: { sesion: 'cerrada' } });
        }
        break;
      case 'entrada':
        this.cargarSesion().subscribe();
        break;
      case 'renovada':
        this.vigencia.registrar(mensaje.inactividadSegundos, mensaje.restanteSegundos);
        break;
    }
  }

  /** La sesion que dijo la API. A medias, a la espera del segundo factor, todavia no es una sesion. */
  private mostrar(sesion: SesionActual): void {
    if (sesion.segundoFactorPendiente) {
      this.olvidar();
      return;
    }
    this.usuarioSubject.next(sesion.usuario);
    this.vigencia.registrar(sesion.inactividadSegundos, sesion.restanteSegundos);
  }

  private guardarSesion(respuesta: LoginResponse): void {
    escribir(HAY_SESION, '1');
    this.usuarioSubject.next(respuesta.usuario);
    this.vigencia.registrar(respuesta.inactividadSegundos, respuesta.restanteSegundos);
  }

  private olvidar(): void {
    borrar(HAY_SESION);
    this.usuarioSubject.next(null);
    this.vigencia.olvidar();
  }
}

// El almacenamiento puede no estar (modo privado estricto, cookies bloqueadas): sin el, se pregunta siempre a la API
function leer(clave: string): string | null {
  try {
    return localStorage.getItem(clave);
  } catch {
    return null;
  }
}

function escribir(clave: string, valor: string): void {
  try {
    localStorage.setItem(clave, valor);
  } catch {
    // Sin almacenamiento la web sigue: solo pregunta de mas
  }
}

function borrar(clave: string): void {
  try {
    localStorage.removeItem(clave);
  } catch {
    // Nada que borrar
  }
}
