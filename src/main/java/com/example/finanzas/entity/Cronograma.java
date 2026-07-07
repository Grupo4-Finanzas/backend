package com.example.finanzas.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDate;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(
        name = "cronograma",
        uniqueConstraints = @UniqueConstraint(columnNames = {"id_credito", "periodo"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class Cronograma {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_cronograma")
    @EqualsAndHashCode.Include
    private Long idCronograma;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_credito", nullable = false)
    private Credito credito;

    @NotNull
    @Column(name = "periodo", nullable = false)
    private Integer periodo;

    @NotNull
    @Column(name = "fecha_pago", nullable = false)
    private LocalDate fechaPago;

    @NotNull
    @Column(name = "saldo_inicial", nullable = false, precision = 10, scale = 2)
    private BigDecimal saldoInicial;

    @NotNull
    @Column(name = "interes", nullable = false, precision = 10, scale = 2)
    private BigDecimal interes;

    @NotNull
    @Column(name = "amortizacion", nullable = false, precision = 10, scale = 2)
    private BigDecimal amortizacion;

    @NotNull
    @Column(name = "cuota_seguro_desgravamen", nullable = false, precision = 10, scale = 2)
    private BigDecimal cuotaSeguroDesgravamen;

    @NotNull
    @Column(name = "cuota_seguro_vehicular", nullable = false, precision = 10, scale = 2)
    private BigDecimal cuotaSeguroVehicular;

    @NotNull
    @Column(name = "gastos_administrativos", nullable = false, precision = 10, scale = 2)
    private BigDecimal gastosAdministrativos;

    @NotNull
    @Column(name = "cuota_balon", nullable = false, precision = 10, scale = 2)
    private BigDecimal cuotaBalon;

    @NotNull
    @Column(name = "cuota_mensual_ordinaria", nullable = false, precision = 10, scale = 2)
    private BigDecimal cuotaMensualOrdinaria;

    @NotNull
    @Column(name = "cuota_total", nullable = false, precision = 10, scale = 2)
    private BigDecimal cuotaTotal;

    @NotNull
    @Column(name = "saldo_final", nullable = false, precision = 10, scale = 2)
    private BigDecimal saldoFinal;
}
