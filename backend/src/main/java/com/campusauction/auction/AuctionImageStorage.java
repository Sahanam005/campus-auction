package com.campusauction.auction;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Iterator;
import java.util.Locale;
import java.util.UUID;

@Component
public class AuctionImageStorage {
    public static final long MAX_IMAGE_SIZE_BYTES = 5 * 1024 * 1024;
    private static final long MAX_IMAGE_PIXELS = 20_000_000;

    private final Path storageDirectory;

    public AuctionImageStorage(
            @Value("${campus-auction.image-storage-dir:${user.home}/.campus-auction/images}")
            String storageDirectory) {
        this.storageDirectory = Path.of(storageDirectory).toAbsolutePath().normalize();
    }

    public String store(MultipartFile image) throws IOException {
        if (image.isEmpty() || image.getSize() > MAX_IMAGE_SIZE_BYTES) {
            throw new IllegalArgumentException("Image must be between 1 byte and 5 MB");
        }

        byte[] content = image.getBytes();
        String extension = validateImage(content);
        String imageKey = UUID.randomUUID() + "." + extension;
        Files.createDirectories(storageDirectory);
        Path destination = resolve(imageKey);
        try {
            Files.write(destination, content, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
        } catch (IOException exception) {
            Files.deleteIfExists(destination);
            throw exception;
        }
        return imageKey;
    }

    public Resource load(String imageKey) {
        if (imageKey == null || !imageKey.matches(
                "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\\.(png|jpg)")) {
            return null;
        }
        Path imagePath = resolve(imageKey);
        return Files.isRegularFile(imagePath) ? new FileSystemResource(imagePath) : null;
    }

    public String mediaType(String imageKey) {
        return imageKey.endsWith(".png") ? "image/png" : "image/jpeg";
    }

    public void delete(String imageKey) throws IOException {
        Files.deleteIfExists(resolve(imageKey));
    }

    private Path resolve(String imageKey) {
        Path imagePath = storageDirectory.resolve(imageKey).normalize();
        if (!imagePath.startsWith(storageDirectory)) {
            throw new IllegalArgumentException("Invalid image key");
        }
        return imagePath;
    }

    private String validateImage(byte[] content) throws IOException {
        try (ImageInputStream input = ImageIO.createImageInputStream(
                new ByteArrayInputStream(content))) {
            if (input == null) {
                throw new IllegalArgumentException("Upload a valid JPEG or PNG image");
            }
            Iterator<ImageReader> readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) {
                throw new IllegalArgumentException("Upload a valid JPEG or PNG image");
            }

            ImageReader reader = readers.next();
            try {
                reader.setInput(input, true, true);
                String format = reader.getFormatName().toLowerCase(Locale.ROOT);
                if (!format.equals("png") && !format.equals("jpeg")) {
                    throw new IllegalArgumentException("Only JPEG and PNG images are supported");
                }
                int width = reader.getWidth(0);
                int height = reader.getHeight(0);
                if (width <= 0 || height <= 0 || (long) width * height > MAX_IMAGE_PIXELS) {
                    throw new IllegalArgumentException("Image dimensions exceed the supported limit");
                }
                if (reader.read(0) == null) {
                    throw new IllegalArgumentException("Upload a valid JPEG or PNG image");
                }
                return format.equals("png") ? "png" : "jpg";
            } catch (IOException exception) {
                throw new IllegalArgumentException("Upload a valid JPEG or PNG image", exception);
            } finally {
                reader.dispose();
            }
        }
    }
}
