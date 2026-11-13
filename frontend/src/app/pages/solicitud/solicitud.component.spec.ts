import { TestBed } from '@angular/core/testing';
import { ActivatedRoute, convertToParamMap, provideRouter } from '@angular/router';
import { Observable, of } from 'rxjs';
import { afterEach, describe, expect, it, vi } from 'vitest';

import { Accion } from '../../models/concesion.model';
import { Solicitud } from '../../models/solicitud.model';
import { AuthService } from '../../service/auth.service';
import { ConcesionService } from '../../service/concesion.service';
import { ModeloService } from '../../service/modelo.service';
import { SolicitudService } from '../../service/solicitud.service';
import { VersionService } from '../../service/version.service';
import { SolicitudComponent } from './solicitud.component';

/**
 * Lo que la pagina de una solicitud hace con sus botones, sin pintarla: quien la puede retirar despues de decidir, y
 * que retirarla pregunta antes. Los servicios son dobles.
 */
describe('SolicitudComponent: decidir y retirar', () => {
  const ANA = 3;
  const BETO = 4;
  const abierta: Solicitud = {
    id: 9,
    procesoId: 7,
    proceso: 'Order fulfillment',
    estado: 'ABIERTA',
    solicitante: { id: BETO, nombre: 'Beto' },
    markdown: null,
    html: null,
    numero: 2,
    versionBase: 1,
    revisionId: 31,
    aprobacionesNecesarias: 2,
    aprobaciones: 0,
    aprobadores: [{ id: ANA, nombre: 'Ana' }],
    decisiones: [],
    motivo: null,
    cerradaPor: null,
    cerradaEn: null,
    versionPublicada: null,
    fechaCreacion: '2026-11-12T10:00:00Z',
    diferencia: null,
  };
  let solicitudes: {
    obtener: () => Observable<Solicitud>;
    decidir: ReturnType<typeof vi.fn>;
    retirar: ReturnType<typeof vi.fn>;
  };

  afterEach(() => TestBed.resetTestingModule());

  function abrir(acciones: Accion[]): SolicitudComponent {
    solicitudes = {
      obtener: () => of(abierta),
      decidir: vi.fn(),
      retirar: vi.fn(() => of({ ...abierta, estado: 'RETIRADA' })),
    };
    TestBed.configureTestingModule({
      providers: [
        provideRouter([]),
        { provide: ActivatedRoute, useValue: { paramMap: of(convertToParamMap({ id: '9' })) } },
        { provide: AuthService, useValue: { usuarioActual: () => ({ id: ANA, rolAcceso: 'ADMINISTRADOR' }) } },
        { provide: SolicitudService, useValue: solicitudes },
        { provide: ModeloService, useValue: { modeloDeRevision: () => of('<definitions/>') } },
        { provide: VersionService, useValue: {} },
        { provide: ConcesionService, useValue: { permisos: () => of(acciones) } },
      ],
    });
    const pagina = TestBed.runInInjectionContext(() => new SolicitudComponent());
    pagina.ngOnInit();
    return pagina;
  }

  it('quien administra el proceso sigue pudiendo retirarla tras aprobar, mientras falten aprobaciones', () => {
    const pagina = abrir(['VER', 'APROBAR', 'ADMINISTRAR']);
    expect(pagina.retira).toBe(true);
    solicitudes.decidir.mockReturnValue(of({ ...abierta, aprobaciones: 1 }));

    pagina.elegirDecision('APROBADA');
    pagina.decidir();

    expect(pagina.solicitud?.aprobaciones).toBe(1);
    expect(pagina.retira).toBe(true);
  });

  it('cuando la decision la cierra, ya no hay nada que retirar', () => {
    const pagina = abrir(['VER', 'APROBAR', 'ADMINISTRAR']);
    solicitudes.decidir.mockReturnValue(of({ ...abierta, estado: 'PUBLICADA', versionPublicada: 2 }));

    pagina.elegirDecision('APROBADA');
    pagina.decidir();

    expect(pagina.retira).toBe(false);
  });

  it('quien no la pidio ni administra el proceso no la puede retirar', () => {
    expect(abrir(['VER', 'APROBAR']).retira).toBe(false);
  });

  it('retirarla pregunta primero, y solo la confirmacion la retira', () => {
    const pagina = abrir(['VER', 'ADMINISTRAR']);

    pagina.retirar();
    expect(solicitudes.retirar).not.toHaveBeenCalled();

    pagina.pedirRetiro();
    expect(pagina.confirmandoRetiro).toBe(true);
    expect(solicitudes.retirar).not.toHaveBeenCalled();

    pagina.cancelarRetiro();
    pagina.retirar();
    expect(solicitudes.retirar).not.toHaveBeenCalled();

    pagina.pedirRetiro();
    pagina.retirar();
    expect(solicitudes.retirar).toHaveBeenCalledWith(9);
    expect(pagina.solicitud?.estado).toBe('RETIRADA');
    expect(pagina.confirmandoRetiro).toBe(false);
    expect(pagina.retira).toBe(false);
  });
});
