import { HttpErrorResponse } from '@angular/common/http';
import {
  ChangeDetectionStrategy,
  ChangeDetectorRef,
  Component,
  DestroyRef,
  OnDestroy,
  OnInit,
  ViewChild,
  inject,
} from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { finalize, forkJoin, switchMap } from 'rxjs';

import { Autoguardado, EstadoGuardado } from '../../bpmn/autoguardado';
import { CopiaLocal, CopiaLocalService } from '../../bpmn/copia-local.service';
import { ElementoElegido, LienzoBpmnComponent } from '../../bpmn/lienzo-bpmn.component';
import { elementoDe, marcasDe } from '../../bpmn/marcas';
import { ModoProceso } from '../../bpmn/modulos/reglas';
import { PanelPropiedadesComponent } from '../../bpmn/panel-propiedades.component';
import { mensajeDeError } from '../../helpers/errores-api';
import { Diagnostico, Hallazgo } from '../../models/diagnostico.model';
import { Diagrama } from '../../models/diagrama.model';
import { DiagnosticoService } from '../../service/diagnostico.service';
import { DiagramaService } from '../../service/diagrama.service';
import { ProcesoService } from '../../service/proceso.service';
import { ModeloService } from '../../service/modelo.service';

/** El modelador: dibuja con bpmn-js, guarda solo y dice en vivo lo que el diagnostico encuentra. */
@Component({
  selector: 'app-proceso-modelador',
  imports: [RouterLink, LienzoBpmnComponent, PanelPropiedadesComponent],
  templateUrl: './proceso-modelador.component.html',
  styleUrl: './proceso-modelador.component.scss',
  changeDetection: ChangeDetectionStrategy.Eager,
})
export class ProcesoModeladorComponent implements OnInit, OnDestroy {
  private readonly ruta = inject(ActivatedRoute);
  private readonly modelos = inject(ModeloService);
  private readonly diagramas = inject(DiagramaService);
  private readonly diagnosticos = inject(DiagnosticoService);
  private readonly procesos = inject(ProcesoService);
  private readonly copias = inject(CopiaLocalService);
  private readonly destroyRef = inject(DestroyRef);
  private readonly cambios = inject(ChangeDetectorRef);

  @ViewChild(LienzoBpmnComponent) lienzo?: LienzoBpmnComponent;

  procesoId = 0;
  xml: string | null = null;
  nombre = '';
  modoProceso: ModoProceso = 'EJECUTABLE';
  versionDelProceso = 0;
  error: string | null = null;
  publicado: string | null = null;
  publicando = false;
  elegido: ElementoElegido | null = null;
  diagnostico: Diagnostico | null = null;
  marcas: Record<string, string[]> = {};
  estado: EstadoGuardado = 'guardado';
  copia: CopiaLocal | null = null;
  autoguardado: Autoguardado | null = null;
  private diagrama: Diagrama | null = null;
  private cargando = true;

  readonly textoDeEstado: Record<EstadoGuardado, string> = {
    guardado: $localize`Saved`,
    pendiente: $localize`Unsaved changes`,
    guardando: $localize`Saving…`,
    'sin-conexion': $localize`No connection: retrying`,
    conflicto: $localize`Someone else saved: reload to continue`,
    rechazado: $localize`The file was rejected`,
  };

  ngOnInit(): void {
    this.procesoId = Number(this.ruta.snapshot.paramMap.get('id'));
    this.leer();
  }

  ngOnDestroy(): void {
    this.autoguardado?.detener();
  }

