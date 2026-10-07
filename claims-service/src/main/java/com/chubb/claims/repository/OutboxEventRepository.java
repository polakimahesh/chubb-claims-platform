package com.chubb.claims.repository;

import com.chubb.claims.entity.OutboxEvent;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OutboxEventRepository extends JpaRepository<OutboxEvent, Long> {
    List<OutboxEvent> findByPublishedAtIsNullOrderByIdAsc(Pageable pageable);
}
