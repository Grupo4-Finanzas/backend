CREATE TABLE IF NOT EXISTS cliente (
    id_cliente BIGSERIAL PRIMARY KEY,
    dni VARCHAR(8) NOT NULL UNIQUE,
    nombre VARCHAR(100) NOT NULL,
    email VARCHAR(50) NOT NULL UNIQUE,
    password_hash VARCHAR(256) NOT NULL,
    rol VARCHAR(10) NOT NULL
);

CREATE TABLE IF NOT EXISTS vehiculo (
    id_vehiculo BIGSERIAL PRIMARY KEY,
    moneda VARCHAR(3) NOT NULL,
    precio NUMERIC(10, 2) NOT NULL
);

CREATE TABLE IF NOT EXISTS credito (
    id_credito BIGSERIAL PRIMARY KEY,
    id_cliente BIGINT NOT NULL REFERENCES cliente(id_cliente),
    id_vehiculo BIGINT NOT NULL REFERENCES vehiculo(id_vehiculo),
    porcentaje_cuota_inicial NUMERIC(5, 2) NOT NULL,
    porcentaje_cuota_balon NUMERIC(5, 2) NOT NULL,
    cuota_inicial_monto NUMERIC(10, 2) NOT NULL,
    monto_financiado NUMERIC(10, 2) NOT NULL,
    tipo_tasa VARCHAR(10) NOT NULL,
    valor_tasa NUMERIC(8, 4) NOT NULL,
    frecuencia_capitalizacion VARCHAR(15),
    plazo_meses INTEGER NOT NULL,
    tipo_periodo_gracia VARCHAR(10) NOT NULL,
    meses_gracia INTEGER NOT NULL,
    tasa_seguro_desgravamen NUMERIC(8, 4) NOT NULL,
    tasa_seguro_vehicular NUMERIC(8, 4) NOT NULL,
    gastos_administrativos_mensuales NUMERIC(5, 2) NOT NULL,
    tasa_referencia_van NUMERIC(8, 4),
    tem_calculada NUMERIC(10, 7),
    valor_cuota_balon NUMERIC(10, 2),
    cuota_mensual_ordinaria NUMERIC(10, 2),
    tcea NUMERIC(8, 4),
    tir NUMERIC(8, 4),
    van NUMERIC(12, 2),
    estado VARCHAR(15) NOT NULL,
    fecha_creacion TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE TABLE IF NOT EXISTS cronograma (
    id_cronograma BIGSERIAL PRIMARY KEY,
    id_credito BIGINT NOT NULL REFERENCES credito(id_credito),
    periodo INTEGER NOT NULL,
    fecha_pago DATE NOT NULL,
    saldo_inicial NUMERIC(10, 2) NOT NULL,
    interes NUMERIC(10, 2) NOT NULL,
    amortizacion NUMERIC(10, 2) NOT NULL,
    cuota_seguro_desgravamen NUMERIC(10, 2) NOT NULL,
    cuota_seguro_vehicular NUMERIC(10, 2) NOT NULL,
    gastos_administrativos NUMERIC(10, 2) NOT NULL,
    cuota_balon NUMERIC(10, 2) NOT NULL,
    cuota_mensual_ordinaria NUMERIC(10, 2) NOT NULL,
    cuota_total NUMERIC(10, 2) NOT NULL,
    saldo_final NUMERIC(10, 2) NOT NULL,
    CONSTRAINT uk_cronograma_credito_periodo UNIQUE (id_credito, periodo)
);
