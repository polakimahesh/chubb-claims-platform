package com.chubb.claims.controller;

import com.chubb.claims.dto.ClaimResponses.DocumentInfo;
import com.chubb.claims.service.DocumentService;
import com.chubb.platform.security.Actor;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/** Claim attachments: upload (claimant), list and download (claimant owner and staff). */
@RestController
@RequestMapping("/api/claims/{claimId}/documents")
@RequiredArgsConstructor
@Tag(name = "Documents")
public class DocumentController {

    private final DocumentService service;

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Attach a PDF/PNG/JPEG (max 5 MB) to my claim; multipart part name 'file'")
    public ResponseEntity<DocumentInfo> upload(Authentication auth, @PathVariable UUID claimId,
                                               @RequestPart("file") MultipartFile file) {
        DocumentInfo created = service.upload(claimId, Actor.from(auth), file);
        return ResponseEntity.created(URI.create("/api/claims/" + claimId + "/documents/" + created.id()))
                .body(created);
    }

    @GetMapping
    @Operation(summary = "List a claim's documents")
    public List<DocumentInfo> list(Authentication auth, @PathVariable UUID claimId) {
        return service.list(claimId, Actor.from(auth));
    }

    @GetMapping("/{documentId}")
    @Operation(summary = "Download a document")
    public ResponseEntity<byte[]> download(Authentication auth, @PathVariable UUID claimId,
                                           @PathVariable UUID documentId) {
        DocumentService.Content content = service.download(claimId, documentId, Actor.from(auth));
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(content.document().getContentType()))
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename(content.document().getFileName()).build().toString())
                .header("X-Content-Type-Options", "nosniff")
                .body(content.bytes());
    }
}
