package com.chubb.platform.security;

import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Users for HTTP Basic authentication. If {@code app.security.users} is not configured, a set of demo users is
 * created (officer-1, officer-2, manager-1, tan@example.com, lee@example.com) with {@code app.security.demo-password}
 * (environment variable APP_SECURITY_DEMO_PASSWORD). No password is stored in the repository: when none is configured
 * a random one is generated at startup and logged once, as Spring Security does for its default user. Production would
 * use an identity provider (OIDC/JWT) instead (see docs/decisions-and-assumptions.md).
 */
@ConfigurationProperties(prefix = "app.security")
public class SecurityUsersProperties {

    private String demoPassword;
    private List<UserEntry> users = new ArrayList<>();

    public String getDemoPassword() {
        return demoPassword;
    }

    public void setDemoPassword(String demoPassword) {
        this.demoPassword = demoPassword;
    }

    public List<UserEntry> getUsers() {
        return users;
    }

    public void setUsers(List<UserEntry> users) {
        this.users = users;
    }

    public static class UserEntry {
        private String username;
        private String password;
        private String role;

        public UserEntry() {
        }

        public UserEntry(String username, String password, String role) {
            this.username = username;
            this.password = password;
            this.role = role;
        }

        public String getUsername() {
            return username;
        }

        public void setUsername(String username) {
            this.username = username;
        }

        public String getPassword() {
            return password;
        }

        public void setPassword(String password) {
            this.password = password;
        }

        public String getRole() {
            return role;
        }

        public void setRole(String role) {
            this.role = role;
        }
    }
}
