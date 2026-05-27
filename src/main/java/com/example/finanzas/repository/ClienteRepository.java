package com.example.finanzas.repository;

import com.example.finanzas.entity.Cliente;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ClienteRepository extends JpaRepository<Cliente, Long> {

    Optional<Cliente> findByFirebaseUid(String firebaseUid);
}
