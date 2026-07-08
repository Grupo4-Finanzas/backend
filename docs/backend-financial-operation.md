# Analisis de funcionamiento financiero y tecnico del backend

Este documento describe como funciona el backend del sistema de Credito Vehicular Inteligente, que calculos financieros realiza, que precision decimal usa, que opciones admite, como persiste la informacion y por que el flujo esta alineado con el enfoque academico del curso y con reglas relevantes del modelo Compra Inteligente BCP.

## 1. Proposito del backend

El backend implementa una API REST en Spring Boot para:

- Registrar y autenticar clientes.
- Calcular simulaciones de credito vehicular bajo modalidad Compra Inteligente.
- Aplicar metodo frances vencido ordinario con cuota balon.
- Manejar tasa efectiva anual y tasa nominal anual con capitalizacion.
- Manejar periodos de gracia: ninguno, parcial y total.
- Calcular cronograma de pagos.
- Calcular indicadores VAN, TIR y TCEA desde el punto de vista del deudor.
- Persistir credito, vehiculo, cliente, cronograma e indicadores principales.
- Consultar historial simple y paginado.
- Exportar reporte PDF y cronograma PDF/XLSX.
- Servir datos al frontend Angular mediante JWT.

## 2. Flujo general de la aplicacion

```text
Angular
  -> Controller REST
  -> DTO validado
  -> Mapper de request
  -> Motor financiero
  -> Mapper de respuesta
  -> Entidades JPA
  -> PostgreSQL
```

Flujo de simulacion:

```text
POST /api/v1/simulations/calculate
  -> SimulationController.calculate
  -> SimulationService.calculateAndPersist
  -> SimulationMapper.toEngineRequest
  -> FintechEngineService.calculate
  -> SimulationResponseMapper.toApiResponse
  -> Persistencia de Vehiculo, Credito y Cronograma
```

Endpoint principal:

```http
POST /api/v1/simulations/calculate
```

Endpoint tecnico sin persistencia:

```http
POST /api/v1/simulations/calculate-engine
```

El endpoint `calculate-engine` existe para QA y pruebas financieras directas. El frontend final debe usar `calculate` porque guarda historial y cronograma.

## 3. Estructura principal del backend

| Paquete | Responsabilidad |
|---|---|
| `controller` | Exposicion REST de autenticacion, simulaciones y dashboard. |
| `service` | Orquestacion de casos de uso y motor financiero. |
| `service.calculation` | Utilidades matematicas: BigDecimal, conversion de tasas, TIR/VAN. |
| `mapper` | Conversion entre DTO frontend, DTO motor, entidades y respuestas API. |
| `dto.api` | Contratos usados por Angular. |
| `dto.auth` | Contratos de login, registro, perfil y recuperacion de contrasena. |
| `entity` | Entidades JPA persistidas. |
| `repository` | Repositorios Spring Data JPA. |
| `config` | Seguridad, CORS y configuracion web. |
| `security` | JWT y filtro de autenticacion. |
| `exception` | Manejo centralizado de errores. |

## 4. Entidades persistidas

| Entidad | Tabla | Funcion |
|---|---|---|
| `Cliente` | `cliente` | Usuario autenticado, DNI, nombre, email, password hash y rol. |
| `Vehiculo` | `vehiculo` | Moneda y precio del vehiculo asociado al credito. |
| `Credito` | `credito` | Parametros financieros, indicadores y estado de la simulacion. |
| `Cronograma` | `cronograma` | Filas del cronograma de pagos. |
| `PasswordResetToken` | `password_reset_token` | Tokens temporales para recuperacion de contrasena. |

El modelo no usa tablas fisicas separadas para `FlujoCaja` ni `IndicadoresFinancieros`. La decision es:

- Los flujos se reconstruyen desde `Credito` y `Cronograma`.
- VAN, TIR, TCEA y TEM se almacenan directamente en `Credito` porque el caso actual es 1:1 entre credito e indicadores.

## 5. Precision decimal y redondeo

La clase central es:

```text
src/main/java/com/example/finanzas/service/calculation/BigDecimalMath.java
```

Configuracion:

