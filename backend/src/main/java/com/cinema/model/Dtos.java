package com.cinema.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

public class Dtos {

    @Data
    @AllArgsConstructor
    public static class MovieResponse {
        private String id;
        private String title;
        private int rows;
        private int seatsPerRow;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ProcessSeatRequest {
        private String userId;
    }

    @Data
    @AllArgsConstructor
    public static class HoldResponse {
        private String sessionId;
        private String movieId;
        private String seatId;
        private String expiresAt;
    }

    @Data
    @AllArgsConstructor
    public static class SeatStatusInfo {
        private String seatId;
        private String userId;
        private boolean booked;
        private boolean confirmed;
    }

    @Data
    @AllArgsConstructor
    public static class ActionResponse {
        private String sessionId;
        private String movieId;
        private String seatId;
        private String userId;
        private String status;
    }
}