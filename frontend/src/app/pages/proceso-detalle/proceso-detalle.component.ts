import { DatePipe } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { Component, DestroyRef, OnInit, inject } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { ActivatedRoute, ParamMap, Router, RouterLink } from '@angular/router';
import { EMPTY, catchError, finalize, map, switchMap, tap } from 'rxjs';

import { ModalConfirmarComponent } from '../../components/modal-confirmar/modal-confirmar.component';
import { mensajeDeError } from '../../helpers/errores-api';
import { EstadoProceso, NOMBRE_ESTADO, Proceso, ProcesoDetalle, cambioEnIngles } from '../../models/proceso.model';
import { AuthService } from '../../service/auth.service';
import { ProcesoService } from '../../service/proceso.service';

/** Un proceso con sus datos y su historial de cambios, y las acciones que permite el rol del usuario. */
@Component({
  selector: 'app-proceso-detalle',
  imports: [DatePipe, RouterLink, ModalConfirmarComponent],
  templateUrl: './proceso-detalle.component.html',
  styleUrl: './proceso-detalle.component.scss',
})
export class ProcesoDetalleComponent implements OnInit {
  private readonly procesoService: ProcesoService = inject(ProcesoService);
  private readonly authService: AuthService = inject(AuthService);
  private readonly route: ActivatedRoute = inject(ActivatedRoute);
  private readonly router: Router = inject(Router);
  private readonly destroyRef: DestroyRef = inject(DestroyRef);

  readonly nombreEstado: Record<EstadoProceso, string> = NOMBRE_ESTADO;
  readonly cambioEnIngles: (descripcion: string) => string = cambioEnIngles;
  readonly puedeEditar: boolean = this.authService.puedeEditar();
  readonly esAdministrador: boolean = this.authService.esAdministrador();

  detalle: ProcesoDetalle | null = null;
  cargando: boolean = true;
  noEncontrado: boolean = false;
  enviando: boolean = false;
  error: string | null = null;
  aviso: string | null = null;

  ngOnInit(): void {
    const consulta: ParamMap = this.route.snapshot.queryParamMap;
    if (consulta.has('creado')) {
      this.aviso = 'Process created as a draft.';
    } else if (consulta.has('editado')) {
      this.aviso = 'Changes saved.';
    }
    // Si el id de la ruta cambia, switchMap cancela la peticion anterior y carga el proceso nuevo
    this.route.paramMap
      .pipe(
        map((parametros: ParamMap) => Number(parametros.get('id'))),
        tap(() => {
          this.cargando = true;
          this.noEncontrado = false;
          this.error = null;
        }),
        switchMap((id: number) =>
          this.procesoService.obtener(id).pipe(
            catchError((error: HttpErrorResponse) => {
              this.cargando = false;
              // 404: el proceso es de otra tienda o ya se borro. 400: el id de la ruta no es un numero
              if (error.status === 404 || error.status === 400) {
                this.noEncontrado = true;
              } else {
                this.error = mensajeDeError(error);
              }
              return EMPTY;
            }),
          ),
        ),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe((detalle: ProcesoDetalle) => {
        this.detalle = detalle;
        this.cargando = false;
      });
  }

  publicar(): void {
    const proceso: Proceso | undefined = this.detalle?.proceso;
    if (!proceso) {
      return;
    }
    this.enviando = true;
    this.aviso = null;
    this.error = null;
    // Publicar y volver a pedir el detalle, encadenados: el historial suma el cambio
    this.procesoService
      .publicar(proceso.id)
      .pipe(
        switchMap(() => this.procesoService.obtener(proceso.id)),
        finalize(() => (this.enviando = false)),
      )
      .subscribe({
        next: (detalle: ProcesoDetalle) => {
          this.detalle = detalle;
          this.aviso = 'The process is now published.';
        },
        error: (error: HttpErrorResponse) => (this.error = mensajeDeError(error)),
      });
  }

  eliminar(): void {
    const proceso: Proceso | undefined = this.detalle?.proceso;
    if (!proceso) {
      return;
    }
    this.enviando = true;
    this.error = null;
    this.procesoService
      .eliminar(proceso.id)
      .pipe(finalize(() => (this.enviando = false)))
      .subscribe({
        next: () => this.router.navigate(['/procesos'], { queryParams: { eliminado: 1 } }),
        error: (error: HttpErrorResponse) => (this.error = mensajeDeError(error)),
      });
  }
}
