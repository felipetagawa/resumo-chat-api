-- 1. Alterar o tipo da coluna date_chat de DATE para TIMESTAMP
-- A cláusula 'USING' explica como converter os dados antigos.
-- Aqui pegamos a data antiga e definimos a hora como 00:00:00
ALTER TABLE tb_pre_control
ALTER COLUMN date_chat TYPE TIMESTAMP WITHOUT TIME ZONE
USING date_chat::timestamp;

-- Opcional: Se quiser garantir que o time_chat é o tamanho certo
ALTER TABLE tb_pre_control ALTER COLUMN time_chat TYPE VARCHAR(255);