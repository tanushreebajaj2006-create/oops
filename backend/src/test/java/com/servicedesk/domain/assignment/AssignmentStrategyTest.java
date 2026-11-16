package com.servicedesk.domain.assignment;

import static com.servicedesk.domain.support.TestData.T0;
import static com.servicedesk.domain.support.TestData.skill;
import static com.servicedesk.domain.support.TestData.tech;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.servicedesk.domain.common.StrategyRegistry;
import com.servicedesk.domain.exception.TechnicianUnavailableException;
import com.servicedesk.domain.incident.Category;
import com.servicedesk.domain.incident.Incident;
import com.servicedesk.domain.priority.Priority;
import com.servicedesk.domain.support.TestData;
import com.servicedesk.domain.user.Technician;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Technician assignment strategies")
class AssignmentStrategyTest {

  private final TestData data = new TestData();
  private StrategyRegistry<AssignmentStrategy> registry;
  private TechnicianAssigner assigner;

  // senior network expert, busy
  private final Technician senior = tech("T101", 5, 12, skill("NETWORK", 5));
  // mid-level network engineer, idle
  private final Technician mid = tech("T102", 5, 4, skill("NETWORK", 3));
  // database specialist, idle
  private final Technician dba = tech("T103", 5, 6, skill("DATABASE", 5));
  // junior generalist with a little network knowledge, idle
  private final Technician junior = tech("T104", 5, 1, skill("NETWORK", 2));

  private List<Technician> team;

  @BeforeEach
  void setUp() {
    registry = TechnicianAssigner.defaultStrategies();
    assigner = new TechnicianAssigner(registry);
    senior.takeOn();
    senior.takeOn();
    senior.takeOn(); // 3/5
    team = List.of(senior, mid, dba, junior);
  }

  @Test
  @DisplayName("TC-53 skill-based picks the most proficient, excludes people without the skill")
  void skillBased() {
    Incident i = data.classified(data.network, Priority.P3);
    List<AssignmentCandidate> ranked = assigner.recommend(i, team);
    assertThat(ranked)
        .extracting(AssignmentCandidate::technician)
        .containsExactly(senior, mid, junior);
    assertThat(ranked.get(0).reason()).contains("NETWORK skill 5/5").contains("load 3/5");

    AssignmentDecision d = assigner.assign(i, team, data.system, T0);
    assertThat(d.technician()).isEqualTo(senior);
    assertThat(d.strategy()).isEqualTo("SKILL_BASED");
    assertThat(i.assignee()).contains(senior);
    assertThat(senior.activeWorkload()).isEqualTo(4);
  }

  @Test
  @DisplayName("TC-54 skill-based with no matching specialist fails with TechnicianUnavailable")
  void skillBasedNobody() {
    Incident i = data.classified(new Category("SECURITY", "Security"), Priority.P2);
    assertThatThrownBy(() -> assigner.assign(i, team, data.system, T0))
        .isInstanceOf(TechnicianUnavailableException.class)
        .hasMessageContaining("SKILL_BASED");
    assertThat(i.assignee()).isEmpty();
  }

  @Test
  @DisplayName("TC-55 load-based picks the least utilised technician regardless of skill")
  void loadBased() {
    registry.activate(LoadBasedStrategy.NAME);
    Technician smallTeam = tech("T105", 2, 3, skill("NETWORK", 1));
    smallTeam.takeOn(); // 1/2 = 50%
    mid.takeOn(); // 1/5 = 20%
    Incident i = data.classified(data.network, Priority.P3);
    List<AssignmentCandidate> ranked = assigner.recommend(i, List.of(senior, mid, smallTeam, dba));
    // dba 0%, mid 20%, smallTeam 50%, senior 60%
    assertThat(ranked)
        .extracting(AssignmentCandidate::technician)
        .containsExactly(dba, mid, smallTeam, senior);
  }

  @Test
  @DisplayName("TC-56 round-robin rotates through the team and skips anyone who is full or away")
  void roundRobin() {
    registry.activate(RoundRobinStrategy.NAME);
    junior.markUnavailable();
    List<Technician> picked = new ArrayList<>();
    for (int n = 0; n < 4; n++) {
      Incident i = data.classified(data.network, Priority.P4);
      picked.add(assigner.assign(i, team, data.system, T0).technician());
    }
    // order by id: T101, T102, T103, (T104 unavailable) -> wraps to T101
    assertThat(picked).containsExactly(senior, mid, dba, senior);
    // senior is now 5/5 and must be skipped next time
    Incident next = data.classified(data.network, Priority.P4);
    assertThat(assigner.assign(next, team, data.system, T0).technician()).isEqualTo(mid);
  }

  @Test
  @DisplayName("TC-57 priority-based: P1 goes to the expert, P4 to the least busy specialist")
  void priorityBased() {
    registry.activate(PriorityBasedStrategy.NAME);
    Incident urgent = data.classified(data.network, Priority.P1);
    assertThat(assigner.recommend(urgent, team).get(0).technician()).isEqualTo(senior);

    Incident routine = data.classified(data.network, Priority.P4);
    List<AssignmentCandidate> ranked = assigner.recommend(routine, team);
    // only network people, least loaded first: mid (0/5, T102) and junior (0/5, T104), then senior
    assertThat(ranked)
        .extracting(AssignmentCandidate::technician)
        .containsExactly(mid, junior, senior);
    assertThat(ranked.get(0).reason()).startsWith("P4 routine");
  }

  @Test
  @DisplayName("TC-58 priority-based falls back to anyone free when no specialist is available")
  void priorityBasedFallback() {
    registry.activate(PriorityBasedStrategy.NAME);
    Incident i = data.classified(new Category("SECURITY", "Security"), Priority.P1);
    AssignmentDecision d = assigner.assign(i, team, data.system, T0);
    assertThat(d.technician()).isEqualTo(senior); // most experienced overall
    assertThat(d.reason()).contains("no SECURITY specialist free");
  }

  @Test
  @DisplayName("TC-59 switching strategy changes who is chosen for the same incident")
  void switchingStrategyChangesOutcome() {
    Incident i = data.classified(data.network, Priority.P3);
    assertThat(assigner.recommend(i, team).get(0).technician()).isEqualTo(senior);
    registry.activate("load_based");
    assertThat(assigner.activeStrategy().name()).isEqualTo("LOAD_BASED");
    assertThat(assigner.recommend(i, team).get(0).technician()).isIn(mid, dba, junior);
    assertThat(registry.available())
        .extracting(AssignmentStrategy::name)
        .containsExactly("SKILL_BASED", "LOAD_BASED", "ROUND_ROBIN", "PRIORITY_BASED");
  }

  @Test
  @DisplayName("TC-60 full, unavailable, deactivated and current technicians are never candidates")
  void eligibility() {
    mid.markUnavailable();
    junior.deactivate();
    senior.takeOn();
    senior.takeOn(); // 5/5 full
    Incident i = data.classified(data.network, Priority.P2);
    assertThat(TechnicianAssigner.eligible(i, team)).containsExactly(dba);
    assertThatThrownBy(() -> assigner.assign(i, team, data.system, T0))
        .isInstanceOf(TechnicianUnavailableException.class);

    // re-assignment never offers the current assignee again
    registry.activate(LoadBasedStrategy.NAME);
    Incident j = data.classified(data.database, Priority.P2);
    assigner.assign(j, team, data.system, T0);
    assertThat(j.assignee()).contains(dba);
    assertThat(TechnicianAssigner.eligible(j, team)).doesNotContain(dba);
  }
}
