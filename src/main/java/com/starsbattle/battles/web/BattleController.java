package com.starsbattle.battles.web;

import com.starsbattle.battles.dto.BattleView;
import com.starsbattle.battles.dto.JoinPvpRequest;
import com.starsbattle.battles.dto.StartPveRequest;
import com.starsbattle.battles.dto.StartPvpRequest;
import com.starsbattle.battles.dto.TurnResultView;
import com.starsbattle.battles.service.BattleCreationService;
import com.starsbattle.battles.service.BattleQueryService;
import com.starsbattle.battles.service.PvpBattleService;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/battles")
public class BattleController {

    private static final String ADMIN_AUTHORITY = "ROLE_ADMIN";

    private final BattleCreationService battleCreationService;
    private final BattleQueryService battleQueryService;
    private final PvpBattleService pvpBattleService;

    public BattleController(BattleCreationService battleCreationService, BattleQueryService battleQueryService,
            PvpBattleService pvpBattleService) {
        this.battleCreationService = battleCreationService;
        this.battleQueryService = battleQueryService;
        this.pvpBattleService = pvpBattleService;
    }

    @PreAuthorize("hasAnyRole('USER','ADMIN')")
    @PostMapping("/start/pve")
    public BattleView startPve(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody StartPveRequest request) {
        return battleCreationService.startPve(currentUserId(jwt), request);
    }

    @PreAuthorize("hasAnyRole('USER','ADMIN')")
    @PostMapping("/start/pvp")
    public BattleView startPvp(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody StartPvpRequest request) {
        return battleCreationService.startPvp(currentUserId(jwt), request);
    }

    @PreAuthorize("hasAnyRole('USER','ADMIN')")
    @PostMapping("/{id}/join/pvp")
    public BattleView joinPvp(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id,
            @Valid @RequestBody JoinPvpRequest request) {
        return battleCreationService.joinPvp(currentUserId(jwt), id, request);
    }

    @PreAuthorize("hasAnyRole('USER','ADMIN')")
    @GetMapping("/{id}")
    public BattleView get(Authentication authentication, @AuthenticationPrincipal Jwt jwt, @PathVariable Long id) {
        return battleQueryService.getBattleView(id, currentUserId(jwt), isAdmin(authentication));
    }

    @PreAuthorize("hasAnyRole('USER','ADMIN')")
    @PostMapping("/{id}/turn")
    public TurnResultView turn(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id) {
        return pvpBattleService.applyTurn(currentUserId(jwt), id);
    }

    private Long currentUserId(Jwt jwt) {
        return Long.valueOf(jwt.getSubject());
    }

    private boolean isAdmin(Authentication authentication) {
        return authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(ADMIN_AUTHORITY::equals);
    }
}
