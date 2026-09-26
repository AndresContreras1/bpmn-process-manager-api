import { AsyncPipe } from '@angular/common';
import { Component, OnInit, inject } from '@angular/core';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { Observable } from 'rxjs';

import { NOMBRE_ROL, RolAcceso, Usuario } from '../../models/usuario.model';
import { TokenService } from '../../service/token.service';
import { CambiarClaveComponent } from './cambiar-clave/cambiar-clave.component';
import { AuthService } from '../../service/auth.service';

/** Una accion de la aplicacion y los roles que pueden hacerla: la misma matriz que aplica la API. */
interface Permiso {
  accion: string;
  roles: RolAcceso[];
}

@Component({
  selector: 'app-cuenta',
  imports: [AsyncPipe, RouterLink, CambiarClaveComponent],
  templateUrl: './cuenta.component.html',
  styleUrl: './cuenta.component.scss',
})
export class CuentaComponent implements OnInit {
  private readonly authService: AuthService = inject(AuthService);
  private readonly route: ActivatedRoute = inject(ActivatedRoute);

  readonly usuario$: Observable<Usuario | null> = this.authService.usuario$;
  readonly nombreRol: Record<RolAcceso, string> = NOMBRE_ROL;
  readonly permisos: Permiso[] = [
    { accion: 'View processes and their diagrams', roles: ['ADMINISTRADOR', 'EDITOR', 'SOLO_LECTURA'] },
    { accion: 'Create, edit and publish processes', roles: ['ADMINISTRADOR', 'EDITOR'] },
    { accion: 'Model diagrams: pools, lanes, tasks, gateways and messages', roles: ['ADMINISTRADOR', 'EDITOR'] },
    { accion: 'Delete processes and diagram elements', roles: ['ADMINISTRADOR'] },
    { accion: 'Manage users and process roles', roles: ['ADMINISTRADOR'] },
  ];
  private readonly tokenService: TokenService = inject(TokenService);

  bienvenida: boolean = false;
  /** True mientras la clave siga siendo la temporal con la que entro. */
  claveTemporal: boolean = false;

  ngOnInit(): void {
    this.bienvenida = this.route.snapshot.queryParamMap.has('bienvenida');
    this.claveTemporal = this.tokenService.obtenerUsuario()?.debeCambiarClave === true;
  }

  /** La API devolvio una sesion nueva y el usuario guardado ya no debe cambiar nada. */
  claveCambiada(): void {
    this.claveTemporal = false;
  }
}
