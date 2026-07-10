package com.floodgis.service.impl;

import com.floodgis.config.ApiException;
import com.floodgis.service.AttachmentStorageService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDate;
import java.util.Base64;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@Slf4j
public class AttachmentStorageServiceImpl implements AttachmentStorageService {
    private static final Pattern DATA_URL = Pattern.compile(
            "^data:(image/(?:png|jpeg));base64,([A-Za-z0-9+/=\\r\\n]+)$",
            Pattern.CASE_INSENSITIVE);
    private static final Map<String, String> EXTENSIONS = Map.of(
            "image/png", ".png",
            "image/jpeg", ".jpg");
    private static final int MAX_ORIGINAL_NAME_CODE_POINTS = 255;

    private final Path baseDirectory;
    private final long maxImageBytes;
    private final long maxImagePixels;

    public AttachmentStorageServiceImpl(@Value("${app.upload.directory}") String directory,
                                        @Value("${app.upload.max-image-bytes}") long maxImageBytes,
                                        @Value("${app.upload.max-image-pixels}") long maxImagePixels) {
        this.baseDirectory = Path.of(directory).toAbsolutePath().normalize();
        this.maxImageBytes = maxImageBytes;
        this.maxImagePixels = maxImagePixels;
    }

    @Override
    public StoredFile store(MultipartFile file) {
        if (file == null || file.isEmpty()) throw ApiException.badRequest("图片不能为空");
        String contentType = normalizeContentType(file.getContentType());
        try {
            return storeBytes(safeName(file.getOriginalFilename()), contentType, file.getBytes());
        } catch (IOException e) {
            throw new IllegalStateException("图片读取失败", e);
        }
    }

    @Override
    public StoredFile storeDataUrl(String dataUrl) {
        Matcher matcher = DATA_URL.matcher(dataUrl == null ? "" : dataUrl.trim());
        if (!matcher.matches()) throw ApiException.badRequest("图片必须是 PNG 或 JPEG Data URL");
        String contentType = normalizeContentType(matcher.group(1));
        try {
            byte[] bytes = Base64.getMimeDecoder().decode(matcher.group(2));
            return storeBytes("mobile-report" + EXTENSIONS.get(contentType), contentType, bytes);
        } catch (IllegalArgumentException e) {
            throw ApiException.badRequest("图片 Base64 数据无效");
        }
    }

    @Override
    public void delete(StoredFile storedFile) {
        if (storedFile == null || storedFile.relativePath() == null) return;
        Path destination = baseDirectory.resolve(storedFile.relativePath()).normalize();
        if (!destination.startsWith(baseDirectory) || destination.equals(baseDirectory)) {
            log.warn("拒绝删除上传目录外的文件: {}", storedFile.relativePath());
            return;
        }
        try {
            Files.deleteIfExists(destination);
        } catch (IOException error) {
            // Cleanup is best-effort and must not hide the original database error.
            log.warn("回滚时删除上传文件失败: {}", storedFile.relativePath(), error);
        }
    }

    private StoredFile storeBytes(String originalName, String contentType, byte[] bytes) {
        if (bytes.length == 0 || bytes.length > maxImageBytes) {
            throw ApiException.badRequest("图片大小必须在 1 字节到 " + maxImageBytes + " 字节之间");
        }
        try {
            ImageMetadata metadata = inspectImage(bytes);
            if (!contentType.equals(metadata.contentType())) {
                throw ApiException.badRequest("图片实际格式与 Content-Type 不一致");
            }
            LocalDate today = LocalDate.now();
            String relativeDirectory = "%04d/%02d".formatted(today.getYear(), today.getMonthValue());
            Path directory = baseDirectory.resolve(relativeDirectory).normalize();
            if (!directory.startsWith(baseDirectory)) throw ApiException.badRequest("无效上传路径");
            Files.createDirectories(directory);

            String storedName = UUID.randomUUID() + EXTENSIONS.get(contentType);
            Path destination = directory.resolve(storedName).normalize();
            Files.write(destination, bytes, StandardOpenOption.CREATE_NEW);
            String relativePath = relativeDirectory + "/" + storedName;
            return new StoredFile(originalName, storedName, contentType, bytes.length, relativePath);
        } catch (IOException e) {
            throw new IllegalStateException("图片保存失败", e);
        }
    }

    private ImageMetadata inspectImage(byte[] bytes) throws IOException {
        try (ImageInputStream input = ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
            if (input == null) throw ApiException.badRequest("上传内容不是有效图片");
            Iterator<ImageReader> readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) throw ApiException.badRequest("上传内容不是有效 PNG/JPEG 图片");

            ImageReader reader = readers.next();
            try {
                reader.setInput(input, true, true);
                String format = reader.getFormatName().toLowerCase(Locale.ROOT);
                String detectedContentType = switch (format) {
                    case "png" -> "image/png";
                    case "jpeg", "jpg" -> "image/jpeg";
                    default -> throw ApiException.badRequest("仅支持 PNG 和 JPEG 图片");
                };
                int width = reader.getWidth(0);
                int height = reader.getHeight(0);
                long pixels = (long) width * height;
                if (width <= 0 || height <= 0 || pixels > maxImagePixels) {
                    throw ApiException.badRequest("图片像素总数不得超过 " + maxImagePixels);
                }
                return new ImageMetadata(detectedContentType, width, height);
            } finally {
                reader.dispose();
            }
        }
    }

    private String normalizeContentType(String contentType) {
        String normalized = contentType == null ? "" : contentType.toLowerCase();
        if (!EXTENSIONS.containsKey(normalized)) {
            throw ApiException.badRequest("仅支持 PNG 和 JPEG 图片");
        }
        return normalized;
    }

    private String safeName(String name) {
        if (name == null || name.isBlank()) return "upload";
        String normalized = name.replace('\\', '/');
        String baseName = normalized.substring(normalized.lastIndexOf('/') + 1)
                .replaceAll("[\\r\\n\\x00]", "_");
        if (baseName.isBlank()) baseName = "upload";
        int[] codePoints = baseName.codePoints().limit(MAX_ORIGINAL_NAME_CODE_POINTS).toArray();
        return new String(codePoints, 0, codePoints.length);
    }

    private record ImageMetadata(String contentType, int width, int height) {
    }
}
