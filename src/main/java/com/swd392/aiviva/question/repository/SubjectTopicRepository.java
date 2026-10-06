package com.swd392.aiviva.question.repository;

import com.swd392.aiviva.question.entity.SubjectTopic;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface SubjectTopicRepository extends JpaRepository<SubjectTopic, UUID> {
    Optional<SubjectTopic> findByTopicCode(String topicCode);
}
