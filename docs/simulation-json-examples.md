# Ejemplos JSON para simulaciones

Endpoint principal:

```http
POST /api/v1/simulations/calculate
Authorization: Bearer {{token}}
Content-Type: application/json
```

Este endpoint calcula y persiste la simulacion.

El body de simulacion ya no debe enviar `client`. El backend toma el DNI y nombre completo desde el usuario autenticado por JWT. Para probar con otro DNI/nombre, registra o actualiza el perfil del usuario.

Los resultados esperados son valores aproximados generados con el motor financiero actual del backend. `id`, `createdAt` y `paymentDate` cambian segun la ejecucion. Para comparar rapidamente en Postman, revisa principalmente:

- `results.monthlyPayment`: pago mensual promedio total del cronograma. Incluye el efecto de seguros, gastos y cuota balon prorrateado sobre todo el plazo.
- `results.regularMonthlyPayment`: cuota mensual ordinaria financiera, sin seguros, gastos ni cuota balon.
- `results.initialCapital`: monto financiado.
- `results.tceaPercentage`
- `results.van`
- `results.tirPercentage`
- `results.viability`
- cantidad de filas en `results.schedule`
- primera y ultima fila del cronograma

Para ver la cuota efectivamente pagada por el cliente en cada periodo, usa:

```text
results.schedule[n].totalPayment
```

Ese campo si incluye seguros, gastos administrativos y, en la ultima cuota, la cuota balon. No existe una unica cuota total fija porque el seguro de desgravamen baja con el saldo y la ultima cuota incluye el balon.

## Reglas importantes

`paymentFrequency` solo acepta:

```text
MONTHLY
```

El sistema esta modelado con cuotas mensuales bajo metodo frances vencido ordinario.

`capitalizationFrequency` solo se envia cuando `rateType` es `TNA` o `Nominal`.

Opciones validas de capitalizacion:

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

Si `rateType` es `TEA` o `Efectiva`, usar:

```json
"capitalizationFrequency": null
```

## 1. TEA, sin gracia, PEN

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
    "paymentFrequency": "MONTHLY",
    "capitalizationFrequency": null
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

Resultado esperado aproximado:

```json
{
  "results": {
    "monthlyPayment": 781.88,
    "regularMonthlyPayment": 457.99,
    "initialCapital": 24000.00,
    "balloonPaymentAmount": 10500.00,
    "termMonths": 48,
    "effectiveRatePercentage": 1.00,
    "tceaPercentage": 20.00,
    "van": -2600.07,
    "tirPercentage": 2.00,
    "viability": "NOT_VIABLE"
  },
  "scheduleCheck": {
    "rows": 48,
    "firstTotalPayment": 566.13,
    "firstFinalBalance": 23778.74,
    "lastTotalPayment": 11059.64,
    "lastBalloonPayment": 10500.00,
    "lastFinalBalance": 0.00
  }
}
```

## 2. TNA con capitalizacion mensual

```json
{
  "vehicle": {
    "currency": "PEN",
    "vehiclePrice": 45000
  },
  "credit": {
    "initialFeePercentage": 25,
    "balloonFeePercentage": 40,
    "termMonths": 36
  },
  "interest": {
    "rateType": "TNA",
    "rateValuePercentage": 14,
    "paymentFrequency": "MONTHLY",
    "capitalizationFrequency": "Mensual"
  },
  "gracePeriod": {
    "type": "NONE",
    "months": 0
  },
  "financialAnalysis": {
    "cokAnnualPercentage": 16
  },
  "costs": {
    "lifeInsuranceMonthlyRatePercentage": 0.05,
    "administrativeExpenses": 12,
    "vehicleInsuranceAnnualRatePercentage": 3.2
  }
}
```

Resultado esperado aproximado:

```json
{
  "results": {
    "monthlyPayment": 1391.79,
    "regularMonthlyPayment": 748.30,
    "initialCapital": 33750.00,
    "balloonPaymentAmount": 18000.00,
    "termMonths": 36,
    "effectiveRatePercentage": 1.00,
    "tceaPercentage": 22.00,
    "van": -3539.71,
    "tirPercentage": 2.00,
    "viability": "NOT_VIABLE"
  },
  "scheduleCheck": {
    "rows": 36,
    "firstTotalPayment": 895.30,
    "firstFinalBalance": 33395.45,
    "lastTotalPayment": 18887.79,
    "lastBalloonPayment": 18000.00,
    "lastFinalBalance": 0.00
  }
}
```

## 3. TEA con gracia parcial

