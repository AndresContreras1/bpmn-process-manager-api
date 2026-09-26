import { AsyncPipe } from '@angular/common';
import { Component, inject } from '@angular/core';
import { Router, RouterLink, RouterLinkActive } from '@angular/router';
import { Observable, finalize } from 'rxjs';

import { NOMBRE_ROL, RolAcceso, Usuario } from '../../models/usuario.model';
import { AuthService } from '../../service/auth.service';

@Component({
  selector: 'app-navbar',
  imports: [AsyncPipe, RouterLink, RouterLinkActive],
  templateUrl: './navbar.component.html',
  styleUrl: './navbar.component.scss',
})
export class NavbarComponent {
  private readonly authService: AuthService = inject(AuthService);

  private readonly router: Router = inject(Router);

  readonly usuario$: Observable<Usuario | null> = this.authService.usuario$;
  readonly nombreRol: Record<RolAcceso, string> = NOMBRE_ROL;
  readonly repositorio: string = 'https://github.com/AndresContreras1/bpmn-process-manager-api';

  cerrarSesion(): void {
    // La sesion local se borra en el logout aunque la API no responda; luego se vuelve al login
    this.authService
      .logout()
      .pipe(finalize(() => this.router.navigate(['/login'])))
      .subscribe({ error: () => undefined });
  }
}
