import { HttpErrorResponse } from '@angular/common/http';
import { ChangeDetectionStrategy, Component, DestroyRef, OnInit, inject } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormsModule } from '@angular/forms';
import { finalize } from 'rxjs';

import { mensajeDeError } from '../../helpers/errores-api';
import {
  FiltrosDeBitacora,
  PaginaDeBitacora,
  RegistroDeBitacora,
  VerificacionDeBitacora,
} from '../../models/seguridad.model';
import { FechaPipe } from '../../pipes/fecha.pipe';
import { AuthService } from '../../service/auth.service';
import { SeguridadDeLaCuentaService } from '../../service/seguridad-de-la-cuenta.service';

/**
 * F14: la bitacora de la organizacion (PR 31), para su administrador: quien hizo que cosa de seguridad, desde donde y
 * si salio bien, filtrada por accion y por fechas, exportable, y con la comprobacion de que nadie la toco: cada
 * registro lleva la huella del anterior.
 */
@Component({
  selector: 'app-bitacora',
  imports: [FormsModule, FechaPipe],
  changeDetection: ChangeDetectionStrategy.Eager,
  templateUrl: './bitacora.component.html',
})
export class BitacoraComponent implements OnInit {
  private readonly seguridad: SeguridadDeLaCuentaService = inject(SeguridadDeLaCuentaService);
  private readonly destroyRef: DestroyRef = inject(DestroyRef);

  readonly esAdministrador: boolean = inject(AuthService).esAdministrador();
  /** Las acciones que la API registra, para sugerirlas al filtrar: el filtro es por el nombre exacto. */
  readonly acciones: readonly string[] = [
    'SESION_INICIADA',
    'CLAVE_RESTABLECIDA',
    'SEGUNDO_FACTOR_ACTIVADO',
    'SEGUNDO_FACTOR_QUITADO',
    'CODIGOS_DE_RECUPERACION_RENOVADOS',
    'PASSKEY_REGISTRADA',
    'PASSKEY_QUITADA',
    'IDENTIDAD_VINCULADA',
    'IDENTIDAD_DESVINCULADA',
    'INVITACION_ENVIADA',
    'USUARIO_CAMBIADO',
    'USUARIO_DESACTIVADO',
    'USUARIO_ANONIMIZADO',
    'USUARIO_UNIDO_POR_DOMINIO',
    'DOMINIO_VERIFICADO',
    'CONFIGURACION_CAMBIADA',
    'PREFERENCIAS_CAMBIADAS',
    'PROCESO_PUBLICADO',
    'TIENDA_DADA_DE_BAJA',
    'BAJA_CANCELADA',
  ];

  accion: string = '';
  /** Fechas del formulario, como las da un input de tipo date: el dia entero, en la zona del navegador. */
  desde: string = '';
  hasta: string = '';
  registros: RegistroDeBitacora[] = [];
  siguiente: number | null = null;
  cargando: boolean = false;
  verificando: boolean = false;
  verificacion: VerificacionDeBitacora | null = null;
  error: string | null = null;

  ngOnInit(): void {
    if (this.esAdministrador) {
      this.buscar();
    }
  }

  buscar(): void {
    this.registros = [];
    this.siguiente = null;
    this.leer(null);
  }

  masAntiguos(): void {
    this.leer(this.siguiente);
  }

  verificar(): void {
    this.verificando = true;
    this.verificacion = null;
    this.seguridad
      .verificarBitacora()
      .pipe(
        finalize(() => (this.verificando = false)),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe({
        next: (verificacion: VerificacionDeBitacora) => (this.verificacion = verificacion),
        error: (error: HttpErrorResponse) => (this.error = mensajeDeError(error)),
      });
  }

  exportacion(formato: 'csv' | 'json'): string {
    return this.seguridad.exportacion(formato, this.filtros(null));
  }

  private leer(antes: number | null): void {
    this.cargando = true;
    this.error = null;
    this.seguridad
      .bitacora(this.filtros(antes))
      .pipe(
        finalize(() => (this.cargando = false)),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe({
        next: (pagina: PaginaDeBitacora) => {
          this.registros = [...this.registros, ...pagina.registros];
          this.siguiente = pagina.siguiente;
        },
        error: (error: HttpErrorResponse) => (this.error = mensajeDeError(error)),
      });
  }

  /** La API guarda y filtra en UTC: el dia elegido empieza y termina a la medianoche de quien mira. */
  private filtros(antes: number | null): FiltrosDeBitacora {
    return {
      accion: this.accion.trim().toUpperCase() || null,
      desde: this.desde ? utc(`${this.desde}T00:00:00`) : null,
      hasta: this.hasta ? utc(`${this.hasta}T23:59:59`) : null,
      antes,
    };
  }
}

/** Una hora local del navegador como la espera la API: ISO sin zona, en UTC. */
function utc(local: string): string {
  return new Date(local).toISOString().slice(0, 19);
}
