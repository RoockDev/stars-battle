package com.starsbattle.users.service;

import com.starsbattle.users.domain.User;
import com.starsbattle.users.dto.RankingRow;
import com.starsbattle.users.repository.UserRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

/**
 * Ranking query (spec: "Ranking Query"). {@code limit} is clamped to
 * [1,100], defaulting to 10 when absent — mirrors the source's
 * clamp-not-reject behavior (design: "Validation").
 */
@Service
public class UserRankingService {

    private static final int DEFAULT_LIMIT = 10;
    private static final int MIN_LIMIT = 1;
    private static final int MAX_LIMIT = 100;

    private final UserRepository userRepository;

    public UserRankingService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Transactional(readOnly = true)
    public List<RankingRow> getRanking(Integer limit) {
        int effectiveLimit = clamp(limit);
        List<User> ranked = userRepository.findAllByOrderByWinsDescLossesAscXpDescIdAsc(
                PageRequest.of(0, effectiveLimit));

        List<RankingRow> rows = new ArrayList<>(ranked.size());
        int rank = 1;
        for (User user : ranked) {
            rows.add(new RankingRow(rank, user.getId(), user.getWins(), user.getLosses(),
                    user.getXp(), user.getLevel()));
            rank++;
        }
        return rows;
    }

    private int clamp(Integer limit) {
        int value = limit == null ? DEFAULT_LIMIT : limit;
        if (value < MIN_LIMIT) {
            return MIN_LIMIT;
        }
        if (value > MAX_LIMIT) {
            return MAX_LIMIT;
        }
        return value;
    }
}
