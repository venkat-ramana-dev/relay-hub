package dev.venkat.relayhub.service;

import dev.venkat.relayhub.dto.response.NotificationResponse;
import dev.venkat.relayhub.dto.response.SystemMetricsResponse;
import dev.venkat.relayhub.enums.NotificationStatus;
import dev.venkat.relayhub.mapper.NotificationMapper;
import dev.venkat.relayhub.repository.NotificationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class AdminService {

    private final NotificationRepository notificationRepository;
    private final NotificationMapper notificationMapper;

    @Transactional(readOnly = true)
    public SystemMetricsResponse getSystemMetrics() {
        long pending = notificationRepository.countByStatus(NotificationStatus.PENDING);
        long retrying = notificationRepository.countByStatus(NotificationStatus.RETRYING);
        long success = notificationRepository.countByStatus(NotificationStatus.SUCCESS);
        long dead = notificationRepository.countByStatus(NotificationStatus.DEAD);

        return new SystemMetricsResponse(pending, retrying, success, dead);
    }

    @Transactional(readOnly = true)
    public Page<NotificationResponse> getAllSystemNotifications(Pageable pageable) {
        return notificationRepository.findAll(pageable)
                .map(notificationMapper::mapToResponse);
    }
}