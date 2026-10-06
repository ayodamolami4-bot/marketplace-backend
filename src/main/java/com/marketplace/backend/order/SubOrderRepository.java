package com.marketplace.backend.order;

import com.marketplace.backend.payment.PaymentStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface SubOrderRepository extends JpaRepository<SubOrder, UUID> {

    List<SubOrder> findByOrderId(UUID orderId);

    List<SubOrder> findByVendorIdOrderByCreatedAtDesc(UUID vendorId);

    @Query("""
            select so
            from SubOrder so
            where so.vendor.id = :vendorId
              and exists (
                  select p.id
                  from Payment p
                  where p.order = so.order
                    and p.status = :status
              )
            order by so.createdAt desc
            """)
    List<SubOrder> findPaidByVendorId(
            @Param("vendorId") UUID vendorId,
            @Param("status") PaymentStatus status
    );

    @Query("""
            select so
            from SubOrder so
            where exists (
                select p.id
                from Payment p
                where p.order = so.order
                  and p.status = :status
            )
            order by so.createdAt desc
            """)
    List<SubOrder> findPaid(
            @Param("status") PaymentStatus status
    );
}