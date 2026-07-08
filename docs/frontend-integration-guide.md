# Guia de integracion Frontend - Backend Credito Vehicular Inteligente

Esta guia explica como conectar el frontend con el backend Spring Boot del sistema de Credito Vehicular Inteligente.

## 1. Como funciona el backend

El backend expone una API REST en Spring Boot para autenticar usuarios, registrar simulaciones de credito vehicular, persistir el historial del cliente, reconstruir resultados financieros y devolver cronogramas completos.

Flujo interno principal:

```text
Angular
  -> Controller REST
  -> DTO validado con Jakarta Validation
  -> Service de aplicacion
  -> Motor financiero
  -> Entidades JPA
  -> PostgreSQL
```

Componentes principales:

| Componente | Responsabilidad |
|---|---|
| `AuthController` / `AuthService` | Registro, login, emision de JWT y consulta del usuario autenticado. |
| `SimulationController` / `SimulationService` | Calcular, persistir, consultar, paginar y eliminar simulaciones. |
| `FintechEngineService` | Calculo financiero: cuota, cronograma, VAN, TIR, TCEA, seguros y gracia. |
| `DashboardController` / `DashboardService` | Resumen de la ultima simulacion y simulaciones recientes. |
| Repositorios JPA | Persistencia de cliente, vehiculo, credito y cronograma. |
| Flyway | Control versionado del esquema de base de datos. |
| Spring Security JWT | Proteccion de endpoints privados. |

El frontend debe tratar al backend como fuente de verdad para:

- Resultados financieros.
- Persistencia de simulaciones.
- Historial.
- Cronograma.
- Dashboard.
- Validaciones finales de negocio.

## 2. Decisiones academicas y compatibilidad BCP

El sistema se ajusto para priorizar el modelo academico del curso, incorporando reglas de Compra Inteligente BCP cuando corresponden.

### 2.1 Enfoque academico aplicado

| Decision | Como se refleja en backend |
|---|---|
| Metodo frances vencido ordinario | La cuota se calcula con tasa mensual efectiva y plazo mensual. |
| Meses academicos | Se usa logica mensual y fechas mensuales para cronograma. |
| Cuota balon | La cuota ordinaria descuenta el valor presente de la cuota balon. |
| Gracia parcial | Durante gracia parcial se pagan intereses y costos; el saldo no baja. |
| Gracia total | Durante gracia total academica el pago es `0` y se capitalizan intereses. |
| VAN del deudor | Flujo inicial positivo y pagos posteriores negativos. |
| TIR calculada | La TIR se calcula desde los flujos; no es input del usuario. |
| COK anual | Se ingresa como `cokAnnualPercentage` y se usa para VAN. |

### 2.2 Reglas BCP incorporadas

| Regla BCP | Estado |
|---|---|
| Cuota inicial entre 10% y 30% | Implementado en DTO y validacion. |
| Cuota balon entre 35% y 50% | Implementado en DTO y validacion. |
| Seguro desgravamen sobre saldo deudor | Implementado. |
| Seguro vehicular sobre precio del vehiculo | Implementado. |
| Ajuste BCP por dias para seguro vehicular | Implementado con base diaria cuando aplica al motor. |
| Plazos 24, 36 o 48 | Validado en servicio. |
| Moneda PEN o USD | Validado en DTO. |

### 2.3 Decisiones de alcance

| Tema | Decision |
|---|---|
| `calculate-engine` | Se mantiene como endpoint tecnico de QA. No debe usarse como flujo principal porque no persiste. |
| `recommendedVehicle` | Es un placeholder para dashboard/demo. No es recomendacion financiera real. |
| `FlujoCaja` fisico | No se expone como tabla separada; los flujos se reconstruyen desde credito y cronograma. |
| `IndicadoresFinancieros` fisico | VAN, TIR, TCEA y TEM viven en `Credito` por relacion 1:1. |
| PDF/Excel | Implementado como exportacion simple desde backend para reporte y cronograma. |
| Recuperacion de password | Implementada con token temporal devuelto por API para demo/desarrollo. En produccion debe enviarse por correo. |
| Edicion de perfil | Implementada para nombre completo. |

## 3. Estado frente al reporte del frontend

El reporte del frontend confirma que el flujo principal ya esta alineado con el backend:

| Pantalla frontend | Estado backend | Endpoint |
|---|---|---|
| Login | Soportado | `POST /api/v1/auth/login` |
| Registro | Soportado | `POST /api/v1/auth/register` |
| Restaurar sesion | Soportado | `GET /api/v1/auth/me` |
| Dashboard | Soportado | `GET /api/v1/dashboard` |
| Nueva simulacion | Soportado | `POST /api/v1/simulations/calculate` |
| Resultado de simulacion | Soportado | `GET /api/v1/simulations/{id}` |
| Cronograma | Soportado | `GET /api/v1/simulations/{id}/schedule` |
| Historial paginado | Soportado | `GET /api/v1/simulations/history/page` |
| Eliminar simulacion | Soportado | `DELETE /api/v1/simulations/{id}` |
| Descargar PDF resultado | Soportado | `GET /api/v1/simulations/{id}/report/pdf` |
| Exportar PDF cronograma | Soportado | `GET /api/v1/simulations/{id}/schedule/export/pdf` |
| Exportar Excel cronograma | Soportado | `GET /api/v1/simulations/{id}/schedule/export/xlsx` |
| Olvide mi contrasena | Soportado demo | `POST /api/v1/auth/forgot-password` y `POST /api/v1/auth/reset-password` |
| Actualizar perfil | Soportado | `PATCH /api/v1/auth/me` |
| FAQ administrable | No implementado | No requerido si es contenido estatico |

