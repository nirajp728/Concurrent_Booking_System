package com.cinema.model;

import java.io.Serializable;

public record BookingConfirmationEvent(
    String bookingId,
    String userId,
    String movieId,
    String seatId,
    String userEmail,
    String htmlBody 
) implements Serializable {}