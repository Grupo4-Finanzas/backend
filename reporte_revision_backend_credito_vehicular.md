# Reporte de Revision Backend - Credito Vehicular Inteligente

Fecha de revision: 2026-07-05

## 1. Resumen ejecutivo

Estado general: **Compatible con el modelo academico y parcialmente compatible alto con el modelo BCP**.

El backend implementa correctamente el nucleo financiero del credito vehicular bajo Compra Inteligente: cuota inicial, monto financiado, cuota balon, conversion de tasas, cuota francesa con balon, periodos de gracia, cronograma de pagos, VAN, TIR y TCEA desde el punto de vista del deudor. Tambien persiste los componentes principales del credito y del cronograma, incluyendo `cuota_inicial_monto`, `cuota_mensual_ordinaria` y `fecha_pago`.

Respecto al modelo BCP, el sistema ya aplica ajuste por dias para seguros usando periodo academico de 30 dias:

- Seguro de desgravamen: `saldo deudor * tasa mensual * 12 * 30 / 365`.
- Seguro vehicular: `precio vehiculo * tasa anual * 30 / 365`.

La compatibilidad no se marca como plena solo por decisiones de alcance: no existen tablas fisicas separadas `FlujoCaja` e `IndicadoresFinancieros`, aunque estan documentadas y sus datos son reconstruibles o persistidos en `Credito`; ademas, el endpoint directo `/calculate-engine` sigue expuesto como herramienta de prueba.

Verificacion ejecutada:

```powershell
.\mvnw.cmd test
```

Resultado: **23 tests contabilizados: 22 ejecutados correctamente y 1 omitido por falta de Docker/Testcontainers**.

## 2. Hallazgos criticos

No se encontraron errores criticos activos que invaliden los calculos financieros principales del modelo academico ni del modelo BCP con periodo de 30 dias.

### Hallazgos relevantes pendientes

| Prioridad | Estado | Archivo | Clase / metodo | Problema | Por que importa | Correccion sugerida |
|---|---|---|---|---|---|---|
| Media | Pendiente documentado | `docs/decisiones-persistencia-financiera.md` | Documento | No existe tabla fisica `FlujoCaja`. | El modelo esperado menciona una tabla equivalente, aunque los flujos se reconstruyen desde `Credito` y `Cronograma`. | Mantener documentacion si el alcance academico lo permite, o crear entidad `FlujoCaja` si el docente exige trazabilidad fisica. |
| Media | Pendiente documentado | `docs/decisiones-persistencia-financiera.md` | Documento | No existe tabla separada `IndicadoresFinancieros`. | VAN, TIR, TCEA, TEM y COK viven en `Credito`. Es suficiente para relacion 1:1, pero no para versionar recalculos. | Crear entidad `IndicadoresFinancieros` solo si se requieren multiples escenarios por credito. |
| Baja | Pendiente | `src/main/java/com/example/finanzas/controller/SimulationController.java` | `calculateEngine` | Endpoint de calculo directo expuesto como endpoint de prueba. | Puede ser util para QA, pero no deberia quedar publico en produccion. | Protegerlo con perfil `dev/test`, rol administrativo o eliminarlo del despliegue productivo. |

### Hallazgos criticos ya corregidos

