import { HttpErrorResponse } from '@angular/common/http';
import { ChangeDetectionStrategy, Component, DestroyRef, OnInit, inject } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormsModule } from '@angular/forms';
import { catchError, finalize, forkJoin, of } from 'rxjs';

import { esConflictoDeVersion, mensajeDeLaApi } from '../../helpers/errores-api';
import { ProblemDetail } from '../../models/problem-detail.model';
import { RolProceso } from '../../models/rol-proceso.model';
import {
  APROBACIONES_MAXIMAS,
  APROBACIONES_MINIMAS,
  APROBADORES_MAXIMOS,
  FaltaEnPolitica,
  PoliticaDePublicacion,
  ValoresDePolitica,
  faltaEnPolitica,
  solicitudDePolitica,
  valoresDePolitica,
} from '../../models/solicitud.model';
import { NOMBRE_ROL, RolAcceso, Usuario } from '../../models/usuario.model';
import { FechaPipe } from '../../pipes/fecha.pipe';
import { AuthService } from '../../service/auth.service';
import { RolProcesoService } from '../../service/rol-proceso.service';
import { SolicitudService } from '../../service/solicitud.service';
import { UsuarioService } from '../../service/usuario.service';

/**
 * PR F21: como publica la organizacion. Directo, como siempre, o con aprobacion: cuantas aprobaciones pide y quien
 * aprueba, una lista de personas o la gente de un rol de proceso. Quien solo lee no aprueba, asi que no se ofrece.
 * La API lee la politica a cualquiera, pero solo el administrador la cambia; los demas ven lo que hay.
 */
@Component({
  selector: 'app-politica-publicacion',
  imports: [FormsModule, FechaPipe],
  templateUrl: './politica-publicacion.component.html',
  changeDetection: ChangeDetectionStrategy.Eager,
})
export class PoliticaPublicacionComponent implements OnInit {
  private readonly solicitudes = inject(SolicitudService);
  private readonly usuarios = inject(UsuarioService);
  private readonly rolesDeProceso = inject(RolProcesoService);
  private readonly destroyRef = inject(DestroyRef);

  readonly esAdministrador: boolean = inject(AuthService).esAdministrador();
  readonly minimo: number = APROBACIONES_MINIMAS;
  readonly maximo: number = APROBACIONES_MAXIMAS;
  readonly tope: number = APROBADORES_MAXIMOS;
  readonly nombreRol: Record<RolAcceso, string> = NOMBRE_ROL;
  readonly mensajeDeFalta: Record<FaltaEnPolitica, string> = {
    aprobaciones: $localize`A request needs from ${APROBACIONES_MINIMAS}:minimo: to ${APROBACIONES_MAXIMAS}:maximo: approvals.`,
    rol: $localize`Choose the process role whose people approve.`,
    aprobadores: $localize`Choose who approves.`,
    demasiados: $localize`The list can have up to ${APROBADORES_MAXIMOS}:tope: people.`,
    pocos: $localize`Choose at least as many people as approvals a request needs.`,
  };

  politica: PoliticaDePublicacion | null = null;
  valores: ValoresDePolitica | null = null;
  personas: Usuario[] = [];
  roles: RolProceso[] = [];
  cargando: boolean = true;
  enviando: boolean = false;
  intentado: boolean = false;
  error: string | null = null;
  /** Los errores de la API por campo, como "aprobadores[1]". */
  erroresDelServidor: string[] = [];
  aviso: string | null = null;

  get falta(): FaltaEnPolitica | null {
    return this.valores ? faltaEnPolitica(this.valores) : null;
  }

  ngOnInit(): void {
    this.cargar();
  }

  cargar(): void {
    this.cargando = true;
    this.error = null;
    forkJoin({
      politica: this.solicitudes.politica(),
      // Elegir personas y roles es del administrador: a los demas la API no les lista la gente
      personas: this.esAdministrador ? this.usuarios.paraElegir() : of([] as Usuario[]),
      roles: this.esAdministrador
        ? this.rolesDeProceso.listar().pipe(catchError(() => of([] as RolProceso[])))
        : of([] as RolProceso[]),
    })
      .pipe(
        finalize(() => (this.cargando = false)),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe({
        next: ({ politica, personas, roles }) => {
          this.mostrar(politica);
          this.personas = personas;
          this.roles = roles;
        },
        error: (error: HttpErrorResponse) => (this.error = mensajeDeLaApi(error)),
      });
  }

  /** Quien solo lee no aprueba: la API responderia 400 nombrandolo. */
  puedeAprobar(persona: Usuario): boolean {
    return persona.activo && persona.rolAcceso !== 'SOLO_LECTURA';
  }

  elegido(persona: Usuario): boolean {
    return this.valores?.aprobadores.includes(persona.id) ?? false;
  }

  alternarPersona(persona: Usuario, marcada: boolean): void {
    if (!this.valores) {
      return;
    }
    const sin = this.valores.aprobadores.filter((id) => id !== persona.id);
    this.valores = { ...this.valores, aprobadores: marcada ? [...sin, persona.id] : sin };
  }

  guardar(): void {
    this.intentado = true;
    this.aviso = null;
    const valores = this.valores;
    const cuerpo = valores ? solicitudDePolitica(valores) : null;
    if (!cuerpo || this.enviando) {
      return;
    }
    this.enviando = true;
    this.error = null;
    this.erroresDelServidor = [];
    this.solicitudes
      .guardarPolitica(cuerpo)
      .pipe(
        finalize(() => (this.enviando = false)),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe({
        next: (guardada) => {
          this.mostrar(guardada);
          this.aviso =
            guardada.modo === 'DIRECTA'
              ? $localize`Saved: processes are published directly.`
              : $localize`Saved: a change is published once it gets its approvals.`;
        },
        error: (error: HttpErrorResponse) => {
          this.error = esConflictoDeVersion(error)
            ? $localize`Someone else changed the policy. Reload the page to see it, and try again.`
            : mensajeDeLaApi(error);
          this.erroresDelServidor = Object.values((error.error as ProblemDetail | null)?.errors ?? {});
        },
      });
  }

  private mostrar(politica: PoliticaDePublicacion): void {
    this.politica = politica;
    this.valores = valoresDePolitica(politica);
    this.intentado = false;
  }
}
