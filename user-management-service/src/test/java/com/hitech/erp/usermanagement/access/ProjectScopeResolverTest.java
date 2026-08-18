package com.hitech.erp.usermanagement.access;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.hitech.erp.usermanagement.security.AuthenticatedUser;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * Guards the rule that a {@code projectId} parameter is a <em>request</em>, not a grant.
 *
 * <p>Before this resolver existed, every module treated it as a display filter: pass one and you
 * got that project, omit it and you got everything. A site member holding {@code VYAPAR:VIEW} could
 * therefore read any project's money by passing its id — or read every project's by passing none.
 * Both directions are covered below, because the second is the one that looks harmless.
 */
@ExtendWith(MockitoExtension.class)
class ProjectScopeResolverTest {

  private static final AuthenticatedUser SITE_MEMBER =
      new AuthenticatedUser(7L, "site@hitech.local", "Site Member", 3L, "Team Member", List.of("VYAPAR:VIEW"));

  @Mock private ObjectProvider<ProjectAccessPort> provider;
  @Mock private ProjectAccessPort port;

  private ProjectScopeResolver resolver;

  @BeforeEach
  void setUp() {
    resolver = new ProjectScopeResolver(provider);
  }

  @AfterEach
  void clearContext() {
    SecurityContextHolder.clearContext();
  }

  private void signIn(AuthenticatedUser user) {
    SecurityContextHolder.getContext()
        .setAuthentication(new UsernamePasswordAuthenticationToken(user, "n/a", List.of()));
  }

  @Nested
  @DisplayName("a member restricted to their own sites")
  class RestrictedMember {

    @BeforeEach
    void restricted() {
      signIn(SITE_MEMBER);
      when(provider.getIfAvailable()).thenReturn(port);
      when(port.seesAllProjects(SITE_MEMBER)).thenReturn(false);
      when(port.accessibleProjectIds(SITE_MEMBER)).thenReturn(List.of(10L, 11L));
    }

    @Test
    @DisplayName("asking for a project they're not on is refused")
    void rejectsForeignProject() {
      assertThatThrownBy(() -> resolver.resolve(99L))
          .isInstanceOf(AccessDeniedException.class)
          .hasMessageContaining("99");
    }

    @Test
    @DisplayName("asking for a project they are on narrows to exactly that project")
    void allowsOwnProject() {
      ProjectScope scope = resolver.resolve(10L);

      assertThat(scope.matches(10L)).isTrue();
      assertThat(scope.matches(11L)).isFalse();
      assertThat(scope.isEverything()).isFalse();
    }

    @Test
    @DisplayName("omitting the filter narrows to their own projects — it does not widen to all")
    void noFilterMeansOwnProjectsOnly() {
      ProjectScope scope = resolver.resolve(null);

      assertThat(scope.isEverything()).isFalse();
      assertThat(scope.matches(10L)).isTrue();
      assertThat(scope.matches(11L)).isTrue();
      assertThat(scope.matches(99L)).isFalse();
    }

    @Test
    @DisplayName("records belonging to no project stay hidden from a site member")
    void untaggedRecordsAreNotVisible() {
      // Office overhead — a purchase booked to the company rather than a site. A site member has
      // no business seeing it, and `null` must not slip through as "matches everything".
      assertThat(resolver.resolve(null).matches(null)).isFalse();
    }

    @Test
    @DisplayName("writes are refused for a project they're not on, and allowed for one they are")
    void writeGuard() {
      assertThatThrownBy(() -> resolver.assertCanWrite(99L)).isInstanceOf(AccessDeniedException.class);
      resolver.assertCanWrite(10L); // no throw
    }
  }

  @Nested
  @DisplayName("an unrestricted user (Super Admin, or Office staff)")
  class Unrestricted {

    private static final AuthenticatedUser ADMIN =
        new AuthenticatedUser(1L, "admin@hitech.local", "Super Admin", 1L, "Super Admin", List.of());

    @BeforeEach
    void unrestricted() {
      signIn(ADMIN);
      when(provider.getIfAvailable()).thenReturn(port);
      when(port.seesAllProjects(ADMIN)).thenReturn(true);
    }

    @Test
    @DisplayName("sees every record, including ones tagged to no project")
    void seesEverything() {
      ProjectScope scope = resolver.resolve(null);

      assertThat(scope.isEverything()).isTrue();
      assertThat(scope.matches(null)).isTrue();
      assertThat(scope.matches(42L)).isTrue();
    }

    @Test
    @DisplayName("can still narrow to one project on request")
    void canFilter() {
      ProjectScope scope = resolver.resolve(42L);

      assertThat(scope.matches(42L)).isTrue();
      assertThat(scope.matches(43L)).isFalse();
    }
  }

  @Test
  @DisplayName("with no project module on the classpath, behaviour is unchanged")
  void degradesWhenPortMissing() {
    // Sibling modules are built and tested in isolation, where project-service isn't present. They
    // must keep working rather than locking every caller out of their own data.
    signIn(SITE_MEMBER);
    when(provider.getIfAvailable()).thenReturn(null);

    assertThat(resolver.resolve(null).isEverything()).isTrue();
    assertThat(resolver.resolve(5L).matches(5L)).isTrue();
  }

  @Test
  @DisplayName("with no authenticated principal, behaviour is unchanged")
  void degradesWhenAnonymous() {
    // Internal calls and bootstrap runners have no principal; they aren't the attack surface, and
    // every externally reachable route is already gated by @PreAuthorize.
    assertThat(resolver.resolve(null).isEverything()).isTrue();
  }

  @Test
  @DisplayName("a null project id is always writable — that's a shared/office record")
  void nullProjectIsWritable() {
    signIn(SITE_MEMBER);
    resolver.assertCanWrite(null); // no throw, no lookup
  }
}
