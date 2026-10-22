package com.chubb.claims.controller;

import com.chubb.platform.security.Roles;
import com.chubb.platform.security.UserDirectory;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** Lookup data for staff screens. */
@RestController
@RequiredArgsConstructor
@Tag(name = "Claims staff")
public class StaffDirectoryController {

    private final UserDirectory users;

    @GetMapping("/api/staff/officers")
    @Operation(summary = "Officer ids a claim can be assigned or reassigned to (roles OFFICER / MANAGER)")
    public List<String> officers() {
        return users.usernamesWithRole(Roles.OFFICER);
    }
}
