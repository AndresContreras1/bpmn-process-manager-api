import { HttpErrorResponse } from '@angular/common/http';
import {
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
  OnChanges,
  OnInit,
  inject,
  input,
  output,
} from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormsModule } from '@angular/forms';

import { esConflictoDeVersion, mensajeDeLaApi } from '../../../../helpers/errores-api';
import { FilaDeCarpeta, aplanarCarpetas } from '../../../../models/carpeta.model';
import {
  CLASIFICACIONES,
  Clasificacion,
  NOMBRE_CLASIFICACION,
  Proceso,
  leerEtiquetas,
} from '../../../../models/proceso.model';
import { Usuario } from '../../../../models/usuario.model';
import { AuthService } from '../../../../service/auth.service';
import { CarpetaService } from '../../../../service/carpeta.service';
import { ProcesoService } from '../../../../service/proceso.service';
import { UsuarioService } from '../../../../service/usuario.service';

/** Donde esta archivado un proceso y quien responde por el. */
@Component({
  selector: 'app-ficha-repositorio',
  imports: [FormsModule],
  templateUrl: './ficha-repositorio.component.html',
  changeDetection: ChangeDetectionStrategy.Eager,
})
export class FichaRepositorioComponent implements OnInit, OnChanges {
  readonly proceso = input.required<Proceso>();
  /** F19: si la ficha se puede guardar, de lo que la API dice que la persona puede hacer con el proceso. */
  readonly puedeArchivar = input<boolean>(false);
  readonly guardado = output<Proceso>();

  private readonly procesos = inject(ProcesoService);
  private readonly carpetas = inject(CarpetaService);
  private readonly usuariosService = inject(UsuarioService);
  private readonly auth = inject(AuthService);
  private readonly destroyRef = inject(DestroyRef);

  readonly clasificaciones = CLASIFICACIONES;
  readonly nombreClasificacion = NOMBRE_CLASIFICACION;
  /** La lista de usuarios es del administrador: quien edita conserva el dueno que hay. */
  readonly eligeDueno = this.auth.esAdministrador();

  filas: FilaDeCarpeta[] = [];
  usuarios: Usuario[] = [];
  etiquetasEnUso: string[] = [];
  carpetaId: number | null = null;
  duenoId: number | null = null;
  proximaRevision = '';
  clasificacion: Clasificacion = 'INTERNA';
  etiquetas = '';
  error: string | null = null;
  aviso: string | null = null;
  enviando = false;

  ngOnInit(): void {
    this.carpetas
      .listar()
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe((carpetas) => (this.filas = aplanarCarpetas(carpetas)));
    this.procesos
      .etiquetas()
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe((etiquetas) => (this.etiquetasEnUso = etiquetas));
    if (this.eligeDueno) {
      this.usuariosService
        .paraElegir()
        .pipe(takeUntilDestroyed(this.destroyRef))
        .subscribe((usuarios) => (this.usuarios = usuarios));
    }
  }

  ngOnChanges(): void {
    const proceso = this.proceso();
    this.carpetaId = proceso.carpetaId ?? null;
    this.duenoId = proceso.duenoId ?? null;
    this.proximaRevision = proceso.proximaRevision ?? '';
    this.clasificacion = proceso.clasificacion ?? 'INTERNA';
    this.etiquetas = (proceso.etiquetas ?? []).join(', ');
  }

  guardar(): void {
    this.error = null;
    this.aviso = null;
    this.enviando = true;
    const proceso = this.proceso();
    this.procesos
      .editarFicha(proceso.id, {
        carpetaId: this.carpetaId,
        duenoId: this.duenoId,
        proximaRevision: this.proximaRevision || null,
        clasificacion: this.clasificacion,
        etiquetas: leerEtiquetas(this.etiquetas),
        version: proceso.version,
      })
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (actualizado) => {
          this.enviando = false;
          this.aviso = $localize`Saved.`;
          this.guardado.emit(actualizado);
        },
        error: (error: HttpErrorResponse) => {
          this.enviando = false;
          this.error = esConflictoDeVersion(error)
            ? $localize`Someone else changed this process: reload the page and try again.`
            : mensajeDeLaApi(error);
        },
      });
  }
}
