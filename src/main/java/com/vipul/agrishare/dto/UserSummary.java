package com.vipul.agrishare.dto;

/** Public view of a farmer — no phone/email unless a booking is confirmed. */
public record UserSummary(Long id, String name, String address) {

    public static UserSummary of(com.vipul.agrishare.entity.User user) {
        return new UserSummary(user.getId(), user.getName(), user.getAddress());
    }
}
