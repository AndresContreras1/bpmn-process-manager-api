import { AsyncPipe, DatePipe } from '@angular/common';
import { Component, DestroyRef, OnInit, inject } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { EMPTY, Observable, catchError } from 'rxjs';

import { Empresa } from '../../models/empresa.model';
import { NOMBRE_ROL, RolAcceso, Usuario } from '../../models/usuario.model';
import { AuthService } from '../../service/auth.service';
import { EmpresaService } from '../../service/empresa.service';
import { TokenService } from '../../service/token.service';
import { CambiarClaveComponent } from './cambiar-clave/cambiar-clave.component';

/** Una accion de la aplicacion y los roles que pueden hacerla: la misma matriz que aplica la API. */
interface Permiso {
  accion: string;
  roles: RolAcceso[];
}

@Component({
  selector: 'app-cuenta',
  imports: [AsyncPipe, DatePipe, RouterLink, CambiarClaveComponent],
  templateUrl: './cuenta.component.html',
  styleUrl: './cuenta.component.scss',
})
export class CuentaComponent implements OnInit {
  private readonly authService: AuthService = inject(AuthService);
  private readonly empresaService: EmpresaService = inject(EmpresaService);
  private readonly tokenService: TokenService = inject(TokenService);
  private readonly route: ActivatedRoute = inject(ActivatedRoute);
  private readonly destroyRef: DestroyRef = inject(DestroyRef);

  readonly usuario$: Observable<Usuario | null> = this.authService.usuario$;
  readonly nombreRol: Record<RolAcceso, string> = NOMBRE_ROL;
  readonly permisos: Permiso[] = [
    { accion: 'View processes and their diagrams', roles: ['ADMINISTRADOR', 'EDITOR', 'SOLO_LECTURA'] },
    { accion: 'Create, edit and publish processes', roles: ['ADMINISTRADOR', 'EDITOR'] },
    { accion: 'Model diagrams: pools, lanes, tasks, gateways and messages', roles: ['ADMINISTRADOR', 'EDITOR'] },
    { accion: 'Delete processes and diagram elements', roles: ['ADMINISTRADOR'] },
    { accion: 'Manage users and process roles', roles: ['ADMINISTRADOR'] },
  ];

  /** La tienda a la que pertenece la cuenta. Si no llega, la tarjeta ensena su id y ya. */
  empresa: Empresa | null = null;
  bienvenida: boolean = false;
  /** True mientras la clave siga siendo la temporal con la que entro. */
  claveTemporal: boolean = false;
  /** Lo que queda dicho cuando la tarjeta obligatoria desaparece llevandose su propio mensaje. */
  avisoDeClave: string | null = null;

  ngOnInit(): void {
    this.bienvenida = this.route.snapshot.queryParamMap.has('bienvenida');
    this.claveTemporal = this.tokenService.obtenerUsuario()?.debeCambiarClave === true;
    if (this.claveTemporal) {
      // Con una clave temporal la API solo deja cambiarla: pedir la tienda contestaria 403 y ensuciaria el log
      return;
    }
    this.empresaService
      .actual()
      .pipe(
        catchError(() => EMPTY),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe((empresa: Empresa) => (this.empresa = empresa));
  }

  /** La API devolvio una sesion nueva y el usuario guardado ya no debe cambiar nada. */
  claveCambiada(): void {
    if (this.claveTemporal) {
      // Cambiar la clave obligatoria cierra la tarjeta que lo confirmaba: sin esto no quedaria nada dicho
      this.avisoDeClave = 'Your password was changed, and the sessions you had open elsewhere were closed.';
    }
    this.claveTemporal = false;
  }
}