Recomendacion para el frontend actual:

- Mantener activos los botones conectados al flujo principal.
- Conectar botones de PDF/Excel a los endpoints de descarga.
- Mantener FAQ, terminos y privacidad como contenido estatico si no se requiere administracion desde backend.
- Mantener el calculo directo `calculate-engine` fuera del flujo final de usuario.

## 4. Base URL en Angular

En desarrollo con Docker:

```ts
// src/environments/environment.ts
export const environment = {
  production: false,
  apiBaseUrl: "http://localhost:8080"
};
```

Para produccion:

```ts
// src/environments/environment.prod.ts
export const environment = {
  production: true,
  apiBaseUrl: "https://tu-dominio-backend.com"
};
```

Todas las rutas usan el prefijo:

```text
/api/v1
```

CORS esta configurado por defecto para:

```properties
app.cors.allowed-origins=http://localhost:4200
```

Si Angular corre en otro puerto u host, actualiza `app.cors.allowed-origins` o la variable correspondiente del entorno.
El backend tambien expone el header `Content-Disposition` para que Angular pueda manejar nombres de archivo en descargas PDF/XLSX.

## 5. Autenticacion

El backend usa JWT. Despues de registrar o iniciar sesion, guarda el `token` y envialo en los endpoints protegidos.

Header requerido:

```http
Authorization: Bearer <token>
Content-Type: application/json
```

En Angular se recomienda centralizar el token en un interceptor.

Ejemplo para Angular standalone con `HttpInterceptorFn`:

```ts
// src/app/core/interceptors/auth.interceptor.ts
import { HttpInterceptorFn } from "@angular/common/http";

export const authInterceptor: HttpInterceptorFn = (req, next) => {
  const token = localStorage.getItem("token");

  if (!token) {
    return next(req);
  }

  return next(req.clone({
    setHeaders: {
      Authorization: `Bearer ${token}`
    }
  }));
};
```

Registro del interceptor:

```ts
// src/app/app.config.ts
import { ApplicationConfig } from "@angular/core";
import { provideHttpClient, withInterceptors } from "@angular/common/http";
import { authInterceptor } from "./core/interceptors/auth.interceptor";

export const appConfig: ApplicationConfig = {
  providers: [
    provideHttpClient(withInterceptors([authInterceptor]))
  ]
};
```

## 6. Flujo recomendado en frontend

1. Registrar usuario o iniciar sesion.
2. Guardar `token` y datos de `user`.
3. Cargar dashboard inicial.
4. Mostrar formulario de simulacion.
5. Enviar simulacion persistente con `POST /api/v1/simulations/calculate`.
6. Guardar el `id` devuelto para navegar al detalle o cronograma.
7. Consultar historial paginado con filtros de fecha.
8. Consultar cronograma completo cuando el usuario abra el detalle.

## 7. Endpoints de autenticacion

### 7.1 Registrar usuario

```http
POST /api/v1/auth/register
```

Body:

```json
{
  "documentNumber": "12345678",
  "fullName": "Carlos Mendoza",
  "email": "carlos.mendoza@example.com",
  "password": "password123",
  "confirmPassword": "password123"
}
```

Respuesta:

```json
{
  "token": "jwt-token",
  "user": {
    "id": 1,
    "documentNumber": "12345678",
    "firstName": "Carlos",
    "fullName": "Carlos Mendoza",
    "email": "carlos.mendoza@example.com",
    "role": "client"
  }
}
```

Accion frontend:

- Guardar `token` en `localStorage`, `sessionStorage` o estado seguro.
- Redirigir al dashboard.

### 7.2 Login

```http
POST /api/v1/auth/login
```

Body:

```json
{
  "email": "carlos.mendoza@example.com",
  "password": "password123"
}
```

Respuesta: igual a registro.

### 7.3 Usuario autenticado

```http
GET /api/v1/auth/me
```

Uso frontend:

- Restaurar sesion al recargar la pagina.
- Validar si el token sigue vigente.

### 7.4 Actualizar perfil

```http
PATCH /api/v1/auth/me
```

Requiere JWT.

Body:

```json
{
  "documentNumber": "12345678",
  "fullName": "Carlos Mendoza Actualizado"
}
```

Respuesta:

```json
{
  "id": 1,
  "documentNumber": "12345678",
  "firstName": "Carlos",
  "fullName": "Carlos Mendoza Actualizado",
  "email": "carlos.mendoza@example.com",
  "role": "client"
}
```

Uso frontend:

- Pantalla de perfil o centro de ayuda.
- Permite persistir el DNI y nombre completo del usuario.
- Despues de actualizar, refrescar el usuario guardado en `localStorage`.

### 7.5 Cambiar contrasena autenticado

```http
POST /api/v1/auth/me/password
```

Requiere JWT.

Body:

```json
{
  "currentPassword": "password123",
  "newPassword": "password456",
  "confirmPassword": "password456"
}
```

Respuesta esperada:

```text
HTTP 200
```

### 7.6 Solicitar recuperacion de contrasena

```http
POST /api/v1/auth/forgot-password
```

Body:

