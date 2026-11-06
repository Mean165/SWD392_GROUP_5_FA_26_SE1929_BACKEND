package com.swd392.aiviva.exam.service;

import com.swd392.aiviva.question.entity.Question;
import com.swd392.aiviva.question.enums.BloomLevel;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Bounded backtracking allocator used by readiness checks; it does not persist papers. */
public final class QuestionAllocationChecker {
    private static final int MAX_SEARCH_NODES = 100_000;
    private static final int MAX_ESTIMATE_ATTEMPTS = 1_000;
    private static final int MAX_EXACT_ESTIMATE_ADDITIONS = 12;

    public Result check(int studentCount, Map<BloomLevel, Integer> distribution,
            Map<BloomLevel, List<Question>> approvedPools, int maxPairOverlap) {
        if (studentCount <= 1) return new Result(true, 0, Map.of(), false);
        Search search = new Search(studentCount, distribution, approvedPools, maxPairOverlap, MAX_SEARCH_NODES);
        if (search.allocate()) return new Result(true, 0, Map.of(), false);
        AdditionEstimate estimate = minimumAdditions(studentCount, distribution, approvedPools, maxPairOverlap);
        return new Result(false, estimate.count(), estimate.byBloom(), estimate.upperBound());
    }

    private AdditionEstimate minimumAdditions(int studentCount, Map<BloomLevel, Integer> distribution,
            Map<BloomLevel, List<Question>> pools, int maxPairOverlap) {
        List<BloomLevel> levels = distribution.entrySet().stream().filter(e -> e.getValue() > 0)
                .map(Map.Entry::getKey).sorted().toList();
        if (levels.isEmpty()) return new AdditionEstimate(null, Map.of(), false);

        EnumMap<BloomLevel, Integer> additions = new EnumMap<>(BloomLevel.class);
        int attempts = 0;
        int maxExact = Math.min(MAX_EXACT_ESTIMATE_ADDITIONS, maximumDisjointAdditionCount(studentCount, distribution, pools));
        for (int total = 1; total <= maxExact && attempts < MAX_ESTIMATE_ATTEMPTS; total++) {
            EstimateSearch candidate = new EstimateSearch(levels, total, additions, studentCount,
                    distribution, pools, maxPairOverlap);
            if (candidate.find()) return new AdditionEstimate(total, Map.copyOf(candidate.result), false);
            attempts += candidate.attempts;
        }

        int upperBound = maximumDisjointAdditionCount(studentCount, distribution, pools);
        if (upperBound <= 0) return new AdditionEstimate(null, Map.of(), false);
        EnumMap<BloomLevel, Integer> sufficient = new EnumMap<>(BloomLevel.class);
        for (BloomLevel level : levels) {
            int needed = distribution.getOrDefault(level, 0) * studentCount;
            int have = pools.getOrDefault(level, List.of()).size();
            if (needed > have) sufficient.put(level, needed - have);
        }
        return new AdditionEstimate(upperBound, Map.copyOf(sufficient), true);
    }

    private int maximumDisjointAdditionCount(int studentCount, Map<BloomLevel, Integer> distribution,
            Map<BloomLevel, List<Question>> pools) {
        return distribution.entrySet().stream().filter(e -> e.getValue() > 0)
                .mapToInt(e -> Math.max(0, studentCount * e.getValue() - pools.getOrDefault(e.getKey(), List.of()).size()))
                .sum();
    }

    public record Result(boolean possible, Integer minimumAdditionalQuestions,
            Map<BloomLevel, Integer> additionalQuestionsByBloom, boolean estimateIsUpperBound) { }

    private record AdditionEstimate(Integer count, Map<BloomLevel, Integer> byBloom, boolean upperBound) { }

    private final class EstimateSearch {
        private final List<BloomLevel> levels;
        private final int total;
        private final EnumMap<BloomLevel, Integer> additions;
        private final int studentCount;
        private final Map<BloomLevel, Integer> distribution;
        private final Map<BloomLevel, List<Question>> pools;
        private final int maxPairOverlap;
        private EnumMap<BloomLevel, Integer> result;
        private int attempts;

        private EstimateSearch(List<BloomLevel> levels, int total, EnumMap<BloomLevel, Integer> additions,
                int studentCount, Map<BloomLevel, Integer> distribution,
                Map<BloomLevel, List<Question>> pools, int maxPairOverlap) {
            this.levels = levels;
            this.total = total;
            this.additions = additions;
            this.studentCount = studentCount;
            this.distribution = distribution;
            this.pools = pools;
            this.maxPairOverlap = maxPairOverlap;
        }

        private boolean find() { return distribute(0, total); }

