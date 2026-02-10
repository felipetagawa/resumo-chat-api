CREATE TABLE IF NOT EXISTS pre_entity (
    id_pre UUID NOT NULL,
    name_pre VARCHAR(255) NOT NULL,
    name_client VARCHAR(255),
    date_chat DATE NOT NULL,
    time_chat VARCHAR(255) NOT NULL,
    negociation_chat BOOLEAN NOT NULL,
    PRIMARY KEY (id_pre)
);