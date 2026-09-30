package com.vipul.agrishare.service;

import com.vipul.agrishare.dto.BookingResponse;
import com.vipul.agrishare.dto.EquipmentResponse;
import com.vipul.agrishare.dto.PaymentVerifyRequest;
import com.vipul.agrishare.entity.Boost;
import com.vipul.agrishare.entity.Equipment;
import com.vipul.agrishare.exception.ApiException;
import com.vipul.agrishare.payment.EarningsProperties;
import com.vipul.agrishare.payment.PaymentGateway;
import com.vipul.agrishare.repository.BoostRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

/**
 * Optional paid "boost": an owner pays a small fixed amount to have a machine listed
 * first in nearby searches for a few days. Renters are never charged for it and still
 * see every machine, so it earns money without putting anyone off.
 */
@Service
@RequiredArgsConstructor
public class BoostService {

    private final BoostRepository boostRepository;
    private final EquipmentService equipmentService;
    private final PaymentGateway paymentGateway;
    private final EarningsProperties earnings;
    private final Clock clock;

    public record BoostCheckout(Long boostId, int days, BookingResponse.Checkout checkout) {}

    /** Step 1: create a gateway order for the boost price. */
    @Transactional
    public BoostCheckout start(Long ownerId, Long equipmentId) {
        EarningsProperties.Boost config = earnings.boost();
        if (!config.enabled()) {
            throw ApiException.badRequest("error.boost.disabled");
        }
        Equipment equipment = equipmentService.getOwned(ownerId, equipmentId);

        Boost boost = Boost.builder()
                .equipment(equipment)
                .owner(equipment.getOwner())
                .days(config.days())
                .amount(config.price())
                .status(Boost.Status.PENDING)
                .gatewayOrderId("pending")
                .build();
        boostRepository.save(boost); // id for the receipt
        PaymentGateway.GatewayOrder order =
                paymentGateway.createOrder(BookingService.toPaise(config.price()), "boost_" + boost.getId());
        boost.setGatewayOrderId(order.orderId());

        return new BoostCheckout(boost.getId(), config.days(), new BookingResponse.Checkout(
                paymentGateway.mode(), paymentGateway.publicKeyId(), order.orderId(), order.amountPaise(), "INR"));
    }

    /** Step 2: checkout finished — verify, take the money at once, feature the machine. */
    @Transactional
    public EquipmentResponse verify(Long ownerId, Long boostId, PaymentVerifyRequest request) {
        Boost boost = boostRepository.findDetailedById(boostId)
                .orElseThrow(() -> ApiException.notFound("error.boost.notFound"));
        if (!boost.getOwner().getId().equals(ownerId)) {
            throw ApiException.forbidden("error.forbidden");
        }
        if (boost.getStatus() != Boost.Status.PENDING) {
            throw ApiException.conflict("error.booking.invalidState");
        }
        if (!request.orderId().equals(boost.getGatewayOrderId())) {
            throw ApiException.badRequest("error.payment.orderMismatch");
        }
        if (!paymentGateway.verifySignature(request.orderId(), request.paymentId(), request.signature())) {
            throw ApiException.badRequest("error.payment.invalidSignature");
        }
        // Unlike bookings there is nobody to approve, so capture straight away.
        paymentGateway.capture(request.paymentId(), BookingService.toPaise(boost.getAmount()));

        Instant now = clock.instant();
        boost.setGatewayPaymentId(request.paymentId());
        boost.setStatus(Boost.Status.PAID);
        boost.setPaidAt(now);

        Equipment equipment = boost.getEquipment();
        // buying again while featured extends the current period instead of wasting it
        Instant from = EquipmentService.isFeatured(equipment, now) ? equipment.getFeaturedUntil() : now;
        equipment.setFeaturedUntil(from.plus(Duration.ofDays(boost.getDays())));
        return equipmentService.toResponse(equipment, null);
    }
}
