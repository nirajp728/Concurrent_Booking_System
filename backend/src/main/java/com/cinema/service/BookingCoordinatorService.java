package com.cinema.service;

import java.time.Instant;
import java.util.List;
import java.util.Optional; // Imported Optional wrapper

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.cinema.model.BookingConfirmationEvent;
import com.cinema.model.BookingEntity;
import com.cinema.model.SeatHold;
import com.cinema.repository.PostgresBookingRepository;
import com.cinema.repository.RedisLockStore;

import io.awspring.cloud.sqs.operations.SqsTemplate;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class BookingCoordinatorService {

    private final RedisLockStore redisStore;
    private final PostgresBookingRepository postgresRepository;
    private final SqsTemplate sqsTemplate;

    // Use Optional here to gracefully handle environments where SQS is disabled
    public BookingCoordinatorService(RedisLockStore redisStore, 
                                     PostgresBookingRepository postgresRepository, 
                                     Optional<SqsTemplate> sqsTemplate) {
        this.redisStore = redisStore;
        this.postgresRepository = postgresRepository;
        this.sqsTemplate = sqsTemplate.orElse(null); // Assigns null if bean is absent
    }

    public SeatHold holdSeatAllocation(String movieId, String seatId, String userId) {
        log.info("[SERVICE REQ] Checking pre-conditions for seat hold on movie '{}', seat '{}' by user '{}'", movieId, seatId, userId);
        
        if (postgresRepository.existsByMovieIdAndSeatId(movieId, seatId)) {
            log.warn("[DB CONFLICT] Hold rejected! Seat '{}' for movie '{}' is already permanently sold in PostgreSQL.", seatId, movieId);
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Seat reservation has already been completed in DB.");
        }
        
        return redisStore.acquireHold(movieId, seatId, userId);
    }

    public List<SeatHold> getLiveHoldsForMovie(String movieId) {
        log.info("[SERVICE REQ] Requesting transient seat holds from Redis cache for movie '{}'", movieId);
        return redisStore.fetchActiveHolds(movieId);
    }

    public List<BookingEntity> getPermanentBookingsForMovie(String movieId) {
        log.info("[SERVICE REQ] Requesting permanent finalized rows from PostgreSQL for movie '{}'", movieId);
        return postgresRepository.findByMovieId(movieId);
    }

    @Transactional
    public BookingEntity finalizeBookingRecord(String sessionId, String userId) {
        log.info("[CHECKOUT ROUTINE] Starting checkout serialization sequence for session ID '{}'", sessionId);
        
        SeatHold hold = redisStore.validateAndExtractHold(sessionId, userId);

        log.info("[PROMOTING LOCK] Hold validated. Transitioning temporary Redis seat hold '{}' into permanent PostgreSQL storage...", hold.getSeatId());

        BookingEntity persistentBooking = new BookingEntity();
        persistentBooking.setId(hold.getId());
        persistentBooking.setMovieId(hold.getMovieId());
        persistentBooking.setSeatId(hold.getSeatId());
        persistentBooking.setUserId(hold.getUserId());
        persistentBooking.setStatus("CONFIRMED");
        persistentBooking.setConfirmedAt(Instant.now());

        BookingEntity savedResult = postgresRepository.save(persistentBooking);
        log.info("[RELATIONAL ROW SAVED] Core database transaction finalized in PostgreSQL for seat '{}'.", hold.getSeatId());

        redisStore.purgeHold(sessionId);
        log.info("[EVICTION COMPLETE] Distributed cache keys evicted from Redis for seat '{}'.", hold.getSeatId());

        // Process message dispatch ONLY if SQS template engine bean is present
        if (sqsTemplate != null) {
            try {
                String renderedHtml = """
                    <div style="font-family: Arial, sans-serif; background: #0f172a; color: #f8fafc; padding: 25px; border-radius: 16px; max-width: 500px; border: 1px solid #1e293b;">
                        <h1 style="color: #2dd4bf; margin-top: 0; font-size: 24px;">Booking Confirmed! 🍿</h1>
                        <p style="color: #94a3b8; font-size: 14px;">Hi %s, your ticket has been securely locked into our system.</p>
                        <hr style="border: 0; border-top: 1px solid #334155; margin: 20px 0;" />
                        <ul style="list-style: none; padding: 0; margin: 0; font-size: 15px;">
                            <li style="margin-bottom: 10px;"><strong style="color: #94a3b8;">Movie:</strong> %s</li>
                            <li style="margin-bottom: 10px;"><strong style="color: #94a3b8;">Seat Allocation:</strong> <span style="background: #334155; padding: 2px 8px; border-radius: 6px; color: #fbbf24; font-family: monospace;">%s</span></li>
                            <li><strong style="color: #94a3b8;">Reference ID:</strong> <span style="font-family: monospace; font-size: 13px; color: #cbd5e1;">%s</span></li>
                        </ul>
                    </div>
                    """.formatted(savedResult.getUserId(), savedResult.getMovieId(), savedResult.getSeatId(), savedResult.getId());

                BookingConfirmationEvent event = new BookingConfirmationEvent(
                    savedResult.getId(),
                    savedResult.getUserId(),
                    savedResult.getMovieId(),
                    savedResult.getSeatId(),
                    userId + "@example.com",
                    renderedHtml
                );
                
                sqsTemplate.send("cinema-booking-queue", event);
                log.info("[SQS PUBLISH] Dynamic HTML ticket package successfully dispatched to SQS queue.");
            } catch (Exception e) {
                log.error("[SQS FAULT] Non-blocking exception caught while publishing to messaging queue: {}", e.getMessage());
            }
        } else {
            log.info("[SQS BYPASS] SQS auto-configuration is disabled locally. Skipping message generation step safely.");
        }

        return savedResult;
    }

    public void abortReservationSession(String sessionId, String userId) {
        log.info("[ABORT ROUTINE] Explicit cancellation request received for session '{}' by user '{}'", sessionId, userId);
        redisStore.validateAndExtractHold(sessionId, userId);
        redisStore.purgeHold(sessionId);
        log.info("[ABORT COMPLETE] Session '{}' safely closed and locks released.", sessionId);
    }
}