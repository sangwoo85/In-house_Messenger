package com.company.messenger.domain.profile;

import com.company.messenger.domain.file.FileProperties;
import com.company.messenger.domain.user.User;
import com.company.messenger.domain.user.UserProfileResponse;
import com.company.messenger.domain.user.UserRepository;
import com.company.messenger.global.exception.BusinessException;
import com.company.messenger.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.UUID;

/** Decodes and normalizes profile images; names, URLs and MIME types supplied by clients are never trusted. */
@Service
@RequiredArgsConstructor
public class ProfileImageService {
    private static final Logger log = LoggerFactory.getLogger(ProfileImageService.class);
    private final UserRepository users;
    private final FileProperties files;
    private final ProfileImageProperties limits;

    /** Replaces only the signed-in user's image and removes old bytes after the database commit succeeds. */
    @Transactional
    public UserProfileResponse upload(String userId, MultipartFile file) {
        User user = lockedUser(userId);
        BufferedImage image = decode(file);
        String imageKey = UUID.randomUUID().toString();
        Path destination = imagePath(imageKey);
        try {
            Files.createDirectories(destination.getParent());
            if (!ImageIO.write(image, "png", destination.toFile())) throw new IOException("PNG encoder unavailable");
        } catch (IOException exception) {
            removeFile(destination);
            throw new BusinessException(ErrorCode.FILE_STORAGE_FAILED);
        }
        String previousKey = user.getProfileImageKey();
        user.setProfileImageKey(imageKey);
        cleanupAfterTransaction(destination, previousKey);
        return UserProfileResponse.from(user);
    }

    /** Restores the generated avatar for the signed-in user only. */
    @Transactional
    public UserProfileResponse delete(String userId) {
        User user = lockedUser(userId);
        String previousKey = user.getProfileImageKey();
        user.setProfileImageKey(null);
        cleanupAfterTransaction(null, previousKey);
        return UserProfileResponse.from(user);
    }

    /** Serves a normalized image to authenticated coworkers; authentication is enforced by Spring Security. */
    @Transactional(readOnly = true)
    public ResponseEntity<Resource> download(String userId) {
        User user = users.findByUserId(userId).orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        if (user.getProfileImageKey() == null) throw new BusinessException(ErrorCode.FILE_NOT_FOUND);
        Resource image = new FileSystemResource(imagePath(user.getProfileImageKey()));
        if (!image.exists()) throw new BusinessException(ErrorCode.FILE_NOT_FOUND);
        return ResponseEntity.ok().contentType(MediaType.IMAGE_PNG).cacheControl(CacheControl.noStore())
                .header("X-Content-Type-Options", "nosniff").body(image);
    }

    private User lockedUser(String userId) {
        return users.findByUserIdForUpdate(userId).orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
    }

    private BufferedImage decode(MultipartFile file) {
        if (file.isEmpty()) throw new BusinessException(ErrorCode.FILE_EMPTY);
        if (file.getSize() > limits.imageMaxBytes()) throw new BusinessException(ErrorCode.FILE_TOO_LARGE);
        try (var raw = file.getInputStream()) {
            // Bound bytes independently of client metadata before the image decoder sees the upload.
            byte[] bytes = raw.readNBytes(Math.toIntExact(limits.imageMaxBytes() + 1));
            if (bytes.length > limits.imageMaxBytes()) throw new BusinessException(ErrorCode.FILE_TOO_LARGE);
            try (ImageInputStream input = ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
                var readers = ImageIO.getImageReaders(input);
                if (!readers.hasNext()) throw invalidImage();
                ImageReader reader = readers.next();
                try {
                    String format = reader.getFormatName().toLowerCase(Locale.ROOT);
                    if (!format.equals("png") && !format.equals("jpeg")) throw invalidImage();
                    reader.setInput(input, true, true);
                    if (reader.getWidth(0) > limits.imageMaxDimension() || reader.getHeight(0) > limits.imageMaxDimension()) {
                        throw invalidImage();
                    }
                    BufferedImage image = reader.read(0);
                    if (image == null) throw invalidImage();
                    return image;
                } finally {
                    reader.dispose();
                }
            }
        } catch (IOException | IllegalArgumentException exception) {
            throw invalidImage();
        }
    }

    private BusinessException invalidImage() {
        return new BusinessException(ErrorCode.INVALID_PROFILE_IMAGE);
    }

    private Path imagePath(String imageKey) {
        // UUID validation also keeps a corrupted database value from escaping the profile directory.
        return files.storageDirectory().resolve("profiles").resolve(UUID.fromString(imageKey) + ".png");
    }

    private void cleanupAfterTransaction(Path newFile, String previousKey) {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status == STATUS_COMMITTED && previousKey != null) removeFile(imagePath(previousKey));
                if (status != STATUS_COMMITTED && newFile != null) removeFile(newFile);
            }
        });
    }

    private void removeFile(Path path) {
        try {
            Files.deleteIfExists(path);
        } catch (IOException exception) {
            log.warn("Could not remove an unused profile image: {}", path.getFileName());
        }
    }
}
