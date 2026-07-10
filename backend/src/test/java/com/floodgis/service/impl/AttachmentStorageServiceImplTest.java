package com.floodgis.service.impl;

import com.floodgis.config.ApiException;
import com.floodgis.service.AttachmentStorageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.zip.CRC32;

import static org.junit.jupiter.api.Assertions.*;

class AttachmentStorageServiceImplTest {
    @TempDir
    Path uploadDirectory;

    private AttachmentStorageServiceImpl storage;

    @BeforeEach
    void setUp() {
        storage = new AttachmentStorageServiceImpl(
                uploadDirectory.toString(), 5 * 1024 * 1024L, 25_000_000L);
    }

    @Test
    void storesPngAndTruncatesOriginalNameToDatabaseLimit() throws Exception {
        String longName = "水".repeat(300) + ".png";
        MockMultipartFile image = new MockMultipartFile(
                "file", longName, "image/png", imageBytes("png"));

        AttachmentStorageService.StoredFile stored = storage.store(image);

        assertEquals(255, stored.originalName().codePointCount(0, stored.originalName().length()));
        assertEquals("image/png", stored.contentType());
        assertTrue(Files.isRegularFile(uploadDirectory.resolve(stored.relativePath())));
    }

    @Test
    void storesJpegAndCanDeleteItDuringRollback() throws Exception {
        MockMultipartFile image = new MockMultipartFile(
                "file", "report.jpg", "image/jpeg", imageBytes("jpeg"));

        AttachmentStorageService.StoredFile stored = storage.store(image);
        Path saved = uploadDirectory.resolve(stored.relativePath());
        assertTrue(Files.exists(saved));

        storage.delete(stored);

        assertFalse(Files.exists(saved));
    }

    @Test
    void rejectsWebpAndMismatchedDeclaredType() throws Exception {
        MockMultipartFile webp = new MockMultipartFile(
                "file", "report.webp", "image/webp", new byte[]{1, 2, 3});
        ApiException unsupported = assertThrows(ApiException.class, () -> storage.store(webp));
        assertEquals(400, unsupported.getStatus().value());

        MockMultipartFile mismatch = new MockMultipartFile(
                "file", "report.jpg", "image/jpeg", imageBytes("png"));
        ApiException mismatchError = assertThrows(ApiException.class, () -> storage.store(mismatch));
        assertTrue(mismatchError.getMessage().contains("Content-Type"));
    }

    @Test
    void rejectsDataUrlThatClaimsWebp() {
        String dataUrl = "data:image/webp;base64," + Base64.getEncoder().encodeToString(new byte[]{1});

        ApiException error = assertThrows(ApiException.class, () -> storage.storeDataUrl(dataUrl));

        assertTrue(error.getMessage().contains("PNG 或 JPEG"));
    }

    @Test
    void rejectsOversizedDimensionsFromHeaderWithoutDecodingPixels() throws Exception {
        byte[] png = imageBytes("png");
        ByteBuffer.wrap(png, 16, 4).putInt(10_000);
        ByteBuffer.wrap(png, 20, 4).putInt(10_000);
        CRC32 crc = new CRC32();
        crc.update(png, 12, 17);
        ByteBuffer.wrap(png, 29, 4).putInt((int) crc.getValue());
        MockMultipartFile bomb = new MockMultipartFile(
                "file", "large.png", "image/png", png);

        ApiException error = assertThrows(ApiException.class, () -> storage.store(bomb));

        assertTrue(error.getMessage().contains("像素总数"));
        try (var paths = Files.walk(uploadDirectory)) {
            assertEquals(0, paths.filter(Files::isRegularFile).count());
        }
    }

    private byte[] imageBytes(String format) throws Exception {
        BufferedImage image = new BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        assertTrue(ImageIO.write(image, format, output));
        return output.toByteArray();
    }
}
