import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Observable, of } from 'rxjs';

import { Manual } from '../../../models/documentacion.model';
import { ManualService } from '../../../service/manual.service';
import { INTERVALO_DEL_MANUAL, ManualDelPortalComponent } from './manual-del-portal.component';

function manual(id: number, cambios: Partial<Manual> = {}): Manual {
  return {
    id,
    procesoId: 7,
    version: 2,
    idioma: 'en-US',
    estado: 'DISPONIBLE',
    motivo: null,
    pedidoEn: '2026-11-12T10:00:00',
    listoEn: '2026-11-12T10:01:00',
    ...cambios,
  };
}

/**
 * El manual del portal con su pantalla: quien no puede exportar no tiene boton para pedirlo ni lo pide aunque se
 * llame al metodo; quien puede lo pide, y mientras la cola lo arma la lista se vuelve a mirar hasta que termina.
 */
describe('ManualDelPortalComponent', () => {
  let pedidos: number;
  let listas: Manual[][];
  let fixture: ComponentFixture<ManualDelPortalComponent>;

  beforeEach(() => {
    vi.useFakeTimers();
    pedidos = 0;
    listas = [];
    TestBed.configureTestingModule({
      imports: [ManualDelPortalComponent],
      providers: [
        {
          provide: ManualService,
          useValue: {
            pedir: (): Observable<Manual> => {
              pedidos++;
              return of(manual(9, { estado: 'PENDIENTE', listoEn: null }));
            },
            listar: (): Observable<Manual[]> => of(listas.shift() ?? []),
            enlace: () => of({ url: '/x', venceEn: '' }),
          },
        },
      ],
    });
    fixture = TestBed.createComponent(ManualDelPortalComponent);
    fixture.componentRef.setInput('procesoId', 7);
    fixture.componentRef.setInput('version', 2);
  });

  afterEach(() => vi.useRealTimers());

  const boton = (id: string): HTMLButtonElement | null => fixture.nativeElement.querySelector(`#${id}`);
  const anuncio = (): string => fixture.nativeElement.querySelector('#portal-manual-anuncio').textContent.trim();

  function con(manuales: Manual[], puedePedir: boolean): void {
    fixture.componentRef.setInput('manualesLeidos', manuales);
    fixture.componentRef.setInput('puedePedir', puedePedir);
    fixture.detectChanges();
  }

  it('quien no puede exportar baja el que hay listo y no tiene boton para pedir otro', () => {
    con([manual(4, { version: 1 }), manual(3)], false);

    expect(fixture.nativeElement.querySelector('#portal-manual-listo').dataset.manual).toBe('3');
    expect(boton('portal-manual-pdf')).not.toBeNull();
    expect(boton('portal-manual-pedir')).toBeNull();
    fixture.componentInstance.pedir();
    expect(pedidos).toBe(0);
  });

  it('sin uno listo de la version vigente lo dice', () => {
    con([manual(4, { version: 1 })], false);

    expect(fixture.nativeElement.querySelector('#portal-manual-vacio')).not.toBeNull();
    expect(boton('portal-manual-pdf')).toBeNull();
  });

  it('quien puede lo pide, se anuncia, y la lista se vuelve a mirar hasta que esta listo', () => {
    con([], true);
    boton('portal-manual-pedir')!.click();
    fixture.detectChanges();

    expect(pedidos).toBe(1);
    expect(anuncio()).toContain('being built');
    expect(fixture.nativeElement.querySelector('#portal-manual-en-curso')).not.toBeNull();
    expect(boton('portal-manual-pedir')).toBeNull();

    listas.push([manual(9, { estado: 'PENDIENTE', listoEn: null })], [manual(9)]);
    vi.advanceTimersByTime(INTERVALO_DEL_MANUAL);
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('#portal-manual-en-curso')).not.toBeNull();
    vi.advanceTimersByTime(INTERVALO_DEL_MANUAL);
    fixture.detectChanges();

    expect(anuncio()).toContain('ready to download');
    expect(fixture.nativeElement.querySelector('#portal-manual-listo').dataset.manual).toBe('9');
    expect(boton('portal-manual-pedir')!.textContent).toContain('Build it again');
    listas.push([manual(10)]);
    vi.advanceTimersByTime(INTERVALO_DEL_MANUAL * 3);
    expect(listas).toHaveLength(1);
  });

  it('un manual que no se pudo armar se anuncia con su motivo', () => {
    con([manual(9, { estado: 'PENDIENTE', listoEn: null })], true);
    listas.push([manual(9, { estado: 'FALLIDO', motivo: 'No font' })]);
    vi.advanceTimersByTime(INTERVALO_DEL_MANUAL);
    fixture.detectChanges();

    expect(anuncio()).toContain('could not be built: No font');
    expect(boton('portal-manual-pedir')!.textContent).toContain('Build the manual');
  });
});
