import { HttpErrorResponse } from '@angular/common/http';
import { ChangeDetectionStrategy, Component, DestroyRef, OnInit, ViewChild, inject } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { Observable, forkJoin, map } from 'rxjs';

import { LeyendaComparacionComponent } from '../../bpmn/leyenda-comparacion.component';
import { LienzoBpmnComponent } from '../../bpmn/lienzo-bpmn.component';
import { marcasDeComparacion } from '../../bpmn/marcas';
import { mensajeDeError } from '../../helpers/errores-api';
import { Comparacion } from '../../models/modelo.model';
import { Version } from '../../models/version.model';
import { ModeloService } from '../../service/modelo.service';
import { VersionService } from '../../service/version.service';

const BORRADOR = 'borrador';

/** Dos estados de un proceso: dibuja el de destino, con color y en palabras lo que cambio desde el otro. */
@Component({
  selector: 'app-proceso-comparar',
  imports: [FormsModule, RouterLink, LienzoBpmnComponent, LeyendaComparacionComponent],
  templateUrl: './proceso-comparar.component.html',
  changeDetection: ChangeDetectionStrategy.Eager,
})
export class ProcesoCompararComponent implements OnInit {
  private readonly ruta = inject(ActivatedRoute);
  private readonly modelos = inject(ModeloService);
  private readonly versiones$ = inject(VersionService);
  private readonly destroyRef = inject(DestroyRef);

  @ViewChild(LienzoBpmnComponent) lienzo?: LienzoBpmnComponent;

  readonly borrador = BORRADOR;
  procesoId = 0;
  versiones: Version[] = [];
  de = '';
  a = BORRADOR;
  xml: string | null = null;
  comparacion: Comparacion | null = null;
  marcas: Record<string, string[]> = {};
  error: string | null = null;
  cargado = false;

  ngOnInit(): void {
    this.procesoId = Number(this.ruta.snapshot.paramMap.get('id'));
    this.versiones$
      .listar(this.procesoId)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (versiones) => {
          this.versiones = versiones;
          this.cargado = true;
          if (versiones.length) {
            this.de = String(versiones[0].numero);
            this.comparar();
          }
        },
        error: (error: HttpErrorResponse) => (this.error = mensajeDeError(error)),
      });
  }

  comparar(): void {
    this.error = null;
    const xml$: Observable<string> =
      this.a === BORRADOR
        ? this.modelos.leer(this.procesoId).pipe(map((modelo) => modelo.xml))
        : this.versiones$.modelo(this.procesoId, Number(this.a));
    forkJoin({ comparacion: this.modelos.comparar(this.procesoId, this.de, this.a), xml: xml$ })
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: ({ comparacion, xml }) => {
          this.xml = xml;
          this.comparacion = comparacion;
          this.marcas = marcasDeComparacion(comparacion);
        },
        error: (error: HttpErrorResponse) => (this.error = mensajeDeError(error)),
      });
  }
}
