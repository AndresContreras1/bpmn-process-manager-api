import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { firstValueFrom } from 'rxjs';

import { environment } from '../../environments/environment';
import { Usuario } from '../models/usuario.model';
import { UsuarioService } from './usuario.service';

describe('UsuarioService.paraElegir', () => {
  const url = `${environment.apiUrl}/api/v1/usuarios`;
  const usuario = (id: number): Usuario => ({ id, nombre: `Persona ${id}` }) as Usuario;
  let http: HttpTestingController;
  let usuarios: UsuarioService;

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
    http = TestBed.inject(HttpTestingController);
    usuarios = TestBed.inject(UsuarioService);
  });

  afterEach(() => http.verify());

  it('pide la primera pagina de cincuenta, por nombre, y devuelve solo las personas', async () => {
    const lista = firstValueFrom(usuarios.paraElegir());

    const peticion = http.expectOne((req) => req.url === url);
    expect(peticion.request.method).toBe('GET');
    expect(peticion.request.params.get('pagina')).toBe('0');
    expect(peticion.request.params.get('tamano')).toBe('50');
    expect(peticion.request.params.get('orden')).toBe('nombre,asc');
    peticion.flush({ content: [usuario(1), usuario(2)], page: 0, size: 50, totalElements: 2, totalPages: 1 });

    await expect(lista).resolves.toEqual([usuario(1), usuario(2)]);
  });

  it('con mas de cincuenta sigue pagina a pagina hasta la ultima y las junta en orden', async () => {
    const lista = firstValueFrom(usuarios.paraElegir());

    http
      .expectOne((req) => req.url === url && req.params.get('pagina') === '0')
      .flush({ content: [usuario(1)], page: 0, size: 50, totalElements: 101, totalPages: 3 });
    http
      .expectOne((req) => req.url === url && req.params.get('pagina') === '1')
      .flush({ content: [usuario(51)], page: 1, size: 50, totalElements: 101, totalPages: 3 });
    http
      .expectOne((req) => req.url === url && req.params.get('pagina') === '2')
      .flush({ content: [usuario(101)], page: 2, size: 50, totalElements: 101, totalPages: 3 });

    await expect(lista).resolves.toEqual([usuario(1), usuario(51), usuario(101)]);
  });

  it('una tienda sin nadie mas no pide una segunda pagina', async () => {
    const lista = firstValueFrom(usuarios.paraElegir());

    http.expectOne((req) => req.url === url).flush({ content: [], page: 0, size: 50, totalElements: 0, totalPages: 0 });

    await expect(lista).resolves.toEqual([]);
  });
});
