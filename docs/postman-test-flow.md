# Guia de pruebas Postman - Backend Credito Vehicular Inteligente

Esta guia prueba el backend de punta a punta usando Postman: autenticacion, simulacion, persistencia, historial, cronograma, dashboard, validaciones y eliminacion.

## 1. Levantar backend con Docker

Desde la raiz del proyecto:

```powershell
docker compose up --build
```

Servicios esperados:

| Servicio | URL |
|---|---|
| Backend | `http://localhost:8080` |
| PostgreSQL | `localhost:5432` |

Si PostgreSQL local ya usa `5432`, cambia el puerto externo en `docker-compose.yml` a `5433:5432`.

## 2. Variables de Postman

Crea un Environment en Postman con estas variables:

| Variable | Valor inicial |
|---|---|
| `baseUrl` | `http://localhost:8080` |
| `token` | vacio |
| `simulationId` | vacio |
| `today` | fecha actual, ejemplo `2026-07-06` |

Headers comunes:

```http
Content-Type: application/json
```

Para endpoints protegidos agrega:

```http
Authorization: Bearer {{token}}
```

## 3. Flujo recomendado

Ejecuta los requests en este orden.

## 3.1 Registrar usuario

```http
POST {{baseUrl}}/api/v1/auth/register
```

Body:

```json
{
  "documentNumber": "12345678",
  "fullName": "Carlos Mendoza",
  "email": "carlos.mendoza.postman@example.com",
  "password": "password123",
  "confirmPassword": "password123"
}
```

Respuesta esperada:

- HTTP `200`
- Campo `token`
- Campo `user`

Tests de Postman:

```javascript
pm.test("Registro OK", function () {
  pm.response.to.have.status(200);
  const json = pm.response.json();
  pm.expect(json.token).to.exist;
  pm.environment.set("token", json.token);
});
```

## 3.2 Login

Usa este request si quieres validar login o si ya registraste el usuario antes.

```http
POST {{baseUrl}}/api/v1/auth/login
```

Body:

```json
{
  "email": "carlos.mendoza.postman@example.com",
  "password": "password123"
}
```

Tests de Postman:

```javascript
pm.test("Login OK", function () {
  pm.response.to.have.status(200);
  const json = pm.response.json();
  pm.expect(json.token).to.exist;
  pm.environment.set("token", json.token);
});
```

## 3.3 Ver usuario autenticado

```http
GET {{baseUrl}}/api/v1/auth/me
```

Headers:

```http
Authorization: Bearer {{token}}
```

Respuesta esperada:

- HTTP `200`
- `email`
- `fullName`
- `role`

## 3.4 Calculo directo del motor, sin persistencia

Este endpoint es de QA. No requiere token y no guarda simulacion.

```http
POST {{baseUrl}}/api/v1/simulations/calculate-engine
```

Body:

```json
{
  "vehiclePrice": 30000,
  "downPaymentPercentage": 0.20,
  "balloonPaymentPercentage": 0.35,
  "rateType": "TEA",
  "rateValue": 0.125,
  "totalTermMonths": 48,
  "gracePeriodMonths": 0,
  "gracePeriodType": "NONE",
  "monthlyDesgravamenRate": 0.0004931507,
  "monthlyVehicleInsuranceRate": 0.0028767123,
  "monthlyAdministrativeExpense": 10,
  "referenceDiscountRate": 0.15
}
```

Notas:

- `monthlyDesgravamenRate` ya debe venir como tasa del periodo. Para BCP se usa `tasa mensual * 12 * 30 / 365`.
- `monthlyVehicleInsuranceRate` ya debe venir como tasa del periodo. Para BCP se usa `tasa anual * 30 / 365`.
- Este endpoint recibe porcentajes en decimal, no en porcentaje entero.

Respuesta esperada:

- `netLoanAmount`
- `downPayment`
- `balloonPayment`
- `regularMonthlyInstallment`
- `monthlyIrr`
- `tcea`
- `schedule` con 48 filas

