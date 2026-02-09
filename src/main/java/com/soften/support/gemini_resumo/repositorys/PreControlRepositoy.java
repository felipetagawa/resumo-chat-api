package com.soften.support.gemini_resumo.repositorys;

import com.soften.support.gemini_resumo.models.entities.PreEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface PreControlRepositoy extends JpaRepository<PreEntity, UUID>, JpaSpecificationExecutor<PreEntity> {
}