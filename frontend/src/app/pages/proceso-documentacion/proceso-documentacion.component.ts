import { HttpErrorResponse } from '@angular/common/http';
import { ChangeDetectionStrategy, Component, DestroyRef, OnInit, inject } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { ActivatedRoute, ParamMap, Router, RouterLink } from '@angular/router';
import { EMPTY, catchError, map, switchMap, tap } from 'rxjs';

import { AdjuntosComponent } from '../../components/documentacion/adjuntos.component';
import { EditorDocumentacionComponent } from '../../components/documentacion/editor-documentacion.component';
import { FichaCaracterizacionComponent } from '../../components/documentacion/ficha-caracterizacion.component';
import { ManualProcesoComponent } from '../../components/documentacion/manual-proceso.component';
import { mensajeDeError } from '../../helpers/errores-api';
import { Proceso, ProcesoDetalle } from '../../models/proceso.model';
import { AuthService } from '../../service/auth.service';
import { ProcesoService } from '../../service/proceso.service';

export type Pestana = 'texto' | 'ficha' | 'adjuntos' | 'manual';

export const PESTANAS: readonly Pestana[] = ['texto', 'ficha', 'adjuntos', 'manual'];

/**
 * PR F20: lo que se escribe y se adjunta a un proceso, en cuatro pestanas: su documentacion en Markdown, la ficha de
 * caracterizacion, los documentos y el manual. Las cuatro se cargan al abrir y las ocultas solo se esconden, para que
 * cambiar de pestana no borre lo que se estaba escribiendo. La pestana elegida queda en la direccion (?pestana=).
 *
 * Las pestanas siguen el patron de WAI-ARIA: las flechas, Inicio y Fin se mueven entre ellas, y solo la elegida
 * esta en el orden del tabulador.
 */
@Component({
  selector: 'app-proceso-documentacion',
  imports: [
    RouterLink,
    EditorDocumentacionComponent,
    FichaCaracterizacionComponent,
    AdjuntosComponent,
    ManualProcesoComponent,
  ],
  templateUrl: './proceso-documentacion.component.html',
  changeDetection: ChangeDetectionStrategy.Eager,
})
export class ProcesoDocumentacionComponent implements OnInit {
  private readonly procesos = inject(ProcesoService);
  private readonly ruta = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly destroyRef = inject(DestroyRef);

  /** Administradores y editores escriben y adjuntan; el rol de solo lectura mira y baja. */
  readonly puedeEditar: boolean = inject(AuthService).puedeEditar();
  readonly pestanas: readonly Pestana[] = PESTANAS;
  readonly nombrePestana: Record<Pestana, string> = {
    texto: $localize`Documentation`,
    ficha: $localize`Characterization sheet`,
    adjuntos: $localize`Documents`,
    manual: $localize`Manual`,
  };

  proceso: Proceso | null = null;
  pestana: Pestana = 'texto';
  cargando: boolean = true;
  noEncontrado: boolean = false;
  error: string | null = null;

  ngOnInit(): void {
    const pedida: string | null = this.ruta.snapshot.queryParamMap.get('pestana');
    if (PESTANAS.includes(pedida as Pestana)) {
      this.pestana = pedida as Pestana;
    }
    this.ruta.paramMap
      .pipe(
        map((parametros: ParamMap) => Number(parametros.get('id'))),
        tap(() => {
          this.cargando = true;
          this.noEncontrado = false;
          this.error = null;
        }),
        switchMap((id: number) =>
          this.procesos.obtener(id).pipe(
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
        this.proceso = detalle.proceso;
        this.cargando = false;
      });
  }

  elegir(pestana: Pestana): void {
    this.pestana = pestana;
    void this.router.navigate([], {
      relativeTo: this.ruta,
      queryParams: { pestana },
      queryParamsHandling: 'merge',
      replaceUrl: true,
    });
  }

  /** Las flechas pasan a la pestana de al lado, dando la vuelta; Inicio y Fin van a la primera y a la ultima. */
  alTeclear(evento: KeyboardEvent): void {
    const actual: number = PESTANAS.indexOf(this.pestana);
    const siguiente: Record<string, number> = {
      ArrowRight: (actual + 1) % PESTANAS.length,
      ArrowLeft: (actual - 1 + PESTANAS.length) % PESTANAS.length,
      Home: 0,
      End: PESTANAS.length - 1,
    };
    const destino: number | undefined = siguiente[evento.key];
    if (destino === undefined) {
      return;
    }
    evento.preventDefault();
    this.elegir(PESTANAS[destino]);
    document.getElementById(`pestana-${PESTANAS[destino]}`)?.focus();
  }
}
