package com.cinema.controller;

import com.cinema.model.BookingEntity;
import com.cinema.model.Dtos.*;
import com.cinema.model.SeatHold;
import com.cinema.service.BookingCoordinatorService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.format.DateTimeFormatter;
import java.util.*;

@RestController
@RequestMapping("/api")
public class BookingController {

    private final BookingCoordinatorService service;
    
    private final List<MovieResponse> staticMovies = Arrays.asList(
            new MovieResponse("inception", "Inception", 5, 8),
            new MovieResponse("dune", "Dune: Part Two", 4, 6)
    );

    public BookingController(BookingCoordinatorService service) {
        this.service = service;
    }

    @GetMapping("/movies")
    public List<MovieResponse> fetchMovies() {
        return staticMovies;
    }

    @GetMapping("/movies/{movieId}/seats")
    public List<SeatStatusInfo> inspectSeatMap(@PathVariable String movieId) {
        Map<String, SeatStatusInfo> consolidatedView = new HashMap<>();

        // Capture live distributed lease holds from memory tier
        List<SeatHold> transientHolds = service.getLiveHoldsForMovie(movieId);
        for (SeatHold hold : transientHolds) {
            consolidatedView.put(hold.getSeatId(), new SeatStatusInfo(
                    hold.getSeatId(), hold.getUserId(), true, false
            ));
        }

        // Overwrite or append with finalized transactions saved in relational store
        List<BookingEntity> databaseBookings = service.getPermanentBookingsForMovie(movieId);
        for (BookingEntity entity : databaseBookings) {
            consolidatedView.put(entity.getSeatId(), new SeatStatusInfo(
                    entity.getSeatId(), entity.getUserId(), true, true
            ));
        }

        return new ArrayList<>(consolidatedView.values());
    }

    @PostMapping("/movies/{movieId}/seats/{seatId}/hold")
    public ResponseEntity<HoldResponse> processHoldRequest(
            @PathVariable String movieId,
            @PathVariable String seatId,
            @RequestBody ProcessSeatRequest payload) {

        SeatHold sessionHold = service.holdSeatAllocation(movieId, seatId, payload.getUserId());
        
        HoldResponse feedbackPayload = new HoldResponse(
                sessionHold.getId(),
                sessionHold.getMovieId(),
                sessionHold.getSeatId(),
                DateTimeFormatter.ISO_INSTANT.format(sessionHold.getExpiresAt())
        );

        return new ResponseEntity<>(feedbackPayload, HttpStatus.CREATED);
    }

    @PutMapping("/sessions/{sessionId}/confirm")
    public ActionResponse executeConfirmation(
            @PathVariable String sessionId,
            @RequestBody ProcessSeatRequest payload) {

        BookingEntity completeBooking = service.finalizeBookingRecord(sessionId, payload.getUserId());
        
        return new ActionResponse(
                completeBooking.getId(),
                completeBooking.getMovieId(),
                completeBooking.getSeatId(),
                completeBooking.getUserId(),
                completeBooking.getStatus()
        );
    }

    @DeleteMapping("/sessions/{sessionId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void discardSessionAllocation(
            @PathVariable String sessionId,
            @RequestBody ProcessSeatRequest payload) {
        
        service.abortReservationSession(sessionId, payload.getUserId());
    }
}