| Concepto | Valor |
|---|---:|
| Escala interna | `10` decimales |
| Escala de salida monetaria | `2` decimales |
| Escala de tasa | `7` decimales |
| Redondeo | `RoundingMode.HALF_UP` |
| MathContext | precision `20`, redondeo `HALF_UP` |

Implicaciones:

- Los calculos financieros usan `BigDecimal`, no `double` ni `float`.
- Las operaciones intermedias se mantienen con escala interna de 10 decimales.
- Los montos que ve el usuario se redondean a 2 decimales.
- Las tasas se redondean a 7 decimales internamente.
- Las potencias enteras y fraccionarias mensuales se calculan con BigDecimal.
- Para exponentes mensuales fraccionarios, el backend soporta fracciones compatibles con meses sobre 12, por ejemplo `1/12`, `4/12`, `-48`.

Esto es importante porque el sistema calcula cuotas, VAN, TIR y TCEA, y pequenas diferencias de redondeo pueden afectar el cronograma.

## 6. Opciones admitidas por el modelo

### 6.1 Moneda

```text
PEN
USD
```

La moneda se guarda en `Vehiculo.moneda`.

### 6.2 Tipo de tasa

En el frontend/API:

```text
TEA
TNA
Efectiva
Nominal
```

El mapper normaliza:

| Input frontend | Motor |
|---|---|
| `TEA` | `TEA` |
| `Efectiva` | `TEA` |
| `TNA` | `TNA` |
| `Nominal` | `TNA` |

En la entidad `Credito`, se persiste como:

```text
Efectiva
Nominal
```

### 6.3 Frecuencia de pago

```text
MONTHLY
```

El backend solo contempla pagos mensuales. Esto es coherente con el credito vehicular academico y con la modalidad de cuotas mensuales.

### 6.4 Frecuencia de capitalizacion para TNA

Solo se usa si la tasa es `TNA` o `Nominal`.

```text
Diaria
Quincenal
Mensual
Bimestral
Trimestral
Cuatrimestral
Semestral
Anual
```

Mapeo financiero:

| Frecuencia | Capitalizaciones por anio |
|---|---:|
| `Diaria` | 360 |
| `Quincenal` | 24 |
| `Mensual` | 12 |
| `Bimestral` | 6 |
| `Trimestral` | 4 |
| `Cuatrimestral` | 3 |
| `Semestral` | 2 |
| `Anual` | 1 |

### 6.5 Periodo de gracia

API:

```text
NONE
PARTIAL
TOTAL
```

Entidad:

```text
Ninguno
Parcial
Total
```

Reglas:

- `NONE`: meses debe ser `0`.
- `PARTIAL`: meses entre `1` y `6`.
- `TOTAL`: meses entre `1` y `6`.
- Meses de gracia no pueden ser iguales o mayores al plazo total.

### 6.6 Plazos

```text
24
36
48
```

### 6.7 Compra Inteligente BCP

| Parametro | Rango |
|---|---:|
| Cuota inicial | 10% a 30% |
| Cuota balon | 35% a 50% |

## 7. Validaciones principales

| Campo | Regla |
|---|---|
| `auth.documentNumber` | 8 digitos numericos, requerido en registro/perfil |
| `auth.fullName` | requerido en registro/perfil, maximo 100 caracteres |
| `vehicle.currency` | `PEN` o `USD` |
| `vehicle.vehiclePrice` | mayor a 0 |
| `credit.initialFeePercentage` | 10 a 30 |
| `credit.balloonFeePercentage` | 35 a 50 |
| `credit.termMonths` | 24, 36 o 48 |
| `interest.rateType` | `TEA`, `TNA`, `Efectiva`, `Nominal` |
| `interest.rateValuePercentage` | mayor a 0 |
| `interest.paymentFrequency` | `MONTHLY` |
| `interest.capitalizationFrequency` | requerido si la tasa es nominal |
| `gracePeriod.type` | `NONE`, `PARTIAL`, `TOTAL` |
| `gracePeriod.months` | 0 a 6 |
| `costs.lifeInsuranceMonthlyRatePercentage` | mayor o igual a 0 |
| `costs.administrativeExpenses` | mayor o igual a 0 |
| `costs.vehicleInsuranceAnnualRatePercentage` | mayor o igual a 0 |