  /** Lo del servidor; si hay una copia local de esa misma base, se ofrece recuperarla. */
  leer(): void {
    forkJoin({ modelo: this.modelos.leer(this.procesoId), diagrama: this.diagramas.obtener(this.procesoId) })
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: ({ modelo, diagrama }) => {
          this.cargando = true;
          this.xml = modelo.xml;
          this.diagrama = diagrama;
          this.nombre = diagrama.proceso.nombre;
          this.modoProceso = diagrama.proceso.modo ?? 'EJECUTABLE';
          this.versionDelProceso = diagrama.proceso.version;
          this.prepararGuardado(modelo.huella);
          this.refrescarDiagnostico();
          void this.buscarCopia(modelo.huella, modelo.xml);
          this.cambios.markForCheck();
        },
        error: (error: HttpErrorResponse) => (this.error = mensajeDeError(error)),
      });
  }

  private prepararGuardado(huella: string | null): void {
    this.autoguardado?.detener();
    this.autoguardado = new Autoguardado(
      {
        leerXml: () => this.lienzo!.leerXml(),
        enviar: (xml, base, clave) => this.modelos.guardar(this.procesoId, xml, base, clave),
        guardarCopia: (xml, base) =>
          void this.copias.guardar({ procesoId: this.procesoId, xml, huella: base, fecha: Date.now() }),
        borrarCopia: () => void this.copias.borrar(this.procesoId),
        nuevaClave: () => crypto.randomUUID(),
        guardado: () => this.refrescarDiagnostico(),
      },
      huella,
    );
    this.autoguardado.estado$.pipe(takeUntilDestroyed(this.destroyRef)).subscribe((estado) => {
      this.estado = estado;
      this.cambios.markForCheck();
    });
  }

  private async buscarCopia(huella: string | null, xml: string): Promise<void> {
    const copia = await this.copias.leer(this.procesoId);
    this.copia = copia && copia.huella === huella && copia.xml !== xml ? copia : null;
    this.cambios.markForCheck();
  }

  recuperarCopia(): void {
    if (this.copia) {
      this.xml = this.copia.xml;
      this.copia = null;
      // Lo recuperado no esta en el servidor: se guarda en cuanto termine de dibujarse
      this.pendienteDeGuardar = true;
    }
  }

  descartarCopia(): void {
    void this.copias.borrar(this.procesoId);
    this.copia = null;
  }

  private pendienteDeGuardar = false;

  alEstarListo(): void {
    this.cargando = false;
    if (this.pendienteDeGuardar) {
      this.pendienteDeGuardar = false;
      void this.autoguardado?.marcarCambio();
    }
  }

  alCambiar(): void {
    if (!this.cargando) {
      void this.autoguardado?.marcarCambio();
    }
  }

  private refrescarDiagnostico(): void {
    forkJoin({
      diagnostico: this.diagnosticos.obtener(this.procesoId),
      diagrama: this.diagramas.obtener(this.procesoId),
    })
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe(({ diagnostico, diagrama }) => {
        this.diagrama = diagrama;
        this.diagnostico = diagnostico;
        this.versionDelProceso = diagrama.proceso.version;
        this.marcas = marcasDe(diagnostico.hallazgos, diagrama);
        this.cambios.markForCheck();
      });
  }

  irAlHallazgo(hallazgo: Hallazgo): void {
    const id = this.diagrama ? elementoDe(hallazgo, this.diagrama) : null;
    if (id) {
      this.lienzo?.mostrar(id);
    }
  }

  cambiarModo(): void {
    const nuevo: ModoProceso = this.modoProceso === 'EJECUTABLE' ? 'DOCUMENTAL' : 'EJECUTABLE';
    this.modelos
      .cambiarModo(this.procesoId, nuevo, this.versionDelProceso)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: () => {
          this.modoProceso = nuevo;
          // El modo va en el archivo: lo que hay en el servidor cambio
          this.leer();
        },
        error: (error: HttpErrorResponse) => (this.error = mensajeDeError(error)),
      });
  }

  /** Lo que se publica es lo guardado: primero se guarda lo que quede, y la version del proceso se lee despues. */
  async publicar(): Promise<void> {
    this.publicando = true;
    this.publicado = null;
    this.error = null;
    await this.autoguardado?.guardarAhora();
    this.diagramas
      .obtener(this.procesoId)
      .pipe(
        switchMap((diagrama) => this.procesos.publicar(this.procesoId, diagrama.proceso.version)),
        finalize(() => (this.publicando = false)),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe({
        next: (publicado) => {
          this.publicado =
            publicado.versionPublicada === null
              ? $localize`The process is published.`
              : $localize`Published as version ${publicado.versionPublicada}.`;
          this.refrescarDiagnostico();
        },
        error: (error: HttpErrorResponse) => (this.error = mensajeDeError(error)),
      });
  }

  async exportarBpmn(): Promise<void> {
    descargar(await this.lienzo!.leerXml(), 'application/xml', `${this.archivo()}.bpmn`);
  }

  async exportarSvg(): Promise<void> {
    descargar(await this.lienzo!.leerSvg(), 'image/svg+xml', `${this.archivo()}.svg`);
  }

  async exportarPng(): Promise<void> {
    const svg = await this.lienzo!.leerSvg();
    const imagen = new Image();
    imagen.onload = () => {
      const lienzo = document.createElement('canvas');
      lienzo.width = imagen.width;
      lienzo.height = imagen.height;
      const contexto = lienzo.getContext('2d')!;
      contexto.fillStyle = '#ffffff';
      contexto.fillRect(0, 0, lienzo.width, lienzo.height);
      contexto.drawImage(imagen, 0, 0);
      lienzo.toBlob((datos) => {
        if (datos) {
          enlazar(URL.createObjectURL(datos), `${this.archivo()}.png`);
        }
      });
    };
    imagen.src = `data:image/svg+xml;charset=utf-8,${encodeURIComponent(svg)}`;
  }

  /** Un archivo elegido a mano reemplaza lo que se ve; el autoguardado lo manda al servidor. */
  async importar(evento: Event): Promise<void> {
    const entrada = evento.target as HTMLInputElement;
    const archivo = entrada.files?.[0];
    if (archivo) {
      this.xml = await archivo.text();
      this.pendienteDeGuardar = true;
      this.cargando = true;
      entrada.value = '';
      this.cambios.markForCheck();
    }
  }

  recargar(): void {
    void this.copias.borrar(this.procesoId);
    this.leer();
  }

  private archivo(): string {
    return this.nombre.toLowerCase().replace(/[^a-z0-9]+/g, '-') || 'proceso';
  }
}

function descargar(contenido: string, tipo: string, nombre: string): void {
  enlazar(URL.createObjectURL(new Blob([contenido], { type: tipo })), nombre);
}

function enlazar(url: string, nombre: string): void {
  const enlace = document.createElement('a');
  enlace.href = url;
  enlace.download = nombre;
  enlace.click();
  URL.revokeObjectURL(url);
}
