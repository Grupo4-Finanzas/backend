package com.example.finanzas.repository;

import com.example.finanzas.entity.Credito;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CreditoRepository extends JpaRepository<Credito, Long> {

    List<Credito> findByClienteIdClienteOrderByFechaCreacionDesc(Long idCliente);

    Page<Credito> findByClienteIdCliente(Long idCliente, Pageable pageable);

    Page<Credito> findByClienteIdClienteAndFechaCreacionGreaterThanEqual(
            Long idCliente,
            Instant createdFrom,
            Pageable pageable);

    Page<Credito> findByClienteIdClienteAndFechaCreacionLessThanEqual(
            Long idCliente,
            Instant createdTo,
            Pageable pageable);

    Page<Credito> findByClienteIdClienteAndFechaCreacionBetween(
            Long idCliente,
            Instant createdFrom,
            Instant createdTo,
            Pageable pageable);

    Optional<Credito> findByIdCreditoAndClienteIdCliente(Long idCredito, Long idCliente);
}
