import {
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
  ElementRef,
  NgZone,
  afterRenderEffect,
  computed,
  inject,
  signal,
  viewChild,
} from '@angular/core';
import { Router } from '@angular/router';

import { FechaPipe } from '../../pipes/fecha.pipe';
import { AuthService } from '../../service/auth.service';
import { VigenciaDeSesionService } from '../../service/vigencia-de-sesion.service';

/** Cuanto antes de cerrarse por inactividad avisa: lo que tarda alguien en volver a la pestaña y decidir. */
export const AVISO_DE_INACTIVIDAD_MS = 2 * 60 * 1000;

/** Cuanto antes del fin de la sesion avisa: no se puede alargar, pero se puede guardar lo que se esta haciendo. */
export const AVISO_DE_FIN_MS = 5 * 60 * 1000;

/**
 * F13: el aviso antes de que la sesion se cierre. Por inactividad, dos minutos antes, con la opcion de seguir, que la
 * renueva; por su fin, cinco minutos antes, solo para avisar: a esa hora hay que volver a entrar. Si llega a cero, la
 * web olvida la sesion en todas las pestañas y vuelve al login.
 *
 * El reloj corre fuera de la zona de Angular y solo toca la pantalla cuando falta poco, para que una sesion larga no
 * repinte la pagina cada segundo.
 */
@Component({
  selector: 'app-aviso-de-inactividad',
  imports: [FechaPipe],
  changeDetection: ChangeDetectionStrategy.Eager,
  templateUrl: './aviso-de-inactividad.component.html',
  styleUrl: './aviso-de-inactividad.component.scss',
})
export class AvisoDeInactividadComponent {
  private readonly vigencia: VigenciaDeSesionService = inject(VigenciaDeSesionService);
  private readonly authService: AuthService = inject(AuthService);
  private readonly router: Router = inject(Router);

  private readonly ahora = signal<number>(Date.now());
  private readonly seguir = viewChild<ElementRef<HTMLButtonElement>>('seguir');

  /** Lo que le queda a la sesion antes de cerrarse por inactividad, en milisegundos; null sin sesion. */
  readonly restante = computed<number | null>(() => {
    const inactiva: number | null = this.vigencia.inactivaEn();
    return inactiva === null ? null : Math.max(0, inactiva - this.ahora());
  });

  readonly avisoDeInactividad = computed<boolean>(() => {
    const restante: number | null = this.restante();
    return restante !== null && restante <= AVISO_DE_INACTIVIDAD_MS;
  });

  /** Cuando termina la sesion, si es pronto. */
  readonly finCercano = computed<Date | null>(() => {
    const termina: number | null = this.vigencia.terminaEn();
    return termina !== null && termina - this.ahora() <= AVISO_DE_FIN_MS ? new Date(termina) : null;
  });

  renovando: boolean = false;

  constructor() {
    const zona: NgZone = inject(NgZone);
    const reloj: ReturnType<typeof setInterval> = zona.runOutsideAngular(() => setInterval(() => this.latir(), 1000));
    inject(DestroyRef).onDestroy(() => clearInterval(reloj));
    // Quien usa el teclado tiene que poder contestar sin buscar el aviso: el foco va a seguir
    afterRenderEffect(() => {
      if (this.avisoDeInactividad()) {
        this.seguir()?.nativeElement.focus();
      }
    });
  }

  /** Minutos y segundos, como 1:05. */
  mmss(milisegundos: number | null): string {
    const segundos: number = Math.ceil((milisegundos ?? 0) / 1000);
    return `${Math.floor(segundos / 60)}:${String(segundos % 60).padStart(2, '0')}`;
  }

  seguirConectado(): void {
    this.renovando = true;
    this.authService.renovar().subscribe({
      next: () => (this.renovando = false),
      // La sesion ya no se pudo renovar: se cerro en otra parte, o llego a su fin
      error: () => this.expirar(),
    });
  }

  salir(): void {
    this.authService.logout().subscribe({
      complete: () => this.router.navigate(['/login']),
      error: () => this.router.navigate(['/login']),
    });
  }

  /** Cada segundo: si falta poco, la pantalla se entera; si ya no queda nada, la sesion se olvida. */
  private latir(): void {
    const ahora: number = Date.now();
    const inactiva: number | null = this.vigencia.inactivaEn();
    const termina: number | null = this.vigencia.terminaEn();
    if (inactiva === null && termina === null) {
      return;
    }
    const cerca: boolean =
      (inactiva !== null && inactiva - ahora <= AVISO_DE_INACTIVIDAD_MS + 1000) ||
      (termina !== null && termina - ahora <= AVISO_DE_FIN_MS + 1000);
    if (cerca || this.avisoDeInactividad() || this.finCercano() !== null) {
      this.ahora.set(ahora);
    }
    if ((inactiva !== null && inactiva <= ahora) || (termina !== null && termina <= ahora)) {
      this.expirar();
    }
  }

  private expirar(): void {
    this.renovando = false;
    this.authService.cerrarSesionLocal();
    this.router.navigate(['/login'], { queryParams: { sesion: 'vencida' } });
  }
}