Con el body anterior, una respuesta esperada coherente es:

```json
{
  "netLoanAmount": 24000.00,
  "downPayment": 6000.00,
  "balloonPayment": 10500.00,
  "monthlyEffectiveRate": 0.01,
  "regularMonthlyInstallment": 457.99,
  "monthlyIrr": 0.02,
  "tcea": 0.20,
  "npvAtIrr": 0.00,
  "npvAtReferenceRate": -2600.07,
  "viability": "NOT_VIABLE",
  "schedule": [
    {
      "period": 1,
      "paymentDate": "2026-08-06",
      "initialBalance": 24000.00,
      "interest": 236.73,
      "amortization": 221.26,
      "desgravamenInsurance": 11.84,
      "vehicleInsurance": 86.30,
      "administrativeExpense": 10.00,
      "balloonPayment": 0.00,
      "regularInstallment": 457.99,
      "totalMonthlyPayment": 566.13,
      "finalBalance": 23778.74
    }
  ]
}
```

Notas de interpretacion:

- La respuesta real trae 48 filas en `schedule`; el ejemplo muestra solo la primera.
- `monthlyEffectiveRate`, `monthlyIrr` y `tcea` se devuelven redondeados a 2 decimales.
- `viability: "NOT_VIABLE"` es esperable con una COK anual de `15%` para este flujo, porque el VAN de referencia resulta negativo.
- En el ultimo periodo, `balloonPayment` debe ser `10500.00` y `finalBalance` debe quedar `0.00`.

Tests:

```javascript
pm.test("Motor calcula cronograma", function () {
  pm.response.to.have.status(200);
  const json = pm.response.json();
  pm.expect(json.schedule).to.have.length(48);
  pm.expect(json.regularMonthlyInstallment).to.exist;
  pm.expect(json.tcea).to.exist;
});
```

## 3.5 Crear simulacion persistida

Este es el endpoint principal para el frontend.

```http
POST {{baseUrl}}/api/v1/simulations/calculate
```

Headers:

```http
Authorization: Bearer {{token}}
Content-Type: application/json
```

Body TEA sin gracia:

```json
{
  "vehicle": {
    "currency": "PEN",
    "vehiclePrice": 30000
  },
  "credit": {
    "initialFeePercentage": 20,
    "balloonFeePercentage": 35,
    "termMonths": 48
  },
  "interest": {
    "rateType": "TEA",
    "rateValuePercentage": 12.5,
    "paymentFrequency": "MONTHLY"
  },
  "gracePeriod": {
    "type": "NONE",
    "months": 0
  },
  "financialAnalysis": {
    "cokAnnualPercentage": 15
  },
  "costs": {
    "lifeInsuranceMonthlyRatePercentage": 0.05,
    "administrativeExpenses": 10,
    "vehicleInsuranceAnnualRatePercentage": 3.5
  }
}
```

Respuesta esperada:

- HTTP `200`
- `id`
- `createdAt`
- `input`
- `results.schedule` con 48 filas

Tests:

```javascript
pm.test("Simulacion persistida OK", function () {
  pm.response.to.have.status(200);
  const json = pm.response.json();
  pm.expect(json.id).to.exist;
  pm.expect(json.results.schedule).to.have.length(48);
  pm.environment.set("simulationId", json.id);
});
```

## 3.6 Crear simulacion con TNA y capitalizacion

```http
POST {{baseUrl}}/api/v1/simulations/calculate
```

Body:

```json
{
  "vehicle": {
    "currency": "USD",
    "vehiclePrice": 18000
  },
  "credit": {
    "initialFeePercentage": 25,
    "balloonFeePercentage": 40,
    "termMonths": 36
  },
  "interest": {
    "rateType": "TNA",
    "rateValuePercentage": 12,
    "paymentFrequency": "MONTHLY",
    "capitalizationFrequency": "Trimestral"
  },
  "gracePeriod": {
    "type": "PARTIAL",
    "months": 2
  },
  "financialAnalysis": {
    "cokAnnualPercentage": 14
  },
  "costs": {
    "lifeInsuranceMonthlyRatePercentage": 0.05,
    "administrativeExpenses": 8,
    "vehicleInsuranceAnnualRatePercentage": 3.2
  }
}
```

