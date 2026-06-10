package com.cinema.repository;

import com.cinema.model.BookingEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface PostgresBookingRepository extends JpaRepository<BookingEntity, String> {
    List<BookingEntity> findByMovieId(String movieId);
    boolean existsByMovieIdAndSeatId(String movieId, String seatId);
}