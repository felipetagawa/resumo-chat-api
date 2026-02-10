CREATE TABLE called_entity (
    id_called UUID NOT NULL,
    problem_called VARCHAR(2000) NOT NULL,
    solution_called VARCHAR(5000) NOT NULL,
    upsell_called VARCHAR(3000) NOT NULL,
    prints_called BOOLEAN NOT NULL,
    mood_called VARCHAR(255) NOT NULL,
    modules_called VARCHAR(255) NOT NULL,
    CONSTRAINT pk_called_entity PRIMARY KEY (id_called)
);