Frecuencias validas:

- `Diaria`
- `Quincenal`
- `Mensual`
- `Bimestral`
- `Trimestral`
- `Cuatrimestral`
- `Semestral`
- `Anual`

## 3.7 Crear simulacion con gracia total academica

```http
POST {{baseUrl}}/api/v1/simulations/calculate
```

Body:

```json
{
  "vehicle": {
    "currency": "PEN",
    "vehiclePrice": 45000
  },
  "credit": {
    "initialFeePercentage": 20,
    "balloonFeePercentage": 35,
    "termMonths": 48
  },
  "interest": {
    "rateType": "TEA",
    "rateValuePercentage": 13,
    "paymentFrequency": "MONTHLY"
  },
  "gracePeriod": {
    "type": "TOTAL",
    "months": 3
  },
  "financialAnalysis": {
    "cokAnnualPercentage": 15
  },
  "costs": {
    "lifeInsuranceMonthlyRatePercentage": 0.05,
    "administrativeExpenses": 10,
    "vehicleInsuranceAnnualRatePercentage": 3.5
  }
}
```

Validacion esperada:

- En las primeras 3 filas del cronograma:
  - `totalPayment = 0`
  - `insurance = 0`
  - `administrativeExpenses = 0`
  - `finalBalance > initialBalance`

## 3.8 Consultar historial simple

```http
GET {{baseUrl}}/api/v1/simulations/history
```

Headers:

```http
Authorization: Bearer {{token}}
```

Respuesta esperada:

```json
[
  {
    "id": 1,
    "createdAt": "2026-07-06T...",
    "vehiclePrice": 30000.00,
    "currency": "PEN",
    "tceaPercentage": 20.00,
    "monthlyPayment": 781.88,
    "termMonths": 48,
    "status": "CALCULATED"
  }
]
```

## 3.9 Consultar historial paginado con filtros de fecha

```http
GET {{baseUrl}}/api/v1/simulations/history/page?page=0&size=10
```

Headers:

```http
Authorization: Bearer {{token}}
```

Variantes de prueba:

```http
GET {{baseUrl}}/api/v1/simulations/history/page?page=0&size=10
```

```http
GET {{baseUrl}}/api/v1/simulations/history/page?page=0&size=10&createdFrom={{today}}
```

```http
GET {{baseUrl}}/api/v1/simulations/history/page?page=0&size=10&createdTo={{today}}
```

```http
GET {{baseUrl}}/api/v1/simulations/history/page?page=0&size=10&createdFrom={{today}}&createdTo={{today}}
```

Usa fechas con formato `YYYY-MM-DD`, por ejemplo `2026-07-06`. El filtro usa la zona horaria `America/Lima`: `createdFrom` se interpreta desde las `00:00:00` del dia y `createdTo` hasta el cierre del dia.

Respuesta esperada:

```json
{
  "content": [
    {
      "id": 1,
      "createdAt": "2026-07-06T...",
      "vehiclePrice": 30000.00,
      "currency": "PEN",
      "tceaPercentage": 20.00,
      "monthlyPayment": 781.88,
      "termMonths": 48,
      "status": "CALCULATED"
    }
  ],
  "page": 0,
  "size": 10,
  "totalElements": 1,
  "totalPages": 1,
  "first": true,
  "last": true
}
```

Tests:

```javascript
pm.test("Historial paginado OK", function () {
  pm.response.to.have.status(200);
  const json = pm.response.json();
  pm.expect(json.content).to.be.an("array");
  pm.expect(json.page).to.eql(0);
});

pm.test("Estructura de paginacion correcta", function () {
  const json = pm.response.json();
  pm.expect(json).to.have.property("size");
  pm.expect(json).to.have.property("totalElements");
  pm.expect(json).to.have.property("totalPages");
  pm.expect(json).to.have.property("first");
  pm.expect(json).to.have.property("last");
});
```

