package com.aiinpocket.btctrade.controller;

import com.aiinpocket.btctrade.model.entity.AppUser;
import com.aiinpocket.btctrade.model.entity.NotificationChannel;
import com.aiinpocket.btctrade.repository.AppUserRepository;
import com.aiinpocket.btctrade.security.AppUserPrincipal;
import com.aiinpocket.btctrade.service.NotificationChannelService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.List;
import java.util.Map;

/**
 * 使用者設定頁面 Controller。
 * 負責渲染通知管道設定頁面（settings.html），
 * 讓使用者可以管理 Discord / Gmail / Telegram 三種通知管道。
 */
@Controller
@RequiredArgsConstructor
@Slf4j
public class SettingsController {

    private final NotificationChannelService channelService;
    private final AppUserRepository userRepo;

    /** 頭像縮圖尺寸 */
    private static final int AVATAR_SIZE = 128;
    /** 最大上傳大小 (10 MB) */
    private static final long MAX_FILE_SIZE = 10 * 1024 * 1024;
    /** 原圖存證目錄 */
    private static final Path AVATAR_ARCHIVE_DIR = Path.of(System.getProperty("user.home"), "btctrade-uploads", "avatars");

    /**
     * 渲染設定頁面。
     * 從資料庫載入使用者的所有通知管道設定，傳遞到前端 Thymeleaf 模板。
     */
    @GetMapping("/settings")
    public String settings(
            @AuthenticationPrincipal AppUserPrincipal principal,
            Model model) {

        AppUser user = principal.getAppUser();
        log.debug("[設定頁面] 使用者 {} 載入設定頁面", user.getEmail());

        // 取得使用者已設定的通知管道
        List<NotificationChannel> channels = channelService.getChannels(user.getId());

        model.addAttribute("user", user);
        model.addAttribute("channels", channels);
        return "settings";
    }

    // ===== 隱私設定 API =====

    /** 取得隱私設定 */
    @GetMapping("/api/user/privacy")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> getPrivacy(
            @AuthenticationPrincipal AppUserPrincipal principal) {
        AppUser user = principal.getAppUser();
        return ResponseEntity.ok(Map.of(
                "hideProfileName", user.isHideProfileName(),
                "hideProfileAvatar", user.isHideProfileAvatar(),
                "hasCustomAvatar", user.getCustomAvatarData() != null
        ));
    }

    /** 更新隱私設定 */
    @PostMapping("/api/user/privacy")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> updatePrivacy(
            @AuthenticationPrincipal AppUserPrincipal principal,
            @RequestBody Map<String, Boolean> body) {
        AppUser user = principal.getAppUser();
        if (body.containsKey("hideProfileName")) {
            user.setHideProfileName(Boolean.TRUE.equals(body.get("hideProfileName")));
        }
        if (body.containsKey("hideProfileAvatar")) {
            user.setHideProfileAvatar(Boolean.TRUE.equals(body.get("hideProfileAvatar")));
        }
        userRepo.save(user);
        log.info("[隱私] 用戶 {} 更新隱私設定: name={}, avatar={}",
                user.getId(), user.isHideProfileName(), user.isHideProfileAvatar());
        return ResponseEntity.ok(Map.of("success", true));
    }

    // ===== 頭像上傳 API =====

    /** 上傳自訂頭像 */
    @PostMapping("/api/user/avatar/upload")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> uploadAvatar(
            @AuthenticationPrincipal AppUserPrincipal principal,
            @RequestParam("file") MultipartFile file) {
        if (file.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "error", "未選擇檔案"));
        }
        if (file.getSize() > MAX_FILE_SIZE) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "error", "檔案大小超過 10MB"));
        }
        String contentType = file.getContentType();
        if (contentType == null || !contentType.startsWith("image/")) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "error", "僅支援圖片檔案"));
        }

        try {
            AppUser user = principal.getAppUser();

            // 1. 讀取原圖
            BufferedImage original = ImageIO.read(file.getInputStream());
            if (original == null) {
                return ResponseEntity.badRequest().body(Map.of("success", false, "error", "無法解析圖片"));
            }

            // 2. 縮圖（128x128，置中裁切）
            BufferedImage thumbnail = createThumbnail(original, AVATAR_SIZE);

            // 3. 轉 base64 data URL
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ImageIO.write(thumbnail, "png", baos);
            String base64 = Base64.getEncoder().encodeToString(baos.toByteArray());
            String dataUrl = "data:image/png;base64," + base64;

            // 4. 存入 DB
            user.setCustomAvatarData(dataUrl);
            userRepo.save(user);

            // 5. 原圖存證（最佳努力，不影響主流程）
            archiveOriginal(user.getId(), file);

            log.info("[頭像] 用戶 {} 上傳自訂頭像 ({} bytes → {} bytes thumbnail)",
                    user.getId(), file.getSize(), baos.size());
            return ResponseEntity.ok(Map.of("success", true, "avatarUrl", dataUrl));
        } catch (IOException e) {
            log.error("[頭像] 處理上傳失敗", e);
            return ResponseEntity.internalServerError().body(Map.of("success", false, "error", "圖片處理失敗"));
        }
    }

    /** 刪除自訂頭像（恢復 Google 頭像） */
    @DeleteMapping("/api/user/avatar")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> deleteAvatar(
            @AuthenticationPrincipal AppUserPrincipal principal) {
        AppUser user = principal.getAppUser();
        user.setCustomAvatarData(null);
        userRepo.save(user);
        log.info("[頭像] 用戶 {} 刪除自訂頭像", user.getId());
        return ResponseEntity.ok(Map.of("success", true));
    }

    /**
     * 建立正方形縮圖（置中裁切 + 縮放）。
     */
    private BufferedImage createThumbnail(BufferedImage src, int size) {
        int srcW = src.getWidth();
        int srcH = src.getHeight();

        // 置中裁切為正方形
        int cropSize = Math.min(srcW, srcH);
        int x = (srcW - cropSize) / 2;
        int y = (srcH - cropSize) / 2;
        BufferedImage cropped = src.getSubimage(x, y, cropSize, cropSize);

        // 縮放
        BufferedImage thumbnail = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = thumbnail.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.drawImage(cropped, 0, 0, size, size, null);
        g.dispose();
        return thumbnail;
    }

    /**
     * 存證原圖（僅用於 debug，不對外開放任何接口）。
     */
    private void archiveOriginal(Long userId, MultipartFile file) {
        try {
            Files.createDirectories(AVATAR_ARCHIVE_DIR);
            String ext = "png";
            String originalName = file.getOriginalFilename();
            if (originalName != null && originalName.contains(".")) {
                ext = originalName.substring(originalName.lastIndexOf('.') + 1).toLowerCase();
            }
            Path dest = AVATAR_ARCHIVE_DIR.resolve(userId + "_" + System.currentTimeMillis() + "." + ext);
            file.transferTo(dest.toFile());
            log.info("[頭像] 原圖存證: {}", dest);
        } catch (Exception e) {
            log.warn("[頭像] 原圖存證失敗（不影響功能）: {}", e.getMessage());
        }
    }
}
