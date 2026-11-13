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
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { Observable, Subscription, catchError, finalize, forkJoin, interval, of, switchMap } from 'rxjs';

import { Autoguardado, EstadoGuardado } from '../../bpmn/autoguardado';
import { CopiaLocal, CopiaLocalService } from '../../bpmn/copia-local.service';
import { LeyendaComparacionComponent } from '../../bpmn/leyenda-comparacion.component';
import { PanelComentariosComponent } from '../../components/comentarios/panel-comentarios.component';
import { PedirRevisionComponent } from '../../components/pedir-revision/pedir-revision.component';
import { ElementoElegido, LienzoBpmnComponent } from '../../bpmn/lienzo-bpmn.component';
import { elementoDe, marcasDe, marcasDeComparacion } from '../../bpmn/marcas';
import { ModoProceso } from '../../bpmn/modulos/reglas';
import { PanelPropiedadesComponent } from '../../bpmn/panel-propiedades.component';
import { VistaEstructuraComponent } from '../../bpmn/vista-estructura.component';
import { mensajeDeError } from '../../helpers/errores-api';
import { Diagnostico, Hallazgo } from '../../models/diagnostico.model';
import { Diagrama } from '../../models/diagrama.model';
import { Bloqueo, EventoEnVivo, Presente } from '../../models/en-vivo.model';
import { Comparacion } from '../../models/modelo.model';
import { Solicitud } from '../../models/solicitud.model';
import { AuthService } from '../../service/auth.service';
import { DiagnosticoService } from '../../service/diagnostico.service';
import { DiagramaService } from '../../service/diagrama.service';
import { EnVivoService } from '../../service/en-vivo.service';
import { ProcesoService } from '../../service/proceso.service';
import { SolicitudService } from '../../service/solicitud.service';
import { ModeloService } from '../../service/modelo.service';
import { VersionService } from '../../service/version.service';

