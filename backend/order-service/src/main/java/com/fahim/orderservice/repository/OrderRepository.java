package com.fahim.orderservice.repository;

import com.fahim.orderservice.model.Order;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrderRepository extends JpaRepository<Order, Long> {

    List<Order> findByUserSub(String userSub);

    /**
     * Ownership is part of the lookup, not a check after it: a miss is indistinguishable from a
     * non-existent order, so another user's order cannot be probed for existence.
     */
    Optional<Order> findByIdAndUserSub(Long id, String userSub);
}
