import { BreakpointObserver } from '@angular/cdk/layout';
import { Component, computed, inject, viewChild } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { MatButtonModule } from '@angular/material/button';
import { MatIcon } from '@angular/material/icon';
import { MatListModule } from '@angular/material/list';
import { MatMenuModule } from '@angular/material/menu';
import { MatSidenav, MatSidenavModule } from '@angular/material/sidenav';
import { MatToolbarModule } from '@angular/material/toolbar';
import { RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { map } from 'rxjs';
import { AuthService } from '../core/auth.service';
import { Role } from '../core/models';

interface NavItem {
  label: string;
  icon: string;
  link: string;
  roles: Role[];
}

const NAV: NavItem[] = [
  { label: 'Dashboard', icon: 'space_dashboard', link: '/dashboard', roles: ['MANAGER'] },
  { label: 'My claims', icon: 'description', link: '/my-claims', roles: ['CLAIMANT'] },
  { label: 'Report an incident', icon: 'add_circle', link: '/my-claims/new', roles: ['CLAIMANT'] },
  { label: 'Intake queue', icon: 'inbox', link: '/queue', roles: ['OFFICER', 'MANAGER'] },
  { label: 'My workload', icon: 'assignment_ind', link: '/workload', roles: ['OFFICER'] },
  { label: 'Officer workload', icon: 'assignment_ind', link: '/workload', roles: ['MANAGER'] },
  { label: 'Team performance', icon: 'groups', link: '/team', roles: ['MANAGER'] },
  { label: 'SLA breaches', icon: 'timer_off', link: '/sla', roles: ['MANAGER'] },
  { label: 'Notifications', icon: 'notifications', link: '/notifications', roles: ['CLAIMANT', 'OFFICER', 'MANAGER'] },
];

const ROLE_LABEL: Record<Role, string> = { CLAIMANT: 'Claimant', OFFICER: 'Claims officer', MANAGER: 'Claims manager' };

@Component({
  selector: 'app-shell',
  imports: [
    RouterOutlet, RouterLink, RouterLinkActive, MatSidenavModule, MatToolbarModule, MatListModule, MatButtonModule,
    MatIcon, MatMenuModule,
  ],
  templateUrl: './shell.html',
  styleUrl: './shell.scss',
})
export class Shell {
  protected readonly auth = inject(AuthService);
  private readonly sidenav = viewChild.required(MatSidenav);

  protected readonly handset = toSignal(
    inject(BreakpointObserver).observe('(max-width: 959px)').pipe(map((r) => r.matches)),
    { initialValue: false },
  );

  protected readonly nav = computed(() => {
    const user = this.auth.user();
    return user ? NAV.filter((n) => n.roles.some((r) => user.roles.includes(r))) : [];
  });

  protected readonly roleLabel = computed(() => {
    const role = this.auth.user()?.roles[0];
    return role ? ROLE_LABEL[role] : '';
  });

  protected readonly initials = computed(() => (this.auth.user()?.username ?? '?').slice(0, 2).toUpperCase());

  protected navigated(): void {
    if (this.handset()) void this.sidenav().close();
  }
}
