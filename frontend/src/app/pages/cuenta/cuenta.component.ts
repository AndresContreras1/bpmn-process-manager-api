import { AsyncPipe } from '@angular/common';
import { Component, OnInit, inject } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { Observable } from 'rxjs';

import { NOMBRE_ROL, RolAcceso, Usuario } from '../../models/usuario.model';
import { AuthService } from '../../service/auth.service';

/** Una accion de la aplicacion y los roles que pueden hacerla: la misma matriz que aplica la API. */
interface Permiso {
  accion: string;
  roles: RolAcceso[];
}

@Component({
  selector: 'app-cuenta',
  imports: [AsyncPipe],
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
  bienvenida: boolean = false;

  ngOnInit(): void {
    this.bienvenida = this.route.snapshot.queryParamMap.has('bienvenida');
  }
}
