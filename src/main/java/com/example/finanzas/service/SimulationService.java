package com.example.finanzas.service;

import com.example.finanzas.dto.SimulationRequestDTO;
import com.example.finanzas.dto.SimulationResponseDTO;
import com.example.finanzas.dto.api.SimulationCalculationResponseDto;
import com.example.finanzas.dto.api.SimulationDraftDto;
import com.example.finanzas.dto.api.SimulationHistoryItemDto;
import com.example.finanzas.dto.api.PageResponseDto;
import com.example.finanzas.dto.api.PaymentScheduleRowApiDto;
import com.example.finanzas.entity.Cliente;
import com.example.finanzas.entity.Credito;
import com.example.finanzas.entity.Cronograma;
import com.example.finanzas.entity.Vehiculo;
import com.example.finanzas.entity.enums.EstadoSimulacion;
import com.example.finanzas.entity.enums.Moneda;
import com.example.finanzas.entity.enums.TipoPeriodoGracia;
import com.example.finanzas.entity.enums.TipoTasa;
import com.example.finanzas.exception.BadRequestException;
import com.example.finanzas.exception.NotFoundException;
import com.example.finanzas.mapper.SimulationMapper;
import com.example.finanzas.mapper.SimulationResponseMapper;
import com.example.finanzas.repository.ClienteRepository;
import com.example.finanzas.repository.CreditoRepository;
import com.example.finanzas.repository.CronogramaRepository;
import com.example.finanzas.repository.VehiculoRepository;
import com.example.finanzas.service.calculation.BigDecimalMath;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class SimulationService {

    private static final BigDecimal HUNDRED = BigDecimalMath.of("100");
    private static final ZoneId BUSINESS_ZONE = ZoneId.of("America/Lima");

    private final FintechEngineService fintechEngineService;
    private final SimulationMapper simulationMapper;
    private final SimulationResponseMapper responseMapper;
    private final CreditoRepository creditoRepository;
    private final CronogramaRepository cronogramaRepository;
    private final VehiculoRepository vehiculoRepository;
    private final ClienteRepository clienteRepository;

    @Transactional
    public SimulationCalculationResponseDto calculateAndPersist(SimulationDraftDto draft, Cliente cliente) {
        validateDraft(draft, cliente);
        SimulationRequestDTO engineRequest = simulationMapper.toEngineRequest(draft);
        SimulationResponseDTO engineResponse = fintechEngineService.calculate(engineRequest);

        updateClienteFromDraft(cliente, draft);
        clienteRepository.save(cliente);

        Vehiculo vehiculo = new Vehiculo();
        vehiculo.setMoneda(Moneda.valueOf(draft.getVehicle().getCurrency().trim().toUpperCase()));
        vehiculo.setPrecio(draft.getVehicle().getVehiclePrice());
        vehiculo = vehiculoRepository.save(vehiculo);

        Credito credito = buildCredito(draft, engineRequest, engineResponse, cliente, vehiculo);
        credito = creditoRepository.save(credito);

        for (var row : engineResponse.getSchedule()) {
            Cronograma cronograma = new Cronograma();
            cronograma.setCredito(credito);
            cronograma.setPeriodo(row.getPeriod());
            cronograma.setFechaPago(row.getPaymentDate());
            cronograma.setSaldoInicial(row.getInitialBalance());
            cronograma.setInteres(row.getInterest());
            cronograma.setAmortizacion(row.getAmortization());
            cronograma.setCuotaSeguroDesgravamen(row.getDesgravamenInsurance());
            cronograma.setCuotaSeguroVehicular(row.getVehicleInsurance());
            cronograma.setGastosAdministrativos(row.getAdministrativeExpense());
            cronograma.setCuotaBalon(row.getBalloonPayment());
            cronograma.setCuotaMensualOrdinaria(row.getRegularInstallment());
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

    @Transactional(readOnly = true)
    public PageResponseDto<SimulationHistoryItemDto> getHistoryPage(
            Cliente cliente,
            int page,
            int size,
            LocalDate createdFrom,
            LocalDate createdTo) {

        Pageable pageable = PageRequest.of(
                Math.max(page, 0),
                Math.max(size, 1),
                Sort.by(Sort.Direction.DESC, "fechaCreacion"));
        Instant from = toStartInstant(createdFrom);
        Instant to = toEndInstant(createdTo);
        var historyPage = findHistoryPage(cliente.getIdCliente(), from, to, pageable);

        List<SimulationHistoryItemDto> content = historyPage.getContent().stream()
                .map(c -> {
                    SimulationDraftDto draft = rebuildDraftFromCredito(c);
                    SimulationCalculationResponseDto response = responseMapper.fromCredito(c, draft);
                    return responseMapper.toHistoryItem(c, response);
                })
                .toList();

        return PageResponseDto.<SimulationHistoryItemDto>builder()
                .content(content)
                .page(historyPage.getNumber())
                .size(historyPage.getSize())
                .totalElements(historyPage.getTotalElements())
                .totalPages(historyPage.getTotalPages())
                .first(historyPage.isFirst())
                .last(historyPage.isLast())
                .build();
    }

    @Transactional(readOnly = true)
    public List<PaymentScheduleRowApiDto> getSchedule(Long id, Cliente cliente) {
        if (!creditoRepository.findByIdCreditoAndClienteIdCliente(id, cliente.getIdCliente()).isPresent()) {
            throw new NotFoundException("Simulation not found");
        }
        return cronogramaRepository
                .findByCreditoIdCreditoAndCreditoClienteIdClienteOrderByPeriodoAsc(id, cliente.getIdCliente())
                .stream()
                .map(responseMapper::toApiScheduleRow)
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

    private void validateDraft(SimulationDraftDto draft, Cliente cliente) {
        String documentNumber = draft.getClient().getDocumentNumber();
        if (clienteRepository.existsByDniAndIdClienteNot(documentNumber, cliente.getIdCliente())) {
            throw new BadRequestException("DNI already registered");
        }

        String currency = draft.getVehicle().getCurrency();
        if (!"PEN".equalsIgnoreCase(currency) && !"USD".equalsIgnoreCase(currency)) {
            throw new BadRequestException("currency must be PEN or USD");
        }

        int termMonths = draft.getCredit().getTermMonths();
        if (termMonths != 24 && termMonths != 36 && termMonths != 48) {
            throw new BadRequestException("termMonths must be 24, 36 or 48");
        }

        String graceType = draft.getGracePeriod().getType();
        int graceMonths = draft.getGracePeriod().getMonths();
        if ("NONE".equalsIgnoreCase(graceType) && graceMonths != 0) {
            throw new BadRequestException("grace months must be 0 when grace type is NONE");
        }

        String rateType = draft.getInterest().getRateType();
        if (("TNA".equalsIgnoreCase(rateType) || "Nominal".equalsIgnoreCase(rateType))
                && (draft.getInterest().getCapitalizationFrequency() == null
                || draft.getInterest().getCapitalizationFrequency().isBlank())) {
            throw new BadRequestException("capitalizationFrequency is required for nominal annual rate");
        }
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
        credito.setCuotaInicialMonto(engineResponse.getDownPayment());
        credito.setMontoFinanciado(engineResponse.getNetLoanAmount());
        credito.setTipoTasa(mapTipoTasa(engineRequest.getRateType()));
        credito.setValorTasa(draft.getInterest().getRateValuePercentage());
        credito.setFrecuenciaCapitalizacion(engineRequest.getCapitalizationFrequency());
        credito.setPlazoMeses(draft.getCredit().getTermMonths());
        credito.setTipoPeriodoGracia(mapGraceType(draft.getGracePeriod().getType()));
        credito.setMesesGracia(draft.getGracePeriod().getMonths());
        credito.setTasaSeguroDesgravamen(draft.getCosts().getLifeInsuranceMonthlyRatePercentage());
        credito.setTasaSeguroVehicular(draft.getCosts().getVehicleInsuranceAnnualRatePercentage());
        credito.setGastosAdministrativosMensuales(draft.getCosts().getAdministrativeExpenses());
        credito.setTasaReferenciaVan(draft.getFinancialAnalysis().getCokAnnualPercentage());
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
        return decimalRate.multiply(HUNDRED).setScale(4, java.math.RoundingMode.HALF_UP);
    }

    private Instant toStartInstant(LocalDate date) {
        return date != null ? date.atStartOfDay(BUSINESS_ZONE).toInstant() : null;
    }

    private Instant toEndInstant(LocalDate date) {
        return date != null ? date.plusDays(1).atStartOfDay(BUSINESS_ZONE).minusNanos(1).toInstant() : null;
    }

    private org.springframework.data.domain.Page<Credito> findHistoryPage(
            Long idCliente,
            Instant createdFrom,
            Instant createdTo,
            Pageable pageable) {
        if (createdFrom != null && createdTo != null) {
            return creditoRepository.findByClienteIdClienteAndFechaCreacionBetween(
                    idCliente, createdFrom, createdTo, pageable);
        }
        if (createdFrom != null) {
            return creditoRepository.findByClienteIdClienteAndFechaCreacionGreaterThanEqual(
                    idCliente, createdFrom, pageable);
        }
        if (createdTo != null) {
            return creditoRepository.findByClienteIdClienteAndFechaCreacionLessThanEqual(
                    idCliente, createdTo, pageable);
        }
        return creditoRepository.findByClienteIdCliente(idCliente, pageable);
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
                        .rateType(credito.getTipoTasa() == TipoTasa.Efectiva ? "TEA" : "TNA")
                        .rateValuePercentage(credito.getValorTasa())
                        .paymentFrequency("MONTHLY")
                        .capitalizationFrequency(credito.getFrecuenciaCapitalizacion() != null
                                ? credito.getFrecuenciaCapitalizacion().name() : null)
                        .build())
                .gracePeriod(com.example.finanzas.dto.api.GracePeriodConfigurationDto.builder()
                        .type(mapGraceTypeToApi(credito.getTipoPeriodoGracia()))
                        .months(credito.getMesesGracia())
                        .build())
                .financialAnalysis(com.example.finanzas.dto.api.FinancialAnalysisConfigurationDto.builder()
                        .cokAnnualPercentage(credito.getTasaReferenciaVan())
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
                .downPayment(credito.getCuotaInicialMonto())
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
                                .paymentDate(c.getFechaPago())
                                .initialBalance(c.getSaldoInicial())
                                .interest(c.getInteres())
                                .amortization(c.getAmortizacion())
                                .desgravamenInsurance(c.getCuotaSeguroDesgravamen())
                                .vehicleInsurance(c.getCuotaSeguroVehicular())
                                .administrativeExpense(c.getGastosAdministrativos())
                                .balloonPayment(c.getCuotaBalon())
                                .regularInstallment(c.getCuotaMensualOrdinaria())
                                .totalMonthlyPayment(c.getCuotaTotal())
                                .finalBalance(c.getSaldoFinal())
                                .build())
                        .toList())
                .build();
    }
}
