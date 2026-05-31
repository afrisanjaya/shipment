package com.afrisanjaya.shipment.billing.domain.repository;

import com.afrisanjaya.shipment.billing.domain.entity.LedgerEntry;
import com.afrisanjaya.shipment.billing.domain.enums.EntryType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Repository
public interface LedgerEntryRepository extends JpaRepository<LedgerEntry, UUID> {
    Page<LedgerEntry> findByWalletIdOrderByCreatedAtDesc(UUID walletId, Pageable pageable);

    boolean existsByTransactionId(UUID transactionId);

    List<LedgerEntry> findByWalletIdAndCreatedAtBetweenOrderByCreatedAtAsc(
            UUID walletId, OffsetDateTime start, OffsetDateTime end);

    @Query("SELECT le.entryType, SUM(le.amount) FROM LedgerEntry le " +
           "WHERE CAST(le.createdAt AS LocalDate) = :date " +
           "GROUP BY le.entryType")
    List<Object[]> sumByEntryTypeAndDate(@Param("date") LocalDate date);
}