| Estado | Archivo | Problema original | Correccion aplicada |
|---|---|---|---|
| Corregido | `FintechEngineService.java` | La cuota con balon multiplicaba por el factor de anualidad. | Ahora divide entre el factor de anualidad. |
| Corregido | `FintechEngineService.java` | El saldo final no descontaba la cuota balon en el ultimo periodo. | Ahora descuenta amortizacion ordinaria y `balloonPaymentPaid`. |
| Corregido | `FintechEngineService.java` | Gracia total cobraba seguros y gastos. | Ahora aplica modelo academico: pago `0`, flujo `0` y capitalizacion solo de interes. |
| Corregido | `FintechEngineService.java` | VAN mensualizaba COK con division simple entre 12. | Ahora convierte COK anual efectiva con `RateConverter.toMonthlyEffective("TEA", ...)`. |
| Corregido | `RateConverter.java` | TNA se convertia como `TNA / 12`. | Ahora usa `TEM = (1 + TNA/m)^(m/12) - 1`. |
| Corregido | `SimulationMapper.java` | Mapeaba `TEM` a `TNA`. | Ahora acepta `TEA`/`Efectiva` y `TNA`/`Nominal`; `TEM` se rechaza. |
| Corregido | `FinancialAnalysisConfigurationDto.java` | Campo `targetTirPercentage` usado como COK. | Ahora se llama `cokAnnualPercentage`. |
| Corregido | `IrrSolver.java` | Si no habia raiz, devolvia una aproximacion basada en guess. | Ahora lanza error controlado si los flujos no bracketan raiz. |
| Corregido | `BigDecimalMath.java` | `pow` usaba `double` y `Math.pow`. | Ahora usa `BigDecimal`, potencias enteras y raices por Newton-Raphson. |
| Corregido | `SimulationRequestDTO.java`, `CreditConfigurationDto.java` | Permitian cuota balon `0%`. | Ahora validan `35%-50%` en API y `0.35-0.50` en engine. |
| Corregido | `Cronograma.java`, `SimulationService.java` | No persistia cuota ordinaria separada. | Se agrego `cuota_mensual_ordinaria` y se reconstruye desde ese campo. |
| Corregido | `Cronograma.java` | No persistia `fecha_pago`. | Se agrego `fecha_pago` y se calculan fechas mensuales. |
| Corregido | `SimulationMapper.java` | Seguros no usaban ajuste BCP por dias. | Desgravamen usa `tasa mensual * 12 * 30 / 365`; vehicular usa `tasa anual * 30 / 365`. |
| Corregido | `Credito.java` | No persistia `cuota_inicial_monto`. | Se agrego `cuotaInicialMonto`, se guarda desde `engineResponse.getDownPayment()` y se creo migracion `V2`. |
| Corregido | `application.properties` | Contenia credenciales reales y `ddl-auto=update`. | Ahora usa variables de entorno, `ddl-auto=validate` y Flyway. |

## 3. Revision financiera

| Punto | Estado | Observacion |
|---|---|---|
| Cuota inicial | Correcto | Implementa `CI = PV * pCI`, valida `10%-30%` y persiste monto en `credito.cuota_inicial_monto`. |
| Monto financiado | Correcto | Implementa `P = PV - CI`. |
| Cuota balon | Correcto | Implementa `CB = PV * pCB` y valida rango BCP `35%-50%`. |
| Conversion TEA a TEM | Correcto | Usa `(1 + TEA)^(1/12) - 1`. |
| Conversion TNA a TEM | Correcto | Usa frecuencia de capitalizacion con el mapeo esperado. |
| Tipo de tasa | Correcto | Soporta `TEA`/`TNA` y `Efectiva`/`Nominal`; rechaza `TEM` como entrada anual. |
| Gracia parcial | Correcto | Paga intereses, seguros y gastos; el saldo no disminuye. |
| Gracia total | Correcto academico | Pago `0`, flujo `0` y capitalizacion solo de interes. |
| Cuota ordinaria con balon | Correcto | Usa valor presente de la cuota balon y divide por el factor de anualidad. |
| Ultimo periodo con balon | Correcto | El pago total incluye balon y el saldo final tambien lo descuenta. |
| Cronograma | Correcto | Incluye periodo, fecha, saldo inicial, interes, amortizacion, cuota ordinaria, seguros, gastos, balon, pago total y saldo final. |
| Seguro desgravamen | Correcto | Sobre saldo deudor vigente con ajuste BCP de 30 dias: `tasa mensual * 12 * 30 / 365`. |
| Seguro vehicular | Correcto | Sobre precio del vehiculo con ajuste BCP de 30 dias: `tasa anual * 30 / 365`. |
| Gastos administrativos | Correcto | Se suman al pago total y al flujo del deudor; valida no negativo. |
| VAN | Correcto | Flujo con `F0` positivo y pagos negativos; COK anual efectiva convertida a mensual efectiva. |
| TIR | Correcto | Se calcula desde flujos del deudor y falla controladamente si no existe raiz bracketada. |
| TCEA | Correcto | Anualiza la TIR mensual y considera pago completo, incluyendo seguros, gastos y balon. |