```json
{
  "email": "carlos.mendoza@example.com"
}
```

Respuesta en desarrollo/demo:

```json
{
  "message": "Password reset token generated",
  "resetToken": "token-temporal"
}
```

Nota: el backend devuelve `resetToken` para facilitar pruebas y demo. En produccion, ese token debe enviarse por correo y no exponerse en la respuesta.

### 7.7 Restablecer contrasena

```http
POST /api/v1/auth/reset-password
```

Body:

```json
{
  "token": "token-temporal",
  "newPassword": "password456",
  "confirmPassword": "password456"
}
```

Respuesta esperada:

```text
HTTP 200
```

## 8. Simulacion de credito

### 8.1 Calcular y persistir simulacion

Este es el endpoint principal para el frontend.

```http
POST /api/v1/simulations/calculate
```

Requiere JWT.

Body:

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

Respuesta:

```json
{
  "id": 1,
  "createdAt": "2026-07-06T23:08:22.846813Z",
  "input": {},
  "results": {
    "currency": "PEN",
    "monthlyPayment": 781.88,
    "regularMonthlyPayment": 457.99,
    "initialCapital": 24000.00,
    "balloonPaymentAmount": 10500.00,
    "termMonths": 48,
    "effectiveRatePercentage": 1.00,
    "tceaPercentage": 20.00,
    "van": -2600.07,
    "tirPercentage": 1.53,
    "viability": "NOT_VIABLE",
    "schedule": []
  }
}
```

Accion frontend:

- Mostrar resultados financieros.
- Guardar `id` para consultar detalle o cronograma.
- Si el usuario confirma la simulacion, navegar a `/simulations/:id`.
- No enviar `client`; el backend usa DNI y nombre del usuario autenticado.

### 8.2 Calculo directo de motor

```http
POST /api/v1/simulations/calculate-engine
```

Uso recomendado:

- QA.
- Pruebas tecnicas.
- Comparar calculos sin persistir datos.

No deberia ser el endpoint principal del usuario final porque no guarda historial.

## 9. Campos de formulario

| Campo frontend | Campo API | Tipo | Regla |
|---|---|---:|---|
| DNI | perfil del usuario | string | Se registra en auth y se obtiene con `/auth/me` |
| Nombre completo | perfil del usuario | string | Se registra en auth y se obtiene con `/auth/me` |
| Moneda | `vehicle.currency` | string | `PEN` o `USD` |
| Precio vehiculo | `vehicle.vehiclePrice` | number | mayor a 0 |
| Cuota inicial % | `credit.initialFeePercentage` | number | 10 a 30 |
| Cuota balon % | `credit.balloonFeePercentage` | number | 35 a 50 |
| Valor cuota balon | `results.balloonPaymentAmount` | number | calculado por backend |
| Plazo | `credit.termMonths` | number | 24, 36 o 48 |
| Tipo tasa | `interest.rateType` | string | `TEA`, `TNA`, `Efectiva`, `Nominal` |
| Tasa anual % | `interest.rateValuePercentage` | number | mayor a 0 |
| Frecuencia pago | `interest.paymentFrequency` | string | usar `MONTHLY` |
| Capitalizacion | `interest.capitalizationFrequency` | string/null | requerida para `TNA` o `Nominal` |
| Tipo gracia | `gracePeriod.type` | string | `NONE`, `PARTIAL`, `TOTAL` |
| Meses gracia | `gracePeriod.months` | number | 0 a 6 |
| COK anual % | `financialAnalysis.cokAnnualPercentage` | number | tasa de descuento para VAN |
| Seguro desgravamen mensual % | `costs.lifeInsuranceMonthlyRatePercentage` | number | mayor o igual a 0 |
| Gastos administrativos | `costs.administrativeExpenses` | number | mayor o igual a 0 |
| Seguro vehicular anual % | `costs.vehicleInsuranceAnnualRatePercentage` | number | mayor o igual a 0 |

Frecuencia de pago:

```text
MONTHLY
```

El backend solo contempla pagos mensuales porque el credito vehicular se modela con cuotas mensuales y metodo frances vencido ordinario. No enviar valores como `WEEKLY`, `BIWEEKLY`, `QUARTERLY` o `ANNUAL` en `paymentFrequency`.

Frecuencias de capitalizacion aceptadas para tasa nominal anual (`TNA` o `Nominal`):

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

## 10. Historial de simulaciones

### 10.1 Historial simple

```http
GET /api/v1/simulations/history
```

Devuelve todas las simulaciones del cliente autenticado, ordenadas por fecha descendente.

Respuesta:

```json
[
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
]
```

### 10.2 Historial paginado

```http
GET /api/v1/simulations/history/page?page=0&size=10
```

Con filtros:

```http
GET /api/v1/simulations/history/page?page=0&size=10&createdFrom=2026-07-01&createdTo=2026-07-31
```

Formato de fechas: `YYYY-MM-DD`.

Respuesta:

```json
{
  "content": [],
  "page": 0,
  "size": 10,
  "totalElements": 0,
  "totalPages": 0,
  "first": true,
  "last": true
}
```

Uso frontend:

- Tabla de historial.
- Paginador.
- Filtros por fecha de creacion.
- Boton "Ver detalle" usando `id`.

## 11. Detalle y cronograma

### 11.1 Detalle de simulacion

```http
GET /api/v1/simulations/{id}
```

Devuelve el input reconstruido y los resultados guardados.

