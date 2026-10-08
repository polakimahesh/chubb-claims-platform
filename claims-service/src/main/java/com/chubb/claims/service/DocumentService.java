package com.chubb.claims.service;

import com.chubb.claims.dto.ClaimResponses.DocumentInfo;
import com.chubb.claims.entity.Claim;
import com.chubb.claims.entity.ClaimDocument;
import com.chubb.claims.exception.BusinessRuleException;
import com.chubb.claims.exception.ClaimNotFoundException;
import com.chubb.claims.exception.DocumentTooLargeException;
import com.chubb.claims.exception.InvalidRequestException;
import com.chubb.claims.exception.InvalidStateTransitionException;
import com.chubb.claims.exception.UnsupportedDocumentTypeException;
import com.chubb.claims.repository.ClaimDocumentRepository;
import com.chubb.claims.storage.DocumentStorage;
import com.chubb.platform.security.Actor;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

/**
 * Claim attachments (police reports, photos, invoices). Only the owning claimant uploads; claimants see their own
 * claims' documents, staff see all. Uploads are restricted to PDF/PNG/JPEG and the content is sniffed so the
 * declared type cannot lie.
 */
@Service
@RequiredArgsConstructor
public class DocumentService {

    static final int MAX_DOCUMENTS_PER_CLAIM = 20;
    private static final Map<String, byte[]> MAGIC = Map.of(
            "application/pdf", new byte[] {'%', 'P', 'D', 'F'},
            "image/png", new byte[] {(byte) 0x89, 'P', 'N', 'G'},
            "image/jpeg", new byte[] {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF});

    public record Content(ClaimDocument document, byte[] bytes) {
    }

    private final ClaimService claimService;
    private final ClaimDocumentRepository documents;
    private final DocumentStorage storage;
    private final Clock clock;
    @org.springframework.beans.factory.annotation.Value("${app.documents.max-bytes:5242880}")
    private long maxBytes;

    @Transactional
    public DocumentInfo upload(UUID claimId, Actor claimant, MultipartFile file) {
        Claim claim = claimService.loadVisible(claimId, claimant);
        if (claim.getStatus().isTerminal()) {
            throw new InvalidStateTransitionException("Documents cannot be added to a closed claim");
        }
        if (file == null || file.isEmpty()) {
            throw new InvalidRequestException("A non-empty file is required in the 'file' part");
        }
        if (documents.countByClaimId(claimId) >= MAX_DOCUMENTS_PER_CLAIM) {
            throw new BusinessRuleException("A claim can have at most " + MAX_DOCUMENTS_PER_CLAIM + " documents");
        }
        if (file.getSize() > maxBytes) {
            throw new DocumentTooLargeException(maxBytes);
        }
        String contentType = file.getContentType() == null ? "" : file.getContentType().toLowerCase();
        byte[] bytes = readBytes(file);
        byte[] magic = MAGIC.get(contentType);
        if (magic == null) {
            throw new UnsupportedDocumentTypeException("Only PDF, PNG and JPEG files are accepted");
        }
        if (!startsWith(bytes, magic)) {
            throw new UnsupportedDocumentTypeException("File content does not match its declared type " + contentType);
        }
        String key = UUID.randomUUID().toString();
        storage.store(claimId, key, bytes);
        try {
            ClaimDocument saved = documents.save(new ClaimDocument(claimId, sanitise(file.getOriginalFilename()),
                    contentType, bytes.length, key, claimant.username(), clock.instant()));
            return DocumentInfo.from(saved);
        } catch (RuntimeException e) {
            storage.delete(claimId, key);
            throw e;
        }
    }

    @Transactional(readOnly = true)
    public List<DocumentInfo> list(UUID claimId, Actor actor) {
        claimService.loadVisible(claimId, actor);
        return documents.findByClaimIdOrderByUploadedAtAsc(claimId).stream().map(DocumentInfo::from).toList();
    }

    @Transactional(readOnly = true)
    public Content download(UUID claimId, UUID documentId, Actor actor) {
        claimService.loadVisible(claimId, actor);
        ClaimDocument doc = documents.findById(documentId).filter(d -> d.getClaimId().equals(claimId))
                .orElseThrow(() -> new ClaimNotFoundException(documentId));
        return new Content(doc, storage.load(claimId, doc.getStorageKey()));
    }

    private static byte[] readBytes(MultipartFile file) {
        try {
            return file.getBytes();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static boolean startsWith(byte[] content, byte[] prefix) {
        if (content.length < prefix.length) {
            return false;
        }
        for (int i = 0; i < prefix.length; i++) {
            if (content[i] != prefix[i]) {
                return false;
            }
        }
        return true;
    }

    /** Keeps only the last path segment and a safe character set; the name is metadata, never a path. */
    static String sanitise(String original) {
        String name = original == null ? "document" : original.replace('\\', '/');
        name = name.substring(name.lastIndexOf('/') + 1).replaceAll("[^A-Za-z0-9._ -]", "_").trim();
        if (name.isEmpty() || name.chars().allMatch(c -> c == '.')) {
            name = "document";
        }
        return name.length() > 150 ? name.substring(0, 150) : name;
    }
}
