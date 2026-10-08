package com.chubb.claims.storage;

import java.util.UUID;

/** Where document bytes live. The local-filesystem implementation can be swapped for S3/Blob storage. */
public interface DocumentStorage {

    void store(UUID claimId, String key, byte[] content);

    byte[] load(UUID claimId, String key);

    void delete(UUID claimId, String key);
}