## 3.10 Obtener detalle de una simulacion

```http
GET {{baseUrl}}/api/v1/simulations/{{simulationId}}
```

Headers:

```http
Authorization: Bearer {{token}}
```

Respuesta esperada:

- `id`
- `createdAt`
- `input`
- `results`
- `results.schedule`

## 3.11 Obtener cronograma completo

```http
GET {{baseUrl}}/api/v1/simulations/{{simulationId}}/schedule
```

Headers:

```http
Authorization: Bearer {{token}}
```

Respuesta esperada:

```json
[
  {
    "period": 1,
    "paymentDate": "2026-08-06",
    "initialBalance": 24000.00,
    "amortization": 221.26,
    "interest": 236.73,
    "insurance": 98.14,
    "administrativeExpenses": 10.00,
    "costs": 108.14,
    "totalPayment": 566.13,
    "finalBalance": 23778.74,
    "status": "PENDING"
  }
]
```

Tests:

```javascript
pm.test("Cronograma completo OK", function () {
  pm.response.to.have.status(200);
  const json = pm.response.json();
  pm.expect(json).to.be.an("array");
  pm.expect(json.length).to.be.greaterThan(0);
  pm.expect(json[0].paymentDate).to.exist;
  pm.expect(json[0].totalPayment).to.exist;
});
```

## 3.12 Dashboard

```http
GET {{baseUrl}}/api/v1/dashboard
```

Headers:

```http
Authorization: Bearer {{token}}
```

Respuesta esperada:

```json
{
  "summary": {
    "tcea": 20.0000,
    "van": -3590.91,
    "monthlyPayment": 1391.79,
    "termMonths": 48
  },
  "simulations": [
    {
      "id": 2,
      "createdAt": "2026-07-06T23:09:32.595099Z",
      "vehiclePrice": 45000.00,
      "currency": "PEN",
      "tceaPercentage": 20.0000,
      "monthlyPayment": 1391.79,
      "termMonths": 48,
      "status": "CALCULATED"
    }
  ],
  "recommendedVehicle": {
    "name": "Model S Pro",
    "price": 28400.00,
    "description": "Vehiculo recomendado para simulacion de credito vehicular.",
    "imageUrl": "https://lh3.googleusercontent.com/aida-public/example"
  }
}
```

Interpretacion:

| Campo | Significado |
|---|---|
| `summary` | Resumen de la ultima simulacion guardada del cliente autenticado. Toma la simulacion mas reciente por `fechaCreacion` descendente. |
| `summary.tcea` | TCEA de la ultima simulacion. |
| `summary.van` | VAN de la ultima simulacion. |
| `summary.monthlyPayment` | Pago mensual promedio total de la ultima simulacion. Incluye el efecto de seguros, gastos y balon prorrateado. |
| `summary.termMonths` | Plazo de la ultima simulacion. |
| `simulations` | Lista de las ultimas 5 simulaciones del cliente autenticado. |
| `recommendedVehicle` | Vehiculo recomendado estatico para poblar el dashboard del frontend. Actualmente no sale de un motor de recomendacion ni de la base de datos. |

Nota tecnica: `recommendedVehicle` es un placeholder util para frontend/demo. No representa una recomendacion financiera real ni depende del perfil del cliente, VAN, TCEA, cuota o historial. Si se quiere una recomendacion real, debe calcularse con reglas de negocio o consultarse desde ofertas vehiculares persistidas.

Tests:

```javascript
pm.test("Dashboard OK", function () {
  pm.response.to.have.status(200);
  const json = pm.response.json();
  pm.expect(json.summary).to.exist;
  pm.expect(json.simulations).to.be.an("array");
  pm.expect(json.recommendedVehicle).to.exist;
});

pm.test("Summary corresponde a la simulacion mas reciente", function () {
  const json = pm.response.json();
  if (json.simulations.length > 0) {
    pm.expect(json.summary.monthlyPayment).to.eql(json.simulations[0].monthlyPayment);
    pm.expect(json.summary.termMonths).to.eql(json.simulations[0].termMonths);
  }
});
```

