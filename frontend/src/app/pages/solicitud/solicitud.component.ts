import { HttpErrorResponse } from '@angular/common/http';
import { ChangeDetectionStrategy, Component, DestroyRef, OnInit, ViewChild, inject } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { Observable, catchError, finalize, forkJoin, map, of, switchMap } from 'rxjs';

import { FilaDeDiferencia, ResumenDeDiferencia, filasDeDiferencia, resumenDeDiferencia } from '../../bpmn/diferencia';
import { LienzoBpmnComponent } from '../../bpmn/lienzo-bpmn.component';
import { marcasDeComparacion } from '../../bpmn/marcas';
import { InsigniaComponent } from '../../components/ui/insignia.component';
import { mensajeDeError, mensajeDeLaApi } from '../../helpers/errores-api';
import { LARGO_MAXIMO_COMENTARIO } from '../../models/comentario.model';
import { Accion } from '../../models/concesion.model';
import {
  NOMBRE_DECISION,
  NOMBRE_ESTADO_SOLICITUD,
  Solicitud,
  TONO_ESTADO_SOLICITUD,
  TipoDeDecision,
  decisionPideTexto,
  puedeDecidir,
  puedeRetirar,
} from '../../models/solicitud.model';
import { FechaPipe } from '../../pipes/fecha.pipe';
import { AuthService } from '../../service/auth.service';
import { ConcesionService } from '../../service/concesion.service';
import { ModeloService } from '../../service/modelo.service';
import { SolicitudService } from '../../service/solicitud.service';
import { VersionService } from '../../service/version.service';

/** Que dibujo se muestra: lo congelado al pedirla, lo que publico, o el borrador de hoy si ya no queda lo congelado. */
type Dibujo = 'congelado' | 'publicado' | 'borrador';

/** El dibujo que llego, o null si no llego, y cual era. */
interface Dibujado {
  xml: string | null;
  cual: Dibujo;
}

/**
 * PR F21: una solicitud de revision. Arriba lo que pide y quien decide; en el medio, la diferencia contra la version
 * vigente de cuando se pidio, pintada en el diagrama con las marcas de la comparacion y dicha en una lista (el color
 * nunca va solo); abajo, lo que decidio cada aprobador y los botones de quien puede decidir o retirarla.
 */
@Component({
  selector: 'app-solicitud',
  imports: [FormsModule, RouterLink, FechaPipe, InsigniaComponent, LienzoBpmnComponent],
  templateUrl: './solicitud.component.html',
  changeDetection: ChangeDetectionStrategy.Eager,
})
export class SolicitudComponent implements OnInit {
  private readonly ruta = inject(ActivatedRoute);
  private readonly solicitudes = inject(SolicitudService);
  private readonly modelos = inject(ModeloService);
  private readonly versiones = inject(VersionService);
  private readonly concesiones = inject(ConcesionService);
  private readonly auth = inject(AuthService);
  private readonly destroyRef = inject(DestroyRef);

  @ViewChild(LienzoBpmnComponent) lienzo?: LienzoBpmnComponent;

  readonly nombreEstado = NOMBRE_ESTADO_SOLICITUD;
  readonly tono = TONO_ESTADO_SOLICITUD;
  readonly nombreDecision = NOMBRE_DECISION;
  readonly largoMaximo: number = LARGO_MAXIMO_COMENTARIO;
  private readonly usuarioId: number | null = this.auth.usuarioActual()?.id ?? null;

  solicitud: Solicitud | null = null;
  xml: string | null = null;
  dibujo: Dibujo = 'congelado';
  marcas: Record<string, string[]> = {};
  filas: FilaDeDiferencia[] = [];
  resumen: ResumenDeDiferencia = { agregado: 0, cambiado: 0, movido: 0, quitado: 0 };
  decide: boolean = false;
  retira: boolean = false;
  /** La decision que se esta escribiendo; null mientras no se eligio ninguna. */
  decision: TipoDeDecision | null = null;
  texto: string = '';
  cargando: boolean = true;
  noEncontrada: boolean = false;
  enviando: boolean = false;
  error: string | null = null;
  aviso: string | null = null;

  get textoObligatorio(): boolean {
    return this.decision !== null && decisionPideTexto(this.decision);
  }

  get sinCambios(): boolean {
    return this.filas.length === 0;
  }

