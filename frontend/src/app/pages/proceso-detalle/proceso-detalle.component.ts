import { DatePipe } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { Component, DestroyRef, OnInit, inject } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { ActivatedRoute, ParamMap, Router, RouterLink } from '@angular/router';
import { EMPTY, Observable, catchError, finalize, forkJoin, map, of, switchMap, tap } from 'rxjs';

import { ModalConfirmarComponent } from '../../components/modal-confirmar/modal-confirmar.component';
import { esConflictoDeVersion, mensajeDeError } from '../../helpers/errores-api';
import { Diagrama } from '../../models/diagrama.model';
import { EstadoProceso, NOMBRE_ESTADO, Proceso, ProcesoDetalle, cambioEnIngles } from '../../models/proceso.model';
import { AuthService } from '../../service/auth.service';
import { DiagramaService } from '../../service/diagrama.service';
import { ProcesoService } from '../../service/proceso.service';
import { DetalleNodo, detallarNodo } from './components/diagrama-bpmn/detalle-nodo';
import { DiagramaBpmnComponent } from './components/diagrama-bpmn/diagrama-bpmn.component';
import { Lienzo, dibujarDiagrama } from './components/diagrama-bpmn/lienzo';

/** Lo que la pagina pide al abrir un proceso: sus datos con el historial y su diagrama. */
interface Carga {
  detalle: ProcesoDetalle;
  diagrama: Diagrama | null;
}

/** Un proceso con sus datos, su diagrama y su historial de cambios, y las acciones que permite el rol del usuario. */
@Component({
  selector: 'app-proceso-detalle',
  imports: [DatePipe, RouterLink, ModalConfirmarComponent, DiagramaBpmnComponent],
  templateUrl: './proceso-detalle.component.html',
  styleUrl: './proceso-detalle.component.scss',
})
export class ProcesoDetalleComponent implements OnInit {
  private readonly procesoService: ProcesoService = inject(ProcesoService);
  private readonly diagramaService: DiagramaService = inject(DiagramaService);
  private readonly authService: AuthService = inject(AuthService);
  private readonly route: ActivatedRoute = inject(ActivatedRoute);
  private readonly router: Router = inject(Router);
  private readonly destroyRef: DestroyRef = inject(DestroyRef);

  readonly nombreEstado: Record<EstadoProceso, string> = NOMBRE_ESTADO;
  readonly cambioEnIngles: (descripcion: string) => string = cambioEnIngles;
  readonly puedeEditar: boolean = this.authService.puedeEditar();
  readonly esAdministrador: boolean = this.authService.esAdministrador();

  detalle: ProcesoDetalle | null = null;
  diagrama: Diagrama | null = null;
  lienzo: Lienzo | null = null;
  nodoElegido: DetalleNodo | null = null;
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
          this.cargar(id).pipe(
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
      .subscribe((carga: Carga) => {
        this.mostrar(carga);
        this.cargando = false;
      });
  }

  /** Muestra el detalle de la tarea, el gateway o el evento elegido; elegirlo otra vez lo cierra. */
  elegirNodo(id: number): void {
    this.nodoElegido =
      this.diagrama && this.lienzo && this.nodoElegido?.id !== id
        ? detallarNodo(this.diagrama, id, this.lienzo.mensajes)
        : null;
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
      .publicar(proceso.id, proceso.version)
      .pipe(
        switchMap(() => this.procesoService.obtener(proceso.id)),
        finalize(() => (this.enviando = false)),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe({
        next: (detalle: ProcesoDetalle) => {
          this.detalle = detalle;
          this.aviso = 'The process is now published.';
        },
        error: (error: HttpErrorResponse) => this.noSePudoPublicar(proceso.id, error),
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
      .pipe(
        finalize(() => (this.enviando = false)),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe({
        next: () => this.router.navigate(['/procesos'], { queryParams: { eliminado: 1 } }),
        error: (error: HttpErrorResponse) => (this.error = mensajeDeError(error)),
      });
  }

  /**
   * Un conflicto de version dice que alguien guardo entre la carga de la pagina y el boton. Aqui no hay nada
   * escrito que perder, asi que la pagina se recarga sola y el aviso explica por que el estado no cambio.
   */
  private noSePudoPublicar(id: number, error: HttpErrorResponse): void {
    if (!esConflictoDeVersion(error)) {
      this.error = mensajeDeError(error);
      return;
    }
    this.error = 'Someone saved a change before you, so the process was not published. This page now shows the '
      + 'latest version: check it and publish again.';
    this.cargar(id)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe((carga: Carga) => this.mostrar(carga));
  }

  /** Deja en pantalla lo que llego: el proceso, su diagrama ya calculado y ningun nodo abierto. */
  private mostrar(carga: Carga): void {
    this.detalle = carga.detalle;
    this.diagrama = carga.diagrama;
    this.lienzo = carga.diagrama ? dibujarDiagrama(carga.diagrama) : null;
    this.nodoElegido = null;
  }

  /**
   * Pide en paralelo los datos del proceso y su diagrama. Si solo falla el diagrama, la pagina se muestra igual y
   * avisa en su lugar; si falla el proceso, el error sigue hacia ngOnInit.
   */
  private cargar(id: number): Observable<Carga> {
    return forkJoin({
      detalle: this.procesoService.obtener(id),
      diagrama: this.diagramaService.obtener(id).pipe(catchError(() => of(null))),
    });
  }
}