Uso frontend:

- Pantalla de detalle.
- Resumen financiero.
- Graficos de interes/amortizacion y evolucion de saldo si estan presentes.
- Boton "Descargar PDF" conectado al endpoint de reporte.

### 11.2 Descargar reporte PDF de simulacion

```http
GET /api/v1/simulations/{id}/report/pdf
```

Respuesta:

- Archivo `application/pdf`.
- Header `Content-Disposition` con nombre `simulation-{id}-report.pdf`.

Uso Angular:

- Llamar con `responseType: "blob"`.
- Crear un link temporal para descargar el archivo.

### 11.3 Cronograma completo

```http
GET /api/v1/simulations/{id}/schedule
```

Respuesta:

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

Uso frontend:

- Tabla exportable.
- Vista de cronograma mensual.
- Mostrar `totalPayment` como pago del periodo.
- Mostrar `paymentDate` en formato local.

### 11.4 Exportar cronograma PDF

```http
GET /api/v1/simulations/{id}/schedule/export/pdf
```

Respuesta:

- Archivo `application/pdf`.
- Header `Content-Disposition` con nombre `simulation-{id}-schedule.pdf`.

### 11.5 Exportar cronograma Excel

```http
GET /api/v1/simulations/{id}/schedule/export/xlsx
```

Respuesta:

- Archivo `application/vnd.openxmlformats-officedocument.spreadsheetml.sheet`.
- Header `Content-Disposition` con nombre `simulation-{id}-schedule.xlsx`.

## 12. Dashboard

```http
GET /api/v1/dashboard
```

Respuesta:

```json
{
  "summary": {
    "tcea": 20.0000,
    "van": -3590.91,
    "monthlyPayment": 1391.79,
    "termMonths": 48
  },
  "simulations": [],
  "recommendedVehicle": {
    "name": "Model S Pro",
    "price": 28400.00,
    "description": "Vehiculo recomendado para simulacion de credito vehicular.",
    "imageUrl": "https://lh3.googleusercontent.com/aida-public/example"
  }
}
```

Interpretacion:

| Campo | Uso |
|---|---|
| `summary` | Resumen de la ultima simulacion del usuario autenticado. |
| `simulations` | Ultimas 5 simulaciones. |
| `recommendedVehicle` | Placeholder para frontend/demo. No es recomendacion financiera real. |

## 13. Eliminar simulacion

```http
DELETE /api/v1/simulations/{id}
```

Respuesta esperada:

```text
HTTP 200
```

Uso frontend:

- Boton eliminar en historial.
- Confirmar con modal antes de ejecutar.
- Refrescar historial y dashboard despues de eliminar.

## 14. Manejo de errores

Formato general:

```json
{
  "error": "Bad Request",
  "message": "dni size must be 8 digits",
  "timestamp": "2026-07-06T23:27:27.087080369Z",
  "status": 400
}
```

Recomendacion frontend:

- Mostrar `message` al usuario.
- Para `401` o `403`, limpiar token y enviar a login.
- Para `400`, marcar campos del formulario cuando el mensaje sea reconocible.
- Para `404`, mostrar "Simulacion no encontrada".

Mensajes comunes:

| Caso | Mensaje |
|---|---|
| DNI corto | `dni size must be 8 digits` |
| DNI no numerico | `documentNumber must contain exactly 8 digits` |
| Moneda invalida | `currency must be PEN or USD` |
| Balon menor a 35 | `balloonFeePercentage must be at least 35` |
| Balon mayor a 50 | `balloonFeePercentage must be at most 50` |
| TNA sin capitalizacion | `capitalizationFrequency is required for nominal annual rate` |

## 15. Ejemplo Angular con HttpClient

### 15.1 Modelos TypeScript

```ts
// src/app/core/models/simulation.models.ts
export type SimulationDraft = {
  client?: {
    documentNumber: string;
    fullName: string;
  };
  vehicle: {
    currency: "PEN" | "USD";
    vehiclePrice: number;
  };
  credit: {
    initialFeePercentage: number;
    balloonFeePercentage: number;
    termMonths: 24 | 36 | 48;
  };
  interest: {
    rateType: "TEA" | "TNA" | "Efectiva" | "Nominal";
    rateValuePercentage: number;
    paymentFrequency: "MONTHLY";
    capitalizationFrequency?: string | null;
  };
  gracePeriod: {
    type: "NONE" | "PARTIAL" | "TOTAL";
    months: number;
  };
  financialAnalysis: {
    cokAnnualPercentage: number;
  };
  costs: {
    lifeInsuranceMonthlyRatePercentage: number;
    administrativeExpenses: number;
    vehicleInsuranceAnnualRatePercentage: number;
  };
};

export type SimulationHistoryItem = {
  id: number;
  createdAt: string;
  vehiclePrice: number;
  currency: "PEN" | "USD";
  tceaPercentage: number;
  monthlyPayment: number;
  regularMonthlyPayment?: number;
  termMonths: number;
  status: string;
};

export type PageResponse<T> = {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
  first: boolean;
  last: boolean;
};

export type PaymentScheduleRow = {
  period: number;
  paymentDate: string;
  initialBalance: number;
  amortization: number;
  interest: number;
  insurance: number;
  administrativeExpenses: number;
  costs: number;
  totalPayment: number;
  finalBalance: number;
  status: string;
};
```

### 15.2 AuthService

