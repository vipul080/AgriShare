package com.vipul.agrishare.service;

import com.vipul.agrishare.dto.EquipmentRequest;
import com.vipul.agrishare.dto.EquipmentResponse;
import com.vipul.agrishare.dto.UserSummary;
import com.vipul.agrishare.entity.Equipment;
import com.vipul.agrishare.entity.User;
import com.vipul.agrishare.exception.ApiException;
import com.vipul.agrishare.repository.EquipmentRepository;
import com.vipul.agrishare.repository.EquipmentRepository.EquipmentDistance;
import com.vipul.agrishare.repository.EquipmentSpecs;
import com.vipul.agrishare.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class EquipmentService {

    public static final double DEFAULT_RADIUS_KM = 25;
    public static final double MAX_RADIUS_KM = 200;
    private static final int MAX_RESULTS = 100;

    private final EquipmentRepository equipmentRepository;
    private final UserRepository userRepository;
    private final FileStorageService fileStorageService;
    private final RatingService ratingService;

    @Transactional
    public EquipmentResponse create(Long ownerId, EquipmentRequest request) {
        User owner = userRepository.findById(ownerId)
                .orElseThrow(() -> ApiException.notFound("error.user.notFound"));

        Equipment equipment = Equipment.builder()
                .owner(owner)
                .build();
        apply(equipment, request);
        return toResponse(equipmentRepository.save(equipment), null);
    }

    @Transactional
    public EquipmentResponse update(Long ownerId, Long equipmentId, EquipmentRequest request) {
        Equipment equipment = getOwned(ownerId, equipmentId);
        apply(equipment, request);
        return toResponse(equipment, null);
    }

    @Transactional
    public void delete(Long ownerId, Long equipmentId) {
        Equipment equipment = getOwned(ownerId, equipmentId);
        equipment.setActive(false);
        equipment.setAvailable(false);
    }

    @Transactional
    public EquipmentResponse uploadImage(Long ownerId, Long equipmentId, MultipartFile file) {
        Equipment equipment = getOwned(ownerId, equipmentId);
        String url = fileStorageService.storeImage(file);
        fileStorageService.deleteQuietly(equipment.getImageUrl());
        equipment.setImageUrl(url);
        return toResponse(equipment, null);
    }

    @Transactional(readOnly = true)
    public EquipmentResponse get(Long equipmentId) {
        Equipment equipment = equipmentRepository.findActiveById(equipmentId)
                .orElseThrow(() -> ApiException.notFound("error.equipment.notFound"));
        return toResponse(equipment, null);
    }

    @Transactional(readOnly = true)
    public List<EquipmentResponse> listMine(Long ownerId) {
        return toResponses(equipmentRepository.findByOwnerIdAndActiveTrueOrderByCreatedAtDesc(ownerId), Map.of());
    }

    /**
     * With lat/lng: radius search sorted by distance (nearest first).
     * Without: newest listings, optionally filtered by category/text.
     */
    @Transactional(readOnly = true)
    public List<EquipmentResponse> search(Double lat, Double lng, Double radiusKm,
                                          Equipment.Category category, String query) {
        String q = StringUtils.hasText(query) ? query.trim() : null;

        if (lat != null && lng != null) {
            double radius = radiusKm == null ? DEFAULT_RADIUS_KM : Math.min(Math.max(radiusKm, 1), MAX_RADIUS_KM);
            List<EquipmentDistance> hits = equipmentRepository.searchNearby(
                    lat, lng, radius * 1000, category == null ? null : category.name(), q, MAX_RESULTS);

            Map<Long, Double> distances = hits.stream()
                    .collect(Collectors.toMap(EquipmentDistance::getId, EquipmentDistance::getDistanceKm));
            Map<Long, Equipment> byId = equipmentRepository.findByIdIn(distances.keySet()).stream()
                    .collect(Collectors.toMap(Equipment::getId, Function.identity()));

            // preserve distance ordering from the native query
            List<Equipment> ordered = hits.stream()
                    .map(h -> byId.get(h.getId()))
                    .filter(Objects::nonNull)
                    .toList();
            return toResponses(ordered, distances);
        }

        Specification<Equipment> spec = Specification.where(EquipmentSpecs.isActive())
                .and(EquipmentSpecs.hasCategory(category))
                .and(EquipmentSpecs.matches(q));
        List<Equipment> results = equipmentRepository.findAll(spec,
                PageRequest.of(0, MAX_RESULTS, Sort.by(Sort.Direction.DESC, "createdAt"))).getContent();
        return toResponses(results, Map.of());
    }

    Equipment getOwned(Long ownerId, Long equipmentId) {
        Equipment equipment = equipmentRepository.findActiveById(equipmentId)
                .orElseThrow(() -> ApiException.notFound("error.equipment.notFound"));
        if (!equipment.getOwner().getId().equals(ownerId)) {
            throw ApiException.forbidden("error.equipment.notOwner");
        }
        return equipment;
    }

    private void apply(Equipment equipment, EquipmentRequest request) {
        equipment.setName(request.name().trim());
        equipment.setCategory(request.category());
        equipment.setDescription(request.description());
        equipment.setPricePerDay(request.pricePerDay());
        equipment.setLatitude(request.latitude());
        equipment.setLongitude(request.longitude());
        equipment.setAddress(request.address());
        if (request.available() != null) {
            equipment.setAvailable(request.available());
        }
    }

    private List<EquipmentResponse> toResponses(Collection<Equipment> equipment, Map<Long, Double> distances) {
        Map<Long, RatingService.RatingSummary> ratings =
                ratingService.forEquipment(equipment.stream().map(Equipment::getId).toList());
        return equipment.stream()
                .map(e -> toResponse(e, distances.get(e.getId()), ratings.get(e.getId())))
                .toList();
    }

    EquipmentResponse toResponse(Equipment e, Double distanceKm) {
        return toResponse(e, distanceKm, ratingService.forEquipment(List.of(e.getId())).get(e.getId()));
    }

    private EquipmentResponse toResponse(Equipment e, Double distanceKm, RatingService.RatingSummary rating) {
        RatingService.RatingSummary r = rating != null ? rating : RatingService.RatingSummary.EMPTY;
        return new EquipmentResponse(
                e.getId(),
                e.getName(),
                e.getCategory().name(),
                e.getDescription(),
                e.getPricePerDay(),
                e.getLatitude(),
                e.getLongitude(),
                e.getAddress(),
                e.getImageUrl(),
                e.isAvailable(),
                UserSummary.of(e.getOwner()),
                distanceKm == null ? null : Math.round(distanceKm * 10) / 10.0,
                r.average(),
                r.count(),
                e.getCreatedAt()
        );
    }
}
