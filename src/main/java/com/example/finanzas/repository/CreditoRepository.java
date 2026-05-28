package com.example.finanzas.repository;

import com.example.finanzas.entity.Credito;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CreditoRepository extends JpaRepository<Credito, Long> {

    List<Credito> findByClienteIdCliente(Long idCliente);

    @Query("SELECT c FROM Credito c WHERE c.cliente.firebaseUid = :firebaseUid")
    List<Credito> findByClienteFirebaseUid(@Param("firebaseUid") String firebaseUid);
}