```ts
// src/app/core/services/auth.service.ts
import { Injectable } from "@angular/core";
import { HttpClient } from "@angular/common/http";
import { tap } from "rxjs";
import { environment } from "../../../environments/environment";

type AuthResponse = {
  token: string;
  user: {
    id: number;
    documentNumber: string;
    firstName: string;
    fullName: string;
    email: string;
    role: string;
  };
};

@Injectable({ providedIn: "root" })
export class AuthService {
  private readonly baseUrl = `${environment.apiBaseUrl}/api/v1/auth`;

  constructor(private readonly http: HttpClient) {}

  register(payload: {
    documentNumber: string;
    fullName: string;
    email: string;
    password: string;
    confirmPassword: string;
  }) {
    return this.http.post<AuthResponse>(`${this.baseUrl}/register`, payload)
      .pipe(tap(response => this.saveSession(response)));
  }

  login(payload: { email: string; password: string }) {
    return this.http.post<AuthResponse>(`${this.baseUrl}/login`, payload)
      .pipe(tap(response => this.saveSession(response)));
  }

  me() {
    return this.http.get<AuthResponse["user"]>(`${this.baseUrl}/me`);
  }

  updateProfile(payload: { documentNumber: string; fullName: string }) {
    return this.http.patch<AuthResponse["user"]>(`${this.baseUrl}/me`, payload)
      .pipe(tap(user => localStorage.setItem("user", JSON.stringify(user))));
  }

  changePassword(payload: {
    currentPassword: string;
    newPassword: string;
    confirmPassword: string;
  }) {
    return this.http.post<void>(`${this.baseUrl}/me/password`, payload);
  }

  forgotPassword(payload: { email: string }) {
    return this.http.post<{ message: string; resetToken?: string }>(`${this.baseUrl}/forgot-password`, payload);
  }

  resetPassword(payload: {
    token: string;
    newPassword: string;
    confirmPassword: string;
  }) {
    return this.http.post<void>(`${this.baseUrl}/reset-password`, payload);
  }

  logout() {
    localStorage.removeItem("token");
    localStorage.removeItem("user");
  }

  private saveSession(response: AuthResponse) {
    localStorage.setItem("token", response.token);
    localStorage.setItem("user", JSON.stringify(response.user));
  }
}
```

### 15.3 SimulationService

```ts
// src/app/core/services/simulation.service.ts
import { Injectable } from "@angular/core";
import { HttpClient, HttpParams } from "@angular/common/http";
import { environment } from "../../../environments/environment";
import {
  PageResponse,
  PaymentScheduleRow,
  SimulationDraft,
  SimulationHistoryItem
} from "../models/simulation.models";

@Injectable({ providedIn: "root" })
export class SimulationService {
  private readonly baseUrl = `${environment.apiBaseUrl}/api/v1/simulations`;

  constructor(private readonly http: HttpClient) {}

  calculate(payload: SimulationDraft) {
    return this.http.post(`${this.baseUrl}/calculate`, payload);
  }

  calculateEngine(payload: SimulationDraft) {
    return this.http.post(`${this.baseUrl}/calculate-engine`, payload);
  }

  getById(id: number) {
    return this.http.get(`${this.baseUrl}/${id}`);
  }

  getHistory() {
    return this.http.get<SimulationHistoryItem[]>(`${this.baseUrl}/history`);
  }

  getHistoryPage(page = 0, size = 10, createdFrom?: string, createdTo?: string) {
    let params = new HttpParams()
      .set("page", page)
      .set("size", size);

    if (createdFrom) params = params.set("createdFrom", createdFrom);
    if (createdTo) params = params.set("createdTo", createdTo);

    return this.http.get<PageResponse<SimulationHistoryItem>>(`${this.baseUrl}/history/page`, { params });
  }

  getSchedule(id: number) {
    return this.http.get<PaymentScheduleRow[]>(`${this.baseUrl}/${id}/schedule`);
  }

  downloadSimulationPdf(id: number) {
    return this.http.get(`${this.baseUrl}/${id}/report/pdf`, { responseType: "blob" });
  }

  downloadSchedulePdf(id: number) {
    return this.http.get(`${this.baseUrl}/${id}/schedule/export/pdf`, { responseType: "blob" });
  }

  downloadScheduleXlsx(id: number) {
    return this.http.get(`${this.baseUrl}/${id}/schedule/export/xlsx`, { responseType: "blob" });
  }

  delete(id: number) {
    return this.http.delete<void>(`${this.baseUrl}/${id}`);
  }
}
```

Helper de descarga:

```ts
export function downloadBlob(blob: Blob, filename: string) {
  const url = window.URL.createObjectURL(blob);
  const link = document.createElement("a");
  link.href = url;
  link.download = filename;
  link.click();
  window.URL.revokeObjectURL(url);
}
```

### 15.4 DashboardService

```ts
// src/app/core/services/dashboard.service.ts
import { Injectable } from "@angular/core";
import { HttpClient } from "@angular/common/http";
import { environment } from "../../../environments/environment";
import { SimulationHistoryItem } from "../models/simulation.models";

type DashboardData = {
  summary: {
    tcea: number;
    van: number;
    monthlyPayment: number;
    termMonths: number;
  };
  simulations: SimulationHistoryItem[];
  recommendedVehicle: {
    name: string;
    price: number;
    description: string;
    imageUrl: string;
  };
};

@Injectable({ providedIn: "root" })
export class DashboardService {
  private readonly baseUrl = `${environment.apiBaseUrl}/api/v1/dashboard`;

  constructor(private readonly http: HttpClient) {}

  getDashboard() {
    return this.http.get<DashboardData>(this.baseUrl);
  }
}
```

