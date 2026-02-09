-- V1__schema_inicial.sql
CREATE TABLE IF NOT EXISTS tb_pre_control (
    id_pre UUID NOT NULL,
    name_pre VARCHAR(255) NOT NULL,
    name_client VARCHAR(255),
    date_chat DATE NOT NULL,     -- Note que ainda é DATE aqui (como está hoje)
    time_chat VARCHAR(255) NOT NULL,
    negociation_chat BOOLEAN NOT NULL,
    PRIMARY KEY (id_pre)
);