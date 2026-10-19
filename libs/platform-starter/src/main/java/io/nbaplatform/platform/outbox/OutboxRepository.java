package io.nbaplatform.platform.outbox;

import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;
import java.time.Instant;
import java.util.List;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;

public interface OutboxRepository extends JpaRepository<OutboxMessage, Long> {

    /** Locks the oldest unpublished rows; other relay replicas skip them (SKIP LOCKED on PostgreSQL). */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "-2"))
    @Query("select m from OutboxMessage m where m.publishedAt is null order by m.id")
    List<OutboxMessage> lockUnpublished(Limit limit);

    long countByPublishedAtIsNull();

    @Modifying
    @Query("delete from OutboxMessage m where m.publishedAt < :before")
    int deletePublishedBefore(Instant before);
}