### 15.5 Ejemplo de uso en componente

```ts
// src/app/features/simulations/simulation-form.component.ts
import { Component } from "@angular/core";
import { FormBuilder, Validators } from "@angular/forms";
import { Router } from "@angular/router";
import { SimulationService } from "../../core/services/simulation.service";

@Component({
  selector: "app-simulation-form",
  templateUrl: "./simulation-form.component.html"
})
export class SimulationFormComponent {
  loading = false;
  errorMessage = "";

  form = this.fb.nonNullable.group({
    currency: ["PEN", [Validators.required]],
    vehiclePrice: [30000, [Validators.required, Validators.min(1)]],
    initialFeePercentage: [20, [Validators.required, Validators.min(10), Validators.max(30)]],
    balloonFeePercentage: [35, [Validators.required, Validators.min(35), Validators.max(50)]],
    termMonths: [48, [Validators.required]],
    rateType: ["TEA", [Validators.required]],
    rateValuePercentage: [12.5, [Validators.required, Validators.min(0.0000001)]],
    capitalizationFrequency: [null as string | null],
    graceType: ["NONE", [Validators.required]],
    graceMonths: [0, [Validators.required, Validators.min(0), Validators.max(6)]],
    cokAnnualPercentage: [15, [Validators.required]],
    lifeInsuranceMonthlyRatePercentage: [0.05, [Validators.required, Validators.min(0)]],
    administrativeExpenses: [10, [Validators.required, Validators.min(0)]],
    vehicleInsuranceAnnualRatePercentage: [3.5, [Validators.required, Validators.min(0)]]
  });

  constructor(
    private readonly fb: FormBuilder,
    private readonly simulationService: SimulationService,
    private readonly router: Router
  ) {}

  submit() {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }

    const value = this.form.getRawValue();
    const payload = {
      vehicle: {
        currency: value.currency as "PEN" | "USD",
        vehiclePrice: value.vehiclePrice
      },
      credit: {
        initialFeePercentage: value.initialFeePercentage,
        balloonFeePercentage: value.balloonFeePercentage,
        termMonths: value.termMonths as 24 | 36 | 48
      },
      interest: {
        rateType: value.rateType as "TEA" | "TNA",
        rateValuePercentage: value.rateValuePercentage,
        paymentFrequency: "MONTHLY" as const,
        capitalizationFrequency: value.capitalizationFrequency
      },
      gracePeriod: {
        type: value.graceType as "NONE" | "PARTIAL" | "TOTAL",
        months: value.graceType === "NONE" ? 0 : value.graceMonths
      },
      financialAnalysis: {
        cokAnnualPercentage: value.cokAnnualPercentage
      },
      costs: {
        lifeInsuranceMonthlyRatePercentage: value.lifeInsuranceMonthlyRatePercentage,
        administrativeExpenses: value.administrativeExpenses,
        vehicleInsuranceAnnualRatePercentage: value.vehicleInsuranceAnnualRatePercentage
      }
    };

    this.loading = true;
    this.errorMessage = "";

    this.simulationService.calculate(payload).subscribe({
      next: (response: any) => {
        this.loading = false;
        this.router.navigate(["/simulations", response.id]);
      },
      error: (error) => {
        this.loading = false;
        this.errorMessage = error.error?.message || "No se pudo calcular la simulacion";
      }
    });
  }
}
```

## 16. Pantallas del frontend y flujo funcional

Esta seccion describe que debe tener cada pantalla y como se conecta con el backend.

### 16.1 Login

Objetivo: autenticar a un usuario existente.

Debe tener:

- Campo `email`.
- Campo `password`.
- Boton "Iniciar sesion".
- Link a registro.
- Link "Olvidaste tu contrasena?" conectado a recuperacion.
- Mensaje de error si las credenciales son invalidas.
- Estado de carga mientras se ejecuta la peticion.

Endpoint:

```http
POST /api/v1/auth/login
```

Funcionamiento:

1. El usuario ingresa correo y password.
2. Angular valida formato de correo y password minimo de 8 caracteres.
3. Se llama a `AuthService.login()`.
4. Si responde correctamente, se guarda `token` y `user`.
5. Se navega a `/dashboard`.
6. Si responde `401`, mostrar mensaje de credenciales invalidas.
7. Si usa "Olvidaste tu contrasena?", navegar a una pantalla que llame a `POST /api/v1/auth/forgot-password`.

### 16.2 Registro

Objetivo: crear una cuenta y dejar al usuario autenticado.

Debe tener:

- Campo `documentNumber`.
- Campo `fullName`.
- Campo `email`.
- Campo `password`.
- Campo `confirmPassword`.
- Validacion de coincidencia de passwords.
- Link a login.

Endpoint:

```http
POST /api/v1/auth/register
```

Funcionamiento:

1. El usuario llena el formulario.
2. Angular valida campos requeridos, correo valido y password minimo.
3. Se llama a `AuthService.register()`.
4. Si responde correctamente, se guarda `token` y `user`.
5. Se navega a `/dashboard`.

### 16.3 Dashboard

Objetivo: mostrar el estado general del usuario y accesos rapidos.

