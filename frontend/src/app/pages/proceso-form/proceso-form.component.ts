import { HttpErrorResponse } from '@angular/common/http';
import { Component, DestroyRef, OnInit, inject } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { Observable, finalize } from 'rxjs';

import { ErrorCampoComponent } from '../../components/error-campo/error-campo.component';
import { marcarErroresDelServidor, mensajeDeError } from '../../helpers/errores-api';
import { Proceso, ProcesoDetalle, ProcesoRequest } from '../../models/proceso.model';
import { AuthService } from '../../service/auth.service';
import { ProcesoService } from '../../service/proceso.service';

/** Crear y editar un proceso con el mismo formulario: si la ruta trae un id, edita ese proceso. */
@Component({
  selector: 'app-proceso-form',
  imports: [ReactiveFormsModule, RouterLink, ErrorCampoComponent],
  templateUrl: './proceso-form.component.html',
  styleUrl: './proceso-form.component.scss',
})
export class ProcesoFormComponent implements OnInit {
  private readonly procesoService: ProcesoService = inject(ProcesoService);
  private readonly router: Router = inject(Router);
  private readonly route: ActivatedRoute = inject(ActivatedRoute);
  private readonly destroyRef: DestroyRef = inject(DestroyRef);

  // Los mismos limites que valida la API, para avisar antes de enviar
  readonly procesoForm = new FormGroup({
    nombre: new FormControl('', [Validators.required, Validators.maxLength(120)]),
    categoria: new FormControl('', [Validators.required, Validators.maxLength(80)]),
    descripcion: new FormControl('', [Validators.required, Validators.maxLength(4000)]),
  });

  readonly puedeEditar: boolean = inject(AuthService).puedeEditar();
  isEdit: boolean = false;
  procesoId: number | null = null;
  cargando: boolean = false;
  enviando: boolean = false;
  noEncontrado: boolean = false;
  error: string | null = null;

  ngOnInit(): void {
    const id: string | null = this.route.snapshot.paramMap.get('id');
    if (id === null) {
      return;
    }
    this.isEdit = true;
    this.procesoId = Number(id);
    this.cargando = true;
    this.procesoService
      .obtener(this.procesoId)
      .pipe(
        finalize(() => (this.cargando = false)),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe({
        next: (detalle: ProcesoDetalle) => {
          const { nombre, categoria, descripcion } = detalle.proceso;
          this.procesoForm.patchValue({ nombre, categoria, descripcion });
        },
        error: (error: HttpErrorResponse) => {
          // 404: el proceso es de otra tienda o ya se borro. 400: el id de la ruta no es un numero
          if (error.status === 404 || error.status === 400) {
            this.noEncontrado = true;
          } else {
            this.error = mensajeDeError(error);
          }
        },
      });
  }

  guardar(): void {
    this.enviando = true;
    this.error = null;
    const valores = this.procesoForm.value;
    const solicitud: ProcesoRequest = {
      nombre: valores.nombre ?? '',
      categoria: valores.categoria ?? '',
      descripcion: valores.descripcion ?? '',
    };
    const guardado$: Observable<Proceso> =
      this.procesoId === null
        ? this.procesoService.crear(solicitud)
        : this.procesoService.editar(this.procesoId, solicitud);
    guardado$.pipe(finalize(() => (this.enviando = false))).subscribe({
      next: (proceso: Proceso) =>
        this.router.navigate(['/procesos', proceso.id], { queryParams: this.isEdit ? { editado: 1 } : { creado: 1 } }),
      error: (error: HttpErrorResponse) => this.mostrarError(error),
    });
  }

  private mostrarError(error: HttpErrorResponse): void {
    if (error.status === 404) {
      // Otro usuario lo borro mientras se editaba
      this.noEncontrado = true;
    } else if (error.status === 409) {
      // El nombre es unico entre los procesos activos de la tienda
      this.procesoForm.get('nombre')?.setErrors({ servidor: 'Another active process of your store already uses this name.' });
      this.error = 'Check the highlighted fields.';
    } else if (error.status === 400) {
      marcarErroresDelServidor(this.procesoForm, error);
      this.error = 'Check the highlighted fields.';
    } else {
      this.error = mensajeDeError(error);
    }
  }
}
