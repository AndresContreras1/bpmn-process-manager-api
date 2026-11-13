import { AsyncPipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, DestroyRef, inject, OnInit } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { NavigationEnd, Router, RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { Observable, filter, finalize, map, of, switchMap } from 'rxjs';

import { Idioma, Preferencias } from '../../models/preferencias.model';
import { NOMBRE_ROL, RolAcceso, Usuario } from '../../models/usuario.model';
import { atajoDeBusqueda, esAtajoDeBusqueda, hayOtroDialogoAbierto } from '../../helpers/teclado';
import { AuthService } from '../../service/auth.service';
import { AvisosService } from '../../service/avisos.service';
import { BusquedaGlobalService } from '../../service/busqueda-global.service';
import { IDIOMAS, IdiomaService, OpcionDeIdioma } from '../../service/idioma.service';
import { PreferenciasService } from '../../service/preferencias.service';
import { TemaService } from '../../service/tema.service';
import { VerificacionService } from '../../service/verificacion.service';
import { AvisoDeInactividadComponent } from '../aviso-de-inactividad/aviso-de-inactividad.component';
import { BusquedaGlobalComponent } from '../busqueda-global/busqueda-global.component';
import { DialogoDeVerificacionComponent } from '../dialogo-de-verificacion/dialogo-de-verificacion.component';
import { AvisosComponent } from '../ui/avisos.component';
import { Miga, migasDe } from './migas';

/**
 * La estructura de toda la web: el encabezado con la marca, la busqueda, el tema y la cuenta; la barra lateral con las
 * secciones, para quien entro; las migas de la pantalla abierta, el contenido y el pie. Los enlaces conservan los id
 * que tenia la barra de navegacion, que son los que buscan las pruebas de punta a punta.
 */
@Component({
  selector: 'app-estructura',
  imports: [
    AsyncPipe,
    RouterLink,
    RouterLinkActive,
    RouterOutlet,
    AvisosComponent,
    AvisoDeInactividadComponent,
    BusquedaGlobalComponent,
    DialogoDeVerificacionComponent,
  ],
  templateUrl: './estructura.component.html',
  changeDetection: ChangeDetectionStrategy.Eager,
  styleUrl: './estructura.component.scss',
  host: { '(document:keydown)': 'atajo($event)' },
})
export class EstructuraComponent implements OnInit {
  private readonly authService: AuthService = inject(AuthService);
  private readonly router: Router = inject(Router);
  private readonly destroyRef: DestroyRef = inject(DestroyRef);
  private readonly preferenciasService: PreferenciasService = inject(PreferenciasService);
  private readonly avisos: AvisosService = inject(AvisosService);
  readonly temaService: TemaService = inject(TemaService);
  readonly idiomaService: IdiomaService = inject(IdiomaService);
  readonly verificacion: VerificacionService = inject(VerificacionService);
  readonly busquedaGlobal: BusquedaGlobalService = inject(BusquedaGlobalService);
  readonly idiomas: readonly OpcionDeIdioma[] = IDIOMAS;

  readonly usuario$: Observable<Usuario | null> = this.authService.usuario$;
  readonly nombreRol: Record<RolAcceso, string> = NOMBRE_ROL;
  readonly repositorio: string = 'https://github.com/AndresContreras1/bpmn-process-manager-api';
  /** El atajo como se lee en el boton del encabezado: Cmd K en un Mac. */
  readonly atajoVisible: string = atajoDeBusqueda(navigator.userAgent);

  migas: Miga[] = [];

  ngOnInit(): void {
    this.router.events
      .pipe(
        filter((evento): evento is NavigationEnd => evento instanceof NavigationEnd),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe(() => {
        this.migas = migasDe(this.router.routerState.snapshot.root);
      });
  }

  /**
   * F19: Ctrl+K, o Cmd+K, abre la busqueda global desde cualquier pantalla, tambien con el foco en un campo, que es
   * lo que hacen las aplicaciones que la tienen. Sin sesion no hay nada que buscar, y el navegador conserva su atajo.
   * Con otro dialogo abierto no se apila encima: primero se cierra el que hay.
   */
  atajo(evento: KeyboardEvent): void {
    if (!esAtajoDeBusqueda(evento) || !this.authService.usuarioActual()) {
      return;
    }
    evento.preventDefault();
    if (!hayOtroDialogoAbierto(document, 'busqueda-dialogo')) {
      this.busquedaGlobal.abrir();
    }
  }

  /**
   * F12: quien entro elige su idioma, y queda guardado en su cuenta con la zona que ya tenia; quien no, solo en el
   * navegador. Si la API no lo guarda, la web se queda en el que estaba y lo dice: la pagina del idioma nuevo leeria
   * la preferencia guardada y volveria a este.
   */
  cambiarIdioma(idioma: Idioma): void {
    if (!this.authService.usuarioActual()) {
      this.idiomaService.ir(idioma);
      return;
    }
    // La zona propia viaja tal cual: cambiar el idioma no puede llevarse la zona que la persona ya habia elegido
    const actuales: Preferencias | null = this.preferenciasService.actuales();
    const zonaPropia: Observable<string | null> = actuales
      ? of(actuales.zonaHorariaPropia)
      : this.preferenciasService.cargar().pipe(map((cargadas: Preferencias) => cargadas.zonaHorariaPropia));
    zonaPropia
      .pipe(
        switchMap((zonaHoraria: string | null) => this.preferenciasService.cambiarLasPropias({ idioma, zonaHoraria })),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe({
        next: () => this.idiomaService.ir(idioma),
        error: () => this.avisos.error($localize`Your language could not be saved, so it did not change. Try again.`),
      });
  }

  cerrarSesion(): void {
    // La sesion local se borra en el logout aunque la API no responda; luego se vuelve al login
    this.authService
      .logout()
      .pipe(
        finalize(() => this.router.navigate(['/login'])),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe({ error: () => undefined });
  }
}
