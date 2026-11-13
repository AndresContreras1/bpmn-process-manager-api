import { HttpErrorResponse } from '@angular/common/http';
import { TestBed } from '@angular/core/testing';
import { ActivatedRoute, convertToParamMap, provideRouter } from '@angular/router';
import { Observable, of, throwError } from 'rxjs';

import { Accion } from '../../models/concesion.model';
import { PageResponse } from '../../models/page-response.model';
import { Proceso } from '../../models/proceso.model';
import { RolAcceso } from '../../models/usuario.model';
import { AuthService } from '../../service/auth.service';
import { CompartirService } from '../../service/compartir.service';
import { ConcesionService } from '../../service/concesion.service';
import { ProcesoService } from '../../service/proceso.service';
import { ProcesosComponent } from './procesos.component';

/** Los botones de cada fila de la lista, de lo que la API dice de cada proceso, sin pintar la tabla. */
describe('ProcesosComponent: los botones de cada fila', () => {
  const proceso = (id: number, nombre: string): Proceso => ({
    id,
    nombre,
    descripcion: '',
    categoria: 'People',
    estado: 'BORRADOR',
    activo: true,
    fechaCreacion: '2026-11-01T10:00:00Z',
    fechaModificacion: '2026-11-01T10:00:00Z',
    version: 0,
    versionPublicada: null,
    borradorPendiente: null,
  });
  const nomina = proceso(1, 'Payroll');
  const horario = proceso(2, 'Opening hours');
  const pagina: PageResponse<Proceso> = {
    content: [nomina, horario],
    page: 0,
    size: 10,
    totalElements: 2,
    totalPages: 1,
  };
  let permisos: Record<number, Observable<Accion[]>>;
  let preguntados: number[];

  function abrir(rol: RolAcceso): ProcesosComponent {
    preguntados = [];
    TestBed.configureTestingModule({
      providers: [
        provideRouter([]),
        {
          provide: ActivatedRoute,
          useValue: {
            snapshot: { queryParamMap: convertToParamMap({}) },
            queryParamMap: of(convertToParamMap({})),
          },
        },
        {
          provide: AuthService,
          useValue: {
            puedeEditar: () => rol !== 'SOLO_LECTURA',
            esAdministrador: () => rol === 'ADMINISTRADOR',
            usuarioActual: () => ({ id: 3, rolAcceso: rol }),
          },
        },
        { provide: ProcesoService, useValue: { listar: () => of(pagina) } },
        {
          provide: CompartirService,
          useValue: { recibidos: () => of({ content: [], page: 0, size: 10, totalElements: 0, totalPages: 0 }) },
        },
        {
          provide: ConcesionService,
          useValue: {
            permisos: (id: number) => {
              preguntados.push(id);
              return permisos[id];
            },
          },
        },
      ],
    });
    const lista = TestBed.runInInjectionContext(() => new ProcesosComponent());
    lista.ngOnInit();
    // La lista espera a que se deje de escribir antes de pedir
    vi.advanceTimersByTime(250);
    return lista;
  }

  beforeEach(() => vi.useFakeTimers());
  afterEach(() => vi.useRealTimers());

  it('cada fila sigue lo que se puede hacer con su proceso, no el rol', () => {
    // Una concesion de modelar a otra persona le quita a este editor editar y mover Payroll, no publicarlo
    permisos = {
      1: of(['VER', 'COMENTAR', 'PUBLICAR', 'EXPORTAR']),
      2: of(['VER', 'COMENTAR', 'MODELAR', 'PUBLICAR', 'EXPORTAR']),
    };

    const lista = abrir('EDITOR');

    expect(preguntados).toEqual([1, 2]);
    expect(lista.botonesDe(nomina)).toEqual(expect.objectContaining({ editar: false, modelar: false, publicar: true }));
    expect(lista.botonesDe(horario)).toEqual(expect.objectContaining({ editar: true, modelar: true, publicar: true }));
  });

  it('una fila cuya pregunta falla, o que todavia no llega, sigue al rol', () => {
    permisos = { 1: throwError(() => new HttpErrorResponse({ status: 500 })), 2: of(['VER']) };

    const lista = abrir('EDITOR');

    expect(lista.botonesDe(nomina)).toEqual(expect.objectContaining({ editar: true, eliminar: false }));
    expect(lista.botonesDe(proceso(99, 'Otro'))).toEqual(expect.objectContaining({ editar: true, eliminar: false }));
  });

  it('quien solo lee no tiene botones en la lista y no pregunta por ninguna fila', () => {
    permisos = {};

    abrir('SOLO_LECTURA');

    expect(preguntados).toEqual([]);
  });
});
