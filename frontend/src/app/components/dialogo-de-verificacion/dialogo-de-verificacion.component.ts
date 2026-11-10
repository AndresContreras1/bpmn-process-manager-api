import { HttpErrorResponse } from '@angular/common/http';
import {
  ChangeDetectionStrategy,
  Component,
  ElementRef,
  afterRenderEffect,
  effect,
  inject,
  untracked,
  viewChild,
} from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Observable, catchError, of } from 'rxjs';

import { mensajeDeError } from '../../helpers/errores-api';
import { fueCancelada, soportaPasskeys } from '../../helpers/webauthn';
import { LoginResponse } from '../../models/login.model';
import { EstadoSegundoFactor } from '../../models/seguridad.model';
import { SegundoFactorService } from '../../service/segundo-factor.service';
import { VerificacionService } from '../../service/verificacion.service';

/**
 * F14: el dialogo que pide confirmar quien se es para lo que exige una verificacion reciente. Con la clave, con un
 * codigo de la aplicacion o de recuperacion, o firmando con una passkey; lo que cada persona tenga.
 */
@Component({
  selector: 'app-dialogo-de-verificacion',
  imports: [FormsModule],
  changeDetection: ChangeDetectionStrategy.Eager,
  templateUrl: './dialogo-de-verificacion.component.html',
})
export class DialogoDeVerificacionComponent {
  readonly verificacion: VerificacionService = inject(VerificacionService);
  private readonly segundoFactor: SegundoFactorService = inject(SegundoFactorService);
  private readonly campo = viewChild<ElementRef<HTMLInputElement>>('campo');

  modo: 'clave' | 'codigo' = 'clave';
  clave: string = '';
  codigo: string = '';
  factores: EstadoSegundoFactor | null = null;
  readonly passkeysEnEsteNavegador: boolean = soportaPasskeys();
  enviando: boolean = false;
  error: string | null = null;

  constructor() {
    // Al abrirse, lo que la persona tiene para confirmar; si no se sabe, la clave sirve siempre
    effect(() => {
      if (this.verificacion.pidiendo()) {
        untracked(() => this.preparar());
      }
    });
    afterRenderEffect(() => {
      if (this.verificacion.pidiendo()) {
        this.campo()?.nativeElement.focus();
      }
    });
  }

  confirmar(): void {
    const prueba = this.modo === 'clave' ? { password: this.clave } : { codigo: this.codigo.trim() };
    this.enviar(this.segundoFactor.reautenticar(prueba));
  }

  conPasskey(): void {
    this.enviar(this.segundoFactor.reautenticarConPasskey());
  }

  cancelar(): void {
    this.verificacion.cancelada();
  }

  cambiarModo(): void {
    this.modo = this.modo === 'clave' ? 'codigo' : 'clave';
    this.error = null;
  }

  private preparar(): void {
    this.modo = 'clave';
    this.clave = '';
    this.codigo = '';
    this.error = null;
    this.enviando = false;
    this.segundoFactor
      .estado()
      .pipe(catchError(() => of(null)))
      .subscribe((estado: EstadoSegundoFactor | null) => (this.factores = estado));
  }

  private enviar(peticion: Observable<LoginResponse>): void {
    this.enviando = true;
    this.error = null;
    peticion.subscribe({
      next: () => {
        this.enviando = false;
        this.verificacion.confirmada();
      },
      error: (error: unknown) => {
        this.enviando = false;
        if (fueCancelada(error)) {
          return;
        }
        this.error =
          error instanceof HttpErrorResponse && error.status === 401
            ? $localize`That did not confirm it. Check it and try again.`
            : mensajeDeError(error as HttpErrorResponse);
      },
    });
  }
}
