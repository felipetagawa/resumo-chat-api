package com.soften.support.gemini_resumo.models.entities;

import com.soften.support.gemini_resumo.models.enums.ModulesCalled;
import com.soften.support.gemini_resumo.models.enums.MoodClient;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

@Entity
@Getter
@Setter
public class CalledEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id_called", unique = true, nullable = false)
    UUID id;
    @Column(name = "problem_called", length = 2000, nullable = false)
    String problem;
    @Column(name = "solution_called", length = 5000, nullable = false)
    String solution;
    @Column(name = "upsell_called", length = 3000, nullable = false)
    String upsell;
    @Column(name = "prints_called", nullable = false)
    boolean prints;
    @Column(name = "mood_called", nullable = false)
    @Enumerated(EnumType.STRING)
    MoodClient moodClient;
    @Column(name = "modules_called", nullable = false)
    @Enumerated(EnumType.STRING)
    ModulesCalled modulesCalled;

    public CalledEntity() {}

    public CalledEntity(UUID id, String problem, String solution, String upsell, boolean prints, MoodClient moodClient, ModulesCalled modulesCalled) {
        this.id = id;
        this.problem = problem;
        this.solution = solution;
        this.upsell = upsell;
        this.prints = prints;
        this.moodClient = moodClient;
        this.modulesCalled = modulesCalled;
    }
}


