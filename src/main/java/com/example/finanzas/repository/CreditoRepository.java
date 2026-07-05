package com.example.finanzas.repository;

import com.example.finanzas.entity.Credito;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CreditoRepository extends JpaRepository<Credito, Long> {

    List<Credito> findByClienteIdClienteOrderByFechaCreacionDesc(Long idCliente);

    Optional<Credito> findByIdCreditoAndClienteIdCliente(Long idCredito, Long idCliente);
}
