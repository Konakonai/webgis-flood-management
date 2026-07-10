package com.floodgis.service;

import org.springframework.web.multipart.MultipartFile;

public interface AttachmentStorageService {
    StoredFile store(MultipartFile file);
    StoredFile storeDataUrl(String dataUrl);
    void delete(StoredFile storedFile);

    record StoredFile(String originalName, String storedName, String contentType,
                      long fileSize, String relativePath) {
    }
}
