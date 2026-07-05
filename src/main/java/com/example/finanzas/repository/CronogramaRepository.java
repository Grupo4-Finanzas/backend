package com.example.finanzas.repository;

import com.example.finanzas.entity.Cronograma;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CronogramaRepository extends JpaRepository<Cronograma, Long> {

    List<Cronograma> findByCreditoIdCreditoAndCreditoClienteIdClienteOrderByPeriodoAsc(
            Long idCredito,
            Long idCliente);
}
