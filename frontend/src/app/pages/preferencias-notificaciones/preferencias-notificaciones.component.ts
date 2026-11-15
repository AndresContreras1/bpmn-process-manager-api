import { HttpErrorResponse } from '@angular/common/http';
import { ChangeDetectionStrategy, Component, DestroyRef, OnInit, inject } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormsModule } from '@angular/forms';
import { finalize } from 'rxjs';

import { mensajeDeError } from '../../helpers/errores-api';
import {
  ENVIOS,
  EnvioPorCorreo,
  NOMBRE_ENVIO,
  NOMBRE_TIPO_DE_NOTIFICACION,
  PreferenciaDeNotificacion,
  TIPOS_DE_NOTIFICACION,
  TipoDeNotificacion,
} from '../../models/notificacion.model';
import { NotificacionService } from '../../service/notificacion.service';

/**
 * PR F21: como le llega a cada persona por correo cada tipo de aviso: en el momento, en el resumen del dia o nunca.
 * En la web llegan siempre. Se guardan solo los tipos que cambiaron, que es lo que la API deja como estaban si no se
 * mandan; un tipo que nadie toco sigue el valor por defecto aunque ese cambie.
 */
@Component({
  selector: 'app-preferencias-notificaciones',
  imports: [FormsModule],
  templateUrl: './preferencias-notificaciones.component.html',
  changeDetection: ChangeDetectionStrategy.Eager,
})
export class PreferenciasNotificacionesComponent implements OnInit {
  private readonly notificaciones = inject(NotificacionService);
  private readonly destroyRef = inject(DestroyRef);

  readonly envios: readonly EnvioPorCorreo[] = ENVIOS;
  readonly nombreEnvio: Record<EnvioPorCorreo, string> = NOMBRE_ENVIO;
  readonly nombreTipo: Record<TipoDeNotificacion, string> = NOMBRE_TIPO_DE_NOTIFICACION;

  preferencias: PreferenciaDeNotificacion[] = [];
  /** Lo elegido en pantalla, por tipo. */
  elegidas: Partial<Record<TipoDeNotificacion, EnvioPorCorreo>> = {};
  cargando: boolean = true;
  enviando: boolean = false;
  error: string | null = null;
  aviso: string | null = null;

  /** Los tipos cuyo envio elegido no es el guardado. */
  get cambiadas(): { tipo: TipoDeNotificacion; envio: EnvioPorCorreo }[] {
    return this.preferencias
      .filter((una) => this.elegidas[una.tipo] && this.elegidas[una.tipo] !== una.envio)
      .map((una) => ({ tipo: una.tipo, envio: this.elegidas[una.tipo] as EnvioPorCorreo }));
  }

  ngOnInit(): void {
    this.notificaciones
      .preferencias()
      .pipe(
        finalize(() => (this.cargando = false)),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe({
        next: (preferencias) => this.mostrar(preferencias),
        error: (error: HttpErrorResponse) => (this.error = mensajeDeError(error)),
      });
  }

  guardar(): void {
    const cambiadas = this.cambiadas;
    if (cambiadas.length === 0 || this.enviando) {
      return;
    }
    this.enviando = true;
    this.error = null;
    this.aviso = null;
    this.notificaciones
      .guardarPreferencias(cambiadas)
      .pipe(
        finalize(() => (this.enviando = false)),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe({
        next: (preferencias) => {
          this.mostrar(preferencias);
          this.aviso = $localize`Your notification settings were saved.`;
        },
        error: (error: HttpErrorResponse) => (this.error = mensajeDeError(error)),
      });
  }

  /** En el orden de la pantalla, aunque la API devuelva otro. */
  private mostrar(preferencias: PreferenciaDeNotificacion[]): void {
    this.preferencias = [...preferencias].sort(
      (una, otra) => TIPOS_DE_NOTIFICACION.indexOf(una.tipo) - TIPOS_DE_NOTIFICACION.indexOf(otra.tipo),
    );
    this.elegidas = Object.fromEntries(this.preferencias.map((una) => [una.tipo, una.envio]));
  }
}