## 3.13 Eliminar simulacion

```http
DELETE {{baseUrl}}/api/v1/simulations/{{simulationId}}
```

Headers:

```http
Authorization: Bearer {{token}}
```

Respuesta esperada:

- HTTP `200`
- Sin body

Luego valida que ya no exista:

```http
GET {{baseUrl}}/api/v1/simulations/{{simulationId}}
```

Respuesta esperada:

- HTTP `404`

## 4. Pruebas negativas recomendadas

## 4.1 DNI invalido

```http
POST {{baseUrl}}/api/v1/auth/register
```

Body: usa `documentNumber` con menos de 8 digitos.

```json
{
  "documentNumber": "123",
  "fullName": "Carlos Mendoza",
  "email": "dni.invalido@example.com",
  "password": "password123",
  "confirmPassword": "password123"
}
```

Respuesta esperada:

- HTTP `400`
- `message`: `dni size must be 8 digits`

Ejemplo:

```json
{
  "error": "Bad Request",
  "message": "dni size must be 8 digits",
  "timestamp": "2026-07-06T23:27:27.087080369Z",
  "status": 400
}
```

Tests:

```javascript
pm.test("DNI invalido devuelve 400", function () {
  pm.response.to.have.status(400);
  const json = pm.response.json();
  pm.expect(json.message).to.eql("dni size must be 8 digits");
});
```

## 4.2 Cuota balon fuera de rango BCP

Usa `balloonFeePercentage: 20`.

Respuesta esperada:

- HTTP `400`
- `balloonFeePercentage must be at least 35`

## 4.3 TNA sin capitalizacion

Usa:

```json
"interest": {
  "rateType": "TNA",
  "rateValuePercentage": 12,
  "paymentFrequency": "MONTHLY"
}
```

Respuesta esperada:

- HTTP `400`
- `capitalizationFrequency is required for nominal annual rate`

## 4.4 Gracia NONE con meses mayor a cero

Usa:

```json
"gracePeriod": {
  "type": "NONE",
  "months": 2
}
```

Respuesta esperada:

- HTTP `400`

## 4.5 Sin token en endpoint protegido

```http
GET {{baseUrl}}/api/v1/simulations/history
```

Sin header `Authorization`.

Respuesta esperada:

- HTTP `401` o `403`, segun el filtro de seguridad.

## 5. Reglas de datos importantes

| Campo | Regla |
|---|---|
| `documentNumber` | 8 digitos numericos |
| `currency` | `PEN` o `USD` |
| `initialFeePercentage` | `10` a `30` |
| `balloonFeePercentage` | `35` a `50` |
| `termMonths` | `24`, `36` o `48` |
| `rateType` | `TEA`, `TNA`, `Efectiva`, `Nominal` |
| `capitalizationFrequency` | requerido si `rateType` es `TNA` o `Nominal` |
| `gracePeriod.type` | `NONE`, `PARTIAL`, `TOTAL` |
| `gracePeriod.months` | `0` a `6` |
| `costs.*` | mayor o igual a `0` |

## 6. Interpretacion rapida de resultados

| Campo | Significado |
|---|---|
| `monthlyPayment` | Pago mensual promedio total del cronograma |
| `tceaPercentage` | TCEA calculada desde flujo completo del deudor |
| `tirPercentage` | TIR mensual expresada como porcentaje |
| `van` | VAN con COK anual convertida a mensual efectiva |
| `schedule[].insurance` | Seguro desgravamen + seguro vehicular |
| `schedule[].costs` | Seguros + gastos administrativos |
| `schedule[].totalPayment` | Cuota ordinaria + seguros + gastos + balon cuando corresponda |

## 7. Variables utiles para Postman

Script para setear `today` automaticamente en Pre-request Script del environment o collection:

```javascript
const today = new Date().toISOString().slice(0, 10);
pm.environment.set("today", today);
```
