package com.cinema.repository;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList; 
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Repository;
import org.springframework.web.server.ResponseStatusException;

import com.cinema.model.SeatHold;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.extern.slf4j.Slf4j;

@Slf4j // 2. Added the annotation to auto-inject the 'log' instance
@Repository
public class RedisLockStore {

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private static final Duration HOLD_TTL = Duration.ofMinutes(2);

    public RedisLockStore(StringRedisTemplate redisTemplate, ObjectMapper objectMapper) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }

    private String buildSeatKey(String movieId, String seatId) {
        return String.format("seat:%s:%s", movieId, seatId);
    }

    private String buildSessionKey(String id) {
        return "session:" + id;
    }

    public SeatHold acquireHold(String movieId, String seatId, String userId) {
        String sessionId = UUID.randomUUID().toString();
        Instant expiryTime = Instant.now().plus(HOLD_TTL);
        String seatKey = buildSeatKey(movieId, seatId);

        SeatHold hold = new SeatHold(sessionId, movieId, seatId, userId, "held", expiryTime);

        try {
            String jsonPayload = objectMapper.writeValueAsString(hold);
            
            log.info("[LOCK ATTEMPT] User '{}' trying to acquire lock for seat '{}' on movie '{}'", userId, seatId, movieId);
            
            // ATOMIC SET IF NOT EXISTS (Pessimistic Isolation Implementation)
            Boolean lockAcquired = redisTemplate.opsForValue().setIfAbsent(seatKey, jsonPayload, HOLD_TTL);

            if (lockAcquired == null || !lockAcquired) {
                log.warn("[LOCK CONFLICT] Distributed lock denied for seat '{}'. Key already exists in Redis memory store.", seatId);
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Lock acquisition denied. Seat is held.");
            }

            log.info("[LOCK ACQUIRED] Distributed lock secured successfully for seat '{}'. Lease expires in 2 minutes.", seatId);
            redisTemplate.opsForValue().set(buildSessionKey(sessionId), seatKey, HOLD_TTL);
            return hold;
        } catch (Exception e) {
            if (e instanceof ResponseStatusException) throw (ResponseStatusException) e;
            log.error("[CACHE ERROR] Exception thrown during lock processing for seat '{}': {}", seatId, e.getMessage());
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Distributed cache processing failure.");
        }
    }

    public List<SeatHold> fetchActiveHolds(String movieId) {
        String matchPattern = String.format("seat:%s:*", movieId);
        
        log.info("[FETCH ACTIVE HOLDS] Scanning Redis cache for active locks matching pattern '{}'", matchPattern);
        Set<String> keys = redisTemplate.keys(matchPattern);
        List<SeatHold> activeHolds = new ArrayList<>();

        if (keys == null) return activeHolds;

        for (String key : keys) {
            String payload = redisTemplate.opsForValue().get(key);
            if (payload != null) {
                try {
                    activeHolds.add(objectMapper.readValue(payload, SeatHold.class));
                } catch (Exception ignored) {}
            }
        }
        log.info("[FETCH ACTIVE HOLDS] Found {} active transient holds in memory for movie '{}'", activeHolds.size(), movieId);
        return activeHolds;
    }

    public SeatHold validateAndExtractHold(String sessionId, String userId) {
        String sessionKey = buildSessionKey(sessionId);
        
        log.info("[VALIDATING HOLD] Validating ownership tokens for session '{}'", sessionId);
        String seatKey = redisTemplate.opsForValue().get(sessionKey);

        if (seatKey == null) {
            log.warn("[LEASE EXPIRED] Verification failed. Session key '{}' has expired or does not exist in Redis.", sessionId);
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Lease time expired or authorization token missing.");
        }

        String rawHoldData = redisTemplate.opsForValue().get(seatKey);
        if (rawHoldData == null) {
            log.error("[DATA CORRUPTION] Session key exists but the corresponding seat lock structure '{}' was dropped.", seatKey);
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Structural allocation frame dropped.");
        }

        try {
            SeatHold hold = objectMapper.readValue(rawHoldData, SeatHold.class);
            if (!hold.getUserId().equals(userId)) {
                log.warn("[ACCESS VIOLATION] Security verification failure! Session '{}' belongs to user '{}', but request was made by user '{}'.", 
                        sessionId, hold.getUserId(), userId);
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Security verification failure: ID mismatch.");
            }
            log.info("[VALIDATION SUCCESS] Lease ownership confirmed for user '{}' on seat '{}'.", userId, hold.getSeatId());
            return hold;
        } catch (Exception e) {
            if (e instanceof ResponseStatusException) throw (ResponseStatusException) e;
            log.error("[SERIALIZATION ERROR] Failed to parse payload data context from cache: {}", e.getMessage());
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "JSON serialization conflict.");
        }
    }

    public void purgeHold(String sessionId) {
        String sessionKey = buildSessionKey(sessionId);
        String seatKey = redisTemplate.opsForValue().get(sessionKey);
        
        if (seatKey != null) {
            log.info("[PURGING HOLD] Explicitly evicting memory cache keys for seat lock '{}' and session '{}'", seatKey, sessionKey);
            redisTemplate.delete(seatKey);
            redisTemplate.delete(sessionKey);
        } else {
            log.warn("[PURGE SKIP] Request received to purge session '{}', but it has already been cleared or expired.", sessionId);
        }
    }
}