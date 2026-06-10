package com.cinema.consumer;

import org.springframework.stereotype.Component;

import com.cinema.model.BookingConfirmationEvent;

import io.awspring.cloud.sqs.annotation.SqsListener;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class BookingNotificationConsumer {

    // Listens to the specified queue name on AWS continuously
    @SqsListener("cinema-booking-queue")
    public void processBookingNotification(BookingConfirmationEvent event) {
        log.info("[SQS CONSUME] Worker intercepted new message from queue! Processing booking ID: {}", event.bookingId());
        
        // Simulation area for long-running heavy work
        log.info("[PROCESSING TICKET] Generating PDF ticket layout details for Seat {}...", event.seatId());
        
        try {
            Thread.sleep(1500); // Simulating network lag connecting to mail servers
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        log.info("[SUCCESS] Async processing finalized! Ticket sent to execution target: {}", event.userEmail());
        // NOTE FOR FUTURE WORK: This is where you call your Amazon SES (Simple Email Service) client wrapper!
    }
}