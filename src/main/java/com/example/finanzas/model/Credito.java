package com.example.finanzas.entity;

import com.example.finanzas.entity.enums.FrecuenciaCapitalizacion;
import com.example.finanzas.entity.enums.TipoPeriodoGracia;
import com.example.finanzas.entity.enums.TipoTasa;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "credito")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class Credito {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_credito")
    @EqualsAndHashCode.Include
    private Long idCredito;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_cliente", nullable = false)
    private Cliente cliente;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_vehiculo", nullable = false)
    private Vehiculo vehiculo;

    @NotNull
    @Column(name = "porcentaje_cuota_inicial", nullable = false, precision = 5, scale = 2)
    private BigDecimal porcentajeCuotaInicial;

    @NotNull
    @Column(name = "porcentaje_cuota_balon", nullable = false, precision = 5, scale = 2)
    private BigDecimal porcentajeCuotaBalon;

    @NotNull
    @Column(name = "monto_financiado", nullable = false, precision = 10, scale = 2)
    private BigDecimal montoFinanciado;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "tipo_tasa", nullable = false, length = 10)
    private TipoTasa tipoTasa;

    @NotNull
    @Column(name = "valor_tasa", nullable = false, precision = 8, scale = 4)
    private BigDecimal valorTasa;

    @Enumerated(EnumType.STRING)
    @Column(name = "frecuencia_capitalizacion", length = 15)
    private FrecuenciaCapitalizacion frecuenciaCapitalizacion;

    @NotNull
    @Column(name = "plazo_meses", nullable = false)
    private Integer plazoMeses;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "tipo_periodo_gracia", nullable = false, length = 10)
    private TipoPeriodoGracia tipoPeriodoGracia;

    @NotNull
    @Column(name = "meses_gracia", nullable = false)
    private Integer mesesGracia;

    @NotNull
    @Column(name = "tasa_seguro_desgravamen", nullable = false, precision = 8, scale = 4)
    private BigDecimal tasaSeguroDesgravamen;

    @NotNull
    @Column(name = "tasa_seguro_vehicular", nullable = false, precision = 8, scale = 4)
    private BigDecimal tasaSeguroVehicular;

    @NotNull
    @Column(name = "gastos_administrativos_mensuales", nullable = false, precision = 5, scale = 2)
    private BigDecimal gastosAdministrativosMensuales;

    @Column(name = "cuota_mensual_ordinaria", precision = 10, scale = 2)
    private BigDecimal cuotaMensualOrdinaria;

    @Column(name = "tcea", precision = 8, scale = 4)
    private BigDecimal tcea;

    @Column(name = "tir", precision = 8, scale = 4)
    private BigDecimal tir;

    @Column(name = "van", precision = 12, scale = 2)
    private BigDecimal van;

    @OneToMany(mappedBy = "credito", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Cronograma> cronogramas = new ArrayList<>();
}
