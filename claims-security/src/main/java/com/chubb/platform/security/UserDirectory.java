package com.chubb.platform.security;

import com.chubb.platform.security.SecurityUsersProperties.UserEntry;
import java.util.List;

/** The configured users. Lets a service list who holds a role (e.g. officers to reassign to) without exposing secrets. */
public class UserDirectory {

    private final List<UserEntry> entries;

    UserDirectory(List<UserEntry> entries) {
        this.entries = List.copyOf(entries);
    }

    List<UserEntry> entries() {
        return entries;
    }

    /** Usernames holding the role, sorted. */
    public List<String> usernamesWithRole(String role) {
        return entries.stream().filter(u -> role.equals(u.getRole())).map(UserEntry::getUsername).sorted().toList();
    }
}