## 8. Calculo financiero paso a paso

La clase central es:

```text
src/main/java/com/example/finanzas/service/FintechEngineService.java
```

### 8.1 Cuota inicial

Formula:

```text
CI = PV * pCI
```

Donde:

- `PV`: precio del vehiculo.
- `pCI`: porcentaje de cuota inicial en decimal.

Ejemplo: si el frontend envia `20`, el mapper lo convierte a `0.20`.

### 8.2 Monto financiado

Formula:

```text
P = PV - CI
```

Este valor se usa como flujo inicial positivo del deudor.

### 8.3 Cuota balon

Formula:

```text
CB = PV * pCB
```

Donde:

- `pCB`: porcentaje de cuota balon en decimal.

La cuota balon debe estar entre 35% y 50%.

### 8.4 Conversion de TEA a TEM

Clase:

```text
RateConverter.toMonthlyEffective
```

Formula:

```text
TEM = (1 + TEA)^(30/360) - 1
```

Equivalente academico:

```text
TEM = (1 + TEA)^(1/12) - 1
```

El backend usa `30/360`, consistente con meses academicos de 30 dias.

### 8.5 Conversion de TNA a TEM

Formula:

```text
TEM = (1 + TNA/m)^(m/12) - 1
```

Donde:

- `m`: numero de capitalizaciones por anio.

El backend aplica el mapeo de capitalizacion indicado en la seccion 6.4.

### 8.6 Cuota mensual ordinaria con cuota balon

Formula:

```text
C = (P' - CB / (1 + r)^n) / ((1 - (1 + r)^-n) / r)
```

Donde:

- `C`: cuota mensual ordinaria.
- `P'`: saldo sobre el que se recalcula la cuota. Si hubo gracia total, es el saldo capitalizado.
- `CB`: cuota balon.
- `r`: TEM.
- `n`: cuotas restantes.

Esta formula descuenta el valor presente de la cuota balon. Por eso no se calcula una cuota francesa tradicional sobre todo el saldo; se calcula una cuota menor porque el deudor pagara una cuota balon al final.

Caso especial:

Si `TEM = 0`, el backend divide el saldo neto del valor presente del balon entre las cuotas restantes.

### 8.7 Gracia parcial

Durante gracia parcial:

```text
Interes = Saldo * TEM
Pago = Interes + seguro desgravamen + seguro vehicular + gastos administrativos
Amortizacion = 0
Saldo final = Saldo inicial
```

El cliente paga intereses y costos, pero no reduce capital.

En flujo del deudor:

```text
Ft = -Pago del periodo
```

### 8.8 Gracia total

Durante gracia total academica:

```text
Interes = Saldo * TEM
Pago = 0
Amortizacion = 0
Saldo final = Saldo inicial + Interes
```

Decision importante:

- El backend sigue el enfoque academico estricto.
- Durante gracia total no cobra seguros ni gastos.
- Solo capitaliza intereses.

Esto evita distorsionar VAN, TIR y TCEA con pagos que el enunciado academico no contempla durante gracia total.

### 8.9 Recalculo posterior a gracia

Despues de aplicar gracia:

```text
Saldo recalculado = saldo despues de gracia
Cuotas restantes = plazo total - meses de gracia
```

La cuota mensual ordinaria se recalcula con:

- saldo despues de gracia,
- cuotas restantes,
- TEM,
- cuota balon.

### 8.10 Cronograma ordinario

Para cada mes ordinario:

```text
Interes = Saldo inicial * TEM
Amortizacion = Cuota ordinaria - Interes
Saldo final = Saldo inicial - Amortizacion
```

En el ultimo periodo:

```text
Cuota balon pagada = CB
Saldo final = 0
Pago total = cuota ordinaria + seguros + gastos + cuota balon
```

El backend asegura que el saldo final del ultimo periodo sea `0.00`.

## 9. Seguros y gastos

### 9.1 Seguro de desgravamen

El frontend envia:

```text
lifeInsuranceMonthlyRatePercentage
```

