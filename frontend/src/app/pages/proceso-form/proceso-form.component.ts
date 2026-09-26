import { HttpErrorResponse } from '@angular/common/http';
import { Component, DestroyRef, OnInit, inject } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { Observable, finalize } from 'rxjs';

import { ErrorCampoComponent } from '../../components/error-campo/error-campo.component';
import { esConflictoDeVersion, marcarErroresDelServidor, mensajeDeError } from '../../helpers/errores-api';
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
  conflicto: boolean = false;

  // La version del ultimo GET; se reenvia al guardar para que la API sepa sobre que se edito
  private version: number | null = null;

  ngOnInit(): void {
    const id: string | null = this.route.snapshot.paramMap.get('id');
    if (id === null) {
      return;
    }
    this.isEdit = true;
    this.procesoId = Number(id);
    this.cargar();
  }

  /** Descarta lo escrito y deja el formulario en la version que hay ahora en la API. */
  recargar(): void {
    this.conflicto = false;
    this.error = null;
    this.cargar();
  }

  guardar(): void {
    this.enviando = true;
    this.error = null;
    this.conflicto = false;
    const valores = this.procesoForm.value;
    const solicitud: ProcesoRequest = {
      nombre: valores.nombre ?? '',
      categoria: valores.categoria ?? '',
      descripcion: valores.descripcion ?? '',
    };
    if (this.procesoId !== null && this.version === null) {
      // El proceso no se llego a cargar. Sin version no se puede editar, y crear otro seria peor
      this.enviando = false;
      this.error = 'The process could not be loaded, so the change cannot be saved. Reload the page.';
      return;
    }
    const guardado$: Observable<Proceso> =
      this.procesoId === null
        ? this.procesoService.crear(solicitud)
        : this.procesoService.editar(this.procesoId, { ...solicitud, version: this.version as number });
    guardado$.pipe(finalize(() => (this.enviando = false))).subscribe({
      next: (proceso: Proceso) =>
        this.router.navigate(['/procesos', proceso.id], { queryParams: this.isEdit ? { editado: 1 } : { creado: 1 } }),
      error: (error: HttpErrorResponse) => this.mostrarError(error),
    });
  }

  /** Pide el proceso y deja el formulario con sus valores y su version. */
  private cargar(): void {
    if (this.procesoId === null) {
      return;
    }
    this.cargando = true;
    this.procesoService
      .obtener(this.procesoId)
      .pipe(
        finalize(() => (this.cargando = false)),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe({
        next: (detalle: ProcesoDetalle) => {
          const { nombre, categoria, descripcion, version } = detalle.proceso;
          this.procesoForm.patchValue({ nombre, categoria, descripcion });
          this.version = version;
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

  private mostrarError(error: HttpErrorResponse): void {
    if (error.status === 404) {
      // Otro usuario lo borro mientras se editaba
      this.noEncontrado = true;
    } else if (esConflictoDeVersion(error)) {
      // Alguien guardo entre el GET y el PUT. Lo escrito sigue en pantalla: recargar es decision del usuario
      this.conflicto = true;
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