/** El modelador: dibuja con bpmn-js, guarda solo y dice en vivo lo que el diagnostico encuentra. */
@Component({
  selector: 'app-proceso-modelador',
  imports: [
    RouterLink,
    LienzoBpmnComponent,
    PanelPropiedadesComponent,
    VistaEstructuraComponent,
    LeyendaComparacionComponent,
    PanelComentariosComponent,
    PedirRevisionComponent,
  ],
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
  private readonly versiones = inject(VersionService);
  private readonly enVivo = inject(EnVivoService);
  private readonly solicitudes = inject(SolicitudService);
  private readonly router = inject(Router);
  private readonly auth = inject(AuthService);
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
  /** La vista de estructura, y cuantos cambios lleva el dibujo: es lo que la hace releer. */
  estructuraAbierta = false;
  revision = 0;
  dibujado = false;
  /** Quien esta y quien edita. Sin el bloqueo se mira, no se cambia. */
  personas: Presente[] = [];
  bloqueo: Bloqueo | null = null;
  soloLectura = false;
  conectado = true;
  avisoControl: string | null = null;
  comparacion: Comparacion | null = null;
  private latidos: Subscription | null = null;
  elegido: ElementoElegido | null = null;
  diagnostico: Diagnostico | null = null;
  marcas: Record<string, string[]> = {};
  estado: EstadoGuardado = 'guardado';
  copia: CopiaLocal | null = null;
  autoguardado: Autoguardado | null = null;
  private diagrama: Diagrama | null = null;
  private cargando = true;
  /** F21: con aprobacion, el modelador pide la revision en vez de publicar; y no otra si ya hay una abierta. */
  conAprobacion = false;
  solicitudAbierta: Solicitud | null = null;
  pidiendoRevision = false;
  /** F21: cuantos hilos abiertos tiene cada elemento, que el panel de comentarios cuenta y el lienzo pinta. */
  contadoresDeComentarios: Record<string, number> = {};

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
    this.leer(this.enVivo.adquirir(this.procesoId));
    this.leerRevision();
    this.enVivo
      .eventos(this.procesoId)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe((evento) => this.alEvento(evento));
  }

  ngOnDestroy(): void {
    this.autoguardado?.detener();
    this.latidos?.unsubscribe();
    if (this.bloqueo?.mio) {
      this.enVivo.soltar(this.procesoId).subscribe({ error: () => undefined });
    }
  }

  /** Lo del servidor; si hay una copia local de esa misma base, se ofrece recuperarla. */
  leer(bloqueo$: Observable<Bloqueo> = this.enVivo.estado(this.procesoId)): void {
    forkJoin({
      modelo: this.modelos.leer(this.procesoId),
      diagrama: this.diagramas.obtener(this.procesoId),
      // Quien no puede editar recibe un 403: mira sin bloqueo
      bloqueo: bloqueo$.pipe(catchError(() => of(null))),
    })
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: ({ modelo, diagrama, bloqueo }) => {
          const antes = this.soloLectura;
          this.aplicarBloqueo(bloqueo);
          // Sin un dibujo nuevo el lienzo no avisa que termino: solo se espera si de verdad va a dibujar
          this.cargando = modelo.xml !== this.xml || antes !== this.soloLectura;
          this.dibujado = !this.cargando;
          this.comparacion = null;
          this.xml = modelo.xml;
          this.diagrama = diagrama;
          this.nombre = diagrama.proceso.nombre;
          this.modoProceso = diagrama.proceso.modo ?? 'EJECUTABLE';
          this.versionDelProceso = diagrama.proceso.version;
          this.prepararGuardado(modelo.huella);
          this.refrescarDiagnostico();
          if (!this.soloLectura) {
            void this.buscarCopia(modelo.huella, modelo.xml);
          }
          this.cambios.markForCheck();
        },
        error: (error: HttpErrorResponse) => (this.error = mensajeDeError(error)),
      });
  }

  private aplicarBloqueo(bloqueo: Bloqueo | null): void {
    this.bloqueo = bloqueo;
    this.soloLectura = !bloqueo?.mio;
    this.latidos?.unsubscribe();
    this.latidos = null;
    if (bloqueo?.mio) {
      // El bloqueo vence a los 60 segundos sin latido
      this.latidos = interval(20000).subscribe(() =>
        this.enVivo.latido(this.procesoId).subscribe({ error: () => this.perderControl(null) }),
      );
    }
  }

  private alEvento(evento: EventoEnVivo): void {
    const yo = this.auth.usuarioActual()?.id;
    switch (evento.tipo) {
      case 'conectado':
        this.conectado = true;
        break;
      case 'desconectado':
        this.conectado = false;
        break;
      case 'presencia':
        this.personas = evento.datos.personas;
        break;
      case 'bloqueo':
        this.refrescarBloqueo();
        break;
      case 'control-tomado':
        if (evento.datos.anteriorId === yo) {
          this.perderControl(evento.datos.nombre);
        } else {
          this.refrescarBloqueo();
        }
        break;
      case 'guardado':
      case 'publicado':
        // Quien solo mira ve lo ultimo; quien edita se entera al guardar, con un conflicto que puede comparar
        if (this.soloLectura && evento.datos.autor !== yo) {
          this.leer();
        }
        break;
    }
    this.cambios.markForCheck();
  }

  private refrescarBloqueo(): void {
    this.enVivo
      .estado(this.procesoId)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe((bloqueo) => {
        if (!this.soloLectura && !bloqueo.libre && !bloqueo.mio) {
          this.perderControl(bloqueo.nombre);
        } else {
          this.bloqueo = bloqueo;
          this.cambios.markForCheck();
        }
      });
  }

  /** Otra persona tomo el control: lo que no se guardo sigue en la copia de este navegador. */
  private perderControl(nombre: string | null): void {
    if (this.soloLectura) {
      return;
    }
    this.autoguardado?.detener();
    this.avisoControl = nombre
      ? $localize`${nombre} took control. Your unsaved changes stay in this browser.`
      : $localize`You lost control of this process. Your unsaved changes stay in this browser.`;
    this.leer();
  }

  tomarControl(): void {
    this.enVivo
      .tomarControl(this.procesoId)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: () => {
          this.avisoControl = null;
          this.leer();
        },
        error: (error: HttpErrorResponse) => (this.error = mensajeDeError(error)),
      });
  }

  /** Lo que este dibujo le haria a lo que otra persona guardo. */
  async compararConflicto(): Promise<void> {
    const xml = await this.lienzo!.leerXml();
    this.modelos
      .compararArchivo(this.procesoId, xml)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (comparacion) => {
          this.comparacion = comparacion;
          this.marcas = marcasDeComparacion(comparacion);
          this.cambios.markForCheck();
        },
        error: (error: HttpErrorResponse) => (this.error = mensajeDeError(error)),
      });
  }

  iniciales(nombre: string): string {
    return nombre
      .split(/\s+/)
      .filter((parte) => parte)
      .slice(0, 2)
      .map((parte) => parte[0].toUpperCase())
      .join('');
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
    this.dibujado = true;
    this.revision++;
    if (this.pendienteDeGuardar) {
      this.pendienteDeGuardar = false;
      void this.autoguardado?.marcarCambio();
    }
  }

  alCambiar(): void {
    this.revision++;
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

  /**
   * F21: como publica la organizacion y si ya hay una revision abierta. Sin la respuesta se ofrece publicar, y la API
   * dice con un 409 que hay que pedir la revision.
   */
  private leerRevision(): void {
    forkJoin({
      politica: this.solicitudes.politica().pipe(catchError(() => of(null))),
      abiertas: this.solicitudes.delProceso(this.procesoId, 'ABIERTA').pipe(catchError(() => of(null))),
    })
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe(({ politica, abiertas }) => {
        this.conAprobacion = politica?.modo === 'CON_APROBACION';
        this.solicitudAbierta = abiertas?.content[0] ?? null;
        this.cambios.markForCheck();
      });
  }

  /** F21: lo que se congela es lo guardado, asi que primero se guarda lo que quede, como al publicar. */
  async pedirRevision(): Promise<void> {
    this.error = null;
    await this.autoguardado?.guardarAhora();
    this.pidiendoRevision = true;
    this.cambios.markForCheck();
  }

  alPedirRevision(solicitud: Solicitud): void {
    this.pidiendoRevision = false;
    void this.router.navigate(['/solicitudes', solicitud.id]);
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
          if (publicado.versionPublicada !== null) {
            void this.guardarDibujo(publicado.versionPublicada);
          }
          this.refrescarDiagnostico();
        },
        error: (error: HttpErrorResponse) => (this.error = mensajeDeError(error)),
      });
  }

  /**
   * PR 62: el dibujo de la version recien publicada, para su manual. Si no llega, el manual lo dice; publicar ya
   * salio bien y no se deshace por esto.
   */
  private async guardarDibujo(numero: number): Promise<void> {
    const svg = await this.lienzo?.leerSvg();
    if (svg) {
      this.versiones
        .guardarDibujo(this.procesoId, numero, svg)
        .pipe(takeUntilDestroyed(this.destroyRef))
        .subscribe({ error: () => undefined });
    }
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
