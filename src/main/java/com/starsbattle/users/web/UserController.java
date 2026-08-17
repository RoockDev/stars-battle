package com.starsbattle.users.web;

import com.starsbattle.users.dto.RankingRow;
import com.starsbattle.users.service.UserRankingService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/users")
public class UserController {

    private final UserRankingService userRankingService;

    public UserController(UserRankingService userRankingService) {
        this.userRankingService = userRankingService;
    }

    @PreAuthorize("hasAnyRole('USER','ADMIN')")
    @GetMapping("/ranking")
    public List<RankingRow> ranking(@RequestParam(required = false) Integer limit) {
        return userRankingService.getRanking(limit);
    }
}
