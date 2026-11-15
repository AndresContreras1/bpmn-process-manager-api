import { HttpErrorResponse } from '@angular/common/http';
import { TestBed } from '@angular/core/testing';
import { ActivatedRoute, convertToParamMap, provideRouter } from '@angular/router';
import { Observable, of, throwError } from 'rxjs';

import { Accion } from '../../models/concesion.model';
import { ProcesoDetalle } from '../../models/proceso.model';
import { RolAcceso } from '../../models/usuario.model';
import { AuthService } from '../../service/auth.service';
import { ConcesionService } from '../../service/concesion.service';
import { DiagramaService } from '../../service/diagrama.service';
import { ModeloService } from '../../service/modelo.service';
import { ProcesoService } from '../../service/proceso.service';
import { VersionService } from '../../service/version.service';
import { ProcesoDetalleComponent } from './proceso-detalle.component';

/**
 * Lo que la pagina de un proceso hace con lo que carga, sin pintarla: que botones salen de la respuesta de permisos y
 * que dice al llegar del formulario. Los servicios son dobles; el diagrama no llega, que la pagina ya lo tolera.
 */
describe('ProcesoDetalleComponent: lo que hace con la carga', () => {
  const detalle: ProcesoDetalle = {
    proceso: {
      id: 7,
      nombre: 'Payroll',
      descripcion: 'Pay the people of the store.',
      categoria: 'People',
      estado: 'BORRADOR',
      activo: true,
      fechaCreacion: '2026-11-01T10:00:00Z',
      fechaModificacion: '2026-11-01T10:00:00Z',
      version: 0,
      versionPublicada: null,
      borradorPendiente: null,
    },
    historial: [],
  };
  const sinDiagrama = (): Observable<never> => throwError(() => new HttpErrorResponse({ status: 404 }));
  let permisos: Observable<Accion[]>;

  function abrir(rol: RolAcceso, consulta: Record<string, string> = {}): ProcesoDetalleComponent {
    TestBed.configureTestingModule({
      providers: [
        provideRouter([]),
        {
          provide: ActivatedRoute,
          useValue: {
            snapshot: { queryParamMap: convertToParamMap(consulta) },
            paramMap: of(convertToParamMap({ id: '7' })),
            queryParamMap: of(convertToParamMap(consulta)),
          },
        },
        {
          provide: AuthService,
          useValue: {
            esAdministrador: () => rol === 'ADMINISTRADOR',
            usuarioActual: () => ({ id: 3, rolAcceso: rol }),
          },
        },
        { provide: ProcesoService, useValue: { obtener: () => of(detalle) } },
        { provide: DiagramaService, useValue: { obtener: sinDiagrama } },
        { provide: ModeloService, useValue: { leer: sinDiagrama } },
        { provide: VersionService, useValue: { listar: () => of([]) } },
        { provide: ConcesionService, useValue: { permisos: () => permisos } },
      ],
    });
    const pagina = TestBed.runInInjectionContext(() => new ProcesoDetalleComponent());
    pagina.ngOnInit();
    return pagina;
  }

  it('los botones salen de lo que la API dice que se puede, no del rol', () => {
    // Un editor al que una concesion de otra persona le quita modelar: el rol solo le daria editar y mover
    permisos = of(['VER', 'COMENTAR', 'PUBLICAR', 'EXPORTAR']);

    const pagina = abrir('EDITOR');

    expect(pagina.cargando).toBe(false);
    expect(pagina.botones).toEqual(
      expect.objectContaining({ editar: false, modelar: false, publicar: true, exportar: true, permisos: false }),
    );
  });

  it('con ADMINISTRAR concedido, un editor ve los permisos del proceso, que su rol no le daria', () => {
    permisos = of(['VER', 'MODELAR', 'PUBLICAR', 'EXPORTAR', 'ADMINISTRAR']);

    expect(abrir('EDITOR').botones).toEqual(
      expect.objectContaining({ editar: true, permisos: true, compartir: false, eliminar: false }),
    );
  });

  it('si la respuesta de permisos no llega, los botones siguen al rol', () => {
    permisos = throwError(() => new HttpErrorResponse({ status: 500 }));

    expect(abrir('EDITOR').botones).toEqual(
      expect.objectContaining({ editar: true, modelar: true, publicar: true, permisos: false }),
    );
  });

  it('al llegar de crear sin ficha, dice las dos cosas despues de cargar, que limpia los mensajes', () => {
    permisos = of(['VER']);

    const pagina = abrir('EDITOR', { creado: '1', sinArchivar: '1' });

    expect(pagina.aviso).toBe('Process created as a draft.');
    expect(pagina.error).toContain('filing could not be saved');
  });

  it('al llegar de editar, lo dice', () => {
    permisos = of(['VER']);

    const pagina = abrir('EDITOR', { editado: '1' });

    expect(pagina.aviso).toBe('Changes saved.');
    expect(pagina.error).toBeNull();
  });
});
