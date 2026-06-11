package com.archive.service;

import com.archive.entity.Notification;
import com.archive.entity.Notification.NotificationType;
import com.archive.repository.NotificationRepository;
import com.archive.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.NoSuchElementException;

/**
 * 通知中心服务 (RI-63).
 */
@Service
@RequiredArgsConstructor
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;

    public Page<Notification> list(Boolean unread, Pageable pageable) {
        Long userId = currentUserId();
        if (Boolean.TRUE.equals(unread)) {
            return notificationRepository.findByUserIdAndReadOrderByCreatedAtDesc(userId, false, pageable);
        }
        return notificationRepository.findByUserIdOrderByCreatedAtDesc(userId, pageable);
    }

    @Transactional
    public void markRead(Long id) {
        Notification n = notificationRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("通知不存在: id=" + id));
        n.setRead(true);
        notificationRepository.save(n);
    }

    @Transactional
    public void markAllRead() {
        Long userId = currentUserId();
        notificationRepository.findByUserIdAndReadOrderByCreatedAtDesc(userId, false, Pageable.unpaged())
                .forEach(n -> {
                    n.setRead(true);
                    notificationRepository.save(n);
                });
    }

    @Transactional
    public void notifyAdmin(String message) {
        userRepository.findAll().stream()
                .filter(u -> u.getDeletedAt() == null)
                .forEach(u -> {
                    Notification n = Notification.builder()
                            .userId(u.getId())
                            .type(NotificationType.SYSTEM)
                            .title("系统通知")
                            .content(message)
                            .read(false)
                            .build();
                    notificationRepository.save(n);
                });
    }

    @Transactional
    public Notification create(Long userId, NotificationType type, String title, String content, String link) {
        Notification n = Notification.builder()
                .userId(userId)
                .type(type)
                .title(title)
                .content(content)
                .link(link)
                .read(false)
                .build();
        return notificationRepository.save(n);
    }

    public long countUnread() {
        return notificationRepository.countByUserIdAndRead(currentUserId(), false);
    }

    private Long currentUserId() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof com.archive.security.JwtAuthFilter.AuthenticatedUser u) {
            return u.id();
        }
        return 1L;
    }
}