El mapper lo convierte a una tasa ajustada por dias bajo criterio BCP:

```text
tasa decimal mensual = porcentaje / 100
TDA aproximada = tasa mensual * 12
tasa periodo = TDA * 30 / 365
Seguro desgravamen = saldo deudor * tasa periodo
```

Se calcula sobre saldo deudor vigente.

### 9.2 Seguro vehicular

El frontend envia:

```text
vehicleInsuranceAnnualRatePercentage
```

El mapper calcula tasa periodo:

```text
tasa periodo = tasa anual decimal * 30 / 365
Seguro vehicular = precio vehiculo * tasa periodo
```

Se calcula sobre precio del vehiculo, no sobre saldo deudor.

### 9.3 Gastos administrativos

El frontend envia:

```text
administrativeExpenses
```

Se aplican como monto mensual fijo.

## 10. Flujos de caja del deudor

El backend arma los flujos asi:

```text
F0 = +monto financiado
Ft = -pago total del periodo
```

Esto es correcto desde el punto de vista del deudor porque:

- Al inicio recibe el financiamiento.
- Luego realiza pagos mensuales.

Los pagos incluyen:

- cuota ordinaria,
- interes,
- amortizacion,
- seguro de desgravamen,
- seguro vehicular,
- gastos administrativos,
- cuota balon en el ultimo periodo,
- efectos de gracia parcial o total.

## 11. VAN

Formula:

```text
VAN = F0 + Σ Ft / (1 + COK_mensual)^t
```

La COK que ingresa el frontend es anual efectiva:

```text
financialAnalysis.cokAnnualPercentage
```

El backend la convierte a mensual efectiva usando:

```text
COK_mensual = (1 + COK_anual)^(1/12) - 1
```

En codigo se reutiliza `RateConverter.toMonthlyEffective("TEA", referenceDiscountRate)`.

Regla de viabilidad:

```text
VAN >= 0 -> VIABLE
VAN < 0  -> NOT_VIABLE
```

## 12. TIR

Clase:

```text
IrrSolver
```

La TIR se calcula desde los flujos:

```text
0 = F0 + Σ Ft / (1 + TIR_mensual)^t
```

Caracteristicas:

- La TIR no es input del usuario.
- Se resuelve por biseccion.
- Busca raiz entre `0` y `0.5` mensual.
- Usa hasta 100 iteraciones.
- Tolerancia: `0.00001`.
- Si no hay cambio de signo, lanza error controlado en vez de devolver una TIR enganosa.

## 13. TCEA

Una vez calculada la TIR mensual:

```text
TCEA = (1 + TIR_mensual)^12 - 1
```

La TCEA incorpora el flujo completo:

- cuota mensual ordinaria,
- intereses,
- amortizacion,
- seguro de desgravamen,
- seguro vehicular,
- gastos administrativos,
- cuota balon,
- gracia parcial,
- gracia total.

Por eso la TCEA no es igual necesariamente a la TEA de entrada.

## 14. Cronograma persistido

La tabla `cronograma` guarda:

| Campo | Significado |
|---|---|
| `periodo` | Numero de cuota |
| `fecha_pago` | Fecha mensual de pago |
| `saldo_inicial` | Saldo antes del pago |
| `interes` | Interes del periodo |
| `amortizacion` | Capital amortizado |
| `cuota_seguro_desgravamen` | Seguro sobre saldo vigente |
| `cuota_seguro_vehicular` | Seguro sobre precio del vehiculo |
| `gastos_administrativos` | Gasto mensual fijo |
| `cuota_balon` | Balon pagado en el periodo, normalmente solo ultimo mes |
| `cuota_mensual_ordinaria` | Cuota ordinaria separada del pago total |
| `cuota_total` | Pago total del periodo |
| `saldo_final` | Saldo despues del pago |

Esto permite reconstruir el cronograma completo sin recalcular.

## 15. Credito persistido

La tabla `credito` guarda:

