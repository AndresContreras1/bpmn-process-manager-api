import { HttpErrorResponse } from '@angular/common/http';
import { Component, DestroyRef, OnInit, inject } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, ParamMap, Router, RouterLink } from '@angular/router';
import { finalize } from 'rxjs';

import { ErrorCampoComponent } from '../../components/error-campo/error-campo.component';
import { mensajeDeError } from '../../helpers/errores-api';
import { environment } from '../../../environments/environment';
import { AuthService } from '../../service/auth.service';

@Component({
  selector: 'app-login',
  imports: [ReactiveFormsModule, RouterLink, ErrorCampoComponent],
  templateUrl: './login.component.html',
  styleUrl: './login.component.scss',
})
export class LoginComponent implements OnInit {
  /** La cuenta de demostracion solo existe cuando la API corre en dev. */
  readonly demo: boolean = environment.demo;
  private readonly authService: AuthService = inject(AuthService);
  private readonly router: Router = inject(Router);
  private readonly route: ActivatedRoute = inject(ActivatedRoute);
  private readonly destroyRef: DestroyRef = inject(DestroyRef);

  readonly loginForm = new FormGroup({
    email: new FormControl('', [Validators.required, Validators.email, Validators.maxLength(254)]),
    password: new FormControl('', [Validators.required, Validators.maxLength(72)]),
  });

  enviando: boolean = false;
  error: string | null = null;
  sesionVencida: boolean = false;
  private volver: string = '/procesos';

  ngOnInit(): void {
    const parametros: ParamMap = this.route.snapshot.queryParamMap;
    this.sesionVencida = parametros.get('sesion') === 'vencida';
    const volver: string | null = parametros.get('volver');
    // Solo rutas internas: un enlace manipulado no puede mandar al usuario a otro sitio
    if (volver?.startsWith('/') && !volver.startsWith('//')) {
      this.volver = volver;
    }
  }

  usarCuentaDemo(): void {
    this.loginForm.setValue({ email: 'admin@demo.com', password: 'admin123' });
  }

  iniciarSesion(): void {
    this.enviando = true;
    this.error = null;
    const credenciales = { email: this.loginForm.value.email ?? '', password: this.loginForm.value.password ?? '' };
    this.authService
      .login(credenciales)
      .pipe(
        finalize(() => (this.enviando = false)),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe({
        next: () => this.router.navigateByUrl(this.volver),
        error: (error: HttpErrorResponse) => {
          this.error = error.status === 401 ? 'Wrong email or password.' : mensajeDeError(error);
        },
      });
  }
}
