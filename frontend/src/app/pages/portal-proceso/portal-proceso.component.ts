import { HttpErrorResponse } from '@angular/common/http';
import {
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
  ElementRef,
  Injector,
  OnInit,
  afterNextRender,
  computed,
  inject,
  signal,
  viewChild,
} from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { ActivatedRoute, ParamMap, RouterLink } from '@angular/router';
import { EMPTY, catchError, map, switchMap, tap } from 'rxjs';

import { InsigniaComponent } from '../../components/ui/insignia.component';
import { VacioComponent } from '../../components/ui/vacio.component';
import { ElementoElegido, LienzoBpmnComponent } from '../../bpmn/lienzo-bpmn.component';
import { VistaEstructuraComponent } from '../../bpmn/vista-estructura.component';
import { mensajeDeError } from '../../helpers/errores-api';
import { NOMBRE_CAMPO_CARACTERIZACION, CampoCaracterizacion } from '../../models/documentacion.model';
import {
  CampoLeido,
  ControlesDelPortal,
  ProcesoDelPortal,
  camposLlenos,
  controlesDelPortal,
  marcasDeDocumentados,
  revisionVencida,
} from '../../models/portal.model';
import { Clasificacion, NOMBRE_CLASIFICACION } from '../../models/proceso.model';
import { FechaPipe } from '../../pipes/fecha.pipe';
import { AuthService } from '../../service/auth.service';
import { PortalService } from '../../service/portal.service';
import { ElementoLeido, PanelElementoComponent } from './components/panel-elemento.component';
import { ManualDelPortalComponent } from './components/manual-del-portal.component';

/**
 * F22: un proceso publicado, para leerlo. La ficha (dueno, clasificacion, proxima revision y caracterizacion), el
 * diagrama de la version vigente con sus elementos documentados marcados, y al lado lo que se lee del elemento
 * elegido: un clic en el lienzo, o Enter en el arbol de la estructura, que lo recorre con el teclado. Ningun control
 * cambia nada: lo unico que se puede pedir es el manual, y solo si la API dice que se puede exportar.
 */
@Component({
  selector: 'app-portal-proceso',
  imports: [
    RouterLink,
    FechaPipe,
    InsigniaComponent,
    VacioComponent,
    LienzoBpmnComponent,
    VistaEstructuraComponent,
    PanelElementoComponent,
    ManualDelPortalComponent,
  ],
  templateUrl: './portal-proceso.component.html',
  styleUrl: './portal-proceso.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class PortalProcesoComponent implements OnInit {
  private readonly portal = inject(PortalService);
  private readonly auth = inject(AuthService);
  private readonly ruta = inject(ActivatedRoute);
  private readonly destroyRef = inject(DestroyRef);
  private readonly anfitrion = inject<ElementRef<HTMLElement>>(ElementRef);
  private readonly inyector = inject(Injector);

  readonly nombreClasificacion: Record<Clasificacion, string> = NOMBRE_CLASIFICACION;
  readonly nombreCampo: Record<CampoCaracterizacion, string> = NOMBRE_CAMPO_CARACTERIZACION;
  readonly lienzo = viewChild(LienzoBpmnComponent);

  readonly leido = signal<ProcesoDelPortal | null>(null);
  readonly cargando = signal<boolean>(true);
  readonly noEncontrado = signal<boolean>(false);
  readonly error = signal<string | null>(null);
  readonly elemento = signal<ElementoLeido | null>(null);
  /** Sube cada vez que el lienzo termina de dibujar: la estructura vuelve a leer el arbol. */
  readonly revision = signal<number>(0);
  readonly anuncio = signal<string>('');

  readonly proceso = computed(() => this.leido()?.detalle.proceso ?? null);
  readonly vencida = computed((): boolean => {
    const proceso = this.proceso();
    return proceso !== null && revisionVencida(proceso);
  });
  readonly controles = computed((): ControlesDelPortal =>
    controlesDelPortal(this.leido()?.acciones ?? null, this.auth.usuarioActual()?.rolAcceso ?? null),
  );
  readonly ficha = computed((): CampoLeido[] => camposLlenos(this.leido()?.caracterizacion ?? null));
  readonly marcas = computed((): Record<string, string[]> => {
    const leido = this.leido();
    return leido ? marcasDeDocumentados(leido.textos, leido.documentos) : {};
  });
  readonly elegidoEnElLienzo = computed((): ElementoElegido | null => {
    const elemento = this.elemento();
    return elemento ? { id: elemento.id, tipo: '', nombre: elemento.nombre } : null;
  });

  /** El bpmnId que trae la direccion (?elemento=), hasta que el diagrama llega y se elige. */
  private elementoPedido: string | null = null;
  private dibujado: boolean = false;
  private id: number = 0;

  ngOnInit(): void {
    this.ruta.queryParamMap.pipe(takeUntilDestroyed(this.destroyRef)).subscribe((parametros: ParamMap) => {
      this.elementoPedido = parametros.get('elemento');
      this.elegirElPedido();
    });
    this.ruta.paramMap
      .pipe(
        map((parametros: ParamMap) => Number(parametros.get('id'))),
        tap((id: number) => {
          this.id = id;
          this.cargando.set(true);
          this.noEncontrado.set(false);
          this.error.set(null);
          this.elemento.set(null);
          this.dibujado = false;
        }),
        switchMap((id: number) =>
          this.portal.abrir(id).pipe(
            catchError((error: HttpErrorResponse) => {
              this.cargando.set(false);
              // 404: de otra tienda, oculto por una concesion o borrado. 400: el id de la ruta no es un numero
              if (error.status === 404 || error.status === 400) {
                this.noEncontrado.set(true);
              } else {
                this.error.set(mensajeDeError(error));
              }
              return EMPTY;
            }),
          ),
        ),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe((leido: ProcesoDelPortal) => {
        this.leido.set(leido);
        this.cargando.set(false);
      });
  }

  reintentar(): void {
    this.error.set(null);
    this.cargando.set(true);
    this.portal
      .abrir(this.id)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (leido: ProcesoDelPortal) => {
          this.leido.set(leido);
          this.cargando.set(false);
        },
        error: (error: HttpErrorResponse) => {
          this.cargando.set(false);
          this.error.set(mensajeDeError(error));
        },
      });
  }

  /** El lienzo termino de dibujar: la estructura lee el arbol, y si un enlace pedia un elemento, se elige ahora. */
  alDibujar(): void {
    this.dibujado = true;
    this.revision.update((valor) => valor + 1);
    this.elegirElPedido();
  }

  /**
   * Un clic en el lienzo, o Enter en la estructura, que lo lleva al lienzo: el panel pasa a hablar de ese elemento, y
   * se anuncia. Soltarlo vuelve al proceso entero.
   */
  elegir(elegido: ElementoElegido | null): void {
    const elemento = elegido ? { id: elegido.id, nombre: elegido.nombre } : null;
    this.elemento.set(elemento);
    this.anuncio.set(
      elemento
        ? $localize`Showing what is written about ${elemento.nombre || elemento.id}.`
        : $localize`Showing what is written about the whole process.`,
    );
  }

  /** Lleva el foco al panel, para leer lo que se eligio sin buscarlo. */
  leerPanel(): void {
    afterNextRender(() => this.anfitrion.nativeElement.querySelector<HTMLElement>('#portal-panel-titulo')?.focus(), {
      injector: this.inyector,
    });
  }

  private elegirElPedido(): void {
    const pedido = this.elementoPedido;
    const lienzo = this.lienzo();
    if (pedido && this.dibujado && lienzo) {
      this.elementoPedido = null;
      lienzo.mostrar(pedido);
    }
  }
}
