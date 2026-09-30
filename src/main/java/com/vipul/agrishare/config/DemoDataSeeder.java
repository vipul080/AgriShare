package com.vipul.agrishare.config;

import com.vipul.agrishare.entity.Booking;
import com.vipul.agrishare.entity.Equipment;
import com.vipul.agrishare.entity.Equipment.Category;
import com.vipul.agrishare.entity.Review;
import com.vipul.agrishare.entity.User;
import com.vipul.agrishare.repository.BookingRepository;
import com.vipul.agrishare.repository.EquipmentRepository;
import com.vipul.agrishare.repository.ReviewRepository;
import com.vipul.agrishare.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;

/**
 * Sample farmers and machines around Paonta Sahib for demos (APP_DEMO_DATA=true).
 * Off by default so a real deployment never shows made-up listings. Runs only on an
 * empty database. Phone numbers start with 5 — no Indian mobile does — so they can't
 * belong to anyone; every demo account's password is {@value #DEMO_PASSWORD}.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.demo-data", havingValue = "true")
public class DemoDataSeeder implements ApplicationRunner {

    static final String DEMO_PASSWORD = "demo1234";

    private final UserRepository userRepository;
    private final EquipmentRepository equipmentRepository;
    private final BookingRepository bookingRepository;
    private final ReviewRepository reviewRepository;
    private final PasswordEncoder passwordEncoder;
    private final Clock clock;

    private record Farmer(String name, String phone, String village, double lat, double lng, String lang) {}

    private record Machine(int owner, String name, Category category, String price, String description) {}

    private static final List<Farmer> FARMERS = List.of(
            new Farmer("Gurpreet Singh", "5000000001", "Paonta Sahib", 30.4384, 77.6245, "pa"),
            new Farmer("Harjit Kaur", "5000000002", "Kolar", 30.4580, 77.5930, "pa"),
            new Farmer("Ramesh Chauhan", "5000000003", "Majra", 30.4105, 77.6630, "hi"),
            new Farmer("Sukhwinder Singh", "5000000004", "Rajban", 30.5020, 77.6420, "pa"),
            new Farmer("Anil Kumar", "5000000005", "Bhagani", 30.3990, 77.5870, "hi"),
            new Farmer("Meena Devi", "5000000006", "Puruwala", 30.4720, 77.6950, "hi"));

    private static final List<Machine> MACHINES = List.of(
            new Machine(0, "Mahindra 575 DI tractor", Category.TRACTOR, "1500", "45 HP, with driver. Diesel extra."),
            new Machine(0, "Sonalika 6 ft rotavator", Category.ROTAVATOR, "800", "Good for paddy field puddling."),
            new Machine(1, "Swaraj 744 tractor", Category.TRACTOR, "1400", "48 HP. Trolley hitch available."),
            new Machine(1, "9-row seed drill", Category.SEED_DRILL, "500", "For wheat sowing. Fits any 35+ HP tractor."),
            new Machine(2, "Wheat thresher", Category.THRESHER, "1000", "Multi-crop thresher, needs tractor PTO."),
            new Machine(2, "Battery sprayer 16 L", Category.SPRAYER, "150", "Charged and ready. Two nozzles."),
            new Machine(3, "Kartar combine harvester", Category.HARVESTER, "3500", "Self-propelled, with operator. Book early in April."),
            new Machine(3, "Tractor trolley (4 ton)", Category.TROLLEY, "400", "Tipping trolley for grain and sand."),
            new Machine(4, "VST power tiller", Category.POWER_TILLER, "700", "Good for small plots and orchards."),
            new Machine(4, "5 HP diesel water pump", Category.WATER_PUMP, "300", "With 30 m pipe."),
            new Machine(5, "9-tine cultivator", Category.CULTIVATOR, "450", "Spring-loaded tines."),
            new Machine(5, "MB plough (2 bottom)", Category.PLOUGH, "500", "Reversible, for deep ploughing."));

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (userRepository.count() > 0) {
            return; // never mix demo rows into real data
        }
        String hash = passwordEncoder.encode(DEMO_PASSWORD);
        List<User> users = FARMERS.stream().map(f -> userRepository.save(User.builder()
                .name(f.name()).phone(f.phone()).passwordHash(hash).role(User.Role.BOTH)
                .latitude(f.lat()).longitude(f.lng()).address(f.village() + ", Sirmaur")
                .preferredLanguage(f.lang())
                .build())).toList();

        List<Equipment> machines = MACHINES.stream().map(m -> {
            Farmer f = FARMERS.get(m.owner());
            return equipmentRepository.save(Equipment.builder()
                    .owner(users.get(m.owner())).name(m.name()).category(m.category())
                    .pricePerDay(new BigDecimal(m.price()))
                    .description("Demo listing. " + m.description())
                    // small offset so machines of one owner don't sit on the exact same pin
                    .latitude(f.lat() + 0.002 * (m.name().length() % 5))
                    .longitude(f.lng() - 0.002 * (m.name().length() % 3))
                    .address(f.village() + ", Sirmaur")
                    .build());
        }).toList();

        LocalDate today = LocalDate.now(clock);
        // a finished job with reviews both ways, so ratings show up
        Booking done = booking(machines.get(0), users.get(2), today.minusDays(12), 2, Booking.Status.COMPLETED);
        review(done, users.get(2), users.get(0), Review.ReviewerRole.RENTER, 5, "Tractor in good condition, driver was on time.");
        review(done, users.get(0), users.get(2), Review.ReviewerRole.OWNER, 5, "Returned on time. Good farmer.");
        Booking done2 = booking(machines.get(6), users.get(1), today.minusDays(20), 1, Booking.Status.COMPLETED);
        review(done2, users.get(1), users.get(3), Review.ReviewerRole.RENTER, 4, "Harvested 6 acres in one day.");
        // upcoming work so the calendars show taken days
        booking(machines.get(3), users.get(4), today.plusDays(3), 2, Booking.Status.CONFIRMED);
        booking(machines.get(1), users.get(5), today.plusDays(5), 1, Booking.Status.REQUESTED);

        log.info("Demo data: {} farmers, {} machines around Paonta Sahib (password '{}')",
                users.size(), machines.size(), DEMO_PASSWORD);
    }

    private Booking booking(Equipment e, User renter, LocalDate start, int days, Booking.Status status) {
        return bookingRepository.save(Booking.builder()
                .equipment(e).renter(renter).startDate(start).endDate(start.plusDays(days - 1L)).days(days)
                .pricePerDay(e.getPricePerDay()).totalAmount(e.getPricePerDay().multiply(BigDecimal.valueOf(days)))
                .status(status).paymentMethod(Booking.PaymentMethod.CASH).paymentStatus(Booking.PaymentStatus.NOT_REQUIRED)
                .requestedAt(clock.instant())
                .build());
    }

    private void review(Booking b, User reviewer, User reviewee, Review.ReviewerRole role, int rating, String comment) {
        reviewRepository.save(Review.builder()
                .booking(b).reviewer(reviewer).reviewee(reviewee).equipment(b.getEquipment())
                .reviewerRole(role).rating((short) rating).comment(comment)
                .build());
    }
}
