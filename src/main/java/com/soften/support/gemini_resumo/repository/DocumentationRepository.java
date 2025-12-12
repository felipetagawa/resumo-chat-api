package com.soften.support.gemini_resumo.repository;

import com.soften.support.gemini_resumo.entity.Documentation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface DocumentationRepository extends JpaRepository<Documentation, Long> {

    @Query(value = "SELECT * FROM documentacoes ORDER BY embedding <-> cast(?1 as vector) LIMIT ?2", nativeQuery = true)
    List<Documentation> findNearest(float[] embedding, int limit);
}