```json
{
  "vehicle": {
    "currency": "PEN",
    "vehiclePrice": 38000
  },
  "credit": {
    "initialFeePercentage": 20,
    "balloonFeePercentage": 35,
    "termMonths": 48
  },
  "interest": {
    "rateType": "TEA",
    "rateValuePercentage": 13.5,
    "paymentFrequency": "MONTHLY",
    "capitalizationFrequency": null
  },
  "gracePeriod": {
    "type": "PARTIAL",
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

Resultado esperado aproximado:

```json
{
  "results": {
    "monthlyPayment": 1010.15,
    "regularMonthlyPayment": 620.96,
    "initialCapital": 30400.00,
    "balloonPaymentAmount": 13300.00,
    "termMonths": 48,
    "effectiveRatePercentage": 1.00,
    "tceaPercentage": 21.00,
    "van": -3823.58,
    "tirPercentage": 2.00,
    "viability": "NOT_VIABLE"
  },
  "scheduleCheck": {
    "rows": 48,
    "firstTotalPayment": 456.81,
    "firstFinalBalance": 30400.00,
    "lastTotalPayment": 14047.07,
    "lastBalloonPayment": 13300.00,
    "lastFinalBalance": 0.00
  }
}
```

## 4. TEA con gracia total

```json
{
  "vehicle": {
    "currency": "PEN",
    "vehiclePrice": 52000
  },
  "credit": {
    "initialFeePercentage": 20,
    "balloonFeePercentage": 45,
    "termMonths": 48
  },
  "interest": {
    "rateType": "TEA",
    "rateValuePercentage": 12.5,
    "paymentFrequency": "MONTHLY",
    "capitalizationFrequency": null
  },
  "gracePeriod": {
    "type": "TOTAL",
    "months": 2
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

Resultado esperado aproximado:

```json
{
  "results": {
    "monthlyPayment": 1372.58,
    "regularMonthlyPayment": 747.29,
    "initialCapital": 41600.00,
    "balloonPaymentAmount": 23400.00,
    "termMonths": 48,
    "effectiveRatePercentage": 1.00,
    "tceaPercentage": 19.00,
    "van": -3739.48,
    "tirPercentage": 1.00,
    "viability": "NOT_VIABLE"
  },
  "scheduleCheck": {
    "rows": 48,
    "firstTotalPayment": 0.00,
    "firstFinalBalance": 42010.32,
    "lastTotalPayment": 24318.67,
    "lastBalloonPayment": 23400.00,
    "lastFinalBalance": 0.00
  }
}
```

## 5. USD, TNA con capitalizacion trimestral

```json
{
  "vehicle": {
    "currency": "USD",
    "vehiclePrice": 18000
  },
  "credit": {
    "initialFeePercentage": 30,
    "balloonFeePercentage": 50,
    "termMonths": 24
  },
  "interest": {
    "rateType": "TNA",
    "rateValuePercentage": 10.5,
    "paymentFrequency": "MONTHLY",
    "capitalizationFrequency": "Trimestral"
  },
  "gracePeriod": {
    "type": "NONE",
    "months": 0
  },
  "financialAnalysis": {
    "cokAnnualPercentage": 12
  },
  "costs": {
    "lifeInsuranceMonthlyRatePercentage": 0.04,
    "administrativeExpenses": 5,
    "vehicleInsuranceAnnualRatePercentage": 2.8
  }
}
```

Resultado esperado aproximado:

```json
{
  "results": {
    "monthlyPayment": 670.61,
    "regularMonthlyPayment": 244.87,
    "initialCapital": 12600.00,
    "balloonPaymentAmount": 9000.00,
    "termMonths": 24,
    "effectiveRatePercentage": 1.00,
    "tceaPercentage": 17.00,
    "van": -893.52,
    "tirPercentage": 1.00,
    "viability": "NOT_VIABLE"
  },
  "scheduleCheck": {
    "rows": 24,
    "firstTotalPayment": 296.27,
    "firstFinalBalance": 12464.43,
    "lastTotalPayment": 9294.91,
    "lastBalloonPayment": 9000.00,
    "lastFinalBalance": 0.00
  }
}
```

## 6. TNA con capitalizacion diaria y gracia parcial maxima

```json
{
  "vehicle": {
    "currency": "PEN",
    "vehiclePrice": 60000
  },
  "credit": {
    "initialFeePercentage": 20,
    "balloonFeePercentage": 40,
    "termMonths": 48
  },
  "interest": {
    "rateType": "TNA",
    "rateValuePercentage": 13,
    "paymentFrequency": "MONTHLY",
    "capitalizationFrequency": "Diaria"
  },
  "gracePeriod": {
    "type": "PARTIAL",
    "months": 6
  },
  "financialAnalysis": {
    "cokAnnualPercentage": 16
  },
  "costs": {
    "lifeInsuranceMonthlyRatePercentage": 0.05,
    "administrativeExpenses": 15,
    "vehicleInsuranceAnnualRatePercentage": 3.8
  }
}
```

Resultado esperado aproximado:

```json
{
  "results": {
    "monthlyPayment": 1641.14,
    "regularMonthlyPayment": 976.46,
    "initialCapital": 48000.00,
    "balloonPaymentAmount": 24000.00,
    "termMonths": 48,
    "effectiveRatePercentage": 1.00,
    "tceaPercentage": 22.00,
    "van": -5761.89,
    "tirPercentage": 2.00,
    "viability": "NOT_VIABLE"
  },
  "scheduleCheck": {
    "rows": 48,
    "firstTotalPayment": 748.80,
    "firstFinalBalance": 48000.00,
    "lastTotalPayment": 25191.04,
    "lastBalloonPayment": 24000.00,
    "lastFinalBalance": 0.00
  }
}
```

## 7. TNA con capitalizacion semestral y gracia total

```json
{
  "vehicle": {
    "currency": "PEN",
    "vehiclePrice": 42000
  },
  "credit": {
    "initialFeePercentage": 15,
    "balloonFeePercentage": 35,
    "termMonths": 36
  },
  "interest": {
    "rateType": "TNA",
    "rateValuePercentage": 11.8,
    "paymentFrequency": "MONTHLY",
    "capitalizationFrequency": "Semestral"
  },
  "gracePeriod": {
    "type": "TOTAL",
    "months": 4
  },
  "financialAnalysis": {
    "cokAnnualPercentage": 14
  },
  "costs": {
    "lifeInsuranceMonthlyRatePercentage": 0.04,
    "administrativeExpenses": 8,
    "vehicleInsuranceAnnualRatePercentage": 3.1
  }
}
```

Resultado esperado aproximado:

```json
{
  "results": {
    "monthlyPayment": 1370.75,
    "regularMonthlyPayment": 957.13,
    "initialCapital": 35700.00,
    "balloonPaymentAmount": 14700.00,
    "termMonths": 36,
    "effectiveRatePercentage": 1.00,
    "tceaPercentage": 17.00,
    "van": -2067.91,
    "tirPercentage": 1.00,
    "viability": "NOT_VIABLE"
  },
  "scheduleCheck": {
    "rows": 36,
    "firstTotalPayment": 0.00,
    "firstFinalBalance": 36042.72,
    "lastTotalPayment": 15778.26,
    "lastBalloonPayment": 14700.00,
    "lastFinalBalance": 0.00
  }
}
```

## 8. Efectiva, sin gracia

```json
{
  "vehicle": {
    "currency": "PEN",
    "vehiclePrice": 35000
  },
  "credit": {
    "initialFeePercentage": 20,
    "balloonFeePercentage": 35,
    "termMonths": 24
  },
  "interest": {
    "rateType": "Efectiva",
    "rateValuePercentage": 12,
    "paymentFrequency": "MONTHLY",
    "capitalizationFrequency": null
  },
  "gracePeriod": {
    "type": "NONE",
    "months": 0
  },
  "financialAnalysis": {
    "cokAnnualPercentage": 13
  },
  "costs": {
    "lifeInsuranceMonthlyRatePercentage": 0,
    "administrativeExpenses": 0,
    "vehicleInsuranceAnnualRatePercentage": 0
  }
}
```

Resultado esperado aproximado:

```json
{
  "results": {
    "monthlyPayment": 1363.56,
    "regularMonthlyPayment": 853.14,
    "initialCapital": 28000.00,
    "balloonPaymentAmount": 12250.00,
    "termMonths": 24,
    "effectiveRatePercentage": 1.00,
    "tceaPercentage": 12.00,
    "van": 333.85,
    "tirPercentage": 1.00,
    "viability": "VIABLE"
  },
  "scheduleCheck": {
    "rows": 24,
    "firstTotalPayment": 853.14,
    "firstFinalBalance": 27412.55,
    "lastTotalPayment": 13103.14,
    "lastBalloonPayment": 12250.00,
    "lastFinalBalance": 0.00
  }
}
```

## Pruebas negativas

### 1. TNA sin capitalizacion

Debe fallar porque `capitalizationFrequency` es requerido para tasa nominal.

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
    "rateType": "TNA",
    "rateValuePercentage": 12,
    "paymentFrequency": "MONTHLY",
    "capitalizationFrequency": null
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

Mensaje esperado:

```text
capitalizationFrequency is required for nominal annual rate
```

### 2. Frecuencia de pago invalida

Debe fallar porque solo se acepta `MONTHLY`.

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
    "paymentFrequency": "ANNUAL",
    "capitalizationFrequency": null
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

Mensaje esperado:

```text
paymentFrequency must be MONTHLY
```

### 3. Cuota balon fuera de rango

Debe fallar porque Compra Inteligente BCP exige balon entre 35% y 50%.

```json
{
  "vehicle": {
    "currency": "PEN",
    "vehiclePrice": 30000
  },
  "credit": {
    "initialFeePercentage": 20,
    "balloonFeePercentage": 20,
    "termMonths": 48
  },
  "interest": {
    "rateType": "TEA",
    "rateValuePercentage": 12.5,
    "paymentFrequency": "MONTHLY",
    "capitalizationFrequency": null
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

Mensaje esperado:

```text
balloonFeePercentage must be at least 35
```

### 4. DNI invalido en registro o perfil

La simulacion ya no recibe DNI. Para probar DNI invalido, usa `POST /api/v1/auth/register` o `PATCH /api/v1/auth/me` con:

```json
{
  "documentNumber": "123",
  "fullName": "Error DNI",
  "email": "error.dni@example.com",
  "password": "password123",
  "confirmPassword": "password123"
}
```

Mensaje esperado:

```text
dni size must be 8 digits
```
