import { HttpErrorResponse } from '@angular/common/http';
import { TestBed } from '@angular/core/testing';
import { ActivatedRoute, ParamMap, convertToParamMap, provideRouter } from '@angular/router';
import { BehaviorSubject, Observable, Subject, of, throwError } from 'rxjs';

import { ResultadoDeBusqueda } from '../../models/busqueda.model';
import { PageResponse } from '../../models/page-response.model';
import { Proceso } from '../../models/proceso.model';
import { PortalService } from '../../service/portal.service';
import { PortalComponent } from './portal.component';

const VACIA: PageResponse<Proceso> = { content: [], page: 0, size: 10, totalElements: 0, totalPages: 0 };

/**
 * La entrada del portal sin pintarla: la direccion decide si se lista una carpeta o se busca, una busqueda corta la
 * carga de la carpeta que iba en camino, y un error se dice. Los servicios son dobles.
 */
describe('PortalComponent', () => {
  let consulta: BehaviorSubject<ParamMap>;
  let publicados: ReturnType<
    typeof vi.fn<(carpeta: number | null, pagina: number) => Observable<PageResponse<Proceso>>>
  >;
  let buscar: ReturnType<typeof vi.fn<(texto: string) => Observable<ResultadoDeBusqueda[]>>>;

  beforeEach(() => {
    consulta = new BehaviorSubject(convertToParamMap({}));
    publicados = vi.fn(() => of(VACIA));
    buscar = vi.fn(() => of([]));
  });

  afterEach(() => TestBed.resetTestingModule());

  function crear(): PortalComponent {
    TestBed.configureTestingModule({
      providers: [
        provideRouter([]),
        { provide: ActivatedRoute, useValue: { queryParamMap: consulta } },
        { provide: PortalService, useValue: { publicados, buscar } },
      ],
    });
    const pagina = TestBed.runInInjectionContext(() => new PortalComponent());
    pagina.ngOnInit();
    return pagina;
  }

  it('sin texto lista lo publicado de la carpeta y la pagina de la direccion', () => {
    consulta.next(convertToParamMap({ carpeta: '4', pagina: '2' }));
    const pagina = crear();

    expect(publicados).toHaveBeenCalledWith(4, 2);
    expect(buscar).not.toHaveBeenCalled();
    expect(pagina.pagina()).toEqual(VACIA);
    expect(pagina.resultados()).toBeNull();
    expect(pagina.cargando()).toBe(false);
  });

  it('una busqueda corta la carga de la carpeta que iba en camino, y lo encontrado reemplaza la lista', () => {
    const enCamino = new Subject<PageResponse<Proceso>>();
    publicados.mockReturnValue(enCamino);
    const resultado = { tipo: 'PROCESO', procesoId: 7, titulo: 'Order fulfillment' } as ResultadoDeBusqueda;
    buscar.mockReturnValue(of([resultado]));
    const pagina = crear();
    expect(enCamino.observed).toBe(true);

    consulta.next(convertToParamMap({ q: 'order' }));

    expect(enCamino.observed).toBe(false);
    expect(buscar).toHaveBeenCalledWith('order');
    expect(pagina.consulta).toBe('order');
    expect(pagina.resultados()).toEqual([resultado]);
    expect(pagina.cargando()).toBe(false);
  });

  it('volver a la carpeta deja de mostrar los resultados', () => {
    consulta.next(convertToParamMap({ q: 'order' }));
    const pagina = crear();
    expect(pagina.resultados()).toEqual([]);

    consulta.next(convertToParamMap({}));

    expect(pagina.resultados()).toBeNull();
    expect(publicados).toHaveBeenCalledWith(null, 0);
  });

  it('un error de la API se dice, y la pagina deja de cargar', () => {
    publicados.mockReturnValue(throwError(() => new HttpErrorResponse({ status: 500 })));

    const pagina = crear();

    expect(pagina.error()).not.toBeNull();
    expect(pagina.cargando()).toBe(false);
  });
});
