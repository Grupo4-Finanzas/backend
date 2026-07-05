ALTER TABLE credito
ADD COLUMN IF NOT EXISTS cuota_inicial_monto NUMERIC(10, 2);

UPDATE credito c
SET cuota_inicial_monto = v.precio - c.monto_financiado
FROM vehiculo v
WHERE c.id_vehiculo = v.id_vehiculo
  AND c.cuota_inicial_monto IS NULL;

ALTER TABLE credito
ALTER COLUMN cuota_inicial_monto SET NOT NULL;
