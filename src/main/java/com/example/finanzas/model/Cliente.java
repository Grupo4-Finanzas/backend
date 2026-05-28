package com.example.finanzas.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "cliente")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class Cliente {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_cliente")
    @EqualsAndHashCode.Include
    private Long idCliente;

    @NotBlank
    @Size(max = 8)
    @Column(name = "dni", nullable = false, length = 8)
    private String dni;

    @NotBlank
    @Size(max = 100)
    @Column(name = "nombre", nullable = false, length = 100)
    private String nombre;

    @NotBlank
    @Size(max = 50)
    @Column(name = "email", nullable = false, length = 50)
    private String email;

    @NotBlank
    @Size(max = 128)
    @Column(name = "firebase_uid", nullable = false, unique = true, length = 128)
    private String firebaseUid;
}
