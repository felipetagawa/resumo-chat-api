package com.soften.support.gemini_resumo.repositorys;


import com.soften.support.gemini_resumo.models.entities.PreEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Date;
import java.util.List;
import java.util.UUID;

public interface PreControlRepositoy extends JpaRepository<PreEntity, UUID> {

    List<PreEntity> findByName(String name);

    List<PreEntity> findByDate(Date date);

    List<PreEntity> findByNameAndDate(String name, Date date);

}