## 4. Revision tecnica Spring Boot

| Area | Estado | Observacion |
|---|---|---|
| Estructura de paquetes | Correcto | Separacion por `controller`, `service`, `repository`, `entity`, `dto`, `mapper`, `exception`, `security`. |
| Entidades JPA | Correcto parcial | Existen `Cliente`, `Vehiculo`, `Credito`, `Cronograma`. `FlujoCaja` e `IndicadoresFinancieros` estan documentados como no separados. |
| DTOs | Correcto | DTOs de API y motor tienen validaciones de rango, moneda, DNI, tasa, costos, gracia y balon. |
| Servicios | Correcto | `FintechEngineService` concentra calculo financiero; `SimulationService` valida dominio y persiste. |
| Controladores REST | Correcto parcial | Endpoints funcionales. `/calculate-engine` sigue publico para pruebas. |
| Repositorios | Correcto | Repositorios JPA para cliente, vehiculo, credito y cronograma. |
| Validaciones | Correcto | DNI exacto, DNI unico, moneda, CI, balon, plazo, tasa, costos y frecuencia TNA cubiertos. |
| Manejo de errores | Correcto | `GlobalExceptionHandler` cubre validacion, bad request, unauthorized, not found e `IllegalArgumentException`. |
| Tests | Correcto | Pruebas cubren formulas principales, tasa nominal, COK/VAN, gracia total, TIR sin raiz, BigDecimalMath, seguros BCP y persistencia. |
| BigDecimal | Correcto | Calculos financieros usan `BigDecimal`; `pow` ya no usa `double`/`Math.pow`. |
| Persistencia | Correcto parcial | Persiste credito, cronograma e indicadores dentro de `Credito`; flujos no se persisten por decision documentada. |
| Configuracion | Correcto | Secretos por variables de entorno, Flyway habilitado y JPA en `validate`. |

## 5. Revision de base de datos

Modelo actual: **suficiente para el alcance academico**, con dos diferencias documentadas frente al modelo conceptual ideal.

Tablas o entidades presentes:

| Entidad esperada | Estado | Observacion |
|---|---|---|
| Usuario | Parcial | `Cliente` cumple tambien rol de usuario autenticado. No hay entidad `Usuario` separada. |
| Cliente | Presente | Guarda DNI unico, nombre, email unico, password hash y rol. |
| Vehiculo | Presente | Guarda moneda y precio. |
| Credito | Presente | Guarda cuota inicial porcentaje y monto, monto financiado, tasa, frecuencia, plazo, gracia, costos, TEM, balon, cuota ordinaria, VAN, TIR y TCEA. |
| CronogramaPago | Presente | Guarda periodo, `fecha_pago`, saldo, interes, amortizacion, seguros, gastos, balon, cuota ordinaria, pago total y saldo final. |
| FlujoCaja | No separado | Documentado: se reconstruye desde `Credito` y `Cronograma`. |
| IndicadoresFinancieros | No separado | Documentado: indicadores viven en `Credito` por relacion 1:1. |

Migraciones:

- Flyway agregado en `pom.xml`.
- Migracion inicial: `src/main/resources/db/migration/V1__initial_schema.sql`.
- Migracion incremental: `src/main/resources/db/migration/V2__add_cuota_inicial_monto_to_credito.sql`.
- `spring.jpa.hibernate.ddl-auto=validate`.

Configuracion:

- `application.properties` usa `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`, `JWT_SECRET`.
- No quedan credenciales reales en `src/main/resources`.

## 6. Correcciones propuestas

1. Restringir o condicionar `/api/v1/simulations/calculate-engine` a perfil de desarrollo/pruebas o a rol administrativo.
2. Decidir formalmente si se crean tablas `FlujoCaja` e `IndicadoresFinancieros`; por ahora estan justificadas como derivadas/documentadas.
3. Ampliar tests con valores esperados de VAN/TIR/TCEA contra una hoja de calculo de referencia del informe.
4. Revisar precision de columnas monetarias `NUMERIC(10,2)` si se simularan vehiculos o carteras de mayor monto.

## 7. Checklist final

