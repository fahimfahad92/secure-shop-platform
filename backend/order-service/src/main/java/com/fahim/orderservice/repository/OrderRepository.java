package com.fahim.orderservice.repository;

import com.fahim.orderservice.model.Order;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

/**
 * Status and quantity change only through the conditional updates below (ADR-0016). Each is one
 * {@code UPDATE ... WHERE id AND user_sub AND <allowed current status>}: the {@code WHERE} is the
 * check, so a transition is atomic without a service-level transaction (ADR-0015) or a version
 * column. Each returns the number of rows changed, 0 or 1; on 0 the service re-reads the order to
 * tell "not yours", "already done" and "not allowed" apart.
 *
 * <p>The {@code @Transactional} here is the repository call's own transaction, exactly as for
 * {@code save()}: ADR-0015's "each repository call commits on its own". {@code @Modifying} queries
 * need one; services and controllers have none.
 */
public interface OrderRepository extends JpaRepository<Order, Long> {

    List<Order> findByUserSub(String userSub);

    /**
     * Ownership is part of the lookup, not a check after it: a miss is indistinguishable from a
     * non-existent order, so another user's order cannot be probed for existence.
     */
    Optional<Order> findByIdAndUserSub(Long id, String userSub);

    /** Changes the quantity of a {@code PENDING} order. */
    @Modifying
    @Transactional
    @Query(
            """
            UPDATE Order o
               SET o.quantity = :quantity, o.totalPrice = :totalPrice, o.updatedAt = :now
             WHERE o.id = :id AND o.userSub = :userSub
               AND o.status = com.fahim.orderservice.model.OrderStatus.PENDING
            """)
    int updateQuantity(
            @Param("id") Long id,
            @Param("userSub") String userSub,
            @Param("quantity") Integer quantity,
            @Param("totalPrice") BigDecimal totalPrice,
            @Param("now") Instant now);

    /** {@code PENDING} → {@code CONFIRMED}. */
    @Modifying
    @Transactional
    @Query(
            """
            UPDATE Order o
               SET o.status = com.fahim.orderservice.model.OrderStatus.CONFIRMED,
                   o.statusChangedAt = :now, o.updatedAt = :now
             WHERE o.id = :id AND o.userSub = :userSub
               AND o.status = com.fahim.orderservice.model.OrderStatus.PENDING
            """)
    int confirm(@Param("id") Long id, @Param("userSub") String userSub, @Param("now") Instant now);

    /**
     * {@code PENDING} → {@code CANCELLED} at any time; {@code CONFIRMED} → {@code CANCELLED} only
     * while it was confirmed after {@code cutoff} (now minus {@code orders.cancel-window}).
     */
    @Modifying
    @Transactional
    @Query(
            """
            UPDATE Order o
               SET o.status = com.fahim.orderservice.model.OrderStatus.CANCELLED,
                   o.statusChangedAt = :now, o.updatedAt = :now
             WHERE o.id = :id AND o.userSub = :userSub
               AND (o.status = com.fahim.orderservice.model.OrderStatus.PENDING
                    OR (o.status = com.fahim.orderservice.model.OrderStatus.CONFIRMED
                        AND o.statusChangedAt > :cutoff))
            """)
    int cancel(
            @Param("id") Long id,
            @Param("userSub") String userSub,
            @Param("now") Instant now,
            @Param("cutoff") Instant cutoff);
}
