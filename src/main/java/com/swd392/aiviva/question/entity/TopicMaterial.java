package com.swd392.aiviva.question.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.io.Serializable;
import java.time.OffsetDateTime;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "topic_material")
public class TopicMaterial implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "material_id", nullable = false)
    private UUID materialId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "topic_id", nullable = false)
    private SubjectTopic topic;

    @Column(name = "title", nullable = false)
    private String title;

    @Column(name = "file_url", nullable = false)
    private String fileUrl;

    @Column(name = "is_indexed", nullable = false)
    private Boolean isIndexed;

    @Column(name = "uploaded_at")
    private OffsetDateTime uploadedAt;
}
