package com.soften.support.gemini_resumo.repositorys;

import com.soften.support.gemini_resumo.models.entities.CalledEntity;
import com.soften.support.gemini_resumo.models.enums.ModulesCalled;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface CalledRepository extends JpaRepository<CalledEntity, UUID> {

    List<CalledEntity> findByModulesCalledIn(List<ModulesCalled> modules);
}
