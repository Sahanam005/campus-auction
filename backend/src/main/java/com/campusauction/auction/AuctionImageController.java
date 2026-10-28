package com.campusauction.auction;

import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;

@RestController
public class AuctionImageController {
    private final AuctionImageStorage imageStorage;

    public AuctionImageController(AuctionImageStorage imageStorage) {
        this.imageStorage = imageStorage;
    }

    @GetMapping("/api/images/{imageKey:.+}")
    public ResponseEntity<Resource> getImage(@PathVariable String imageKey) {
        Resource image = imageStorage.load(imageKey);
        if (image == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Image not found");
        }
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(imageStorage.mediaType(imageKey)))
                .cacheControl(CacheControl.maxAge(Duration.ofDays(30)).cachePublic())
                .body(image);
    }
}