Debe tener:

- Tarjeta resumen con TCEA, VAN, cuota mensual ordinaria y plazo.
- Lista de ultimas simulaciones.
- Boton "Nueva simulacion".
- Boton "Ver historial".
- Tarjeta de vehiculo recomendado, dejando claro que es demostrativo si se mantiene como placeholder.

Endpoint:

```http
GET /api/v1/dashboard
```

Funcionamiento:

1. Al entrar a `/dashboard`, Angular llama a `DashboardService.getDashboard()`.
2. `summary` representa la ultima simulacion guardada del usuario.
3. `simulations` contiene las ultimas simulaciones.
4. `recommendedVehicle` actualmente es un dato fijo de demostracion.
5. Si no hay simulaciones, mostrar estado vacio con accion "Crear primera simulacion".

### 16.4 Nueva simulacion

Objetivo: calcular el credito usando el cliente autenticado y los datos ingresados de vehiculo, credito, tasas, gracia y costos.

Debe tener secciones:

- Datos del cliente: mostrar DNI y nombre completo desde `/auth/me` en modo lectura.
- Vehiculo: moneda y precio.
- Credito: cuota inicial, cuota balon y plazo.
- Tasa: tipo de tasa, valor y capitalizacion si aplica.
- Periodo de gracia: tipo y meses.
- Costos: seguro desgravamen, seguro vehicular y gastos administrativos.
- Analisis financiero: COK anual para VAN.
- Boton "Calcular".

Endpoint principal:

```http
POST /api/v1/simulations/calculate
```

Funcionamiento:

1. Angular arma un `SimulationDraft`.
2. No envia `client`; el backend lo completa desde el usuario autenticado.
3. Si `rateType` es `TEA` o `Efectiva`, enviar `capitalizationFrequency: null`.
4. Si `rateType` es `TNA` o `Nominal`, exigir `capitalizationFrequency`.
5. Si `gracePeriod.type` es `NONE`, enviar `months: 0`.
6. Al enviar, el backend calcula y persiste la simulacion.
7. La respuesta incluye `id`, `input` y `results`.
8. Navegar a `/simulations/:id` para mostrar el detalle.

Validaciones frontend recomendadas:

| Campo | Regla |
|---|---|
| DNI | 8 digitos numericos |
| Precio vehiculo | mayor a 0 |
| Cuota inicial | 10 a 30 |
| Cuota balon | 35 a 50 |
| Plazo | 24, 36 o 48 |
| Tasa | mayor a 0 |
| Meses gracia | 0 a 6 |
| Costos | mayor o igual a 0 |

### 16.5 Resultado o detalle de simulacion

Objetivo: mostrar una simulacion guardada con sus resultados financieros.

Debe tener:

- Datos de entrada principales: vehiculo, moneda, tasa, plazo, gracia y costos.
- Resultados: cuota mensual ordinaria, monto financiado, TEM, TCEA, TIR, VAN y viabilidad.
- Acceso al cronograma completo.
- Acceso para volver al historial.
- Boton "Descargar PDF".
- Boton eliminar simulacion, si el flujo lo permite.

Endpoint:

```http
GET /api/v1/simulations/{id}
```

Funcionamiento:

1. Angular obtiene el `id` desde la ruta.
2. Llama a `SimulationService.getById(id)`.
3. Renderiza `input` para mostrar lo que se envio.
4. Renderiza `results` para mostrar indicadores.
5. Usar `results.monthlyPayment` como pago mensual promedio total para el usuario final.
6. Usar `results.regularMonthlyPayment` si se necesita mostrar la cuota ordinaria financiera.
6. El boton PDF llama a `GET /api/v1/simulations/{id}/report/pdf` con `responseType: "blob"`.

### 16.6 Cronograma

Objetivo: mostrar todas las cuotas de la simulacion.

Debe tener:

- Tabla con numero de cuota.
- Fecha de pago.
- Saldo inicial.
- Interes.
- Amortizacion.
- Seguros y gastos.
- Pago total.
- Saldo final.
- Estado de la cuota.
- Boton "Exportar PDF".
- Boton "Exportar Excel".

Endpoint:

```http
GET /api/v1/simulations/{id}/schedule
```

Funcionamiento:

1. Se puede mostrar dentro del detalle o en una pantalla dedicada.
2. Angular llama a `SimulationService.getSchedule(id)`.
3. Mostrar `totalPayment` como pago del periodo.
4. Mostrar `paymentDate` en formato local.
5. En la ultima cuota, el pago total puede ser mayor por la cuota balon.
6. Exportar PDF con `GET /api/v1/simulations/{id}/schedule/export/pdf`.
7. Exportar Excel con `GET /api/v1/simulations/{id}/schedule/export/xlsx`.

### 16.7 Recuperacion de contrasena

Objetivo: permitir que el usuario regenere su contrasena con un token temporal.

Debe tener:

- Pantalla para solicitar token por correo.
- Pantalla para ingresar token, nueva contrasena y confirmacion.
- Mensaje claro de exito.

Endpoints:

```http
POST /api/v1/auth/forgot-password
POST /api/v1/auth/reset-password
```

Funcionamiento:

1. El usuario ingresa su correo.
2. El backend genera un token temporal de 30 minutos.
3. En desarrollo/demo, el token vuelve en la respuesta para facilitar pruebas.
4. En produccion, el token debe enviarse por correo y no mostrarse en la respuesta.
5. El usuario ingresa token y nueva contrasena.
6. El backend marca el token como usado.

