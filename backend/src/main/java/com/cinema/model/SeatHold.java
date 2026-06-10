package com.cinema.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.Instant;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class SeatHold {
    private String id;
    private String movieId;
    private String seatId;
    private String userId;
    private String status; // "held"
    private Instant expiresAt;
}