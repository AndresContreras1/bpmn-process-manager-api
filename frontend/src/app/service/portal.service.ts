import { Injectable, inject } from '@angular/core';
import { Observable, catchError, forkJoin, map, of, switchMap } from 'rxjs';

import { ResultadoDeBusqueda } from '../models/busqueda.model';
import { PageResponse } from '../models/page-response.model';
import { ProcesoDelPortal, resultadosDelPortal } from '../models/portal.model';
import { Proceso, ProcesoDetalle } from '../models/proceso.model';
import { BusquedaService } from './busqueda.service';
import { ConcesionService } from './concesion.service';
import { DocumentacionService } from './documentacion.service';
import { DocumentoService } from './documento.service';
import { ManualService } from './manual.service';
import { ProcesoService } from './proceso.service';
import { VersionService } from './version.service';

/**
 * F22: lo que lee el portal, armado con los servicios que ya hablan con la API. Solo lee: el unico POST que el portal
 * puede mandar, pedir el manual, va por ManualService y solo cuando controlesDelPortal lo deja.
 */
@Injectable({ providedIn: 'root' })
export class PortalService {
  private readonly procesos = inject(ProcesoService);
  private readonly versiones = inject(VersionService);
  private readonly concesiones = inject(ConcesionService);
  private readonly documentacion = inject(DocumentacionService);
  private readonly documentos = inject(DocumentoService);
  private readonly manuales = inject(ManualService);
  private readonly busqueda = inject(BusquedaService);

  /** Lo publicado de una carpeta y de las que tiene dentro, o de la organizacion entera sin carpeta, por nombre. */
  publicados(carpetaId: number | null, pagina: number): Observable<PageResponse<Proceso>> {
    return this.procesos.listar({
      nombre: '',
      estado: 'PUBLICADO',
      categoria: '',
      carpetaId,
      orden: 'nombre',
      direccion: 'asc',
      pagina,
    });
  }

  /** La busqueda del PR 65, la misma del Ctrl+K, sin lo que no es del portal (resultadosDelPortal). */
  buscar(texto: string): Observable<ResultadoDeBusqueda[]> {
    return this.busqueda
      .buscar(texto)
      .pipe(map((pagina: PageResponse<ResultadoDeBusqueda>) => resultadosDelPortal(pagina.content)));
  }

  /**
   * Un proceso entero para el portal. El detalle manda: si falla (404 de otra tienda, sin permiso de ver), el error
   * sigue. Lo demas es complemento y, si falla, la pagina se muestra sin eso: sin permisos no se ofrece nada mas que
   * leer, y sin documentacion se ve el diagrama igual. El dibujo es el de la version vigente, pedido para mostrarlo
   * (para=dibujar), que solo exige poder verlo.
   */
  abrir(id: number): Observable<ProcesoDelPortal> {
    return forkJoin({
      detalle: this.procesos.obtener(id),
      acciones: this.concesiones.permisos(id).pipe(catchError(() => of(null))),
      caracterizacion: this.documentacion.caracterizacion(id).pipe(catchError(() => of(null))),
      textos: this.documentacion.listar(id).pipe(catchError(() => of([]))),
      documentos: this.documentos.listar(id).pipe(catchError(() => of([]))),
      manuales: this.manuales.listar(id).pipe(catchError(() => of([]))),
    }).pipe(
      switchMap((leido) =>
        this.dibujo(leido.detalle).pipe(
          map((xml: string | null): ProcesoDelPortal => ({
            ...leido,
            xml,
          })),
        ),
      ),
    );
  }

  private dibujo(detalle: ProcesoDetalle): Observable<string | null> {
    const numero = detalle.proceso.versionPublicada;
    if (numero === null || numero === undefined) {
      return of(null);
    }
    return this.versiones.modelo(detalle.proceso.id, numero);
  }
}
