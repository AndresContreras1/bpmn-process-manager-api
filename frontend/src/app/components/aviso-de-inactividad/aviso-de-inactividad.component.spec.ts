import { LOCALE_ID, signal } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';
import { of, throwError } from 'rxjs';

import { AuthService } from '../../service/auth.service';
import { PreferenciasService } from '../../service/preferencias.service';
import { VigenciaDeSesionService } from '../../service/vigencia-de-sesion.service';
import { AvisoDeInactividadComponent } from './aviso-de-inactividad.component';

describe('AvisoDeInactividadComponent', () => {
  const sesion = { renovar: vi.fn(), logout: vi.fn(() => of(undefined)), cerrarSesionLocal: vi.fn() };
  let vigencia: VigenciaDeSesionService;
  let fixture: ComponentFixture<AvisoDeInactividadComponent>;
  let navegar: ReturnType<typeof vi.spyOn>;

  beforeEach(() => {
    vi.useFakeTimers();
    vi.clearAllMocks();
    TestBed.configureTestingModule({
      imports: [AvisoDeInactividadComponent],
      providers: [
        provideRouter([]),
        { provide: AuthService, useValue: sesion },
        { provide: LOCALE_ID, useValue: 'en-US' },
        { provide: PreferenciasService, useValue: { zonaHoraria: signal('UTC') } },
      ],
    });
    vigencia = TestBed.inject(VigenciaDeSesionService);
    navegar = vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);
    fixture = TestBed.createComponent(AvisoDeInactividadComponent);
    fixture.detectChanges();
  });

  afterEach(() => vi.useRealTimers());

  function pasar(segundos: number): void {
    vi.advanceTimersByTime(segundos * 1000);
    fixture.detectChanges();
  }

  const aviso = (): HTMLElement | null => fixture.nativeElement.querySelector('#aviso-inactividad');

  it('no dice nada mientras falte mas de dos minutos', () => {
    vigencia.registrar(3600, 86400);
    pasar(60);

    expect(aviso()).toBeNull();
  });

  it('a dos minutos avisa, cuenta hacia atras, y seguir renueva la sesion', () => {
    sesion.renovar.mockImplementation(() => {
      vigencia.registrar(3600, 86400);
      return of(undefined);
    });
    vigencia.registrar(125, 86400);
    pasar(6);

    expect(aviso()).not.toBeNull();
    expect(fixture.nativeElement.querySelector('#aviso-inactividad-restante').textContent).toBe('1:59');
    pasar(10);
    expect(fixture.nativeElement.querySelector('#aviso-inactividad-restante').textContent).toBe('1:49');

    fixture.nativeElement.querySelector('#aviso-inactividad-seguir').click();
    pasar(1);

    expect(sesion.renovar).toHaveBeenCalledTimes(1);
    expect(aviso()).toBeNull();
  });

  it('a cero olvida la sesion en todas las pestañas y vuelve al login', () => {
    vigencia.registrar(30, 86400);
    pasar(31);

    expect(sesion.cerrarSesionLocal).toHaveBeenCalled();
    expect(navegar).toHaveBeenCalledWith(['/login'], { queryParams: { sesion: 'vencida' } });
  });

  it('si la sesion ya no se deja renovar, tambien vuelve al login', () => {
    sesion.renovar.mockReturnValue(throwError(() => new Error('401')));
    vigencia.registrar(60, 86400);
    pasar(1);
    fixture.nativeElement.querySelector('#aviso-inactividad-seguir').click();

    expect(sesion.cerrarSesionLocal).toHaveBeenCalled();
    expect(navegar).toHaveBeenCalledWith(['/login'], { queryParams: { sesion: 'vencida' } });
  });

  it('cinco minutos antes del fin avisa la hora: eso no se alarga', () => {
    vigencia.registrar(3600, 290);
    pasar(1);

    expect(aviso()).toBeNull();
    expect(fixture.nativeElement.querySelector('#aviso-fin-de-sesion')).not.toBeNull();
  });
});
