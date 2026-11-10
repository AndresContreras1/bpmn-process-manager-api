import { HttpErrorResponse } from '@angular/common/http';
import { Component, DestroyRef, inject, ChangeDetectionStrategy } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { finalize, switchMap } from 'rxjs';

import { ErrorCampoComponent } from '../../components/error-campo/error-campo.component';
import { marcarErroresDelServidor, mensajeDeError } from '../../helpers/errores-api';
import { RegistroEmpresaRequest } from '../../models/empresa.model';
import { AuthService } from '../../service/auth.service';
import { EmpresaService } from '../../service/empresa.service';
import { IdiomaService } from '../../service/idioma.service';
import { zonaDelNavegador } from '../../service/preferencias.service';

@Component({
  selector: 'app-registro',
  imports: [ReactiveFormsModule, RouterLink, ErrorCampoComponent],
  templateUrl: './registro.component.html',
  changeDetection: ChangeDetectionStrategy.Eager,
  styleUrl: './registro.component.scss',
})
export class RegistroComponent {
  private readonly empresaService: EmpresaService = inject(EmpresaService);
  private readonly authService: AuthService = inject(AuthService);
  private readonly idiomaService: IdiomaService = inject(IdiomaService);
  private readonly router: Router = inject(Router);
  private readonly destroyRef: DestroyRef = inject(DestroyRef);

  // Los mismos limites que valida la API, para avisar antes de enviar
  readonly registroForm = new FormGroup({
    nombreEmpresa: new FormControl('', [Validators.required, Validators.maxLength(120)]),
    nit: new FormControl('', [Validators.required, Validators.maxLength(20)]),
    correoContacto: new FormControl('', [Validators.required, Validators.email, Validators.maxLength(254)]),
    nombreAdmin: new FormControl('', [Validators.required, Validators.maxLength(120)]),
    emailAdmin: new FormControl('', [Validators.required, Validators.email, Validators.maxLength(254)]),
    passwordAdmin: new FormControl('', [Validators.required, Validators.minLength(15), Validators.maxLength(72)]),
  });

  enviando: boolean = false;
  error: string | null = null;

  registrar(): void {
    this.enviando = true;
    this.error = null;
    const valores = this.registroForm.value;
    const solicitud: RegistroEmpresaRequest = {
      nombreEmpresa: valores.nombreEmpresa ?? '',
      nit: valores.nit ?? '',
      correoContacto: valores.correoContacto ?? '',
      nombreAdmin: valores.nombreAdmin ?? '',
      emailAdmin: valores.emailAdmin ?? '',
      passwordAdmin: valores.passwordAdmin ?? '',
      // F12: la organizacion empieza a trabajar en el idioma de esta web y en la hora de este navegador
      idioma: this.idiomaService.actual,
      zonaHoraria: zonaDelNavegador(),
    };
    // Registrar y entrar con el administrador nuevo, encadenados sin un subscribe dentro de otro
    this.empresaService
      .registrar(solicitud)
      .pipe(
        switchMap(() => this.authService.login({ email: solicitud.emailAdmin, password: solicitud.passwordAdmin })),
        finalize(() => (this.enviando = false)),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe({
        next: () => this.router.navigate(['/cuenta'], { queryParams: { bienvenida: 1 } }),
        error: (error: HttpErrorResponse) => this.mostrarError(error),
      });
  }

  private mostrarError(error: HttpErrorResponse): void {
    if (error.status === 409) {
      this.error = $localize`An organization with that tax ID, or an account with that email, already exists.`;
    } else if (error.status === 400) {
      marcarErroresDelServidor(this.registroForm, error);
      this.error = $localize`Check the highlighted fields.`;
    } else {
      this.error = mensajeDeError(error);
    }
  }
}
