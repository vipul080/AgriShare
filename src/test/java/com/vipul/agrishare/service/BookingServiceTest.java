package com.vipul.agrishare.service;

import com.vipul.agrishare.config.AppConfig;
import com.vipul.agrishare.dto.BookingRequest;
import com.vipul.agrishare.dto.BookingResponse;
import com.vipul.agrishare.dto.PaymentVerifyRequest;
import com.vipul.agrishare.entity.Booking;
import com.vipul.agrishare.entity.Booking.PaymentMethod;
import com.vipul.agrishare.entity.Booking.PaymentStatus;
import com.vipul.agrishare.entity.Booking.Status;
import com.vipul.agrishare.entity.Equipment;
import com.vipul.agrishare.entity.User;
import com.vipul.agrishare.exception.ApiException;
import com.vipul.agrishare.payment.PaymentGateway;
import com.vipul.agrishare.repository.BookingRepository;
import com.vipul.agrishare.repository.EquipmentRepository;
import com.vipul.agrishare.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class BookingServiceTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 10, 1);
    private static final Clock CLOCK = Clock.fixed(
            ZonedDateTime.of(TODAY.atTime(10, 0), AppConfig.ZONE).toInstant(), AppConfig.ZONE);

    @Mock private BookingRepository bookingRepository;
    @Mock private EquipmentRepository equipmentRepository;
    @Mock private UserRepository userRepository;
    @Mock private PaymentGateway paymentGateway;

    private BookingService service;
    private User owner;
    private User renter;
    private Equipment tractor;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        service = new BookingService(bookingRepository, equipmentRepository, userRepository, paymentGateway, CLOCK);

        owner = User.builder().id(1L).name("Gurpreet").phone("9876500001").build();
        renter = User.builder().id(2L).name("Ramesh").phone("9876500002").build();
        tractor = Equipment.builder().id(10L).owner(owner).name("Mahindra 575")
                .category(Equipment.Category.TRACTOR).pricePerDay(new BigDecimal("1500.00"))
                .latitude(30.4).longitude(77.6).build();

        when(equipmentRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(tractor));
        when(userRepository.findById(2L)).thenReturn(Optional.of(renter));
        when(bookingRepository.save(any(Booking.class))).thenAnswer(inv -> {
            Booking b = inv.getArgument(0);
            b.setId(100L);
            return b;
        });
        when(paymentGateway.mode()).thenReturn("mock");
    }

    private BookingRequest request(LocalDate start, LocalDate end, PaymentMethod method) {
        return new BookingRequest(10L, start, end, method, null);
    }

    @Test
    void cashBooking_goesStraightToRequested_withTotalForInclusiveDays() {
        BookingResponse response = service.create(2L, request(TODAY.plusDays(2), TODAY.plusDays(4), PaymentMethod.CASH));

        assertThat(response.status()).isEqualTo("REQUESTED");
        assertThat(response.paymentStatus()).isEqualTo("NOT_REQUIRED");
        assertThat(response.days()).isEqualTo(3);
        assertThat(response.totalAmount()).isEqualByComparingTo("4500.00");
        assertThat(response.checkout()).isNull();
        verify(paymentGateway, never()).createOrder(anyLong(), anyString());
    }

    @Test
    void onlineBooking_createsGatewayOrderInPaise_andReturnsCheckout() {
        when(paymentGateway.createOrder(anyLong(), anyString()))
                .thenReturn(new PaymentGateway.GatewayOrder("order_1", 150000, "INR"));

        BookingResponse response = service.create(2L, request(TODAY, TODAY, PaymentMethod.ONLINE));

        verify(paymentGateway).createOrder(150000L, "booking_100");
        assertThat(response.status()).isEqualTo("AWAITING_PAYMENT");
        assertThat(response.checkout().orderId()).isEqualTo("order_1");
        assertThat(response.checkout().amountPaise()).isEqualTo(150000L);
    }

    @Test
    void create_rejectsOverlap() {
        when(bookingRepository.existsOverlap(eq(10L), any(), any(), eq(Booking.BLOCKING_STATUSES))).thenReturn(true);

        assertThatThrownBy(() -> service.create(2L, request(TODAY, TODAY.plusDays(1), PaymentMethod.CASH)))
                .isInstanceOf(ApiException.class).hasMessage("error.booking.overlap");
        verify(bookingRepository, never()).save(any());
    }

    @Test
    void create_rejectsPastDates_ownEquipment_andTooLong() {
        assertThatThrownBy(() -> service.create(2L, request(TODAY.minusDays(1), TODAY, PaymentMethod.CASH)))
                .hasMessage("error.booking.pastDate");
        assertThatThrownBy(() -> service.create(1L, request(TODAY, TODAY, PaymentMethod.CASH)))
                .hasMessage("error.booking.ownEquipment");
        assertThatThrownBy(() -> service.create(2L, request(TODAY, TODAY.plusDays(30), PaymentMethod.CASH)))
                .hasMessage("error.booking.tooLong");
    }

    @Test
    void create_locksEquipmentRowBeforeCheckingOverlap() {
        service.create(2L, request(TODAY, TODAY, PaymentMethod.CASH));

        var order = inOrder(equipmentRepository, bookingRepository);
        order.verify(equipmentRepository).findByIdForUpdate(10L);
        order.verify(bookingRepository).existsOverlap(any(), any(), any(), any());
    }

    private Booking existing(Status status, PaymentMethod method, PaymentStatus paymentStatus) {
        Booking b = Booking.builder().id(100L).equipment(tractor).renter(renter)
                .startDate(TODAY.plusDays(3)).endDate(TODAY.plusDays(4)).days(2)
                .pricePerDay(new BigDecimal("1500.00")).totalAmount(new BigDecimal("3000.00"))
                .status(status).paymentMethod(method).paymentStatus(paymentStatus)
                .gatewayOrderId("order_1").gatewayPaymentId(paymentStatus == PaymentStatus.PENDING ? null : "pay_1")
                .build();
        when(bookingRepository.findDetailedById(100L)).thenReturn(Optional.of(b));
        return b;
    }

    @Test
    void verifyPayment_movesToRequested_whenSignatureValid() {
        Booking b = existing(Status.AWAITING_PAYMENT, PaymentMethod.ONLINE, PaymentStatus.PENDING);
        when(paymentGateway.verifySignature("order_1", "pay_9", "sig")).thenReturn(true);

        service.verifyPayment(2L, 100L, new PaymentVerifyRequest("order_1", "pay_9", "sig"));

        assertThat(b.getStatus()).isEqualTo(Status.REQUESTED);
        assertThat(b.getPaymentStatus()).isEqualTo(PaymentStatus.AUTHORIZED);
        assertThat(b.getGatewayPaymentId()).isEqualTo("pay_9");
        assertThat(b.getRequestedAt()).isEqualTo(CLOCK.instant());
    }

    @Test
    void verifyPayment_rejectsBadSignature() {
        Booking b = existing(Status.AWAITING_PAYMENT, PaymentMethod.ONLINE, PaymentStatus.PENDING);
        when(paymentGateway.verifySignature(any(), any(), any())).thenReturn(false);

        assertThatThrownBy(() -> service.verifyPayment(2L, 100L, new PaymentVerifyRequest("order_1", "pay_9", "x")))
                .hasMessage("error.payment.invalidSignature");
        assertThat(b.getStatus()).isEqualTo(Status.AWAITING_PAYMENT);
    }

    @Test
    void approve_capturesOnlinePayment_andRevealsPhones() {
        Booking b = existing(Status.REQUESTED, PaymentMethod.ONLINE, PaymentStatus.AUTHORIZED);

        BookingResponse response = service.approve(1L, 100L);

        verify(paymentGateway).capture("pay_1", 300000L);
        assertThat(b.getStatus()).isEqualTo(Status.CONFIRMED);
        assertThat(b.getPaymentStatus()).isEqualTo(PaymentStatus.CAPTURED);
        assertThat(response.renterPhone()).isEqualTo("9876500002");
        assertThat(response.ownerPhone()).isEqualTo("9876500001");
    }

    @Test
    void approve_isOwnerOnly() {
        existing(Status.REQUESTED, PaymentMethod.CASH, PaymentStatus.NOT_REQUIRED);

        assertThatThrownBy(() -> service.approve(2L, 100L)).hasMessage("error.booking.notOwner");
    }

    @Test
    void requestedBooking_hidesPhones() {
        existing(Status.REQUESTED, PaymentMethod.CASH, PaymentStatus.NOT_REQUIRED);

        BookingResponse response = service.get(2L, 100L);

        assertThat(response.renterPhone()).isNull();
        assertThat(response.ownerPhone()).isNull();
    }

    @Test
    void reject_releasesAuthorization_withoutCallingGateway() {
        Booking b = existing(Status.REQUESTED, PaymentMethod.ONLINE, PaymentStatus.AUTHORIZED);

        service.reject(1L, 100L, "Tractor needed at home");

        assertThat(b.getStatus()).isEqualTo(Status.REJECTED);
        assertThat(b.getPaymentStatus()).isEqualTo(PaymentStatus.RELEASED);
        assertThat(b.getRejectReason()).isEqualTo("Tractor needed at home");
        verify(paymentGateway, never()).refund(any(), anyLong());
    }

    @Test
    void cancelConfirmed_refundsCapturedPayment() {
        Booking b = existing(Status.CONFIRMED, PaymentMethod.ONLINE, PaymentStatus.CAPTURED);

        service.cancel(2L, 100L);

        verify(paymentGateway).refund("pay_1", 300000L);
        assertThat(b.getStatus()).isEqualTo(Status.CANCELLED);
        assertThat(b.getPaymentStatus()).isEqualTo(PaymentStatus.REFUNDED);
    }

    @Test
    void cancel_notAllowedOnceStarted() {
        Booking b = existing(Status.CONFIRMED, PaymentMethod.CASH, PaymentStatus.NOT_REQUIRED);
        b.setStartDate(TODAY);

        assertThatThrownBy(() -> service.cancel(2L, 100L)).hasMessage("error.booking.alreadyStarted");
    }

    @Test
    void complete_onlyFromStartDate() {
        Booking b = existing(Status.CONFIRMED, PaymentMethod.CASH, PaymentStatus.NOT_REQUIRED);

        assertThatThrownBy(() -> service.complete(1L, 100L)).hasMessage("error.booking.notStarted");

        b.setStartDate(TODAY);
        service.complete(1L, 100L);
        assertThat(b.getStatus()).isEqualTo(Status.COMPLETED);
    }
}