  ngOnInit(): void {
    this.ruta.paramMap
      .pipe(
        map((parametros) => Number(parametros.get('id'))),
        switchMap((id) => {
          this.cargando = true;
          this.noEncontrada = false;
          return this.solicitudes.obtener(id).pipe(
            catchError((error: HttpErrorResponse) => {
              this.cargando = false;
              if (error.status === 404 || error.status === 400) {
                this.noEncontrada = true;
              } else {
                this.error = mensajeDeError(error);
              }
              return of(null);
            }),
          );
        }),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe((solicitud) => {
        if (solicitud) {
          this.mostrar(solicitud, true);
        }
      });
  }

  elegirDecision(decision: TipoDeDecision): void {
    this.decision = decision;
    this.error = null;
    setTimeout(() => document.getElementById('solicitud-texto')?.focus());
  }

  cancelarDecision(): void {
    this.decision = null;
    this.texto = '';
  }

  decidir(): void {
    const decision = this.decision;
    const solicitud = this.solicitud;
    if (!decision || !solicitud || this.enviando || (this.textoObligatorio && !this.texto.trim())) {
      return;
    }
    this.enviar(this.solicitudes.decidir(solicitud.id, decision, this.texto), (resultado) =>
      resultado.estado === 'PUBLICADA'
        ? $localize`Approved and published as version ${resultado.versionPublicada}:version:.`
        : $localize`Your decision was saved.`,
    );
  }

  retirar(): void {
    const solicitud = this.solicitud;
    if (solicitud && !this.enviando) {
      this.enviar(this.solicitudes.retirar(solicitud.id), () => $localize`The request was withdrawn.`);
    }
  }

  mostrarElemento(fila: FilaDeDiferencia): void {
    if (fila.enElDibujo) {
      this.lienzo?.mostrar(fila.bpmnId);
    }
  }

  private enviar(peticion: Observable<Solicitud>, hecho: (resultado: Solicitud) => string): void {
    this.enviando = true;
    this.error = null;
    this.aviso = null;
    peticion
      .pipe(
        finalize(() => (this.enviando = false)),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe({
        next: (resultado) => {
          this.decision = null;
          this.texto = '';
          this.aviso = hecho(resultado);
          this.mostrar(resultado, false);
        },
        error: (error: HttpErrorResponse) => (this.error = mensajeDeLaApi(error)),
      });
  }

  /**
   * Deja la solicitud en pantalla. La respuesta de decidir trae la diferencia igual que la de leerla; el dibujo y lo
   * que puede hacer quien mira solo se piden al abrir la pagina, o al cambiar lo que se dibuja.
   */
  private mostrar(solicitud: Solicitud, conDibujo: boolean): void {
    this.solicitud = solicitud;
    this.filas = filasDeDiferencia(solicitud.diferencia);
    this.resumen = resumenDeDiferencia(this.filas);
    this.marcas = solicitud.diferencia ? marcasDeComparacion(solicitud.diferencia) : {};
    this.decide = puedeDecidir(solicitud, this.usuarioId);
    if (!conDibujo) {
      this.retira = puedeRetirar(solicitud, this.usuarioId, false) && this.retira;
      return;
    }
    forkJoin({
      dibujo: this.dibujoDe(solicitud),
      // Sin la respuesta, solo quien la pidio ve el boton de retirar; la API sigue mandando
      acciones: this.concesiones.permisos(solicitud.procesoId).pipe(catchError(() => of([] as Accion[]))),
    })
      .pipe(
        finalize(() => (this.cargando = false)),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe(({ dibujo, acciones }) => {
        this.xml = dibujo.xml;
        this.dibujo = dibujo.cual;
        this.retira = puedeRetirar(solicitud, this.usuarioId, acciones.includes('ADMINISTRAR'));
      });
  }

  /**
   * Lo que se revisa es la revision congelada al pedirla. Si ya se limpio, una solicitud publicada se ve en su version,
   * y una que no, en el borrador de hoy, avisando que puede haber cambiado desde entonces.
   */
  private dibujoDe(solicitud: Solicitud): Observable<Dibujado> {
    let xml$: Observable<string>;
    let cual: Dibujo;
    if (solicitud.revisionId !== null) {
      xml$ = this.modelos.modeloDeRevision(solicitud.procesoId, solicitud.revisionId);
      cual = 'congelado';
    } else if (solicitud.versionPublicada !== null) {
      xml$ = this.versiones.modelo(solicitud.procesoId, solicitud.versionPublicada);
      cual = 'publicado';
    } else {
      xml$ = this.modelos.leer(solicitud.procesoId).pipe(map((modelo) => modelo.xml));
      cual = 'borrador';
    }
    return xml$.pipe(
      map((xml): Dibujado => ({ xml, cual })),
      catchError(() => of<Dibujado>({ xml: null, cual })),
    );
  }
}
