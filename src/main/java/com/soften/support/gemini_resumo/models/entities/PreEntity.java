package com.soften.support.gemini_resumo.models.entities;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Getter
@Setter
public class PreEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id_pre", unique = true, nullable = false)
    UUID id;
    @Column(name = "name_pre", nullable = false, length = 255)
    String name;
    @Column(name = "name_client", nullable = false, length = 255)
    String nameClient;
    @Column(name = "date_chat", nullable = false)
    LocalDateTime date;
    @Column(name = "time_chat", nullable = false)
    String time;
    @Column(name = "negociation_chat", nullable = false)
    boolean negociation;

    public PreEntity() {
    }

    public PreEntity(UUID id, String name, LocalDateTime date, String time, Boolean negociation) {
        this.id = id;
        this.name = name;
        this.date = date;
        this.time = time;
        this.negociation = negociation;
    }
}
