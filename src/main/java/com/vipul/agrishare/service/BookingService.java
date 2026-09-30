package com.vipul.agrishare.service;

import com.vipul.agrishare.dto.BookedRange;
import com.vipul.agrishare.dto.BookingRequest;
import com.vipul.agrishare.dto.BookingResponse;
import com.vipul.agrishare.dto.PaymentVerifyRequest;
import com.vipul.agrishare.dto.UserSummary;
import com.vipul.agrishare.entity.Booking;
import com.vipul.agrishare.entity.Booking.PaymentMethod;
import com.vipul.agrishare.entity.Booking.PaymentStatus;
import com.vipul.agrishare.entity.Booking.Status;
import com.vipul.agrishare.entity.Equipment;
import com.vipul.agrishare.entity.Notification;
import com.vipul.agrishare.entity.User;
import com.vipul.agrishare.exception.ApiException;
import com.vipul.agrishare.payment.PaymentGateway;
import com.vipul.agrishare.repository.BookingRepository;
import com.vipul.agrishare.repository.EquipmentRepository;
import com.vipul.agrishare.repository.ReviewRepository;
import com.vipul.agrishare.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class BookingService {

    public static final int MAX_BOOKING_DAYS = 30;
    public static final int MAX_DAYS_AHEAD = 180;

    private final BookingRepository bookingRepository;
    private final EquipmentRepository equipmentRepository;
    private final UserRepository userRepository;
    private final PaymentGateway paymentGateway;
    private final NotificationService notificationService;
    private final ReviewRepository reviewRepository;
    private final Clock clock;

    @Transactional
    public BookingResponse create(Long renterId, BookingRequest request) {
        LocalDate today = LocalDate.now(clock);
        LocalDate start = request.startDate();
        LocalDate end = request.endDate();

        if (start.isBefore(today)) {
            throw ApiException.badRequest("error.booking.pastDate");
        }
        if (end.isBefore(start)) {
            throw ApiException.badRequest("error.booking.endBeforeStart");
        }
        int days = (int) ChronoUnit.DAYS.between(start, end) + 1;
        if (days > MAX_BOOKING_DAYS) {
            throw ApiException.badRequest("error.booking.tooLong", MAX_BOOKING_DAYS);
        }
        if (start.isAfter(today.plusDays(MAX_DAYS_AHEAD))) {
            throw ApiException.badRequest("error.booking.tooFarAhead", MAX_DAYS_AHEAD);
        }

        // Row lock on the machine: concurrent requests for it queue here, so the
        // overlap check below can't be passed by two bookings at once.
        Equipment equipment = equipmentRepository.findByIdForUpdate(request.equipmentId())
                .filter(Equipment::isActive)
                .orElseThrow(() -> ApiException.notFound("error.equipment.notFound"));
        if (equipment.getOwner().getId().equals(renterId)) {
            throw ApiException.badRequest("error.booking.ownEquipment");
        }
        if (!equipment.isAvailable()) {
            throw ApiException.conflict("error.booking.unavailable");
        }
        if (bookingRepository.existsOverlap(equipment.getId(), start, end, Booking.BLOCKING_STATUSES)) {
            throw ApiException.conflict("error.booking.overlap");
        }

        User renter = userRepository.findById(renterId)
                .orElseThrow(() -> ApiException.notFound("error.user.notFound"));

        Booking booking = Booking.builder()
                .equipment(equipment)
                .renter(renter)
                .startDate(start)
                .endDate(end)
                .days(days)
                .pricePerDay(equipment.getPricePerDay())
                .totalAmount(equipment.getPricePerDay().multiply(BigDecimal.valueOf(days)))
                .paymentMethod(request.paymentMethod())
                .note(StringUtils.hasText(request.note()) ? request.note().trim() : null)
                .build();

        if (request.paymentMethod() == PaymentMethod.CASH) {
            booking.setStatus(Status.REQUESTED);
            booking.setPaymentStatus(PaymentStatus.NOT_REQUIRED);
            booking.setRequestedAt(clock.instant());
            bookingRepository.save(booking);
            notificationService.notify(equipment.getOwner(), Notification.Type.BOOKING_REQUESTED, booking);
        } else {
            booking.setStatus(Status.AWAITING_PAYMENT);
            booking.setPaymentStatus(PaymentStatus.PENDING);
            bookingRepository.save(booking); // need the id for the gateway receipt
            PaymentGateway.GatewayOrder order =
                    paymentGateway.createOrder(toPaise(booking.getTotalAmount()), "booking_" + booking.getId());
            booking.setGatewayOrderId(order.orderId());
        }
        return toResponse(booking, renterId);
    }

    /** Renter finished checkout: money is now authorized (blocked, not taken) and the owner can decide. */
    @Transactional
    public BookingResponse verifyPayment(Long renterId, Long bookingId, PaymentVerifyRequest request) {
        Booking booking = load(bookingId);
        if (!booking.isRenter(renterId)) {
            throw ApiException.forbidden("error.booking.notRenter");
        }
        requireStatus(booking, Status.AWAITING_PAYMENT);
        if (!request.orderId().equals(booking.getGatewayOrderId())) {
            throw ApiException.badRequest("error.payment.orderMismatch");
        }
        if (!paymentGateway.verifySignature(request.orderId(), request.paymentId(), request.signature())) {
            throw ApiException.badRequest("error.payment.invalidSignature");
        }
        booking.setGatewayPaymentId(request.paymentId());
        booking.setPaymentStatus(PaymentStatus.AUTHORIZED);
        booking.setStatus(Status.REQUESTED);
        booking.setRequestedAt(clock.instant());
        notificationService.notify(booking.getEquipment().getOwner(), Notification.Type.BOOKING_REQUESTED, booking);
        return toResponse(booking, renterId);
    }

    @Transactional
    public BookingResponse approve(Long ownerId, Long bookingId) {
        Booking booking = loadAsOwner(ownerId, bookingId);
        requireStatus(booking, Status.REQUESTED);
        if (booking.getEndDate().isBefore(LocalDate.now(clock))) {
            throw ApiException.conflict("error.booking.invalidState");
        }
        if (booking.getPaymentMethod() == PaymentMethod.ONLINE) {
            // if capture fails the gateway throws and the transaction rolls back to REQUESTED
            paymentGateway.capture(booking.getGatewayPaymentId(), toPaise(booking.getTotalAmount()));
            booking.setPaymentStatus(PaymentStatus.CAPTURED);
        }
        booking.setStatus(Status.CONFIRMED);
        notificationService.notify(booking.getRenter(), Notification.Type.BOOKING_CONFIRMED, booking);
        return toResponse(booking, ownerId);
    }

    @Transactional
    public BookingResponse reject(Long ownerId, Long bookingId, String reason) {
        Booking booking = loadAsOwner(ownerId, bookingId);
        requireStatus(booking, Status.REQUESTED);
        booking.setStatus(Status.REJECTED);
        booking.setRejectReason(StringUtils.hasText(reason) ? reason.trim() : null);
        releaseAuthorization(booking);
        notificationService.notify(booking.getRenter(), Notification.Type.BOOKING_REJECTED, booking);
        return toResponse(booking, ownerId);
    }

    /**
     * Renter may withdraw anything not yet started; the owner may call off a
     * confirmed booking (e.g. machine broke down). Captured money is refunded.
     */
    @Transactional
    public BookingResponse cancel(Long userId, Long bookingId) {
        Booking booking = load(bookingId);
        boolean renter = booking.isRenter(userId);
        boolean owner = booking.isOwner(userId);
        if (!renter && !owner) {
            throw ApiException.forbidden("error.forbidden");
        }

        EnumSet<Status> cancellable = renter
                ? EnumSet.of(Status.AWAITING_PAYMENT, Status.REQUESTED, Status.CONFIRMED)
                : EnumSet.of(Status.CONFIRMED);
        if (!cancellable.contains(booking.getStatus())) {
            throw ApiException.conflict("error.booking.invalidState");
        }
        if (booking.getStatus() == Status.CONFIRMED && !LocalDate.now(clock).isBefore(booking.getStartDate())) {
            throw ApiException.conflict("error.booking.alreadyStarted");
        }

        if (booking.getPaymentStatus() == PaymentStatus.CAPTURED) {
            paymentGateway.refund(booking.getGatewayPaymentId(), toPaise(booking.getTotalAmount()));
            booking.setPaymentStatus(PaymentStatus.REFUNDED);
        } else {
            releaseAuthorization(booking);
        }
        Status before = booking.getStatus();
        booking.setStatus(Status.CANCELLED);
        if (owner) {
            notificationService.notify(booking.getRenter(), Notification.Type.BOOKING_CANCELLED, booking);
        } else if (before != Status.AWAITING_PAYMENT) { // owner never saw an unpaid request
            notificationService.notify(booking.getEquipment().getOwner(), Notification.Type.BOOKING_CANCELLED, booking);
        }
        return toResponse(booking, userId);
    }

    @Transactional
    public BookingResponse complete(Long ownerId, Long bookingId) {
        Booking booking = loadAsOwner(ownerId, bookingId);
        requireStatus(booking, Status.CONFIRMED);
        if (LocalDate.now(clock).isBefore(booking.getStartDate())) {
            throw ApiException.conflict("error.booking.notStarted");
        }
        booking.setStatus(Status.COMPLETED);
        notificationService.notify(booking.getRenter(), Notification.Type.BOOKING_COMPLETED, booking);
        return toResponse(booking, ownerId);
    }

    @Transactional(readOnly = true)
    public BookingResponse get(Long userId, Long bookingId) {
        Booking booking = load(bookingId);
        if (!booking.isRenter(userId) && !booking.isOwner(userId)) {
            throw ApiException.forbidden("error.forbidden");
        }
        return toResponse(booking, userId, reviewRepository.existsByBookingIdAndReviewerId(bookingId, userId));
    }

    @Transactional(readOnly = true)
    public List<BookingResponse> listMine(Long renterId) {
        return withReviewFlags(bookingRepository.findByRenterIdOrderByCreatedAtDesc(renterId), renterId);
    }

    @Transactional(readOnly = true)
    public List<BookingResponse> listIncoming(Long ownerId) {
        return withReviewFlags(bookingRepository.findByEquipmentOwnerIdOrderByCreatedAtDesc(ownerId), ownerId);
    }

    /** Taken date ranges from today on, for greying out the booking calendar. */
    @Transactional(readOnly = true)
    public List<BookedRange> bookedDates(Long equipmentId) {
        return bookingRepository.findBlocking(equipmentId, LocalDate.now(clock), Booking.BLOCKING_STATUSES).stream()
                .map(b -> new BookedRange(b.getStartDate(), b.getEndDate()))
                .toList();
    }

    /**
     * Called by the expiry job, one booking per transaction. Re-checks the status
     * because the renter/owner may have acted since the job listed it.
     */
    @Transactional
    public boolean expireIfStill(Long bookingId, Status expected) {
        Booking booking = bookingRepository.findById(bookingId).orElse(null);
        if (booking == null || booking.getStatus() != expected) {
            return false;
        }
        booking.setStatus(Status.EXPIRED);
        releaseAuthorization(booking);
        notificationService.notify(booking.getRenter(), Notification.Type.BOOKING_EXPIRED, booking);
        if (expected == Status.REQUESTED) {
            notificationService.notify(booking.getEquipment().getOwner(), Notification.Type.BOOKING_EXPIRED, booking);
        }
        return true;
    }

    /** Authorized-but-uncaptured money is returned by the gateway on its own; just record that. */
    static void releaseAuthorization(Booking booking) {
        if (booking.getPaymentStatus() == PaymentStatus.AUTHORIZED
                || booking.getPaymentStatus() == PaymentStatus.PENDING) {
            booking.setPaymentStatus(PaymentStatus.RELEASED);
        }
    }

    static long toPaise(BigDecimal rupees) {
        return rupees.movePointRight(2).longValueExact();
    }

    private Booking load(Long bookingId) {
        return bookingRepository.findDetailedById(bookingId)
                .orElseThrow(() -> ApiException.notFound("error.booking.notFound"));
    }

    private Booking loadAsOwner(Long ownerId, Long bookingId) {
        Booking booking = load(bookingId);
        if (!booking.isOwner(ownerId)) {
            throw ApiException.forbidden("error.booking.notOwner");
        }
        return booking;
    }

    private static void requireStatus(Booking booking, Status expected) {
        if (booking.getStatus() != expected) {
            throw ApiException.conflict("error.booking.invalidState");
        }
    }

    private List<BookingResponse> withReviewFlags(List<Booking> bookings, Long viewerId) {
        List<Long> completed = bookings.stream()
                .filter(b -> b.getStatus() == Status.COMPLETED)
                .map(Booking::getId)
                .toList();
        Set<Long> reviewed = completed.isEmpty()
                ? Set.of()
                : new HashSet<>(reviewRepository.findReviewedBookingIds(viewerId, completed));
        return bookings.stream()
                .map(b -> toResponse(b, viewerId, reviewed.contains(b.getId())))
                .toList();
    }

    BookingResponse toResponse(Booking b, Long viewerId) {
        return toResponse(b, viewerId, false); // right after an action: no review can exist yet
    }

    private BookingResponse toResponse(Booking b, Long viewerId, boolean reviewedByMe) {
        Equipment e = b.getEquipment();
        User owner = e.getOwner();
        boolean contactsVisible = b.getStatus() == Status.CONFIRMED || b.getStatus() == Status.COMPLETED;

        BookingResponse.Checkout checkout = null;
        if (b.getStatus() == Status.AWAITING_PAYMENT && b.isRenter(viewerId)) {
            checkout = new BookingResponse.Checkout(paymentGateway.mode(), paymentGateway.publicKeyId(),
                    b.getGatewayOrderId(), toPaise(b.getTotalAmount()), "INR");
        }

        return new BookingResponse(
                b.getId(),
                new BookingResponse.EquipmentBrief(e.getId(), e.getName(), e.getCategory().name(),
                        e.getImageUrl(), e.getAddress()),
                UserSummary.of(b.getRenter()),
                UserSummary.of(owner),
                b.getStartDate(),
                b.getEndDate(),
                b.getDays(),
                b.getPricePerDay(),
                b.getTotalAmount(),
                b.getStatus().name(),
                b.getPaymentMethod().name(),
                b.getPaymentStatus().name(),
                b.getNote(),
                b.getRejectReason(),
                contactsVisible ? b.getRenter().getPhone() : null,
                contactsVisible ? owner.getPhone() : null,
                checkout,
                reviewedByMe,
                b.getCreatedAt()
        );
    }
}
