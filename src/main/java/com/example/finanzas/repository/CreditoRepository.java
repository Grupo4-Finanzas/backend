package com.example.finanzas.repository;

import com.example.finanzas.entity.Credito;
import java.util.List;
import java.util.Optional;
import java.time.Instant;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CreditoRepository extends JpaRepository<Credito, Long> {

    List<Credito> findByClienteIdClienteOrderByFechaCreacionDesc(Long idCliente);

    @Query("""
            select c
            from Credito c
            where c.cliente.idCliente = :idCliente
              and (:createdFrom is null or c.fechaCreacion >= :createdFrom)
              and (:createdTo is null or c.fechaCreacion <= :createdTo)
            order by c.fechaCreacion desc
            """)
    Page<Credito> findHistory(
            @Param("idCliente") Long idCliente,
            @Param("createdFrom") Instant createdFrom,
            @Param("createdTo") Instant createdTo,
            Pageable pageable);

    Optional<Credito> findByIdCreditoAndClienteIdCliente(Long idCredito, Long idCliente);
}
