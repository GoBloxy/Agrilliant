package smartfarm.ui.nav;

import smartfarm.model.User;

/**
 * Process-wide navigation and session state shared between Views.
 *
 * <p>Reads and writes are thread-safe via {@code volatile}. The class holds
 * read-once-on-show data only; for live UI updates use
 * {@link javafx.application.Platform#runLater(Runnable)} after writes from
 * background threads.
 *
 * <p>Phase 2 is expected to add more shared state here (selected plot,
 * selected crop, navigation hints, etc.) as controllers are refactored.
 *
 * TODO(phase-2): add unit tests once Hagag adds JUnit/Surefire to pom.xml.
 */
public final class NavContext {

    private static final NavContext INSTANCE = new NavContext();

    private volatile User currentUser;

    private NavContext() { }

    public static NavContext get() {
        return INSTANCE;
    }

    public User getCurrentUser() {
        return currentUser;
    }

    public void setCurrentUser(User user) {
        this.currentUser = user;
    }

    /** Clear all session-bound state. Call on logout. */
    public void clear() {
        this.currentUser = null;
    }
}
