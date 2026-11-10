import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';
import { Subject, firstValueFrom } from 'rxjs';

import { authInterceptor } from '../interceptors/auth.interceptor';
import { Usuario } from '../models/usuario.model';
import { AuthService } from './auth.service';
import { CanalDeSesionService, MensajeDeSesion } from './canal-de-sesion.service';
import { VigenciaDeSesionService } from './vigencia-de-sesion.service';

const ANA: Usuario = {
  id: 7,
  nombre: 'Ana',
  email: 'ana@acme.test',
  rolAcceso: 'ADMINISTRADOR',
  activo: true,
  empresaId: 3,
  version: 0,
  debeCambiarClave: false,
};

describe('AuthService', () => {
  const recibidos = new Subject<MensajeDeSesion>();
  const canal = { mensajes$: recibidos.asObservable(), publicar: vi.fn() };
  let api: HttpTestingController;

  function servicio(): AuthService {
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(withInterceptors([authInterceptor])),
        provideHttpClientTesting(),
        provideRouter([]),
        { provide: CanalDeSesionService, useValue: canal },
      ],
    });
    api = TestBed.inject(HttpTestingController);
    return TestBed.inject(AuthService);
  }

  beforeEach(() => {
    localStorage.clear();
    vi.clearAllMocks();
  });

  afterEach(() => api.verify());

  it('ya no guarda el perfil en el navegador, y borra el que dejaron las versiones anteriores', async () => {
    localStorage.setItem('usuario', JSON.stringify(ANA));
    const auth = servicio();
    const login = firstValueFrom(auth.login({ email: ANA.email, password: 'x' }));
    api.expectOne('/api/v1/auth/login').flush({ expiresIn: 900, usuario: ANA, inactividadSegundos: 3600 });
    await login;

    expect(localStorage.getItem('usuario')).toBeNull();
    expect(auth.usuarioActual()).toEqual(ANA);
    expect(canal.publicar).toHaveBeenCalledWith({ tipo: 'entrada' });
    expect(TestBed.inject(VigenciaDeSesionService).inactivaEn()).toBeGreaterThan(Date.now() + 3590_000);
  });

  it('sin rastro de una sesion en este navegador, arranca sin preguntar a la API', async () => {
    const auth = servicio();
    await firstValueFrom(auth.cargarSesion());

    expect(auth.estaAutenticado()).toBe(false);
  });

  it('al arrancar pregunta quien entro; con el acceso vencido renueva, y la renovacion dice quien es', async () => {
    localStorage.setItem('bpm-hay-sesion', '1');
    const auth = servicio();
    const carga = firstValueFrom(auth.cargarSesion());
    api.expectOne('/api/v1/auth/sesion').flush(null, { status: 401, statusText: 'Unauthorized' });
    await Promise.resolve();
    api.expectOne('/api/v1/auth/refresh').flush({ expiresIn: 900, usuario: ANA, inactividadSegundos: 3600 });
    await carga;

    expect(auth.usuarioActual()).toEqual(ANA);
    expect(canal.publicar).toHaveBeenCalledWith(expect.objectContaining({ tipo: 'renovada' }));
  });

  it('si la renovacion tambien falla, no hay nadie, y no manda al login: solo es alguien sin sesion', async () => {
    localStorage.setItem('bpm-hay-sesion', '1');
    const auth = servicio();
    const navegar = vi.spyOn(TestBed.inject(Router), 'navigate');
    const carga = firstValueFrom(auth.cargarSesion());
    api.expectOne('/api/v1/auth/sesion').flush(null, { status: 401, statusText: 'Unauthorized' });
    await Promise.resolve();
    api.expectOne('/api/v1/auth/refresh').flush(null, { status: 401, statusText: 'Unauthorized' });
    await carga;

    expect(auth.estaAutenticado()).toBe(false);
    expect(localStorage.getItem('bpm-hay-sesion')).toBeNull();
    expect(navegar).not.toHaveBeenCalled();
  });

  it('si otra pestaña acaba de renovar, no manda el refresh token: las cookies ya son nuevas', async () => {
    const auth = servicio();
    localStorage.setItem('bpm-sesion-renovada-en', String(Date.now()));
    await firstValueFrom(auth.renovar(), { defaultValue: undefined });

    api.expectNone('/api/v1/auth/refresh');
  });

  it('dos peticiones que vencen a la vez comparten una sola renovacion', async () => {
    const auth = servicio();
    const una = firstValueFrom(auth.renovar(), { defaultValue: undefined });
    const otra = firstValueFrom(auth.renovar(), { defaultValue: undefined });
    await Promise.resolve();
    api.expectOne('/api/v1/auth/refresh').flush({ expiresIn: 900, usuario: ANA });
    await Promise.all([una, otra]);

    expect(auth.usuarioActual()).toEqual(ANA);
  });

  it('salir avisa a las demas pestañas; y cuando otra sale, esta vuelve al login', async () => {
    const auth = servicio();
    const login = firstValueFrom(auth.login({ email: ANA.email, password: 'x' }));
    api.expectOne('/api/v1/auth/login').flush({ expiresIn: 900, usuario: ANA });
    await login;
    const navegar = vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);

    recibidos.next({ tipo: 'salida' });

    expect(auth.estaAutenticado()).toBe(false);
    expect(navegar).toHaveBeenCalledWith(['/login'], { queryParams: { sesion: 'cerrada' } });

    const salida = firstValueFrom(auth.logout(), { defaultValue: undefined });
    api.expectOne('/api/v1/auth/logout').flush(null);
    await salida;
    expect(canal.publicar).toHaveBeenCalledWith({ tipo: 'salida' });
  });

  it('cuando otra pestaña renueva, esta cuenta lo que le queda a la sesion desde ahi', () => {
    servicio();
    recibidos.next({ tipo: 'renovada', inactividadSegundos: 1800, restanteSegundos: 7200 });

    const vigencia: VigenciaDeSesionService = TestBed.inject(VigenciaDeSesionService);
    expect(vigencia.inactivaEn()).toBeGreaterThan(Date.now() + 1790_000);
    expect(vigencia.terminaEn()).toBeGreaterThan(Date.now() + 7190_000);
  });
});
