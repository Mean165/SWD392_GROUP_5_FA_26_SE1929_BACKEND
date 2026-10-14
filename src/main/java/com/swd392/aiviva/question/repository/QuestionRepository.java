package com.swd392.aiviva.question.repository;

import com.swd392.aiviva.question.entity.Question;
import java.util.UUID;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface QuestionRepository extends JpaRepository<Question, UUID> {
    List<Question> findByTopic_TopicId(UUID topicId);
}
