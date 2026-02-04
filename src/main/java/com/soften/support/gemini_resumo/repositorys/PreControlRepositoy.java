package com.soften.support.gemini_resumo.repositorys;

import com.soften.support.gemini_resumo.models.entities.PreEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface PreControlRepositoy extends JpaRepository<PreEntity, UUID> {

    List<PreEntity> findByName(String name);
    List<PreEntity> findByDate(LocalDate date);
    List<PreEntity> findByNameAndDate(String name, LocalDate date);
    List<PreEntity> findByNameAndNegociation(String name, boolean negociation);
    List<PreEntity> findByDateAndNegociation(LocalDate date, boolean negociation);
    List<PreEntity> findByNameAndDateAndNegociation(String name, LocalDate date, boolean negociation);
    List<PreEntity> findByNegociation(boolean negociation);
    List<PreEntity> findByDateBetween(LocalDate from, LocalDate to);
    List<PreEntity> findByNameAndDateBetween(String name, LocalDate from, LocalDate to);
    List<PreEntity> findByDateBetweenAndNegociation(LocalDate from, LocalDate to, boolean negociation);
    List<PreEntity> findByNameAndDateBetweenAndNegociation(String name, LocalDate from, LocalDate to, boolean negociation);
}
