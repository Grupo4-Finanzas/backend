# Decisiones de persistencia financiera

Este documento justifica dos diferencias entre el modelo fisico actual y el modelo conceptual esperado del trabajo: `FlujoCaja` e `IndicadoresFinancieros`.

## FlujoCaja

### Decision actual

No se crea una tabla fisica `flujo_caja` en el alcance actual. Los flujos del deudor se reconstruyen de forma deterministica desde `credito` y `cronograma`.

### Equivalencia con el modelo esperado

El modelo financiero esperado define:

- `F0 = +Monto financiado recibido por el deudor`
- `Ft = -Pago total del periodo t`

En el modelo persistido actual, esos valores equivalen a:

| Flujo esperado | Fuente persistida actual |
|---|---|
| `F0` | `credito.monto_financiado` |
| `periodo` | `cronograma.periodo` |
| `fecha_flujo` | `cronograma.fecha_pago` |
| `Ft` | `-cronograma.cuota_total` |

Por tanto, el flujo completo usado para VAN, TIR y TCEA se puede reconstruir asi:

```text
flujos[0] = credito.monto_financiado
flujos[t] = -cronograma[t].cuota_total
```

### Justificacion

La tabla `cronograma` ya persiste todos los componentes que forman el pago del periodo:

- interes
- amortizacion
- seguro de desgravamen
- seguro vehicular
- gastos administrativos
- cuota mensual ordinaria
- cuota balon
- cuota total
- saldo final

Persistir ademas una tabla `flujo_caja` duplicaria un dato derivado (`cuota_total` con signo negativo). Para el alcance academico actual, esto reduce redundancia y evita inconsistencias entre `cronograma.cuota_total` y una eventual fila `flujo_caja.monto`.

### Limitacion aceptada

No existe trazabilidad fisica independiente de cada flujo calculado. La trazabilidad se obtiene por reconstruccion desde `credito` y `cronograma`.

### Cuando crear tabla `FlujoCaja`

Debe crearse una entidad o tabla separada si el docente o el alcance funcional exige:

- guardar explicitamente cada flujo como registro independiente;
- auditar recalculos historicos de VAN, TIR o TCEA;
- comparar varios escenarios de flujo para el mismo credito;
- exportar flujos sin depender del cronograma;
- almacenar otros flujos no representados por cuotas, seguros, gastos o balon.

Tabla sugerida si se requiere:

| Campo | Descripcion |
|---|---|
| `id_flujo_caja` | Identificador |
| `id_credito` | Credito asociado |
| `periodo` | Periodo del flujo, `0..n` |
| `fecha_flujo` | Fecha asociada al flujo |
| `monto` | Monto con signo desde la perspectiva del deudor |
| `tipo_flujo` | `DESEMBOLSO`, `PAGO`, `AJUSTE` |

## IndicadoresFinancieros

### Decision actual

No se crea una tabla fisica separada `indicadores_financieros`. Los indicadores se guardan directamente en `credito`, porque actualmente existe una relacion uno a uno entre un credito simulado y sus resultados financieros.

### Equivalencia con el modelo esperado

El modelo conceptual espera guardar indicadores como VAN, TIR y TCEA. En el modelo actual se persisten asi:

| Indicador esperado | Fuente persistida actual |
|---|---|
| VAN | `credito.van` |
| TIR | `credito.tir` |
| TCEA | `credito.tcea` |
| TEM calculada | `credito.tem_calculada` |
| COK / tasa de referencia VAN | `credito.tasa_referencia_van` |

### Justificacion

Los indicadores pertenecen directamente al resultado de la simulacion de un credito. Como no se versionan multiples recalculos ni multiples tasas COK por credito, mantenerlos en `credito` simplifica el modelo y evita una tabla uno a uno sin comportamiento propio.

### Limitacion aceptada

El modelo actual conserva solo el ultimo conjunto de indicadores asociado al credito persistido. No permite almacenar varias corridas de indicadores para el mismo credito con distintas COK, distintas fechas de recalculo o distintas hipotesis.

### Cuando crear tabla `IndicadoresFinancieros`

Debe crearse una entidad o tabla separada si el alcance exige:

- versionar recalculos financieros;
- comparar varios valores de COK para el mismo credito;
- guardar indicadores por escenario;
- auditar fecha, usuario o parametros usados en cada calculo;
- asociar varios conjuntos de indicadores a un mismo credito.

Tabla sugerida si se requiere:

| Campo | Descripcion |
|---|---|
| `id_indicadores` | Identificador |
| `id_credito` | Credito asociado |
| `van` | Valor actual neto desde el punto de vista del deudor |
| `tir` | TIR mensual o anual segun definicion documentada |
| `tcea` | TCEA calculada desde flujo completo |
| `tem_calculada` | Tasa efectiva mensual usada |
| `cok_anual` | Tasa de referencia anual para VAN |
| `fecha_calculo` | Fecha/hora del calculo |
| `version` | Numero de corrida o escenario |

## Estado de la decision

| Elemento | Estado | Decision |
|---|---|---|
| `FlujoCaja` | Pendiente documentado | No se persiste como tabla separada; se reconstruye desde `Credito` y `Cronograma`. |
| `IndicadoresFinancieros` | Pendiente documentado | No se persiste como tabla separada; VAN, TIR, TCEA, TEM y COK viven en `Credito`. |