        private boolean distribute(int index, int left) {
            if (attempts >= MAX_ESTIMATE_ATTEMPTS) return false;
            if (index == levels.size() - 1) {
                if (left > 0) additions.put(levels.get(index), left);
                boolean possible = tryCurrent();
                additions.remove(levels.get(index));
                return possible;
            }
            BloomLevel level = levels.get(index);
            for (int count = 0; count <= left; count++) {
                if (count == 0) additions.remove(level); else additions.put(level, count);
                if (distribute(index + 1, left - count)) return true;
                if (attempts >= MAX_ESTIMATE_ATTEMPTS) break;
            }
            additions.remove(level);
            return false;
        }

        private boolean tryCurrent() {
            attempts++;
            EnumMap<BloomLevel, List<Question>> expanded = new EnumMap<>(BloomLevel.class);
            for (BloomLevel level : levels) {
                List<Question> questions = new ArrayList<>(pools.getOrDefault(level, List.of()));
                for (int i = 0; i < additions.getOrDefault(level, 0); i++) {
                    Question synthetic = new Question();
                    synthetic.setQuestionId(UUID.nameUUIDFromBytes(("readiness:" + level + ":" + i)
                            .getBytes(StandardCharsets.UTF_8)));
                    synthetic.setBloomLevel(level.name());
                    questions.add(synthetic);
                }
                expanded.put(level, questions);
            }
            Search search = new Search(studentCount, distribution, expanded, maxPairOverlap, 10_000);
            if (!search.allocate()) return false;
            result = new EnumMap<>(additions);
            return true;
        }
    }

    private static final class Search {
        private final int studentCount;
        private final int maxPairOverlap;
        private final int maxNodes;
        private final List<BloomLevel> levels;
        private final Map<BloomLevel, Integer> distribution;
        private final Map<BloomLevel, List<Question>> pools;
        private final List<Set<UUID>> papers = new ArrayList<>();
        private int visited;

        private Search(int studentCount, Map<BloomLevel, Integer> distribution,
                Map<BloomLevel, List<Question>> pools, int maxPairOverlap, int maxNodes) {
            this.studentCount = studentCount;
            this.distribution = distribution;
            this.pools = pools;
            this.maxPairOverlap = maxPairOverlap;
            this.maxNodes = maxNodes;
            this.levels = distribution.entrySet().stream().filter(e -> e.getValue() > 0)
                    .map(Map.Entry::getKey).sorted(Comparator.comparingInt((BloomLevel l) -> pools.getOrDefault(l, List.of()).size()))
                    .toList();
        }

        private boolean allocate() { return allocateStudent(0); }

        private boolean allocateStudent(int studentIndex) {
            if (studentIndex == studentCount) return true;
            return chooseLevel(0, new HashSet<>(), new int[studentIndex], studentIndex);
        }

        private boolean chooseLevel(int levelIndex, Set<UUID> selected, int[] overlapCounts, int studentIndex) {
            if (levelIndex == levels.size()) {
                papers.add(Set.copyOf(selected));
                if (allocateStudent(studentIndex + 1)) return true;
                papers.remove(papers.size() - 1);
                return false;
            }
            BloomLevel level = levels.get(levelIndex);
            List<Question> candidates = candidates(level);
            int count = distribution.getOrDefault(level, 0);
            if (candidates.size() < count) return false;
            return chooseWithinLevel(levelIndex, candidates, 0, count, selected, overlapCounts, studentIndex);
        }

        private boolean chooseWithinLevel(int levelIndex, List<Question> candidates, int start, int remaining,
                Set<UUID> selected, int[] overlapCounts, int studentIndex) {
            if (remaining == 0) return chooseLevel(levelIndex + 1, selected, overlapCounts, studentIndex);
            if (candidates.size() - start < remaining || ++visited > maxNodes) return false;
            for (int index = start; index <= candidates.size() - remaining; index++) {
                UUID questionId = candidates.get(index).getQuestionId();
                if (questionId == null || !selected.add(questionId)) continue;
                boolean withinLimit = true;
                for (int i = 0; i < papers.size(); i++) {
                    if (papers.get(i).contains(questionId) && ++overlapCounts[i] > maxPairOverlap) withinLimit = false;
                }
                if (withinLimit && chooseWithinLevel(levelIndex, candidates, index + 1, remaining - 1,
                        selected, overlapCounts, studentIndex)) return true;
                for (int i = 0; i < papers.size(); i++) {
                    if (papers.get(i).contains(questionId)) overlapCounts[i]--;
                }
                selected.remove(questionId);
            }
            return false;
        }

        private List<Question> candidates(BloomLevel level) {
            return pools.getOrDefault(level, List.of()).stream().sorted(Comparator
                    .comparingLong((Question question) -> papers.stream().filter(p -> p.contains(question.getQuestionId())).count())
                    .thenComparing(Question::getQuestionId, Comparator.nullsLast(Comparator.naturalOrder()))).toList();
        }
    }
}