| Campo | Significado |
|---|---|
| `precio_vehiculo` | Se obtiene desde `Vehiculo.precio` |
| `porcentaje_cuota_inicial` | Porcentaje CI |
| `cuota_inicial_monto` | Monto CI |
| `monto_financiado` | Capital financiado |
| `porcentaje_cuota_balon` | Porcentaje balon |
| `valor_cuota_balon` | Monto balon |
| `plazo_meses` | Plazo |
| `tipo_tasa` | Efectiva o nominal |
| `frecuencia_capitalizacion` | Si aplica |
| `valor_tasa` | Tasa de entrada |
| `tem_calculada` | TEM |
| `tipo_periodo_gracia` | Ninguno, parcial, total |
| `meses_gracia` | Meses |
| `tasa_seguro_desgravamen` | Tasa de desgravamen |
| `tasa_seguro_vehicular` | Tasa vehicular |
| `gastos_administrativos_mensuales` | Gasto fijo |
| `cuota_mensual_ordinaria` | Cuota ordinaria |
| `van` | VAN |
| `tir` | TIR mensual expresada porcentualmente para API |
| `tcea` | TCEA porcentual para API |
| `estado` | Estado de simulacion |
| `fecha_creacion` | Fecha de registro |

## 16. Respuesta API para Angular

El endpoint `POST /api/v1/simulations/calculate` devuelve:

```json
{
  "id": 1,
  "createdAt": "2026-07-07T...",
  "input": {},
  "results": {
    "currency": "PEN",
    "monthlyPayment": 781.88,
    "regularMonthlyPayment": 457.99,
    "balloonPaymentAmount": 10500.00,
    "includedCostsDescription": "...",
    "initialCapital": 24000.00,
    "termMonths": 48,
    "effectiveRatePercentage": 12.5,
    "temPercentage": 0.9860764,
    "tceaPercentage": 20.00,
    "van": -2600.07,
    "tirPercentage": 2.00,
    "viability": "NOT_VIABLE",
    "schedule": []
  }
}
```

Notas:

- `monthlyPayment` es el pago mensual promedio total del cronograma.
- `regularMonthlyPayment` es la cuota ordinaria financiera.
- `schedule[].totalPayment` es el pago total del periodo.
- En el ultimo periodo, `totalPayment` incluye la cuota balon.
- `effectiveRatePercentage` refleja la tasa anual ingresada.
- `temPercentage` refleja la TEM calculada.

## 17. Endpoints disponibles

### Autenticacion

| Metodo | Endpoint | Funcion |
|---|---|---|
| `POST` | `/api/v1/auth/register` | Registrar usuario |
| `POST` | `/api/v1/auth/login` | Login |
| `GET` | `/api/v1/auth/me` | Usuario autenticado |
| `PATCH` | `/api/v1/auth/me` | Actualizar nombre |
| `POST` | `/api/v1/auth/me/password` | Cambiar contrasena |
| `POST` | `/api/v1/auth/forgot-password` | Generar token de recuperacion |
| `POST` | `/api/v1/auth/reset-password` | Restablecer contrasena |

### Simulaciones

| Metodo | Endpoint | Funcion |
|---|---|---|
| `POST` | `/api/v1/simulations/calculate` | Calcular y persistir |
| `POST` | `/api/v1/simulations/calculate-engine` | Calculo tecnico sin persistir |
| `GET` | `/api/v1/simulations/{id}` | Detalle |
| `GET` | `/api/v1/simulations/history` | Historial simple |
| `GET` | `/api/v1/simulations/history/page` | Historial paginado con fechas |
| `GET` | `/api/v1/simulations/{id}/schedule` | Cronograma completo |
| `GET` | `/api/v1/simulations/{id}/report/pdf` | Reporte PDF |
| `GET` | `/api/v1/simulations/{id}/schedule/export/pdf` | Cronograma PDF |
| `GET` | `/api/v1/simulations/{id}/schedule/export/xlsx` | Cronograma Excel |
| `DELETE` | `/api/v1/simulations/{id}` | Eliminar simulacion |

### Dashboard

| Metodo | Endpoint | Funcion |
|---|---|---|
| `GET` | `/api/v1/dashboard` | Resumen, ultimas simulaciones y vehiculo recomendado demo |

## 18. Seguridad

El backend usa:

- Spring Security.
- JWT.
- Passwords hasheadas con BCrypt.
- API stateless.

