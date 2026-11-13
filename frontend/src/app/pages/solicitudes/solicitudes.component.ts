import { HttpErrorResponse } from '@angular/common/http';
import { ChangeDetectionStrategy, Component, DestroyRef, OnInit, inject } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { Subscription, finalize } from 'rxjs';

import { InsigniaComponent } from '../../components/ui/insignia.component';
import { VacioComponent } from '../../components/ui/vacio.component';
import { mensajeDeError } from '../../helpers/errores-api';
import { PageResponse } from '../../models/page-response.model';
import {
  ESTADOS_SOLICITUD,
  EstadoSolicitud,
  NOMBRE_ESTADO_SOLICITUD,
  Solicitud,
  TONO_ESTADO_SOLICITUD,
} from '../../models/solicitud.model';
import { Tono } from '../../models/tono.model';
import { FechaPipe } from '../../pipes/fecha.pipe';
import { SolicitudService } from '../../service/solicitud.service';

/** Lo que se mira: lo que espera mi decision, o cada solicitud de la organizacion que puedo ver. */
type Bandeja = 'pendientes' | 'todas';

/**
 * PR F21: la bandeja "por aprobar". Por defecto, las solicitudes abiertas que esperan la decision de quien entro;
 * en la otra pestana, las de la organizacion en procesos que ve, filtradas por estado. La pestana y el estado viven
 * en la direccion, para volver a la misma lista.
 */
@Component({
  selector: 'app-solicitudes',
  imports: [FormsModule, RouterLink, FechaPipe, InsigniaComponent, VacioComponent],
  templateUrl: './solicitudes.component.html',
  changeDetection: ChangeDetectionStrategy.Eager,
})
export class SolicitudesComponent implements OnInit {
  private readonly solicitudes = inject(SolicitudService);
  private readonly ruta = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly destroyRef = inject(DestroyRef);

  readonly estados: readonly EstadoSolicitud[] = ESTADOS_SOLICITUD;
  readonly nombreEstado: Record<EstadoSolicitud, string> = NOMBRE_ESTADO_SOLICITUD;
  readonly tono: Record<EstadoSolicitud, Tono> = TONO_ESTADO_SOLICITUD;

  bandeja: Bandeja = 'pendientes';
  estado: EstadoSolicitud | '' = '';
  pagina: PageResponse<Solicitud> | null = null;
  cargando: boolean = true;
  error: string | null = null;
  private carga: Subscription | null = null;

  ngOnInit(): void {
    this.ruta.queryParamMap.pipe(takeUntilDestroyed(this.destroyRef)).subscribe((parametros) => {
      this.bandeja = parametros.get('bandeja') === 'todas' ? 'todas' : 'pendientes';
      const estado = parametros.get('estado') as EstadoSolicitud | null;
      this.estado = estado && ESTADOS_SOLICITUD.includes(estado) ? estado : '';
      this.cargar(Number(parametros.get('pagina') ?? 0) || 0);
    });
  }

  cambiarBandeja(bandeja: Bandeja): void {
    this.navegar({ bandeja: bandeja === 'todas' ? 'todas' : null, estado: null, pagina: null });
  }

  filtrar(): void {
    this.navegar({ estado: this.estado || null, pagina: null });
  }

  irAPagina(pagina: number): void {
    this.navegar({ pagina: pagina || null });
  }

  cargar(pagina: number = this.pagina?.page ?? 0): void {
    this.carga?.unsubscribe();
    this.cargando = true;
    this.error = null;
    const pendientes = this.bandeja === 'pendientes';
    this.carga = this.solicitudes
      .listar(pendientes, pendientes ? null : this.estado || null, pagina)
      .pipe(
        finalize(() => (this.cargando = false)),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe({
        next: (leida) => (this.pagina = leida),
        error: (error: HttpErrorResponse) => (this.error = mensajeDeError(error)),
      });
  }

  private navegar(consulta: Record<string, string | number | null>): void {
    void this.router.navigate([], { relativeTo: this.ruta, queryParams: consulta, queryParamsHandling: 'merge' });
  }
}
