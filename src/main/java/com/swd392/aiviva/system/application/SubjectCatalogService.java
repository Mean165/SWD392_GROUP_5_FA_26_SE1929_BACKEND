package com.swd392.aiviva.system.application;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.swd392.aiviva.common.exception.BusinessException;
import com.swd392.aiviva.common.exception.ResourceNotFoundException;
import com.swd392.aiviva.system.dto.request.AddSubjectTopicsRequest;
import com.swd392.aiviva.system.dto.request.CreateSubjectRequest;
import com.swd392.aiviva.system.dto.request.SaveSubjectRequest;
import com.swd392.aiviva.system.dto.request.SubjectTopicInput;
import com.swd392.aiviva.system.dto.response.SubjectResponse;
import com.swd392.aiviva.system.dto.response.SubjectTopicSummary;
import com.swd392.aiviva.user.dto.response.LecturerSummaryResponse;
import com.swd392.aiviva.user.entity.AppUser;
import com.swd392.aiviva.user.repository.UserRepository;
import com.swd392.aiviva.user.service.UserService;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SubjectCatalogService {
    private static final TypeReference<List<UUID>> UUID_LIST = new TypeReference<>() {};
    private static final String SUBJECT_SELECT = "SELECT s.subject_id, s.subject_code, s.subject_name, "
            + "s.description, s.assigned_lecturer_ids_json::text, "
            + "(SELECT COUNT(*) FROM subject_topic st WHERE st.subject_id=s.subject_id) AS topic_count "
            + "FROM subject s";

    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;
    private final UserService users;
    private final UserRepository userRepository;

    public SubjectCatalogService(JdbcTemplate jdbc, ObjectMapper mapper, UserService users,
            UserRepository userRepository) {
        this.jdbc = jdbc;
        this.mapper = mapper;
        this.users = users;
        this.userRepository = userRepository;
    }

    @Transactional(readOnly = true)
    public List<SubjectResponse> list(String keyword) {
        String normalizedKeyword = keyword == null ? "" : keyword.trim();
        String sql = SUBJECT_SELECT;
        List<SubjectRow> rows;
        if (normalizedKeyword.isEmpty()) {
            rows = jdbc.query(sql + " ORDER BY s.subject_name, s.subject_code", this::mapRow);
        } else {
            String search = "%" + normalizedKeyword.toLowerCase(Locale.ROOT) + "%";
            rows = jdbc.query(sql + " WHERE LOWER(s.subject_code) LIKE ? OR LOWER(s.subject_name) LIKE ? "
                    + "OR LOWER(COALESCE(s.description, '')) LIKE ? ORDER BY s.subject_name, s.subject_code",
                    this::mapRow, search, search, search);
        }
        return toResponses(rows);
    }

    @Transactional(readOnly = true)
    public SubjectResponse get(UUID subjectId) {
        List<SubjectRow> rows = jdbc.query(SUBJECT_SELECT + " WHERE s.subject_id=?", this::mapRow, subjectId);
        if (rows.isEmpty()) throw new ResourceNotFoundException("Subject not found");
        SubjectRow subject = rows.get(0);
        return toResponse(subject, lecturersById(), topicsBySubjectIds(List.of(subject.subjectId())));
    }

    @Transactional
    public SubjectResponse create(CreateSubjectRequest request, String creatorEmail) {
        String code = request.subjectCode().trim();
        String name = request.subjectName().trim();
        ensureCodeIsAvailable(code, null);
        List<SubjectTopicInput> topics = request.topics() == null ? List.of() : request.topics();
        ensureTopicCodesAreAvailable(topics);
        AppUser creator = findCreator(creatorEmail);

        UUID subjectId = UUID.randomUUID();
        jdbc.update("INSERT INTO subject (subject_id, subject_code, subject_name, description) "
                        + "VALUES (?, ?, ?, ?)",
                subjectId, code, name, normalizeDescription(request.description()));
        insertTopics(subjectId, creator.getUserId(), topics);
        return get(subjectId);
    }

    @Transactional
    public SubjectResponse update(UUID subjectId, SaveSubjectRequest request) {
        getRow(subjectId);
        String code = request.subjectCode().trim();
        String name = request.subjectName().trim();
        ensureCodeIsAvailable(code, subjectId);

        jdbc.update("UPDATE subject SET subject_code=?, subject_name=?, description=? WHERE subject_id=?",
                code, name, normalizeDescription(request.description()), subjectId);
        return get(subjectId);
    }

    @Transactional
    public SubjectResponse addTopics(UUID subjectId, AddSubjectTopicsRequest request, String creatorEmail) {
        getRow(subjectId);
        ensureTopicCodesAreAvailable(request.topics());
        AppUser creator = findCreator(creatorEmail);
        insertTopics(subjectId, creator.getUserId(), request.topics());
        return get(subjectId);
    }

    private SubjectRow getRow(UUID subjectId) {
        List<SubjectRow> rows = jdbc.query(SUBJECT_SELECT + " WHERE s.subject_id=?", this::mapRow, subjectId);
        if (rows.isEmpty()) throw new ResourceNotFoundException("Subject not found");
        return rows.get(0);
    }

    private void ensureCodeIsAvailable(String code, UUID excludedSubjectId) {
        boolean exists;
        if (excludedSubjectId == null) {
            exists = Boolean.TRUE.equals(jdbc.queryForObject(
                    "SELECT EXISTS (SELECT 1 FROM subject WHERE LOWER(subject_code)=LOWER(?))",
                    Boolean.class, code));
        } else {
            exists = Boolean.TRUE.equals(jdbc.queryForObject(
                    "SELECT EXISTS (SELECT 1 FROM subject WHERE LOWER(subject_code)=LOWER(?) "
                            + "AND subject_id<>?)",
                    Boolean.class, code, excludedSubjectId));
        }
        if (exists) throw new BusinessException("Subject code already exists: " + code);
    }

    private void ensureTopicCodesAreAvailable(List<SubjectTopicInput> topics) {
        Set<String> requestedCodes = new HashSet<>();
        for (SubjectTopicInput topic : topics) {
            String code = topic.topicCode().trim();
            if (!requestedCodes.add(code.toLowerCase(Locale.ROOT))) {
                throw new BusinessException("Topic code is duplicated in request: " + code);
            }
            boolean exists = Boolean.TRUE.equals(jdbc.queryForObject(
                    "SELECT EXISTS (SELECT 1 FROM subject_topic WHERE LOWER(topic_code)=LOWER(?))",
                    Boolean.class, code));
            if (exists) throw new BusinessException("Topic code already exists: " + code);
        }
    }

    private AppUser findCreator(String creatorEmail) {
        return userRepository.findByEmail(creatorEmail)
                .orElseThrow(() -> new ResourceNotFoundException("Creating administrator not found"));
    }

    private void insertTopics(UUID subjectId, UUID creatorId, List<SubjectTopicInput> topics) {
        for (SubjectTopicInput topic : topics) {
            jdbc.update("INSERT INTO subject_topic (topic_id, created_by, topic_code, topic_name, description, "
                            + "created_at, subject_id) VALUES (?, ?, ?, ?, ?, CURRENT_TIMESTAMP, ?)",
                    UUID.randomUUID(), creatorId, topic.topicCode().trim(), topic.topicName().trim(),
                    normalizeDescription(topic.description()), subjectId);
        }
    }

    private List<SubjectResponse> toResponses(List<SubjectRow> rows) {
        Map<UUID, LecturerSummaryResponse> lecturers = lecturersById();
        Map<UUID, List<SubjectTopicSummary>> topics = topicsBySubjectIds(
                rows.stream().map(SubjectRow::subjectId).toList());
        return rows.stream().map(row -> toResponse(row, lecturers, topics)).toList();
    }

    private SubjectResponse toResponse(SubjectRow row, Map<UUID, LecturerSummaryResponse> lecturers,
            Map<UUID, List<SubjectTopicSummary>> topics) {
        List<LecturerSummaryResponse> assignedLecturers = row.lecturerIds().stream()
                .map(lecturers::get)
                .filter(java.util.Objects::nonNull)
                .toList();
        return new SubjectResponse(row.subjectId(), row.subjectCode(), row.subjectName(), row.description(),
                row.lecturerIds(), assignedLecturers, topics.getOrDefault(row.subjectId(), List.of()), row.topicCount());
    }

    private Map<UUID, List<SubjectTopicSummary>> topicsBySubjectIds(List<UUID> subjectIds) {
        if (subjectIds.isEmpty()) return Map.of();
        String placeholders = String.join(",", java.util.Collections.nCopies(subjectIds.size(), "?"));
        List<TopicRow> topicRows = jdbc.query("SELECT subject_id, topic_id, topic_code, topic_name, description "
                        + "FROM subject_topic WHERE subject_id IN (" + placeholders + ") "
                        + "ORDER BY topic_name, topic_code",
                (rs, rowNumber) -> new TopicRow(rs.getObject("subject_id", UUID.class),
                        new SubjectTopicSummary(rs.getObject("topic_id", UUID.class), rs.getString("topic_code"),
                                rs.getString("topic_name"), rs.getString("description"))),
                subjectIds.toArray());
        return topicRows.stream().collect(Collectors.groupingBy(TopicRow::subjectId,
                Collectors.mapping(TopicRow::topic, Collectors.toList())));
    }

    private Map<UUID, LecturerSummaryResponse> lecturersById() {
        return users.listLecturers().stream()
                .collect(Collectors.toMap(LecturerSummaryResponse::userId, Function.identity()));
    }

    private SubjectRow mapRow(ResultSet rs, int rowNumber) throws SQLException {
        try {
            String rawLecturerIds = rs.getString("assigned_lecturer_ids_json");
            List<UUID> lecturerIds = rawLecturerIds == null ? List.of() : mapper.readValue(rawLecturerIds, UUID_LIST);
            return new SubjectRow(rs.getObject("subject_id", UUID.class), rs.getString("subject_code"),
                    rs.getString("subject_name"), rs.getString("description"), lecturerIds,
                    rs.getLong("topic_count"));
        } catch (Exception e) {
            throw new IllegalStateException("Subject contains invalid Lecturer assignment data", e);
        }
    }

    private String normalizeDescription(String description) {
        if (description == null) return null;
        String normalized = description.trim();
        return normalized.isEmpty() ? null : normalized;
    }

    private record SubjectRow(UUID subjectId, String subjectCode, String subjectName, String description,
            List<UUID> lecturerIds, long topicCount) {
    }

    private record TopicRow(UUID subjectId, SubjectTopicSummary topic) {
    }
}
