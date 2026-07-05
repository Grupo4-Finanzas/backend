package com.example.finanzas.service;

import com.example.finanzas.dto.SimulationRequestDTO;
import com.example.finanzas.dto.SimulationResponseDTO;
import com.example.finanzas.dto.api.SimulationCalculationResponseDto;
import com.example.finanzas.dto.api.SimulationDraftDto;
import com.example.finanzas.dto.api.SimulationHistoryItemDto;
import com.example.finanzas.entity.Cliente;
import com.example.finanzas.entity.Credito;
import com.example.finanzas.entity.Cronograma;
import com.example.finanzas.entity.Vehiculo;
import com.example.finanzas.entity.enums.EstadoSimulacion;
import com.example.finanzas.entity.enums.Moneda;
import com.example.finanzas.entity.enums.TipoPeriodoGracia;
import com.example.finanzas.entity.enums.TipoTasa;
import com.example.finanzas.exception.NotFoundException;
import com.example.finanzas.mapper.SimulationMapper;
import com.example.finanzas.mapper.SimulationResponseMapper;
import com.example.finanzas.repository.ClienteRepository;
import com.example.finanzas.repository.CreditoRepository;
import com.example.finanzas.repository.VehiculoRepository;
import com.example.finanzas.service.calculation.BigDecimalMath;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class SimulationService {

    private static final BigDecimal HUNDRED = BigDecimalMath.of("100");

    private final FintechEngineService fintechEngineService;
    private final SimulationMapper simulationMapper;
    private final SimulationResponseMapper responseMapper;
    private final CreditoRepository creditoRepository;
    private final VehiculoRepository vehiculoRepository;
    private final ClienteRepository clienteRepository;

    @Transactional
    public SimulationCalculationResponseDto calculateAndPersist(SimulationDraftDto draft, Cliente cliente) {
        SimulationRequestDTO engineRequest = simulationMapper.toEngineRequest(draft);
        SimulationResponseDTO engineResponse = fintechEngineService.calculate(engineRequest);

        updateClienteFromDraft(cliente, draft);
        clienteRepository.save(cliente);

        Vehiculo vehiculo = new Vehiculo();
        vehiculo.setMoneda(Moneda.valueOf(draft.getVehicle().getCurrency()));
        vehiculo.setPrecio(draft.getVehicle().getVehiclePrice());
        vehiculo = vehiculoRepository.save(vehiculo);

        Credito credito = buildCredito(draft, engineRequest, engineResponse, cliente, vehiculo);
        credito = creditoRepository.save(credito);

        for (var row : engineResponse.getSchedule()) {
            Cronograma cronograma = new Cronograma();
            cronograma.setCredito(credito);
            cronograma.setPeriodo(row.getPeriod());
            cronograma.setSaldoInicial(row.getInitialBalance());
            cronograma.setInteres(row.getInterest());
            cronograma.setAmortizacion(row.getAmortization());
            cronograma.setCuotaSeguroDesgravamen(row.getDesgravamenInsurance());
            cronograma.setCuotaSeguroVehicular(row.getVehicleInsurance());
            cronograma.setGastosAdministrativos(row.getAdministrativeExpense());
            cronograma.setCuotaBalon(row.getBalloonPayment());
            cronograma.setCuotaTotal(row.getTotalMonthlyPayment());
            cronograma.setSaldoFinal(row.getFinalBalance());
            credito.getCronogramas().add(cronograma);
        }

        creditoRepository.save(credito);

        return responseMapper.toApiResponse(
                credito.getIdCredito(),
                credito.getFechaCreacion(),
                draft,
                engineResponse);
    }

    @Transactional(readOnly = true)
    public SimulationCalculationResponseDto getById(Long id, Cliente cliente) {
        Credito credito = creditoRepository.findByIdCreditoAndClienteIdCliente(id, cliente.getIdCliente())
                .orElseThrow(() -> new NotFoundException("Simulation not found"));

        SimulationDraftDto input = rebuildDraftFromCredito(credito);
        return responseMapper.toApiResponse(
                credito.getIdCredito(),
                credito.getFechaCreacion(),
                input,
                rebuildEngineResponse(credito));
    }

    public List<SimulationHistoryItemDto> getHistory(Cliente cliente) {
        return creditoRepository.findByClienteIdClienteOrderByFechaCreacionDesc(cliente.getIdCliente())
                .stream()
                .map(c -> {
                    SimulationDraftDto draft = rebuildDraftFromCredito(c);
                    SimulationCalculationResponseDto response = responseMapper.fromCredito(c, draft);
                    return responseMapper.toHistoryItem(c, response);
                })
                .toList();
    }

    @Transactional
    public void delete(Long id, Cliente cliente) {
        Credito credito = creditoRepository.findByIdCreditoAndClienteIdCliente(id, cliente.getIdCliente())
                .orElseThrow(() -> new NotFoundException("Simulation not found"));
        creditoRepository.delete(credito);
    }

    public SimulationResponseDTO calculateEngineOnly(SimulationRequestDTO request) {
        return fintechEngineService.calculate(request);
    }

    private void updateClienteFromDraft(Cliente cliente, SimulationDraftDto draft) {
        cliente.setDni(draft.getClient().getDocumentNumber());
        cliente.setNombre(draft.getClient().getFullName());
    }

    private Credito buildCredito(
            SimulationDraftDto draft,
            SimulationRequestDTO engineRequest,
            SimulationResponseDTO engineResponse,
            Cliente cliente,
            Vehiculo vehiculo) {

        Credito credito = new Credito();
        credito.setCliente(cliente);
        credito.setVehiculo(vehiculo);
        credito.setPorcentajeCuotaInicial(draft.getCredit().getInitialFeePercentage());
        credito.setPorcentajeCuotaBalon(draft.getCredit().getBalloonFeePercentage());
        credito.setMontoFinanciado(engineResponse.getNetLoanAmount());
        credito.setTipoTasa(mapTipoTasa(engineRequest.getRateType()));
        credito.setValorTasa(draft.getInterest().getRateValuePercentage());
        credito.setPlazoMeses(draft.getCredit().getTermMonths());
        credito.setTipoPeriodoGracia(mapGraceType(draft.getGracePeriod().getType()));
        credito.setMesesGracia(draft.getGracePeriod().getMonths());
        credito.setTasaSeguroDesgravamen(draft.getCosts().getLifeInsuranceMonthlyRatePercentage());
        credito.setTasaSeguroVehicular(draft.getCosts().getVehicleInsuranceAnnualRatePercentage());
        credito.setGastosAdministrativosMensuales(draft.getCosts().getAdministrativeExpenses());
        credito.setTasaReferenciaVan(draft.getFinancialAnalysis().getTargetTirPercentage());
        credito.setTemCalculada(engineResponse.getMonthlyEffectiveRate());
        credito.setValorCuotaBalon(engineResponse.getBalloonPayment());
        credito.setCuotaMensualOrdinaria(engineResponse.getRegularMonthlyInstallment());
        credito.setTcea(toStoredPercentage(engineResponse.getTcea()));
        credito.setTir(toStoredPercentage(engineResponse.getMonthlyIrr()));
        credito.setVan(engineResponse.getNpvAtReferenceRate());
        credito.setEstado(EstadoSimulacion.CALCULATED);
        credito.setFechaCreacion(Instant.now());
        return credito;
    }

    private TipoTasa mapTipoTasa(String rateType) {
        return "TEA".equalsIgnoreCase(rateType) ? TipoTasa.Efectiva : TipoTasa.Nominal;
    }

    private TipoPeriodoGracia mapGraceType(String type) {
        return switch (type.toUpperCase()) {
            case "TOTAL" -> TipoPeriodoGracia.Total;
            case "PARTIAL" -> TipoPeriodoGracia.Parcial;
            default -> TipoPeriodoGracia.Ninguno;
        };
    }

    private BigDecimal toStoredPercentage(BigDecimal decimalRate) {
        if (decimalRate == null) {
            return null;
        }
        return BigDecimalMath.scaleOutput(decimalRate.multiply(HUNDRED));
    }

    private SimulationDraftDto rebuildDraftFromCredito(Credito credito) {
        return SimulationDraftDto.builder()
                .client(com.example.finanzas.dto.api.ClientDataDto.builder()
                        .documentNumber(credito.getCliente().getDni())
                        .fullName(credito.getCliente().getNombre())
                        .build())
                .vehicle(com.example.finanzas.dto.api.VehicleDataDto.builder()
                        .currency(credito.getVehiculo().getMoneda().name())
                        .vehiclePrice(credito.getVehiculo().getPrecio())
                        .build())
                .credit(com.example.finanzas.dto.api.CreditConfigurationDto.builder()
                        .initialFeePercentage(credito.getPorcentajeCuotaInicial())
                        .balloonFeePercentage(credito.getPorcentajeCuotaBalon())
                        .termMonths(credito.getPlazoMeses())
                        .build())
                .interest(com.example.finanzas.dto.api.InterestConfigurationDto.builder()
                        .rateType(credito.getTipoTasa() == TipoTasa.Efectiva ? "TEA" : "TEM")
                        .rateValuePercentage(credito.getValorTasa())
                        .paymentFrequency("MONTHLY")
                        .build())
                .gracePeriod(com.example.finanzas.dto.api.GracePeriodConfigurationDto.builder()
                        .type(mapGraceTypeToApi(credito.getTipoPeriodoGracia()))
                        .months(credito.getMesesGracia())
                        .build())
                .financialAnalysis(com.example.finanzas.dto.api.FinancialAnalysisConfigurationDto.builder()
                        .targetTirPercentage(credito.getTasaReferenciaVan())
                        .build())
                .costs(com.example.finanzas.dto.api.CostsConfigurationDto.builder()
                        .lifeInsuranceMonthlyRatePercentage(credito.getTasaSeguroDesgravamen())
                        .administrativeExpenses(credito.getGastosAdministrativosMensuales())
                        .vehicleInsuranceAnnualRatePercentage(credito.getTasaSeguroVehicular())
                        .build())
                .build();
    }

    private String mapGraceTypeToApi(TipoPeriodoGracia type) {
        return switch (type) {
            case Total -> "TOTAL";
            case Parcial -> "PARTIAL";
            case Ninguno -> "NONE";
        };
    }

    private SimulationResponseDTO rebuildEngineResponse(Credito credito) {
        return SimulationResponseDTO.builder()
                .netLoanAmount(credito.getMontoFinanciado())
                .balloonPayment(credito.getValorCuotaBalon())
                .monthlyEffectiveRate(credito.getTemCalculada())
                .regularMonthlyInstallment(credito.getCuotaMensualOrdinaria())
                .monthlyIrr(credito.getTir() != null
                        ? BigDecimalMath.divide(credito.getTir(), HUNDRED) : null)
                .tcea(credito.getTcea() != null
                        ? BigDecimalMath.divide(credito.getTcea(), HUNDRED) : null)
                .npvAtReferenceRate(credito.getVan())
                .viability(credito.getVan() != null && credito.getVan().compareTo(BigDecimal.ZERO) >= 0
                        ? "VIABLE" : "NOT_VIABLE")
                .schedule(credito.getCronogramas().stream()
                        .map(c -> com.example.finanzas.dto.PaymentScheduleRowDTO.builder()
                                .period(c.getPeriodo())
                                .initialBalance(c.getSaldoInicial())
                                .interest(c.getInteres())
                                .amortization(c.getAmortizacion())
                                .desgravamenInsurance(c.getCuotaSeguroDesgravamen())
                                .vehicleInsurance(c.getCuotaSeguroVehicular())
                                .administrativeExpense(c.getGastosAdministrativos())
                                .balloonPayment(c.getCuotaBalon())
                                .regularInstallment(c.getCuotaTotal())
                                .totalMonthlyPayment(c.getCuotaTotal())
                                .finalBalance(c.getSaldoFinal())
                                .build())
                        .toList())
                .build();
    }
}
