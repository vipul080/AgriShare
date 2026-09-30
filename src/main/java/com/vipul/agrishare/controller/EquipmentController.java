package com.vipul.agrishare.controller;

import com.vipul.agrishare.dto.BookedRange;
import com.vipul.agrishare.dto.EquipmentRequest;
import com.vipul.agrishare.dto.EquipmentResponse;
import com.vipul.agrishare.dto.ReviewResponse;
import com.vipul.agrishare.entity.Equipment;
import com.vipul.agrishare.security.UserPrincipal;
import com.vipul.agrishare.service.BookingService;
import com.vipul.agrishare.service.EquipmentService;
import com.vipul.agrishare.service.ReviewService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Arrays;
import java.util.List;

@RestController
@RequestMapping("/api/equipment")
@RequiredArgsConstructor
public class EquipmentController {

    private final EquipmentService equipmentService;
    private final BookingService bookingService;
    private final ReviewService reviewService;

    /** Public: browse/search. Pass lat & lng for nearest-first radius search. */
    @GetMapping
    public List<EquipmentResponse> search(@RequestParam(required = false) Double lat,
                                          @RequestParam(required = false) Double lng,
                                          @RequestParam(required = false) Double radiusKm,
                                          @RequestParam(required = false) Equipment.Category category,
                                          @RequestParam(required = false) String q) {
        return equipmentService.search(lat, lng, radiusKm, category, q);
    }

    @GetMapping("/categories")
    public List<String> categories() {
        return Arrays.stream(Equipment.Category.values()).map(Enum::name).toList();
    }

    @GetMapping("/mine")
    public List<EquipmentResponse> mine(@AuthenticationPrincipal UserPrincipal user) {
        return equipmentService.listMine(user.getId());
    }

    @GetMapping("/{id}")
    public EquipmentResponse get(@PathVariable Long id) {
        return equipmentService.get(id);
    }

    /** Public: dates already taken, so the calendar can grey them out. */
    @GetMapping("/{id}/booked-dates")
    public List<BookedRange> bookedDates(@PathVariable Long id) {
        return bookingService.bookedDates(id);
    }

    /** Public: what renters said about this machine. */
    @GetMapping("/{id}/reviews")
    public ReviewResponse.Page reviews(@PathVariable Long id) {
        return reviewService.forEquipment(id);
    }

    @PostMapping
    public ResponseEntity<EquipmentResponse> create(@AuthenticationPrincipal UserPrincipal user,
                                                    @Valid @RequestBody EquipmentRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(equipmentService.create(user.getId(), request));
    }

    @PutMapping("/{id}")
    public EquipmentResponse update(@AuthenticationPrincipal UserPrincipal user,
                                    @PathVariable Long id,
                                    @Valid @RequestBody EquipmentRequest request) {
        return equipmentService.update(user.getId(), id, request);
    }

    @PostMapping(value = "/{id}/image", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public EquipmentResponse uploadImage(@AuthenticationPrincipal UserPrincipal user,
                                         @PathVariable Long id,
                                         @RequestParam("file") MultipartFile file) {
        return equipmentService.uploadImage(user.getId(), id, file);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@AuthenticationPrincipal UserPrincipal user, @PathVariable Long id) {
        equipmentService.delete(user.getId(), id);
        return ResponseEntity.noContent().build();
    }
}