### 16.8 Perfil de usuario

Objetivo: mostrar y actualizar datos basicos del usuario.

Debe tener:

- Nombre completo.
- Email en modo lectura.
- Boton "Actualizar datos".
- Formulario de cambio de contrasena.

Endpoints:

```http
GET /api/v1/auth/me
PATCH /api/v1/auth/me
POST /api/v1/auth/me/password
```

Funcionamiento:

1. Cargar datos con `/auth/me`.
2. Actualizar nombre completo con `PATCH /auth/me`.
3. Cambiar contrasena con `/auth/me/password`.
4. Refrescar `localStorage.user` despues de actualizar perfil.

### 16.9 Historial

Objetivo: permitir revisar simulaciones anteriores.

Debe tener:

- Tabla o lista de simulaciones.
- Filtros `createdFrom` y `createdTo`.
- Paginacion.
- Accion "Ver detalle".
- Accion "Eliminar".
- Estado vacio cuando no existan simulaciones.

Endpoint recomendado:

```http
GET /api/v1/simulations/history/page?page=0&size=10&createdFrom=2026-07-01&createdTo=2026-07-31
```

Funcionamiento:

1. Al cargar, pedir pagina 0 sin filtros.
2. Si el usuario aplica fechas, enviar `createdFrom` y/o `createdTo` en formato `YYYY-MM-DD`.
3. Usar `totalPages`, `first` y `last` para controlar el paginador.
4. Al hacer click en una fila, navegar a `/simulations/:id`.

### 16.10 Eliminacion de simulacion

Objetivo: permitir borrar una simulacion del historial del usuario.

Debe tener:

- Boton eliminar.
- Modal de confirmacion.
- Mensaje de exito o error.

Endpoint:

```http
DELETE /api/v1/simulations/{id}
```

Funcionamiento:

1. El usuario confirma eliminacion.
2. Angular llama a `SimulationService.delete(id)`.
3. Si responde `200`, refrescar historial y dashboard.
4. Si responde `404`, mostrar que la simulacion ya no existe.

### 16.11 Flujo completo del programa

Flujo normal:

```text
Login/Register
  -> Dashboard
  -> Nueva simulacion
  -> Resultado de simulacion
  -> Cronograma
  -> Historial
  -> Detalle de simulacion previa
```

Flujo con sesion existente:

```text
Abrir app
  -> Verificar token con /auth/me
  -> Si token valido: Dashboard
  -> Si token invalido: Login
```

Flujo de errores:

```text
400 -> Mostrar mensaje del backend y marcar formulario
401/403 -> Limpiar token y redirigir a Login
404 -> Mostrar recurso no encontrado
500 -> Mostrar error general y permitir reintentar
```

## 17. Consideraciones de UI

- Usa inputs numericos para porcentajes y montos, pero envia numeros JSON, no strings.
- Usa Reactive Forms para reflejar las mismas reglas del backend antes de enviar.
- Si `rateType` es `TEA` o `Efectiva`, envia `capitalizationFrequency: null`.
- Si `rateType` es `TNA` o `Nominal`, exige `capitalizationFrequency`.
- Si `gracePeriod.type` es `NONE`, fuerza `months = 0`.
- Muestra el pago mensual aproximado desde `results.monthlyPayment`.
- Si necesitas la cuota ordinaria financiera, usa `results.regularMonthlyPayment`.
- Para el cronograma, muestra el pago del periodo desde `totalPayment`.
- Para dashboards, interpreta `summary` como la ultima simulacion guardada.
- Configura un interceptor para agregar `Authorization: Bearer <token>` automaticamente.
- Configura un guard para proteger rutas como dashboard, historial, detalle y cronograma.
- Ante `401` o `403`, limpia `localStorage` y redirige a `/login`.

## 18. Rutas sugeridas en Angular

```ts
export const routes = [
  { path: "login", loadComponent: () => import("./features/auth/login.component").then(m => m.LoginComponent) },
  { path: "register", loadComponent: () => import("./features/auth/register.component").then(m => m.RegisterComponent) },
  { path: "forgot-password", loadComponent: () => import("./features/auth/forgot-password.component").then(m => m.ForgotPasswordComponent) },
  { path: "reset-password", loadComponent: () => import("./features/auth/reset-password.component").then(m => m.ResetPasswordComponent) },
  { path: "dashboard", loadComponent: () => import("./features/dashboard/dashboard.component").then(m => m.DashboardComponent) },
  { path: "profile", loadComponent: () => import("./features/profile/profile.component").then(m => m.ProfileComponent) },
  { path: "simulations/new", loadComponent: () => import("./features/simulations/simulation-form.component").then(m => m.SimulationFormComponent) },
  { path: "simulations/history", loadComponent: () => import("./features/simulations/simulation-history.component").then(m => m.SimulationHistoryComponent) },
  { path: "simulations/:id", loadComponent: () => import("./features/simulations/simulation-detail.component").then(m => m.SimulationDetailComponent) },
  { path: "", pathMatch: "full", redirectTo: "dashboard" }
];
```

Rutas que deben usar guard:

- `/dashboard`
- `/profile`
- `/simulations/new`
- `/simulations/history`
- `/simulations/:id`

Rutas publicas:

- `/login`
- `/register`
- `/forgot-password`
- `/reset-password`