Endpoints publicos:

- `POST /api/v1/auth/register`
- `POST /api/v1/auth/login`
- `POST /api/v1/auth/forgot-password`
- `POST /api/v1/auth/reset-password`
- `POST /api/v1/simulations/calculate-engine`

El resto requiere:

```http
Authorization: Bearer <token>
```

## 19. CORS

Configuracion por defecto:

```properties
app.cors.allowed-origins=http://localhost:4200
```

Metodos permitidos:

```text
GET, POST, PUT, PATCH, DELETE, OPTIONS
```

Header expuesto:

```text
Content-Disposition
```

Esto permite que Angular descargue PDF/XLSX y lea el nombre del archivo.

## 20. Exportaciones

### 20.1 PDF

El backend genera PDF simple sin dependencias externas.

Reportes:

- Reporte de simulacion.
- Cronograma de pagos.

### 20.2 XLSX

El backend genera un `.xlsx` usando OpenXML basico con `ZipOutputStream`.

Incluye columnas:

- periodo,
- fecha pago,
- saldo inicial,
- interes,
- amortizacion,
- seguro,
- gastos,
- costos,
- pago total,
- saldo final,
- estado.

## 21. Compatibilidad academica

| Criterio academico | Estado |
|---|---|
| Metodo frances vencido ordinario | Implementado |
| Meses de 30 dias para TEA -> TEM | Implementado |
| Cuota inicial | Implementado |
| Monto financiado | Implementado |
| Cuota balon | Implementado |
| Cuota francesa con balon descontado | Implementado |
| Gracia parcial | Implementado |
| Gracia total con pago 0 y capitalizacion de interes | Implementado |
| Cronograma completo | Implementado |
| VAN del deudor | Implementado |
| TIR calculada | Implementado |
| TCEA desde flujos completos | Implementado |
| Persistencia de simulaciones | Implementado |
| Persistencia de cronograma | Implementado |

## 22. Compatibilidad con Compra Inteligente BCP

| Regla BCP | Estado |
|---|---|
| Cuota inicial entre 10% y 30% | Implementado |
| Cuota balon entre 35% y 50% | Implementado |
| Plazos 24, 36, 48 | Implementado |
| Moneda PEN/USD | Implementado |
| Seguro desgravamen sobre saldo | Implementado |
| Seguro vehicular sobre precio vehiculo | Implementado |
| Ajuste por dias 30/365 para seguros | Implementado en mapper |
| Cuota balon al ultimo periodo | Implementado |

## 23. Consideraciones y limites conocidos

- El sistema no simula aprobacion crediticia real.
- No valida ingresos del cliente.
- No calcula mora, prepagos ni reprogramaciones.
- No maneja pagos reales, solo simulacion financiera.
- `recommendedVehicle` del dashboard es un placeholder demo.
- La recuperacion de contrasena devuelve el token por API para desarrollo; en produccion debe enviarse por correo.
- La generacion PDF es simple y funcional, no un reporte visual avanzado.
- Los graficos de interes/amortizacion son muestras derivadas para frontend, no una tabla financiera oficial.

## 24. Evidencia de pruebas

Se valido compilacion y pruebas automatizadas:

```powershell
.\mvnw.cmd -DskipTests compile
.\mvnw.cmd test
```

Resultado observado:

```text
BUILD SUCCESS
Tests run: 23, Failures: 0, Errors: 0, Skipped: 1
```

El test omitido corresponde a Testcontainers/PostgreSQL cuando Docker no esta disponible localmente.

## 25. Conclusion tecnica-financiera

El backend esta alineado con el enfoque academico del curso y con las reglas principales de Compra Inteligente BCP. El motor financiero:

- usa `BigDecimal`,
- controla redondeos,
- convierte tasas correctamente,
- calcula cuota con balon descontado,
- maneja gracia parcial y total,
- arma flujos desde el punto de vista del deudor,
- calcula VAN, TIR y TCEA con el flujo completo,
- persiste indicadores y cronograma,
- expone endpoints adecuados para Angular.

Desde el punto de vista financiero, el funcionamiento principal esta correctamente estructurado para sustentar el proyecto academico.
