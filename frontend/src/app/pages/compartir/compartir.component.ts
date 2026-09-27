import { HttpErrorResponse } from '@angular/common/http';
import { DatePipe } from '@angular/common';
import { Component, DestroyRef, OnInit, inject } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { finalize } from 'rxjs';

import { ErrorCampoComponent } from '../../components/error-campo/error-campo.component';
import { ModalConfirmarComponent } from '../../components/modal-confirmar/modal-confirmar.component';
import { mensajeDeError } from '../../helpers/errores-api';
import { EmpresaInvitada } from '../../models/compartir.model';
import { ProcesoDetalle } from '../../models/proceso.model';
import { AuthService } from '../../service/auth.service';
import { CompartirService } from '../../service/compartir.service';
import { ProcesoService } from '../../service/proceso.service';

/**
 * Con que tiendas se comparte un proceso. Es una puerta de solo lectura: la invitada ve la version vigente del
 * diagrama y nada mas, asi que compartir no da acceso a la tienda, solo a ese proceso publicado.
 */
@Component({
  selector: 'app-compartir',
  imports: [DatePipe, ReactiveFormsModule, RouterLink, ErrorCampoComponent, ModalConfirmarComponent],
  templateUrl: './compartir.component.html',
})
export class CompartirComponent implements OnInit {
  private readonly compartirService: CompartirService = inject(CompartirService);
  private readonly procesoService: ProcesoService = inject(ProcesoService);
  private readonly route: ActivatedRoute = inject(ActivatedRoute);
  private readonly destroyRef: DestroyRef = inject(DestroyRef);

  readonly esAdministrador: boolean = inject(AuthService).esAdministrador();

  readonly nitForm = new FormGroup({
    nit: new FormControl('', [Validators.required, Validators.maxLength(20)]),
  });

  procesoId: number = 0;
  nombreProceso: string = '';
  invitadas: EmpresaInvitada[] = [];
  seleccionada: EmpresaInvitada | null = null;
  cargando: boolean = true;
  enviando: boolean = false;
  error: string | null = null;
  aviso: string | null = null;

  ngOnInit(): void {
    this.procesoId = Number(this.route.snapshot.paramMap.get('id'));
    this.procesoService
      .obtener(this.procesoId)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (detalle: ProcesoDetalle) => (this.nombreProceso = detalle.proceso.nombre),
        error: () => (this.nombreProceso = ''),
      });
    this.cargar();
  }

  compartir(): void {
    if (this.nitForm.invalid) {
      this.nitForm.markAllAsTouched();
      return;
    }
    this.enviando = true;
    this.limpiarMensajes();
    this.compartirService
      .compartir(this.procesoId, this.nitForm.getRawValue().nit ?? '')
      .pipe(
        finalize(() => (this.enviando = false)),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe({
        next: (invitada: EmpresaInvitada) => {
          this.aviso = `${invitada.nombre} can now read this process.`;
          this.nitForm.reset({ nit: '' });
          this.cargar();
        },
        error: (error: HttpErrorResponse) => (this.error = mensajeDeError(error)),
      });
  }

  confirmarRetirada(): void {
    const invitada: EmpresaInvitada | null = this.seleccionada;
    if (!invitada) {
      return;
    }
    this.limpiarMensajes();
    this.compartirService
      .dejarDeCompartir(this.procesoId, invitada.empresaId)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: () => {
          this.aviso = `${invitada.nombre} can no longer read it.`;
          this.cargar();
        },
        error: (error: HttpErrorResponse) => (this.error = mensajeDeError(error)),
      });
  }

  private cargar(): void {
    this.cargando = true;
    this.compartirService
      .invitadas(this.procesoId)
      .pipe(
        finalize(() => (this.cargando = false)),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe({
        next: (invitadas: EmpresaInvitada[]) => (this.invitadas = invitadas),
        error: (error: HttpErrorResponse) => (this.error = mensajeDeError(error)),
      });
  }

  private limpiarMensajes(): void {
    this.error = null;
    this.aviso = null;
  }
}
