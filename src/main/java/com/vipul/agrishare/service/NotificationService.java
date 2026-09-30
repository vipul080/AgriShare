package com.vipul.agrishare.service;

import com.vipul.agrishare.dto.NotificationResponse;
import com.vipul.agrishare.entity.Booking;
import com.vipul.agrishare.entity.Notification;
import com.vipul.agrishare.entity.User;
import com.vipul.agrishare.exception.ApiException;
import com.vipul.agrishare.push.PushSender;
import com.vipul.agrishare.repository.NotificationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.util.StringUtils;

import java.time.Clock;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationService {

    private static final int MAX_LIST = 50;

    private final NotificationRepository notificationRepository;
    private final NotificationText notificationText;
    private final PushSender pushSender;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    /** Stored now; pushed to the phone only once the surrounding transaction commits. */
    @Transactional
    public void notify(User recipient, Notification.Type type, Booking booking) {
        Map<String, String> params = new HashMap<>();
        params.put("equipment", booking.getEquipment().getName());
        params.put("renter", booking.getRenter().getName());
        params.put("owner", booking.getEquipment().getOwner().getName());
        params.put("start", booking.getStartDate().toString());
        params.put("end", booking.getEndDate().toString());

        Notification notification = notificationRepository.save(Notification.builder()
                .user(recipient)
                .type(type)
                .params(params)
                .bookingId(booking.getId())
                .build());

        if (StringUtils.hasText(recipient.getFcmToken())) {
            events.publishEvent(new PushRequested(recipient.getFcmToken(), recipient.getPreferredLanguage(),
                    type.name(), params, notification.getId(), booking.getId()));
        }
    }

    /** Never push for a booking change that ended up rolled back. */
    @TransactionalEventListener(fallbackExecution = true)
    public void onPushRequested(PushRequested push) {
        try {
            Locale locale = Locale.forLanguageTag(push.language());
            Map<String, String> data = new HashMap<>();
            data.put("type", push.type());
            data.put("notificationId", String.valueOf(push.notificationId()));
            data.put("bookingId", String.valueOf(push.bookingId()));
            pushSender.send(push.token(),
                    notificationText.title(push.type(), push.params(), locale),
                    notificationText.body(push.type(), push.params(), locale),
                    data);
        } catch (RuntimeException e) {
            log.warn("Push delivery failed for notification {}", push.notificationId(), e); // in-app copy still exists
        }
    }

    @Transactional(readOnly = true)
    public List<NotificationResponse> list(Long userId) {
        Locale locale = LocaleContextHolder.getLocale();
        return notificationRepository.findByUserIdOrderByCreatedAtDesc(userId, PageRequest.of(0, MAX_LIST)).stream()
                .map(n -> toResponse(n, locale))
                .toList();
    }

    @Transactional(readOnly = true)
    public long unreadCount(Long userId) {
        return notificationRepository.countByUserIdAndReadAtIsNull(userId);
    }

    @Transactional
    public void markRead(Long userId, Long notificationId) {
        Notification notification = notificationRepository.findByIdAndUserId(notificationId, userId)
                .orElseThrow(() -> ApiException.notFound("error.notification.notFound"));
        if (notification.getReadAt() == null) {
            notification.setReadAt(clock.instant());
        }
    }

    @Transactional
    public void markAllRead(Long userId) {
        notificationRepository.markAllRead(userId, clock.instant());
    }

    private NotificationResponse toResponse(Notification n, Locale locale) {
        String type = n.getType().name();
        return new NotificationResponse(
                n.getId(),
                type,
                n.getParams(),
                notificationText.title(type, n.getParams(), locale),
                notificationText.body(type, n.getParams(), locale),
                n.getBookingId(),
                n.getReadAt() != null,
                n.getCreatedAt()
        );
    }

    public record PushRequested(String token, String language, String type, Map<String, String> params,
                                Long notificationId, Long bookingId) {}
}
