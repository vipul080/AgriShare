package com.vipul.agrishare.service;

import com.vipul.agrishare.exception.ApiException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Map;
import java.util.UUID;

/**
 * Stores equipment photos on local disk and serves them under /uploads/**.
 * Swap for S3/GCS/Cloudinary in production by re-implementing store()/delete().
 */
@Slf4j
@Service
public class FileStorageService {

    private static final Map<String, String> ALLOWED_TYPES = Map.of(
            "image/jpeg", ".jpg",
            "image/png", ".png",
            "image/webp", ".webp"
    );

    private final Path root;

    public FileStorageService(@Value("${app.upload-dir}") String uploadDir) throws IOException {
        this.root = Path.of(uploadDir).toAbsolutePath().normalize();
        Files.createDirectories(root);
    }

    public Path getRoot() {
        return root;
    }

    /** @return public URL path of the stored file, e.g. /uploads/abc.jpg */
    public String storeImage(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw ApiException.badRequest("error.image.empty");
        }
        String extension = ALLOWED_TYPES.get(file.getContentType());
        if (extension == null) {
            throw ApiException.badRequest("error.image.type");
        }
        String filename = UUID.randomUUID() + extension;
        Path target = root.resolve(filename).normalize();
        try (InputStream in = file.getInputStream()) {
            Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new IllegalStateException("Could not store image", e);
        }
        return "/uploads/" + filename;
    }

    public void deleteQuietly(String publicUrl) {
        if (publicUrl == null || !publicUrl.startsWith("/uploads/")) {
            return;
        }
        Path target = root.resolve(publicUrl.substring("/uploads/".length())).normalize();
        if (!target.startsWith(root)) {
            return; // never delete outside the upload dir
        }
        try {
            Files.deleteIfExists(target);
        } catch (IOException e) {
            log.warn("Could not delete old image {}", target, e);
        }
    }
}
