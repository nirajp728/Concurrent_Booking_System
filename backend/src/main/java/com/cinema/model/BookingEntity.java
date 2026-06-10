package com.cinema.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.Instant;

@Entity
@Table(name = "confirmed_bookings")
@Data
@NoArgsConstructor
public class BookingEntity {

    @Id
    private String id; // Matches the structural session ID string initialized by Redis

    @Column(nullable = false)
    private String movieId;

    @Column(nullable = false)
    private String seatId;

    @Column(nullable = false)
    private String userId;

    @Column(nullable = false)
    private String status; // Permanent status: "CONFIRMED"

    @Column(nullable = false)
    private Instant confirmedAt;
}