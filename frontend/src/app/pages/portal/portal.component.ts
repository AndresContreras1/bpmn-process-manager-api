import { HttpErrorResponse } from '@angular/common/http';
import { ChangeDetectionStrategy, Component, DestroyRef, OnInit, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Params, Router, RouterLink } from '@angular/router';
import { Observable, Subscription, finalize } from 'rxjs';

import { FragmentoComponent } from '../../components/busqueda-global/fragmento.component';
import { CarpetasComponent } from '../../components/carpetas/carpetas.component';
import { InsigniaComponent } from '../../components/ui/insignia.component';
import { VacioComponent } from '../../components/ui/vacio.component';
import { mensajeDeError } from '../../helpers/errores-api';
import {
  DestinoDeResultado,
  NOMBRE_TIPO_DE_RESULTADO,
  ResultadoDeBusqueda,
  TipoDeResultado,
} from '../../models/busqueda.model';
import { PageResponse } from '../../models/page-response.model';
import {
  DireccionDelPortal,
  destinoEnPortal,
  leerDireccionDelPortal,
  revisionVencida,
} from '../../models/portal.model';
import { Clasificacion, NOMBRE_CLASIFICACION, Proceso } from '../../models/proceso.model';
import { FechaPipe } from '../../pipes/fecha.pipe';
import { PortalService } from '../../service/portal.service';

/**
 * F22: la entrada del portal de lectura. A un lado las carpetas, sin nada para cambiarlas; al otro, lo publicado de la
 * carpeta elegida o lo que encontro la busqueda. La carpeta, el texto y la pagina viven en la direccion: un enlace o
 * una recarga dejan la misma lista.
 */
@Component({
  selector: 'app-portal',
  imports: [
    FormsModule,
    RouterLink,
    CarpetasComponent,
    FragmentoComponent,
    InsigniaComponent,
    VacioComponent,
    FechaPipe,
  ],
  templateUrl: './portal.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class PortalComponent implements OnInit {
  private readonly portal = inject(PortalService);
  private readonly ruta = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly destroyRef = inject(DestroyRef);

  readonly nombreClasificacion: Record<Clasificacion, string> = NOMBRE_CLASIFICACION;
  readonly nombreTipo: Record<TipoDeResultado, string> = NOMBRE_TIPO_DE_RESULTADO;
  readonly vencida = revisionVencida;
  readonly destino: (resultado: ResultadoDeBusqueda) => DestinoDeResultado = destinoEnPortal;

  readonly direccion = signal<DireccionDelPortal>({ carpetaId: null, texto: '', pagina: 0 });
  readonly pagina = signal<PageResponse<Proceso> | null>(null);
  readonly resultados = signal<ResultadoDeBusqueda[] | null>(null);
  readonly cargando = signal<boolean>(true);
  readonly error = signal<string | null>(null);
  /** Lo que esta escrito en la caja; se busca al mandarla. */
  consulta: string = '';
  private carga: Subscription | null = null;

  ngOnInit(): void {
    this.ruta.queryParamMap.pipe(takeUntilDestroyed(this.destroyRef)).subscribe((parametros) => {
      const direccion = leerDireccionDelPortal(parametros);
      this.direccion.set(direccion);
      this.consulta = direccion.texto;
      this.cargar();
    });
  }

  elegirCarpeta(carpetaId: number | null): void {
    this.navegar({ carpeta: carpetaId, q: null, pagina: null });
  }

  buscar(): void {
    this.navegar({ q: this.consulta.trim() || null, pagina: null });
  }

  limpiar(): void {
    this.consulta = '';
    this.navegar({ q: null, pagina: null });
  }

  irAPagina(pagina: number): void {
    this.navegar({ pagina: pagina || null });
  }

  cargar(): void {
    this.carga?.unsubscribe();
    this.cargando.set(true);
    this.error.set(null);
    const { carpetaId, texto, pagina } = this.direccion();
    const fallo = (error: HttpErrorResponse): void => this.error.set(mensajeDeError(error));
    const fin = <T>(peticion: Observable<T>): Observable<T> =>
      peticion.pipe(
        finalize(() => this.cargando.set(false)),
        takeUntilDestroyed(this.destroyRef),
      );
    if (texto) {
      this.carga = fin(this.portal.buscar(texto)).subscribe({
        next: (resultados) => this.resultados.set(resultados),
        error: fallo,
      });
      return;
    }
    this.resultados.set(null);
    this.carga = fin(this.portal.publicados(carpetaId, pagina)).subscribe({
      next: (leida) => this.pagina.set(leida),
      error: fallo,
    });
  }

  private navegar(consulta: Params): void {
    void this.router.navigate([], { relativeTo: this.ruta, queryParams: consulta, queryParamsHandling: 'merge' });
  }
}
