package com.swd392.aiviva.question.repository;

import com.swd392.aiviva.question.entity.TopicMaterial;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface TopicMaterialRepository extends JpaRepository<TopicMaterial, UUID> {
    List<TopicMaterial> findByTopic_TopicId(UUID topicId);
}
