import { AsyncPipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, DestroyRef, inject, OnInit } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { NavigationEnd, Router, RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { Observable, filter, finalize } from 'rxjs';

import { NOMBRE_ROL, RolAcceso, Usuario } from '../../models/usuario.model';
import { AuthService } from '../../service/auth.service';
import { TemaService } from '../../service/tema.service';
import { AvisosComponent } from '../ui/avisos.component';
import { Miga, migasDe } from './migas';

/**
 * La estructura de toda la web: el encabezado con la marca, la busqueda, el tema y la cuenta; la barra lateral con las
 * secciones, para quien entro; las migas de la pantalla abierta, el contenido y el pie. Los enlaces conservan los id
 * que tenia la barra de navegacion, que son los que buscan las pruebas de punta a punta.
 */
@Component({
  selector: 'app-estructura',
  imports: [AsyncPipe, ReactiveFormsModule, RouterLink, RouterLinkActive, RouterOutlet, AvisosComponent],
  templateUrl: './estructura.component.html',
  changeDetection: ChangeDetectionStrategy.Eager,
  styleUrl: './estructura.component.scss',
})
export class EstructuraComponent implements OnInit {
  private readonly authService: AuthService = inject(AuthService);
  private readonly router: Router = inject(Router);
  private readonly destroyRef: DestroyRef = inject(DestroyRef);
  readonly temaService: TemaService = inject(TemaService);

  readonly usuario$: Observable<Usuario | null> = this.authService.usuario$;
  readonly nombreRol: Record<RolAcceso, string> = NOMBRE_ROL;
  readonly repositorio: string = 'https://github.com/AndresContreras1/bpmn-process-manager-api';
  readonly busqueda: FormControl<string> = new FormControl<string>('', { nonNullable: true });

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

  /** La busqueda del encabezado busca procesos por su nombre, en la lista de procesos. */
  buscar(): void {
    const texto: string = this.busqueda.value.trim();
    this.router.navigate(['/procesos'], { queryParams: texto ? { nombre: texto } : {} });
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
