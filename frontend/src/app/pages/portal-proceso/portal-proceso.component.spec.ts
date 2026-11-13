import { HttpErrorResponse } from '@angular/common/http';
import { ElementRef } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { ActivatedRoute, ParamMap, convertToParamMap } from '@angular/router';
import { BehaviorSubject, Observable, of, throwError } from 'rxjs';

import { ProcesoDelPortal } from '../../models/portal.model';
import { ProcesoDetalle } from '../../models/proceso.model';
import { AuthService } from '../../service/auth.service';
import { PortalService } from '../../service/portal.service';
import { PortalProcesoComponent } from './portal-proceso.component';

function leido(id: number): ProcesoDelPortal {
  return {
    detalle: {
      proceso: { id, nombre: `Proceso ${id}`, versionPublicada: 1 },
      historial: [],
    } as unknown as ProcesoDetalle,
    xml: '<definitions/>',
    acciones: ['VER'],
    caracterizacion: null,
    textos: [],
    documentos: [],
    manuales: [],
  };
}

/**
 * La pagina de un proceso del portal sin pintarla: que dice cuando el proceso no esta y cuando falla otra cosa, que
 * anuncia al elegir un elemento, y cuando elige el que pide la direccion (?elemento=). Los servicios son dobles.
 */
describe('PortalProcesoComponent', () => {
  let parametros: BehaviorSubject<ParamMap>;
  let consulta: BehaviorSubject<ParamMap>;
  let snapshot: { paramMap: ParamMap };
  let abrir: ReturnType<typeof vi.fn<(id: number) => Observable<ProcesoDelPortal>>>;

  beforeEach(() => {
    parametros = new BehaviorSubject(convertToParamMap({ id: '7' }));
    consulta = new BehaviorSubject(convertToParamMap({}));
    snapshot = { paramMap: convertToParamMap({ id: '7' }) };
    abrir = vi.fn((id: number) => of(leido(id)));
  });

  afterEach(() => TestBed.resetTestingModule());

  function crear(): PortalProcesoComponent {
    TestBed.configureTestingModule({
      providers: [
        { provide: ActivatedRoute, useValue: { paramMap: parametros, queryParamMap: consulta, snapshot } },
        { provide: PortalService, useValue: { abrir } },
        { provide: AuthService, useValue: { usuarioActual: () => ({ rolAcceso: 'SOLO_LECTURA' }) } },
        { provide: ElementRef, useValue: new ElementRef(document.createElement('div')) },
      ],
    });
    const pagina = TestBed.runInInjectionContext(() => new PortalProcesoComponent());
    pagina.ngOnInit();
    return pagina;
  }

  /** Un lienzo de mentira: solo importa a que elemento se le pide ir. */
  function conLienzo(pagina: PortalProcesoComponent): ReturnType<typeof vi.fn> {
    const mostrar = vi.fn();
    Object.defineProperty(pagina, 'lienzo', { value: () => ({ mostrar }) });
    return mostrar;
  }

  it.each([404, 400])('con %s el proceso no esta: no se ofrece reintentar', (estado: number) => {
    abrir.mockReturnValue(throwError(() => new HttpErrorResponse({ status: estado })));

    const pagina = crear();

    expect(pagina.noEncontrado()).toBe(true);
    expect(pagina.error()).toBeNull();
    expect(pagina.cargando()).toBe(false);
  });

  it('otro error se dice, y reintentar lo vuelve a pedir y lo muestra', () => {
    abrir.mockReturnValueOnce(throwError(() => new HttpErrorResponse({ status: 500 })));
    const pagina = crear();

    expect(pagina.noEncontrado()).toBe(false);
    expect(pagina.error()).not.toBeNull();

    pagina.reintentar();

    expect(abrir).toHaveBeenLastCalledWith(7);
    expect(pagina.error()).toBeNull();
    expect(pagina.cargando()).toBe(false);
    expect(pagina.proceso()?.id).toBe(7);
  });

  it('reintentar que vuelve a fallar lo dice otra vez', () => {
    abrir.mockReturnValue(throwError(() => new HttpErrorResponse({ status: 503 })));
    const pagina = crear();

    pagina.reintentar();

    expect(pagina.error()).not.toBeNull();
    expect(pagina.cargando()).toBe(false);
  });

  it('elegir anuncia el elemento por su nombre, o por su id si no tiene, y soltarlo vuelve al proceso entero', () => {
    const pagina = crear();

    pagina.elegir({ id: 'Task_1', tipo: 'bpmn:Task', nombre: 'Wrap the order' });
    expect(pagina.elemento()).toEqual({ id: 'Task_1', nombre: 'Wrap the order' });
    expect(pagina.anuncio()).toContain('Wrap the order');

    pagina.elegir({ id: 'Task_2', tipo: 'bpmn:Task', nombre: '' });
    expect(pagina.anuncio()).toContain('Task_2');

    pagina.elegir(null);
    expect(pagina.elemento()).toBeNull();
    expect(pagina.anuncio()).toContain('whole process');
  });

  it('el elemento de la direccion se elige cuando el diagrama termina de dibujarse', () => {
    consulta.next(convertToParamMap({ elemento: 'Task_1' }));
    const pagina = crear();
    const mostrar = conLienzo(pagina);

    expect(mostrar).not.toHaveBeenCalled();
    pagina.alDibujar();
    expect(mostrar).toHaveBeenCalledWith('Task_1');

    pagina.alDibujar();
    expect(mostrar).toHaveBeenCalledTimes(1);
  });

  it('al pasar a otro proceso, su elemento no se busca en el diagrama que se va sino en el nuevo', () => {
    const pagina = crear();
    const mostrar = conLienzo(pagina);
    pagina.alDibujar();

    // El router cambia la direccion entera, y avisa de la consulta antes que del id
    snapshot.paramMap = convertToParamMap({ id: '8' });
    consulta.next(convertToParamMap({ elemento: 'Task_9' }));
    expect(mostrar).not.toHaveBeenCalled();

    parametros.next(convertToParamMap({ id: '8' }));
    expect(abrir).toHaveBeenLastCalledWith(8);
    pagina.alDibujar();
    expect(mostrar).toHaveBeenCalledWith('Task_9');
  });

  it('en el mismo proceso, un elemento nuevo en la direccion se elige al momento', () => {
    const pagina = crear();
    const mostrar = conLienzo(pagina);
    pagina.alDibujar();

    consulta.next(convertToParamMap({ elemento: 'Task_3' }));

    expect(mostrar).toHaveBeenCalledWith('Task_3');
  });
});