| Criterio | Estado | Observacion | Archivo relacionado |
|---|---|---|---|
| Login y registro | Correcto | JWT + BCrypt implementados. | `AuthService.java`, `SecurityConfig.java` |
| Password hasheada | Correcto | Usa `PasswordEncoder`. | `AuthService.java` |
| Registro cliente | Correcto | DNI exacto, unico y nombre maximo 100. | `Cliente.java`, `ClientDataDto.java`, `SimulationService.java` |
| Registro vehiculo | Correcto | Guarda moneda y precio. | `Vehiculo.java` |
| Moneda PEN/USD | Correcto | Enum y validacion de DTO. | `Moneda.java`, `VehicleDataDto.java` |
| Tipo tasa efectiva/nominal | Correcto | `TEA`/`TNA` y textos equivalentes soportados. | `SimulationMapper.java` |
| Capitalizacion nominal | Correcto | Mapeo de `m` implementado; TNA exige frecuencia. | `RateConverter.java`, `SimulationService.java` |
| Cuota inicial | Correcto | Formula, rango `10%-30%` y persistencia del monto. | `FintechEngineService.java`, `Credito.java` |
| Monto financiado | Correcto | Formula correcta. | `FintechEngineService.java` |
| Cuota balon BCP | Correcto | Formula y validacion `35%-50%`. | `SimulationRequestDTO.java`, `CreditConfigurationDto.java` |
| Cuota francesa con balon | Correcto | Formula corregida con valor presente del balon. | `FintechEngineService.java` |
| Descuento de balon en saldo final | Correcto | Ultimo saldo descuenta `balloonPaymentPaid`. | `FintechEngineService.java` |
| Gracia parcial | Correcto | Paga interes, seguros y gastos; saldo no disminuye. | `FintechEngineService.java` |
| Gracia total | Correcto academico | Pago `0`, flujo `0`, capitalizacion de interes. | `FintechEngineService.java` |
| Cronograma completo | Correcto | Incluye fecha y componentes requeridos. | `Cronograma.java`, `PaymentScheduleRowDTO.java` |
| VAN deudor | Correcto | Signos correctos y COK anual efectiva mensualizada correctamente. | `FintechEngineService.java`, `IrrSolver.java` |
| TIR deudor | Correcto | Flujo deudor y manejo de no raiz. | `IrrSolver.java` |
| TCEA completa | Correcto | Usa flujo completo del deudor. | `FintechEngineService.java` |
| Seguro desgravamen BCP | Correcto | Sobre saldo con ajuste `tasa mensual * 12 * 30 / 365`. | `SimulationMapper.java`, `FintechEngineService.java` |
| Seguro vehicular BCP | Correcto | Sobre precio con ajuste `tasa anual * 30 / 365`. | `SimulationMapper.java`, `FintechEngineService.java` |
| Persistencia cronograma | Correcto | Persiste componentes y fecha. | `Cronograma.java`, `SimulationService.java` |
| Persistencia indicadores | Correcto parcial | Guardados en `Credito`, no en tabla propia. | `Credito.java`, `docs/decisiones-persistencia-financiera.md` |
| Persistencia flujos | Justificado | No hay tabla; se reconstruyen desde pagos persistidos. | `docs/decisiones-persistencia-financiera.md` |
| Migraciones | Correcto | Flyway con migraciones `V1` y `V2`; JPA en validate. | `pom.xml`, `src/main/resources/db/migration` |
| Secretos | Correcto | Configuracion por variables de entorno. | `application.properties` |
| Endpoint QA `calculate-engine` | Pendiente | Sigue permitido publicamente. | `SecurityConfig.java`, `SimulationController.java` |
| Tests financieros | Correcto | Cubren formulas corregidas y casos de error principales. | `src/test/java` |

## 8. Conclusion

El backend ya no presenta errores financieros criticos activos. Para el modelo academico del curso, el nucleo de calculo, validacion y persistencia es compatible. Para el modelo BCP, los puntos financieros principales tambien estan alineados usando periodo academico de 30 dias. Los pendientes restantes son de alcance y arquitectura: decidir si se materializan `FlujoCaja` e `IndicadoresFinancieros`, y proteger el endpoint directo de calculo antes de un despliegue productivo.
